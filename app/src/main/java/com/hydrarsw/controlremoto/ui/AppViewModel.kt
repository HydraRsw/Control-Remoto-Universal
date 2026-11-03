package com.hydrarsw.controlremoto.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.hydrarsw.controlremoto.androidtv.AndroidTvPairing
import com.hydrarsw.controlremoto.core.ConnectionState
import com.hydrarsw.controlremoto.core.RemoteKey
import com.hydrarsw.controlremoto.core.SavedTv
import com.hydrarsw.controlremoto.core.TvClient
import com.hydrarsw.controlremoto.core.TvClientListener
import com.hydrarsw.controlremoto.core.TvKind
import com.hydrarsw.controlremoto.core.TvStore
import com.hydrarsw.controlremoto.core.createClient
import com.hydrarsw.controlremoto.discovery.FoundTv
import com.hydrarsw.controlremoto.discovery.TvDiscovery
import com.hydrarsw.controlremoto.ir.IrBrand
import com.hydrarsw.controlremoto.ir.IrCatalog
import com.hydrarsw.controlremoto.ir.IrEncoder
import com.hydrarsw.controlremoto.ir.IrTransmitter
import com.hydrarsw.controlremoto.update.Release
import com.hydrarsw.controlremoto.update.Updater
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object IrBrands : Screen
    data class Pairing(val found: FoundTv) : Screen
    data class Remote(val tvId: String) : Screen
}

sealed interface PairingState {
    data object Requesting : PairingState
    data object EnterCode : PairingState
    data object WrongCode : PairingState
    data object Checking : PairingState
    data class Failed(val message: String) : PairingState
}

sealed interface UpdateState {
    data object Idle : UpdateState
    data class NeedsPermission(val release: Release) : UpdateState
    data class Installing(val progress: Float) : UpdateState
    data class Failed(val release: Release) : UpdateState
}

class AppViewModel(application: Application) : AndroidViewModel(application) {
    private val store = TvStore(application)
    private val _tvs = MutableStateFlow(store.load())
    val tvs: StateFlow<List<SavedTv>> = _tvs.asStateFlow()

    private val _screen = MutableStateFlow<Screen>(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    private val _found = MutableStateFlow<List<FoundTv>>(emptyList())
    val found: StateFlow<List<FoundTv>> = _found.asStateFlow()

    private val _connection = MutableStateFlow<ConnectionState>(ConnectionState.Connecting)
    val connection: StateFlow<ConnectionState> = _connection.asStateFlow()

    private val _supportedKeys = MutableStateFlow<Set<RemoteKey>>(emptySet())
    val supportedKeys: StateFlow<Set<RemoteKey>> = _supportedKeys.asStateFlow()

    private val _pairing = MutableStateFlow<PairingState>(PairingState.Requesting)
    val pairing: StateFlow<PairingState> = _pairing.asStateFlow()

    private val _update = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val update: StateFlow<UpdateState> = _update.asStateFlow()

    val hasIrEmitter = IrTransmitter.isAvailable(application)
    val irBrands: List<IrBrand> by lazy { IrCatalog.load(application) }

    private var client: TvClient? = null
    private var pairingSession: AndroidTvPairing? = null
    private val discovery = TvDiscovery(application) { found ->
        _found.update { current -> if (current.any { it.host == found.host }) current else current + found }
    }

    fun refreshDiscovery() {
        discovery.stop()
        _found.value = emptyList()
        discovery.start()
    }

    fun back() {
        closeClient()
        pairingSession?.close()
        pairingSession = null
        _screen.value = Screen.Home
    }

    fun showIrBrands() {
        _screen.value = Screen.IrBrands
    }

    fun addFound(found: FoundTv) {
        val existing = _tvs.value.firstOrNull { it.host == found.host && it.kind == found.kind }
        when {
            existing != null -> openRemote(existing)
            found.kind == TvKind.ANDROID_TV -> startPairing(found)
            else -> saveAndOpen(SavedTv(name = found.name, kind = found.kind, host = found.host))
        }
    }

    private fun startPairing(found: FoundTv) {
        _screen.value = Screen.Pairing(found)
        _pairing.value = PairingState.Requesting
        val session = AndroidTvPairing(found.host, "Control Remoto")
        pairingSession = session
        viewModelScope.launch(Dispatchers.IO) {
            _pairing.value = try {
                session.start()
                PairingState.EnterCode
            } catch (_: Exception) {
                session.close()
                PairingState.Failed("No se pudo conectar. Revisa que la TV esté encendida y en tu Wi-Fi.")
            }
        }
    }

    fun submitPairingCode(found: FoundTv, code: String) {
        val session = pairingSession ?: return
        _pairing.value = PairingState.Checking
        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (session.finish(code)) {
                    session.close()
                    pairingSession = null
                    val tv = SavedTv(
                        name = found.name,
                        kind = TvKind.ANDROID_TV,
                        host = found.host,
                        pinnedCert = session.fingerprint,
                    )
                    launch(Dispatchers.Main) { saveAndOpen(tv) }
                } else {
                    _pairing.value = PairingState.WrongCode
                }
            } catch (_: Exception) {
                session.close()
                _pairing.value = PairingState.Failed("La TV rechazó el código. Intenta de nuevo.")
            }
        }
    }

    fun testIrPower(brand: IrBrand) {
        val function = brand.keys[RemoteKey.POWER.name] ?: return
        val signal = IrEncoder.encode(brand, function)
        viewModelScope.launch(Dispatchers.IO) {
            runCatching {
                IrTransmitter.manager(getApplication())?.transmit(signal.carrierHz, signal.pattern)
            }
        }
    }

    fun addIr(brand: IrBrand) {
        val existing = _tvs.value.firstOrNull { it.kind == TvKind.INFRARED && it.irBrand == brand.id }
        if (existing != null) openRemote(existing)
        else saveAndOpen(SavedTv(name = "TV ${brand.name}", kind = TvKind.INFRARED, irBrand = brand.id))
    }

    private fun saveAndOpen(tv: SavedTv) {
        persist(_tvs.value + tv)
        openRemote(tv)
    }

    fun openRemote(tv: SavedTv) {
        discovery.stop()
        _screen.value = Screen.Remote(tv.id)
        connect(tv)
    }

    fun removeTv(tv: SavedTv) = persist(_tvs.value - tv)

    private fun connect(tv: SavedTv) {
        closeClient()
        _connection.value = ConnectionState.Connecting
        val listener = object : TvClientListener {
            override fun onState(state: ConnectionState) {
                _connection.value = state
            }

            override fun onTvUpdated(update: (SavedTv) -> SavedTv) {
                synchronized(this@AppViewModel) {
                    persist(_tvs.value.map { if (it.id == tv.id) update(it) else it })
                }
            }
        }
        val created = createClient(getApplication(), tv, listener)
        client = created
        _supportedKeys.value = created.supportedKeys
        viewModelScope.launch(Dispatchers.IO) { created.connect() }
    }

    fun retryConnection() {
        val id = (_screen.value as? Screen.Remote)?.tvId ?: return
        _tvs.value.firstOrNull { it.id == id }?.let(::connect)
    }

    fun press(key: RemoteKey) {
        val active = client ?: return
        viewModelScope.launch(Dispatchers.IO) { runCatching { active.send(key) } }
    }

    fun onForeground() {
        if (_screen.value is Screen.Remote && client == null) retryConnection()
        if (_screen.value is Screen.Home) discovery.start()
    }

    fun onBackground() {
        discovery.stop()
        closeClient()
    }

    private fun closeClient() {
        val active = client ?: return
        client = null
        viewModelScope.launch(Dispatchers.IO) { runCatching { active.close() } }
    }

    private fun persist(tvs: List<SavedTv>) {
        _tvs.value = tvs
        store.save(tvs)
    }

    fun checkForUpdate() {
        viewModelScope.launch {
            val release = runCatching { Updater.findUpdate() }.getOrNull() ?: return@launch
            startUpdate(release)
        }
    }

    fun startUpdate(release: Release) {
        val app = getApplication<Application>()
        if (!app.packageManager.canRequestPackageInstalls()) {
            _update.value = UpdateState.NeedsPermission(release)
            return
        }
        _update.value = UpdateState.Installing(0f)
        viewModelScope.launch {
            try {
                Updater.install(app, release) { _update.value = UpdateState.Installing(it) }
                _update.value = UpdateState.Idle
            } catch (_: Exception) {
                _update.value = UpdateState.Failed(release)
            }
        }
    }

    override fun onCleared() {
        discovery.stop()
        closeClient()
    }
}

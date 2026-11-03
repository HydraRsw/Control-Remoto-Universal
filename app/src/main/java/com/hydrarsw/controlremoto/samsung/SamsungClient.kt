package com.hydrarsw.controlremoto.samsung

import android.util.Base64
import com.hydrarsw.controlremoto.core.ConnectionState
import com.hydrarsw.controlremoto.core.RemoteKey
import com.hydrarsw.controlremoto.core.SavedTv
import com.hydrarsw.controlremoto.core.TvClient
import com.hydrarsw.controlremoto.core.TvClientListener
import com.hydrarsw.controlremoto.core.WakeOnLan
import com.hydrarsw.controlremoto.core.hasPinMismatch
import com.hydrarsw.controlremoto.core.pinnedHttpClient
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import kotlin.concurrent.thread

/** API WebSocket de los Samsung Tizen (2016 en adelante). */
class SamsungClient(
    private val tv: SavedTv,
    private val listener: TvClientListener,
) : TvClient {
    override val supportedKeys: Set<RemoteKey> = KEYS.keys

    private var pin = tv.pinnedCert
    private var token = tv.token
    private val http: OkHttpClient = pinnedHttpClient(pin) { _, fingerprint ->
        if (pin.isEmpty()) {
            pin = fingerprint
            listener.onTvUpdated { it.copy(pinnedCert = fingerprint) }
        }
    }
    @Volatile private var socket: WebSocket? = null
    @Volatile private var closed = false

    override fun connect() {
        listener.onState(ConnectionState.Connecting)
        fetchMac()
        open(secure = true)
    }

    private fun open(secure: Boolean) {
        val name = Base64.encodeToString("Control Remoto".toByteArray(), Base64.NO_WRAP)
        val url = buildString {
            append(if (secure) "wss" else "ws")
            append("://${tv.host}:${if (secure) 8002 else 8001}")
            append("/api/v2/channels/samsung.remote.control?name=$name")
            if (secure && token.isNotEmpty()) append("&token=$token")
        }
        socket = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onMessage(webSocket: WebSocket, text: String) {
                val message = runCatching { JSONObject(text) }.getOrNull() ?: return
                when (message.optString("event")) {
                    "ms.channel.connect" -> {
                        val newToken = message.optJSONObject("data")?.optString("token").orEmpty()
                        if (newToken.isNotEmpty() && newToken != token) {
                            token = newToken
                            listener.onTvUpdated { it.copy(token = newToken) }
                        }
                        listener.onState(ConnectionState.Ready)
                    }
                    "ms.channel.unauthorized" -> listener.onState(
                        ConnectionState.Failed("La TV rechazó la conexión. Acepta el aviso en la pantalla."),
                    )
                }
            }

            override fun onOpen(webSocket: WebSocket, response: Response) {
                if (token.isEmpty()) {
                    listener.onState(ConnectionState.WaitingForTv("Acepta la conexión en la pantalla de la TV"))
                }
            }

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                socket = null
                if (!closed) listener.onState(ConnectionState.Failed("La TV cerró la conexión."))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                socket = null
                if (closed) return
                when {
                    t.hasPinMismatch() -> listener.onState(
                        ConnectionState.Failed("El certificado de la TV cambió. Elimínala y agrégala de nuevo."),
                    )
                    secure -> open(secure = false)
                    else -> listener.onState(
                        ConnectionState.Failed("No se pudo conectar. Revisa que la TV esté encendida y en tu Wi-Fi."),
                    )
                }
            }
        })
    }

    private fun fetchMac() {
        if (tv.mac.isNotEmpty()) return
        thread(isDaemon = true) {
            runCatching {
                val request = Request.Builder().url("http://${tv.host}:8001/api/v2/").build()
                http.newCall(request).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    val mac = JSONObject(body).optJSONObject("device")?.optString("wifiMac").orEmpty()
                    if (mac.isNotEmpty()) listener.onTvUpdated { it.copy(mac = mac) }
                }
            }
        }
    }

    override fun send(key: RemoteKey) {
        if (key == RemoteKey.POWER && socket == null) {
            thread(isDaemon = true) { WakeOnLan.send(tv.mac) }
            return
        }
        val command = KEYS[key] ?: return
        val params = JSONObject()
            .put("Cmd", "Click")
            .put("DataOfCmd", command)
            .put("Option", "false")
            .put("TypeOfRemote", "SendRemoteKey")
        socket?.send(JSONObject().put("method", "ms.remote.control").put("params", params).toString())
    }

    override fun close() {
        closed = true
        socket?.close(1000, null)
        http.dispatcher.executorService.shutdown()
    }

    private companion object {
        val KEYS: Map<RemoteKey, String> = buildMap {
            put(RemoteKey.POWER, "KEY_POWER")
            put(RemoteKey.VOL_UP, "KEY_VOLUP")
            put(RemoteKey.VOL_DOWN, "KEY_VOLDOWN")
            put(RemoteKey.MUTE, "KEY_MUTE")
            put(RemoteKey.CH_UP, "KEY_CHUP")
            put(RemoteKey.CH_DOWN, "KEY_CHDOWN")
            put(RemoteKey.UP, "KEY_UP")
            put(RemoteKey.DOWN, "KEY_DOWN")
            put(RemoteKey.LEFT, "KEY_LEFT")
            put(RemoteKey.RIGHT, "KEY_RIGHT")
            put(RemoteKey.OK, "KEY_ENTER")
            put(RemoteKey.BACK, "KEY_RETURN")
            put(RemoteKey.HOME, "KEY_HOME")
            put(RemoteKey.MENU, "KEY_MENU")
            put(RemoteKey.INPUT, "KEY_SOURCE")
            put(RemoteKey.PLAY_PAUSE, "KEY_PLAY")
            put(RemoteKey.STOP, "KEY_STOP")
            put(RemoteKey.REWIND, "KEY_REWIND")
            put(RemoteKey.FORWARD, "KEY_FF")
            (0..9).forEach { put(RemoteKey.valueOf("NUM_$it"), "KEY_$it") }
        }
    }
}

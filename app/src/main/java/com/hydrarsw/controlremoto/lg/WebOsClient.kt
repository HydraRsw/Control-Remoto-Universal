package com.hydrarsw.controlremoto.lg

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
import org.json.JSONArray
import org.json.JSONObject
import kotlin.concurrent.thread

/** Control de LG webOS mediante SSAP. */
class WebOsClient(
    private val tv: SavedTv,
    private val listener: TvClientListener,
) : TvClient {
    override val supportedKeys: Set<RemoteKey> = BUTTONS.keys + RemoteKey.POWER

    private var pin = tv.pinnedCert
    private var clientKey = tv.token
    private val http: OkHttpClient = pinnedHttpClient(pin) { _, fingerprint ->
        if (pin.isEmpty()) {
            pin = fingerprint
            listener.onTvUpdated { it.copy(pinnedCert = fingerprint) }
        }
    }
    @Volatile private var main: WebSocket? = null
    @Volatile private var input: WebSocket? = null
    @Volatile private var closed = false

    override fun connect() {
        listener.onState(ConnectionState.Connecting)
        open(secure = true)
    }

    private fun open(secure: Boolean) {
        val url = if (secure) "wss://${tv.host}:3001" else "ws://${tv.host}:3000"
        main = http.newWebSocket(Request.Builder().url(url).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                webSocket.send(registration())
            }

            override fun onMessage(webSocket: WebSocket, text: String) = handle(webSocket, text)

            override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
                dropConnection()
                if (!closed) listener.onState(ConnectionState.Failed("La TV cerró la conexión."))
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                dropConnection()
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

    private fun handle(webSocket: WebSocket, text: String) {
        val message = runCatching { JSONObject(text) }.getOrNull() ?: return
        val payload = message.optJSONObject("payload")
        when {
            message.optString("type") == "registered" -> {
                val key = payload?.optString("client-key").orEmpty()
                if (key.isNotEmpty() && key != clientKey) {
                    clientKey = key
                    listener.onTvUpdated { it.copy(token = key) }
                }
                webSocket.send(request(ID_INPUT, "ssap://com.webos.service.networkinput/getPointerInputSocket"))
                if (tv.mac.isEmpty()) {
                    webSocket.send(request(ID_NETWORK, "ssap://com.webos.service.connectionmanager/getinfo"))
                }
            }
            message.optString("id") == ID_REGISTER && message.optString("type") == "error" ->
                listener.onState(ConnectionState.Failed("La TV rechazó la conexión."))
            message.optString("id") == ID_REGISTER && payload?.optString("pairingType") == "PROMPT" ->
                listener.onState(ConnectionState.WaitingForTv("Acepta la conexión en la pantalla de la TV"))
            message.optString("id") == ID_INPUT -> payload?.optString("socketPath")?.let(::openInput)
            message.optString("id") == ID_NETWORK -> saveMac(payload)
        }
    }

    private fun openInput(path: String) {
        if (path.isEmpty()) return
        input = http.newWebSocket(Request.Builder().url(path).build(), object : WebSocketListener() {
            override fun onOpen(webSocket: WebSocket, response: Response) {
                listener.onState(ConnectionState.Ready)
            }

            override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
                dropConnection()
                if (!closed) listener.onState(ConnectionState.Failed("Se perdió la conexión con la TV."))
            }
        })
    }

    private fun dropConnection() {
        main = null
        input = null
    }

    private fun saveMac(payload: JSONObject?) {
        val mac = sequenceOf("wiredInfo", "wifiInfo")
            .mapNotNull { payload?.optJSONObject(it)?.optString("macAddress") }
            .firstOrNull { it.isNotEmpty() } ?: return
        listener.onTvUpdated { it.copy(mac = mac) }
    }

    override fun send(key: RemoteKey) {
        if (key == RemoteKey.POWER) {
            val socket = main
            if (socket != null && input != null) {
                socket.send(request("power", "ssap://system/turnOff"))
            } else {
                thread(isDaemon = true) { WakeOnLan.send(tv.mac) }
            }
            return
        }
        val button = BUTTONS[key] ?: return
        input?.send("type:button\nname:$button\n\n")
    }

    override fun close() {
        closed = true
        input?.close(1000, null)
        main?.close(1000, null)
        http.dispatcher.executorService.shutdown()
    }

    private fun request(id: String, uri: String) =
        JSONObject().put("id", id).put("type", "request").put("uri", uri).put("payload", JSONObject()).toString()

    private fun registration(): String {
        val manifest = JSONObject()
            .put("appVersion", "1.1")
            .put("manifestVersion", 1)
            .put("permissions", JSONArray(PERMISSIONS))
        val payload = JSONObject()
            .put("forcePairing", false)
            .put("pairingType", "PROMPT")
            .put("manifest", manifest)
        if (clientKey.isNotEmpty()) payload.put("client-key", clientKey)
        return JSONObject().put("type", "register").put("id", ID_REGISTER).put("payload", payload).toString()
    }

    private companion object {
        const val ID_REGISTER = "register_0"
        const val ID_INPUT = "input_socket"
        const val ID_NETWORK = "network_info"

        val PERMISSIONS = listOf(
            "CONTROL_AUDIO",
            "CONTROL_DISPLAY",
            "CONTROL_INPUT_JOYSTICK",
            "CONTROL_INPUT_MEDIA_PLAYBACK",
            "CONTROL_INPUT_TV",
            "CONTROL_MOUSE_AND_KEYBOARD",
            "CONTROL_POWER",
            "READ_NETWORK_STATE",
            "READ_POWER_STATE",
        )

        val BUTTONS: Map<RemoteKey, String> = buildMap {
            put(RemoteKey.VOL_UP, "VOLUMEUP")
            put(RemoteKey.VOL_DOWN, "VOLUMEDOWN")
            put(RemoteKey.MUTE, "MUTE")
            put(RemoteKey.CH_UP, "CHANNELUP")
            put(RemoteKey.CH_DOWN, "CHANNELDOWN")
            put(RemoteKey.UP, "UP")
            put(RemoteKey.DOWN, "DOWN")
            put(RemoteKey.LEFT, "LEFT")
            put(RemoteKey.RIGHT, "RIGHT")
            put(RemoteKey.OK, "ENTER")
            put(RemoteKey.BACK, "BACK")
            put(RemoteKey.HOME, "HOME")
            put(RemoteKey.MENU, "MENU")
            put(RemoteKey.INPUT, "INPUT_HUB")
            put(RemoteKey.PLAY_PAUSE, "PLAY")
            put(RemoteKey.STOP, "STOP")
            put(RemoteKey.REWIND, "REWIND")
            put(RemoteKey.FORWARD, "FASTFORWARD")
            (0..9).forEach { put(RemoteKey.valueOf("NUM_$it"), "$it") }
        }
    }
}

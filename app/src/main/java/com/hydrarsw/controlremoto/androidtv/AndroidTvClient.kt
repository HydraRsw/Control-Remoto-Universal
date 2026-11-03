package com.hydrarsw.controlremoto.androidtv

import com.hydrarsw.controlremoto.core.ConnectionState
import com.hydrarsw.controlremoto.core.PinnedTrustManager
import com.hydrarsw.controlremoto.core.RemoteKey
import com.hydrarsw.controlremoto.core.SavedTv
import com.hydrarsw.controlremoto.core.TvClient
import com.hydrarsw.controlremoto.core.TvClientListener
import com.hydrarsw.controlremoto.core.hasPinMismatch
import java.net.InetSocketAddress
import java.util.concurrent.Executors
import javax.net.ssl.SSLException
import javax.net.ssl.SSLSocket
import kotlin.concurrent.thread

class AndroidTvClient(
    private val tv: SavedTv,
    private val listener: TvClientListener,
) : TvClient {
    override val supportedKeys: Set<RemoteKey> = AndroidTvProtocol.KEY_CODES.keys

    private val writer = Executors.newSingleThreadExecutor()
    @Volatile private var socket: SSLSocket? = null
    @Volatile private var closed = false

    override fun connect() {
        listener.onState(ConnectionState.Connecting)
        thread(name = "android-tv", isDaemon = true) {
            try {
                readLoop()
            } catch (error: Exception) {
                if (!closed) listener.onState(ConnectionState.Failed(describe(error)))
            }
        }
    }

    private fun readLoop() {
        val trust = PinnedTrustManager(tv.pinnedCert) { _, _ -> }
        val ssl = AndroidTvIdentity.sslContext(trust).socketFactory.createSocket() as SSLSocket
        socket = ssl
        ssl.soTimeout = READ_TIMEOUT_MS
        ssl.connect(InetSocketAddress(tv.host, AndroidTvProtocol.REMOTE_PORT), CONNECT_TIMEOUT_MS)
        ssl.startHandshake()
        var features = AndroidTvProtocol.FEATURES
        while (!closed) {
            val message = ProtoMessage.parse(ssl.inputStream.readFrame())
            when {
                message.has(FIELD_CONFIGURE) -> {
                    features = features and (message.message(FIELD_CONFIGURE)?.int(1) ?: features)
                    write(AndroidTvProtocol.configureReply(features))
                }
                message.has(FIELD_SET_ACTIVE) -> write(AndroidTvProtocol.setActive(features))
                message.has(FIELD_PING) ->
                    write(AndroidTvProtocol.pingResponse(message.message(FIELD_PING)?.int(1) ?: 0))
                message.has(FIELD_START) -> listener.onState(ConnectionState.Ready)
            }
        }
    }

    override fun send(key: RemoteKey) {
        val code = AndroidTvProtocol.KEY_CODES[key] ?: return
        write(AndroidTvProtocol.keyPress(code))
    }

    private fun write(message: ByteArray) {
        if (writer.isShutdown) return
        writer.execute {
            runCatching {
                socket?.outputStream?.apply {
                    write(framed(message))
                    flush()
                }
            }
        }
    }

    override fun close() {
        closed = true
        writer.shutdown()
        runCatching { socket?.close() }
    }

    private fun describe(error: Exception): String = when {
        error.hasPinMismatch() -> "El certificado de la TV cambió. Elimínala y vuelve a emparejarla."
        error is SSLException -> "La TV no reconoce este celular. Elimínala y vuelve a emparejarla."
        else -> "No se pudo conectar. Revisa que la TV esté encendida y en tu Wi-Fi."
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 6_000
        // La TV envía un ping cada 5 s; varios ausentes significan conexión perdida.
        const val READ_TIMEOUT_MS = 16_000
        const val FIELD_CONFIGURE = 1
        const val FIELD_SET_ACTIVE = 2
        const val FIELD_PING = 8
        const val FIELD_START = 40
    }
}

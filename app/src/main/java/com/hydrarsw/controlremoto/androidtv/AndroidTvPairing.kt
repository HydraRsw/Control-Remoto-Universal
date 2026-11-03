package com.hydrarsw.controlremoto.androidtv

import com.hydrarsw.controlremoto.core.PinnedTrustManager
import java.io.IOException
import java.net.InetSocketAddress
import java.security.cert.X509Certificate
import java.security.interfaces.RSAPublicKey
import javax.net.ssl.SSLSocket

/** Emparejamiento en dos pasos: [start] pide a la TV mostrar un código y [finish] lo confirma. */
class AndroidTvPairing(private val host: String, private val clientName: String) {
    private var socket: SSLSocket? = null
    private var serverCert: X509Certificate? = null
    var fingerprint: String = ""
        private set

    fun start() {
        val trust = PinnedTrustManager("") { certificate, hash ->
            serverCert = certificate
            fingerprint = hash
        }
        val ssl = AndroidTvIdentity.sslContext(trust).socketFactory.createSocket() as SSLSocket
        socket = ssl
        ssl.soTimeout = TIMEOUT_MS
        ssl.connect(InetSocketAddress(host, AndroidTvProtocol.PAIRING_PORT), TIMEOUT_MS)
        ssl.startHandshake()
        exchange(AndroidTvProtocol.pairingRequest(clientName), FIELD_REQUEST_ACK)
        exchange(AndroidTvProtocol.pairingOptions(), FIELD_OPTIONS)
        exchange(AndroidTvProtocol.pairingConfiguration(), FIELD_CONFIGURATION_ACK)
    }

    /** Devuelve false si el código no es válido, y permite volver a intentarlo. */
    fun finish(code: String): Boolean {
        val client = AndroidTvIdentity.certificate().publicKey as RSAPublicKey
        val server = serverCert?.publicKey as? RSAPublicKey ?: throw IOException("TV sin clave RSA")
        val secret = AndroidTvProtocol.computeSecret(
            code.trim().uppercase(),
            client.modulus,
            client.publicExponent,
            server.modulus,
            server.publicExponent,
        ) ?: return false
        exchange(AndroidTvProtocol.pairingSecret(secret), FIELD_SECRET_ACK)
        return true
    }

    fun close() {
        runCatching { socket?.close() }
    }

    private fun exchange(message: ByteArray, expected: Int) {
        val ssl = socket ?: throw IOException("Sin conexión")
        ssl.outputStream.apply {
            write(framed(message))
            flush()
        }
        val reply = ProtoMessage.parse(ssl.inputStream.readFrame())
        if (reply.int(2) != 200) throw IOException("La TV rechazó el emparejamiento (${reply.int(2)})")
        if (!reply.has(expected)) throw IOException("Respuesta inesperada de la TV")
    }

    private companion object {
        const val TIMEOUT_MS = 10_000
        const val FIELD_REQUEST_ACK = 11
        const val FIELD_OPTIONS = 20
        const val FIELD_CONFIGURATION_ACK = 31
        const val FIELD_SECRET_ACK = 41
    }
}

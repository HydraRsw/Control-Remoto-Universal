package com.hydrarsw.controlremoto.core

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.X509TrustManager

/**
 * Las TV usan certificados autofirmados, así que la validación clásica no sirve.
 * La primera conexión guarda la huella del certificado y las siguientes exigen la misma.
 */
class PinnedTrustManager(
    private val expectedSha256: String,
    private val onSeen: (X509Certificate, String) -> Unit,
) : X509TrustManager {

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) =
        throw CertificateException("No se aceptan clientes")

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull() ?: throw CertificateException("Sin certificado")
        val fingerprint = sha256(leaf.encoded)
        if (expectedSha256.isNotEmpty() && expectedSha256 != fingerprint) {
            throw PinMismatchException()
        }
        onSeen(leaf, fingerprint)
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()

    companion object {
        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}

class PinMismatchException : CertificateException("El certificado de la TV cambió")

fun Throwable.hasPinMismatch(): Boolean =
    generateSequence(this) { it.cause }.any { it is PinMismatchException }

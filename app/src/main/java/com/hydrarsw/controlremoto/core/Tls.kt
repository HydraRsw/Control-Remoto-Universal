package com.hydrarsw.controlremoto.core

import okhttp3.OkHttpClient
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLContext

fun pinnedHttpClient(pin: String, onSeen: (X509Certificate, String) -> Unit): OkHttpClient {
    val trust = PinnedTrustManager(pin, onSeen)
    val context = SSLContext.getInstance("TLS").apply { init(null, arrayOf(trust), SecureRandom()) }
    return OkHttpClient.Builder()
        .sslSocketFactory(context.socketFactory, trust)
        // La identidad de la TV la garantiza el pin, no el nombre del certificado.
        .hostnameVerifier { _, _ -> true }
        .connectTimeout(6, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .pingInterval(20, TimeUnit.SECONDS)
        .build()
}

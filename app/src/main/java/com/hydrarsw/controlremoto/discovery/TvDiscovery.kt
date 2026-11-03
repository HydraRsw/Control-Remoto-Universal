package com.hydrarsw.controlremoto.discovery

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.net.wifi.WifiManager
import com.hydrarsw.controlremoto.core.TvKind
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketTimeoutException
import kotlin.concurrent.thread

data class FoundTv(val name: String, val host: String, val kind: TvKind)

/** Busca televisiones en la red con mDNS (Android TV) y SSDP (Samsung y LG). */
class TvDiscovery(context: Context, private val onFound: (FoundTv) -> Unit) {
    private val app = context.applicationContext
    private val nsd = app.getSystemService(Context.NSD_SERVICE) as NsdManager
    private val resolveQueue = ArrayDeque<NsdServiceInfo>()
    private var resolving = false
    private var browsing: NsdManager.DiscoveryListener? = null
    @Volatile private var running = false

    fun start() {
        if (running) return
        running = true
        startMdns()
        thread(name = "ssdp", isDaemon = true) { runCatching { searchSsdp() } }
    }

    fun stop() {
        running = false
        browsing?.let { runCatching { nsd.stopServiceDiscovery(it) } }
        browsing = null
    }

    private fun startMdns() {
        val listener = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceLost(serviceInfo: NsdServiceInfo) = Unit
            override fun onServiceFound(serviceInfo: NsdServiceInfo) {
                enqueueResolve(serviceInfo)
            }
        }
        browsing = listener
        runCatching { nsd.discoverServices("_androidtvremote2._tcp", NsdManager.PROTOCOL_DNS_SD, listener) }
    }

    // NsdManager solo admite una resolución a la vez en varias versiones de Android.
    @Synchronized
    private fun enqueueResolve(info: NsdServiceInfo) {
        resolveQueue.addLast(info)
        resolveNext()
    }

    @Synchronized
    private fun resolveNext() {
        if (resolving) return
        val next = resolveQueue.removeFirstOrNull() ?: return
        resolving = true
        @Suppress("DEPRECATION")
        nsd.resolveService(next, object : NsdManager.ResolveListener {
            override fun onResolveFailed(serviceInfo: NsdServiceInfo, errorCode: Int) = finished()

            override fun onServiceResolved(serviceInfo: NsdServiceInfo) {
                @Suppress("DEPRECATION")
                val host = serviceInfo.host?.hostAddress
                if (host != null) onFound(FoundTv(serviceInfo.serviceName, host, TvKind.ANDROID_TV))
                finished()
            }

            private fun finished() {
                synchronized(this@TvDiscovery) { resolving = false }
                resolveNext()
            }
        })
    }

    private fun searchSsdp() {
        val wifi = app.getSystemService(Context.WIFI_SERVICE) as WifiManager
        val lock = wifi.createMulticastLock("tv-discovery").apply { acquire() }
        try {
            DatagramSocket().use { socket ->
                socket.soTimeout = 1_000
                val group = InetAddress.getByName("239.255.255.250")
                SEARCHES.keys.forEach { target ->
                    val message = "M-SEARCH * HTTP/1.1\r\nHOST: 239.255.255.250:1900\r\n" +
                        "MAN: \"ssdp:discover\"\r\nMX: 2\r\nST: $target\r\n\r\n"
                    val bytes = message.toByteArray()
                    socket.send(DatagramPacket(bytes, bytes.size, group, 1900))
                }
                val deadline = System.currentTimeMillis() + 5_000
                val buffer = ByteArray(2048)
                while (running && System.currentTimeMillis() < deadline) {
                    val packet = DatagramPacket(buffer, buffer.size)
                    try {
                        socket.receive(packet)
                    } catch (_: SocketTimeoutException) {
                        continue
                    }
                    val text = String(packet.data, 0, packet.length)
                    val kind = SEARCHES.entries.firstOrNull { text.contains(it.key, ignoreCase = true) }?.value
                        ?: continue
                    onFound(FoundTv(kind.label, packet.address.hostAddress.orEmpty(), kind))
                }
            }
        } finally {
            lock.release()
        }
    }

    private companion object {
        val SEARCHES = mapOf(
            "urn:samsung.com:device:RemoteControlReceiver:1" to TvKind.SAMSUNG,
            "urn:lge-com:service:webos-second-screen:1" to TvKind.LG,
        )
    }
}

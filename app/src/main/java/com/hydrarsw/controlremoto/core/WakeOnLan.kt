package com.hydrarsw.controlremoto.core

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

object WakeOnLan {
    fun magicPacket(mac: String): ByteArray? {
        val parts = mac.split(':', '-')
        if (parts.size != 6) return null
        val macBytes = parts.map { it.toIntOrNull(16)?.toByte() ?: return null }.toByteArray()
        return ByteArray(6) { 0xFF.toByte() } + List(16) { macBytes }.reduce(ByteArray::plus)
    }

    /** Debe llamarse fuera del hilo principal. */
    fun send(mac: String): Boolean {
        val packet = magicPacket(mac) ?: return false
        return runCatching {
            DatagramSocket().use { socket ->
                socket.broadcast = true
                val address = InetAddress.getByName("255.255.255.255")
                repeat(3) { socket.send(DatagramPacket(packet, packet.size, address, 9)) }
            }
        }.isSuccess
    }
}

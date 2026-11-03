package com.hydrarsw.controlremoto.androidtv

import com.hydrarsw.controlremoto.core.RemoteKey
import java.math.BigInteger
import java.security.MessageDigest

/** Mensajes de Android TV Remote v2 (polo.proto y remotemessage.proto). */
object AndroidTvProtocol {
    const val PAIRING_PORT = 6467
    const val REMOTE_PORT = 6466

    private const val STATUS_OK = 200
    private const val ROLE_INPUT = 1
    private const val ENCODING_HEX = 3
    private const val SYMBOL_LENGTH = 6
    private const val DIRECTION_SHORT = 3

    // PING | KEY | POWER | VOLUME | APP_LINK
    const val FEATURES = 1 or 2 or 32 or 64 or 512

    private fun outer(body: ProtoWriter.() -> Unit) =
        ProtoWriter().varint(1, 2).varint(2, STATUS_OK).apply(body).toByteArray()

    fun pairingRequest(clientName: String) = outer {
        message(10) {
            string(1, "atvremote")
            string(2, clientName)
        }
    }

    fun pairingOptions() = outer {
        message(20) {
            message(1) {
                varint(1, ENCODING_HEX)
                varint(2, SYMBOL_LENGTH)
            }
            varint(3, ROLE_INPUT)
        }
    }

    fun pairingConfiguration() = outer {
        message(30) {
            message(1) {
                varint(1, ENCODING_HEX)
                varint(2, SYMBOL_LENGTH)
            }
            varint(2, ROLE_INPUT)
        }
    }

    fun pairingSecret(secret: ByteArray) = outer { message(40) { bytes(1, secret) } }

    /** Devuelve el secreto, o null si el código no corresponde a esta sesión. */
    fun computeSecret(
        code: String,
        clientModulus: BigInteger,
        clientExponent: BigInteger,
        serverModulus: BigInteger,
        serverExponent: BigInteger,
    ): ByteArray? {
        if (code.length != 6) return null
        val codeBytes = runCatching { hexBytes(code) }.getOrNull() ?: return null
        val digest = MessageDigest.getInstance("SHA-256")
        digest.update(unsigned(clientModulus))
        digest.update(hexBytes("0" + clientExponent.toString(16)))
        digest.update(unsigned(serverModulus))
        digest.update(hexBytes("0" + serverExponent.toString(16)))
        digest.update(codeBytes, 1, 2)
        return digest.digest().takeIf { it[0] == codeBytes[0] }
    }

    private fun unsigned(value: BigInteger): ByteArray = hexBytes(value.toString(16))

    private fun hexBytes(text: String): ByteArray {
        val hex = if (text.length % 2 == 0) text else "0$text"
        return ByteArray(hex.length / 2) { hex.substring(it * 2, it * 2 + 2).toInt(16).toByte() }
    }

    fun configureReply(features: Int) = ProtoWriter().message(1) {
        varint(1, features)
        message(2) {
            varint(3, 1)
            string(4, "1")
            string(5, "atvremote")
            string(6, "1.0.0")
        }
    }.toByteArray()

    fun setActive(features: Int) = ProtoWriter().message(2) { varint(1, features) }.toByteArray()

    fun pingResponse(value: Int) = ProtoWriter().message(9) { varint(1, value) }.toByteArray()

    fun keyPress(code: Int) = ProtoWriter().message(10) {
        varint(1, code)
        varint(2, DIRECTION_SHORT)
    }.toByteArray()

    val KEY_CODES: Map<RemoteKey, Int> = buildMap {
        put(RemoteKey.POWER, 26)
        put(RemoteKey.VOL_UP, 24)
        put(RemoteKey.VOL_DOWN, 25)
        put(RemoteKey.MUTE, 164)
        put(RemoteKey.CH_UP, 166)
        put(RemoteKey.CH_DOWN, 167)
        put(RemoteKey.UP, 19)
        put(RemoteKey.DOWN, 20)
        put(RemoteKey.LEFT, 21)
        put(RemoteKey.RIGHT, 22)
        put(RemoteKey.OK, 23)
        put(RemoteKey.BACK, 4)
        put(RemoteKey.HOME, 3)
        put(RemoteKey.MENU, 82)
        put(RemoteKey.INPUT, 178)
        put(RemoteKey.PLAY_PAUSE, 85)
        put(RemoteKey.STOP, 86)
        put(RemoteKey.REWIND, 89)
        put(RemoteKey.FORWARD, 90)
        (0..9).forEach { put(RemoteKey.valueOf("NUM_$it"), 7 + it) }
    }
}

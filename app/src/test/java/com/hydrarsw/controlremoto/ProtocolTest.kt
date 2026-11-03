package com.hydrarsw.controlremoto

import com.hydrarsw.controlremoto.androidtv.AndroidTvProtocol
import com.hydrarsw.controlremoto.androidtv.ProtoMessage
import com.hydrarsw.controlremoto.androidtv.framed
import com.hydrarsw.controlremoto.androidtv.readFrame
import com.hydrarsw.controlremoto.core.WakeOnLan
import com.hydrarsw.controlremoto.ir.IrBrand
import com.hydrarsw.controlremoto.ir.IrEncoder
import com.hydrarsw.controlremoto.update.ReleaseParser
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.math.BigInteger
import java.security.MessageDigest

class ProtocolTest {
    private fun hex(text: String) = text.chunked(2).map { it.toInt(16).toByte() }.toByteArray()

    @Test fun pairingMessagesMatchProtoDefinition() {
        val name = "Control Remoto".toByteArray().joinToString("") { "%02x".format(it) }
        val service = "atvremote".toByteArray().joinToString("") { "%02x".format(it) }
        assertArrayEquals(
            hex("080210c801521b0a09${service}120e$name"),
            AndroidTvProtocol.pairingRequest("Control Remoto"),
        )
        assertArrayEquals(hex("080210c801a201080a04080310061801"), AndroidTvProtocol.pairingOptions())
        assertArrayEquals(hex("080210c801f201080a04080310061001"), AndroidTvProtocol.pairingConfiguration())
        assertArrayEquals(hex("080210c801c202060a0400010203"), AndroidTvProtocol.pairingSecret(hex("00010203")))
    }

    @Test fun remoteMessagesMatchProtoDefinition() {
        assertArrayEquals(hex("120308e304"), AndroidTvProtocol.setActive(611))
        assertArrayEquals(hex("4a020807"), AndroidTvProtocol.pingResponse(7))
        assertArrayEquals(hex("5204081a1003"), AndroidTvProtocol.keyPress(26))
    }

    @Test fun frameRoundTrip() {
        val payload = ByteArray(300) { it.toByte() }
        assertArrayEquals(payload, ByteArrayInputStream(framed(payload)).readFrame())
    }

    @Test fun keyPressHasCodeAndShortDirection() {
        val inject = ProtoMessage.parse(AndroidTvProtocol.keyPress(26)).message(10)!!
        assertEquals(26, inject.int(1))
        assertEquals(3, inject.int(2))
    }

    @Test fun secretRequiresMatchingCodeHash() {
        val clientModulus = BigInteger("C0FFEE1234", 16)
        val serverModulus = BigInteger("BADC0DE123", 16)
        val exponent = BigInteger("10001", 16)
        val digest = MessageDigest.getInstance("SHA-256")
        listOf("C0FFEE1234", "010001", "BADC0DE123", "010001", "ABCD").forEach { digest.update(hex(it)) }
        val expected = digest.digest()
        val code = "%02X".format(expected[0]) + "ABCD"
        assertArrayEquals(
            expected,
            AndroidTvProtocol.computeSecret(code, clientModulus, exponent, serverModulus, exponent),
        )
        val wrong = "%02X".format((expected[0] + 1) and 0xFF) + "ABCD"
        assertNull(AndroidTvProtocol.computeSecret(wrong, clientModulus, exponent, serverModulus, exponent))
    }

    private fun necValue(pattern: IntArray, leadMark: Int): Long {
        assertEquals(leadMark, pattern[0])
        var value = 0L
        for (i in 0 until 32) value = (value shl 1) or (if (pattern[3 + i * 2] > 1000) 1L else 0L)
        return value
    }

    @Test fun lgPowerMatchesKnownNecCode() {
        val brand = IrBrand("lg", "LG", "NEC1", 4, -1, emptyMap())
        val signal = IrEncoder.encode(brand, 8)
        assertEquals(0x20DF10EFL, necValue(signal.pattern, 9024))
    }

    @Test fun samsungPowerMatchesKnownCode() {
        val brand = IrBrand("samsung", "Samsung", "NECx2", 7, 7, emptyMap())
        val signal = IrEncoder.encode(brand, 2)
        assertEquals(0xE0E040BFL, necValue(signal.pattern, 4512))
    }

    @Test fun sonyPowerSendsThreeFramesOf45ms() {
        val signal = IrEncoder.encode(IrBrand("sony", "Sony", "Sony12", 1, -1, emptyMap()), 21)
        assertEquals(26 * 3, signal.pattern.size)
        assertEquals(45_000 * 3, signal.pattern.sum())
        assertEquals(1200, signal.pattern[2])
        assertEquals(600, signal.pattern[4])
    }

    @Test fun rcaPowerMatchesKnownCode() {
        val signal = IrEncoder.encode(IrBrand("tcl", "TCL", "RCA-38", 15, -1, emptyMap()), 42)
        var value = 0L
        for (i in 0 until 24) value = (value shl 1) or (if (signal.pattern[3 + i * 2] > 1500) 1L else 0L)
        assertEquals(0xF2A0D5L, value)
    }

    @Test fun panasonicPowerMatchesKnownCode() {
        val signal = IrEncoder.encode(IrBrand("p", "Panasonic", "Panasonic", 128, 0, emptyMap()), 61)
        val bytes = (0 until 6).map { byte ->
            (0 until 8).sumOf { bit ->
                if (signal.pattern[3 + (byte * 8 + bit) * 2] > 800) 1 shl bit else 0
            }
        }
        assertEquals(listOf(0x02, 0x20, 0x80, 0x00, 0x3D, 0xBD), bytes)
    }

    @Test fun rc5PowerIsManchesterEncoded() {
        val signal = IrEncoder.encode(IrBrand("p", "Philips", "RC5", 0, -1, emptyMap()), 12, rc5Toggle = false)
        // Bits 1 1 0 00000 001100: el total útil dura 14 bits menos el primer medio bit.
        assertEquals(114_000, signal.pattern.sum())
        assertEquals(0, signal.pattern.size % 2)
        assertEquals(889, signal.pattern[0])
        assertEquals(889, signal.pattern[1])
    }

    @Test fun releaseParserReadsVersionAndApk() {
        val json = """{"tag_name":"v1.42","assets":[
            {"name":"notas.txt","url":"x","size":1},
            {"name":"control-remoto.apk","url":"https://api.github.com/asset/9","size":2048}]}"""
        val release = ReleaseParser.parse(json)!!
        assertEquals(42, release.versionCode)
        assertEquals("https://api.github.com/asset/9", release.assetUrl)
        assertNull(ReleaseParser.parse("""{"tag_name":"v1.43","assets":[]}"""))
    }

    @Test fun magicPacketHasPrefixAndSixteenCopies() {
        val packet = WakeOnLan.magicPacket("AA:BB:CC:DD:EE:FF")!!
        assertEquals(102, packet.size)
        assertEquals(0xFF.toByte(), packet[0])
        assertEquals(0xAA.toByte(), packet[6])
        assertEquals(0xFF.toByte(), packet[101])
        assertNull(WakeOnLan.magicPacket("no-es-mac"))
    }
}

package com.hydrarsw.controlremoto.ir

import kotlin.math.roundToInt

data class IrSignal(val carrierHz: Int, val pattern: IntArray)

data class IrBrand(
    val id: String,
    val name: String,
    val protocol: String,
    val device: Int,
    val subdevice: Int,
    val keys: Map<String, Int>,
)

/** Convierte (protocolo, dispositivo, función) en la señal que emite el LED infrarrojo. */
object IrEncoder {
    fun encode(brand: IrBrand, function: Int, rc5Toggle: Boolean = false): IrSignal {
        val d = brand.device
        val s = if (brand.subdevice >= 0) brand.subdevice else d.inv() and 0xFF
        return when (brand.protocol) {
            "NEC1" -> nec(d, s, function, leadMark = 16)
            "NECx2" -> nec(d, s, function, leadMark = 8)
            "Sony12" -> sony12(d, function)
            "Panasonic" -> panasonic(d, brand.subdevice.coerceAtLeast(0), function)
            "RC5" -> rc5(d, function, rc5Toggle)
            "RCA-38" -> rca38(d, function)
            else -> throw IllegalArgumentException("Protocolo no soportado: ${brand.protocol}")
        }
    }

    private class Frame(val unit: Double) {
        val durations = ArrayList<Int>()
        fun pair(on: Double, off: Double) {
            durations += (on * unit).roundToInt()
            durations += (off * unit).roundToInt()
        }
        fun lsb(value: Int, bits: Int, zero: Double, one: Double, mark: Double = 1.0) {
            for (i in 0 until bits) pair(mark, if ((value shr i) and 1 == 1) one else zero)
        }
    }

    // {38.4k,564}<1,-1|1,-3>(lead,-8,D:8,S:8,F:8,~F:8,1,^108m)
    private fun nec(d: Int, s: Int, f: Int, leadMark: Int): IrSignal {
        val frame = Frame(564.0)
        frame.pair(leadMark.toDouble(), 8.0)
        frame.lsb(d, 8, 1.0, 3.0)
        frame.lsb(s, 8, 1.0, 3.0)
        frame.lsb(f, 8, 1.0, 3.0)
        frame.lsb(f.inv() and 0xFF, 8, 1.0, 3.0)
        val used = frame.durations.sum() + 564
        frame.durations += 564
        frame.durations += (108_000 - used).coerceAtLeast(564)
        return IrSignal(38_400, frame.durations.toIntArray())
    }

    // {40k,600}<1,-1|2,-1>(4,-1,F:7,D:5,^45m)+ — el receptor exige tres repeticiones.
    private fun sony12(d: Int, f: Int): IrSignal {
        val once = ArrayList<Int>()
        val unit = 600
        once += 4 * unit
        once += unit
        for (i in 0 until 7) {
            once += if ((f shr i) and 1 == 1) 2 * unit else unit
            once += unit
        }
        for (i in 0 until 5) {
            once += if ((d shr i) and 1 == 1) 2 * unit else unit
            once += unit
        }
        once[once.lastIndex] = 45_000 - once.dropLast(1).sum()
        return IrSignal(40_000, (once + once + once).toIntArray())
    }

    // {37k,432}<1,-1|1,-3>(8,-4,2:8,32:8,D:8,S:8,F:8,(D^S^F):8,1,-173)
    private fun panasonic(d: Int, s: Int, f: Int): IrSignal {
        val frame = Frame(432.0)
        frame.pair(8.0, 4.0)
        for (byte in listOf(2, 32, d, s, f, d xor s xor f)) frame.lsb(byte, 8, 1.0, 3.0)
        frame.pair(1.0, 173.0)
        return IrSignal(37_000, frame.durations.toIntArray())
    }

    // {36k,msb,889}<1,-1|-1,1>(1,~F:1:6,T:1,D:5,F:6,^114m) — codificación Manchester.
    private fun rc5(d: Int, f: Int, toggle: Boolean): IrSignal {
        val bits = ArrayList<Boolean>()
        bits += true
        bits += (f and 0x40) == 0
        bits += toggle
        for (i in 4 downTo 0) bits += (d shr i) and 1 == 1
        for (i in 5 downTo 0) bits += (f shr i) and 1 == 1
        // Secuencia de medios bits: 1 = bajo→alto, 0 = alto→bajo.
        val levels = bits.flatMap { if (it) listOf(false, true) else listOf(true, false) }
        val pattern = ArrayList<Int>()
        var current = true
        var run = 0
        for (level in levels.dropWhile { !it }) {
            if (level == current) {
                run++
            } else {
                pattern += run * 889
                current = level
                run = 1
            }
        }
        pattern += run * 889
        if (pattern.size % 2 == 1) {
            pattern += 114_000 - pattern.sum()
        } else {
            pattern[pattern.lastIndex] += 114_000 - pattern.sum()
        }
        return IrSignal(36_000, pattern.toIntArray())
    }

    // {38.7k,460,msb}<1,-2|1,-4>(8,-8,D:4,F:8,~D:4,~F:8,1,-16)
    private fun rca38(d: Int, f: Int): IrSignal {
        val pattern = ArrayList<Int>()
        val unit = 460
        fun bits(value: Int, count: Int) {
            for (i in count - 1 downTo 0) {
                pattern += unit
                pattern += (if ((value shr i) and 1 == 1) 4 else 2) * unit
            }
        }
        pattern += 8 * unit
        pattern += 8 * unit
        bits(d, 4)
        bits(f, 8)
        bits(d.inv() and 0xF, 4)
        bits(f.inv() and 0xFF, 8)
        pattern += unit
        pattern += 16 * unit
        return IrSignal(38_700, pattern.toIntArray())
    }
}

package com.hydrarsw.controlremoto.androidtv

import java.io.ByteArrayOutputStream
import java.io.EOFException
import java.io.InputStream

/** Codificador protobuf mínimo; el protocolo solo usa varints y campos con longitud. */
class ProtoWriter {
    private val out = ByteArrayOutputStream()

    fun varint(field: Int, value: Int) = apply {
        tag(field, 0)
        writeVarint(value.toLong() and 0xFFFFFFFFL)
    }

    fun bytes(field: Int, value: ByteArray) = apply {
        tag(field, 2)
        writeVarint(value.size.toLong())
        out.write(value)
    }

    fun string(field: Int, value: String) = bytes(field, value.toByteArray())

    fun message(field: Int, build: ProtoWriter.() -> Unit) =
        bytes(field, ProtoWriter().apply(build).toByteArray())

    fun toByteArray(): ByteArray = out.toByteArray()

    private fun tag(field: Int, wire: Int) = writeVarint(((field shl 3) or wire).toLong())

    private fun writeVarint(start: Long) {
        var value = start
        while (value >= 0x80) {
            out.write(((value and 0x7F) or 0x80).toInt())
            value = value ushr 7
        }
        out.write(value.toInt())
    }
}

/** Campos de un mensaje protobuf: número → último valor (Long o ByteArray). */
class ProtoMessage(private val fields: Map<Int, Any>) {
    fun has(field: Int) = field in fields
    fun int(field: Int, default: Int = 0) = (fields[field] as? Long)?.toInt() ?: default
    fun message(field: Int): ProtoMessage? = (fields[field] as? ByteArray)?.let(::parse)

    companion object {
        fun parse(data: ByteArray): ProtoMessage {
            val fields = HashMap<Int, Any>()
            var i = 0
            fun varint(): Long {
                var shift = 0
                var result = 0L
                while (true) {
                    if (i >= data.size || shift > 63) throw IllegalArgumentException("Varint inválido")
                    val b = data[i++].toInt()
                    result = result or ((b and 0x7F).toLong() shl shift)
                    if (b and 0x80 == 0) return result
                    shift += 7
                }
            }
            while (i < data.size) {
                val tag = varint().toInt()
                val field = tag ushr 3
                when (tag and 7) {
                    0 -> fields[field] = varint()
                    1 -> i += 8
                    2 -> {
                        val size = varint().toInt()
                        require(size >= 0 && i + size <= data.size) { "Longitud inválida" }
                        fields[field] = data.copyOfRange(i, i + size)
                        i += size
                    }
                    5 -> i += 4
                    else -> throw IllegalArgumentException("Tipo protobuf desconocido")
                }
                require(i <= data.size) { "Mensaje truncado" }
            }
            return ProtoMessage(fields)
        }
    }
}

/** Los mensajes de la TV van precedidos por su tamaño en varint. */
fun InputStream.readFrame(): ByteArray {
    var size = 0
    var shift = 0
    while (true) {
        val b = read()
        if (b < 0) throw EOFException()
        size = size or ((b and 0x7F) shl shift)
        if (b and 0x80 == 0) break
        shift += 7
        if (shift > 28) throw IllegalArgumentException("Tamaño inválido")
    }
    if (size > MAX_FRAME) throw IllegalArgumentException("Mensaje demasiado grande")
    return readExactly(size)
}

private fun InputStream.readExactly(size: Int): ByteArray {
    val data = ByteArray(size)
    var offset = 0
    while (offset < size) {
        val read = read(data, offset, size - offset)
        if (read < 0) throw EOFException()
        offset += read
    }
    return data
}

fun framed(payload: ByteArray): ByteArray {
    val out = ByteArrayOutputStream()
    var value = payload.size
    while (value >= 0x80) {
        out.write((value and 0x7F) or 0x80)
        value = value ushr 7
    }
    out.write(value)
    out.write(payload)
    return out.toByteArray()
}

private const val MAX_FRAME = 1 shl 20

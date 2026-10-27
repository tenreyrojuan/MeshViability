package com.example.meshviability.data.transport

/**
 * Framing de la API por puerto serie de Meshtastic: 0x94 0xC3, largo en 2 bytes
 * (big endian) y el protobuf. Lo que llega fuera de un frame es el log de texto del firmware.
 */
object StreamFraming {
    const val START1: Byte = 0x94.toByte()
    const val START2: Byte = 0xC3.toByte()
    const val MAX_PAYLOAD = 512
    private const val BYTE_MASK = 0xFF
    private const val BITS_PER_BYTE = 8

    // se manda antes del primer frame para despertar la consola del nodo
    val WAKE: ByteArray = ByteArray(32) { START2 }

    fun frame(payload: ByteArray): ByteArray {
        require(payload.size <= MAX_PAYLOAD) { "Paquete demasiado grande: ${payload.size} bytes" }
        return byteArrayOf(
            START1,
            START2,
            (payload.size shr BITS_PER_BYTE).toByte(),
            (payload.size and BYTE_MASK).toByte(),
        ) + payload
    }

    fun header(msb: Byte, lsb: Byte): Int =
        ((msb.toInt() and BYTE_MASK) shl BITS_PER_BYTE) or (lsb.toInt() and BYTE_MASK)
}

/** Separa los frames de un flujo de bytes que puede llegar cortado en cualquier punto. */
class StreamDeframer {
    private var stage = 0
    private var msb: Byte = 0
    private var payload = ByteArray(0)
    private var filled = 0

    fun feed(bytes: ByteArray, count: Int = bytes.size): List<ByteArray> {
        val frames = mutableListOf<ByteArray>()
        for (i in 0 until count) {
            accept(bytes[i])?.let { frames += it }
        }
        return frames
    }

    private fun accept(b: Byte): ByteArray? {
        when (stage) {
            0 -> if (b == StreamFraming.START1) stage = 1
            1 -> stage = when (b) {
                StreamFraming.START2 -> 2
                StreamFraming.START1 -> 1
                else -> 0
            }
            2 -> {
                msb = b
                stage = 3
            }
            3 -> {
                val length = StreamFraming.header(msb, b)
                if (length > StreamFraming.MAX_PAYLOAD) {
                    stage = 0
                } else {
                    payload = ByteArray(length)
                    filled = 0
                    stage = 4
                    if (length == 0) return complete()
                }
            }
            else -> {
                payload[filled++] = b
                if (filled == payload.size) return complete()
            }
        }
        return null
    }

    private fun complete(): ByteArray {
        stage = 0
        return payload
    }
}

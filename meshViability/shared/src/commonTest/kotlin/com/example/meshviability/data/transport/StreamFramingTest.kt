package com.example.meshviability.data.transport

import org.meshtastic.proto.ToRadio
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class StreamFramingTest {

    private val payload = ToRadio(want_config_id = 42).encode()

    @Test
    fun encabezadoConLargoBigEndian() {
        val framed = StreamFraming.frame(ByteArray(300))
        assertContentEquals(byteArrayOf(0x94.toByte(), 0xC3.toByte(), 0x01, 0x2C), framed.copyOf(4))
        assertEquals(304, framed.size)
    }

    @Test
    fun idaYVuelta() {
        val frames = StreamDeframer().feed(StreamFraming.frame(payload))
        assertContentEquals(payload, frames.single())
    }

    @Test
    fun ignoraElLogDeTextoDelFirmware() {
        val stream = "INFO | Booting\r\n".encodeToByteArray() + StreamFraming.frame(payload) + "DEBUG | ok\n".encodeToByteArray()
        assertContentEquals(payload, StreamDeframer().feed(stream).single())
    }

    @Test
    fun frameCortadoEnVariasLecturas() {
        val stream = StreamFraming.frame(payload) + StreamFraming.frame(payload)
        val deframer = StreamDeframer()
        val frames = stream.toList().chunked(3).flatMap { deframer.feed(it.toByteArray()) }
        assertEquals(2, frames.size)
        frames.forEach { assertContentEquals(payload, it) }
    }

    @Test
    fun largoInvalidoSeDescartaYSeResincroniza() {
        val basura = byteArrayOf(0x94.toByte(), 0xC3.toByte(), 0x7F, 0x7F)
        val frames = StreamDeframer().feed(basura + StreamFraming.frame(payload))
        assertContentEquals(payload, frames.single())
    }

    @Test
    fun soloSeLeeLaParteValidaDelBuffer() {
        val buffer = StreamFraming.frame(payload) + ByteArray(100)
        val frames = StreamDeframer().feed(buffer, count = payload.size + 4)
        assertContentEquals(payload, frames.single())
    }

    @Test
    fun paqueteDemasiadoGrandeSeRechaza() {
        assertFailsWith<IllegalArgumentException> { StreamFraming.frame(ByteArray(513)) }
    }

    @Test
    fun despertarSonBytesStart2() {
        assertTrue(StreamFraming.WAKE.all { it == StreamFraming.START2 })
    }
}

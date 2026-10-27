package com.example.meshviability.data.export

import com.example.meshviability.domain.entities.LinkQuality
import com.example.meshviability.domain.entities.Message
import com.example.meshviability.domain.entities.MessageStatus
import com.example.meshviability.domain.link.LinkParameters
import com.example.meshviability.domain.link.LinkThresholds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SessionCsvTest {

    private fun csv(vararg messages: Message) =
        SessionCsv.build(messages.toList(), LinkParameters(), LinkThresholds(), acceptUnknownHops = false) { "n$it" }
            .trimEnd().split("\r\n")

    private fun recibido(hops: Int?, texto: String = "hola") = Message(
        id = 7, fromNodeId = 2, toNodeId = 1, timeStampMs = 1000, content = texto,
        status = MessageStatus.DELIVERED, signalQuality = LinkQuality(-95f, 4f, hops),
    )

    @Test
    fun encabezadoYUnaFilaPorMensaje() {
        val lines = csv(recibido(0), recibido(1))
        assertEquals(SessionCsv.HEADER.joinToString(","), lines[0])
        assertEquals(3, lines.size)
        lines.forEach { assertEquals(SessionCsv.HEADER.size, it.split(",").size) }
    }

    @Test
    fun medicionValidaTraeMargenesYNivel() {
        val f = csv(recibido(0))[1].split(",").zip(SessionCsv.HEADER).associate { (v, k) -> k to v }
        assertEquals("VALID", f["validez"])
        assertEquals("SOLID", f["nivel"])
        assertEquals(21.5, f.getValue("margen_peor_db").toDouble(), 0.1)
        assertEquals(23.3, f.getValue("perdida_extra_db").toDouble(), 0.1)
        assertEquals("1.0", f["distancia_km"])
        assertEquals("11", f["sf"])
    }

    @Test
    fun retransmitidoNoTraeMargenes() {
        val f = csv(recibido(2))[1].split(",").zip(SessionCsv.HEADER).associate { (v, k) -> k to v }
        assertEquals("RELAYED", f["validez"])
        assertEquals("", f["margen_peor_db"])
    }

    @Test
    fun enviadoTraeEstado() {
        val enviado = Message(9, 1, 2, 0, "ok", MessageStatus.FAILED, null)
        assertTrue(csv(enviado)[1].contains(",FAILED,"))
    }

    @Test
    fun textoConComasYComillasSeEscapa() {
        assertTrue(csv(recibido(0, texto = "hola, \"B\""))[1].contains("\"hola, \"\"B\"\"\""))
    }
}

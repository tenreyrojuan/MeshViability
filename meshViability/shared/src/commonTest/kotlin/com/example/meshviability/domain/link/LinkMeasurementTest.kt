package com.example.meshviability.domain.link

import com.example.meshviability.domain.entities.LinkQuality
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkMeasurementTest {

    private fun medicion(hops: Int?, viaMqtt: Boolean = false) =
        LinkQuality(rssiDbm = -95f, snrDb = 4f, hops = hops, viaMqtt = viaMqtt)

    @Test
    fun directoEsValido() {
        assertEquals(MeasurementValidity.VALID, medicion(hops = 0).validity())
    }

    @Test
    fun retransmitidoNoEsValido() {
        assertEquals(MeasurementValidity.RELAYED, medicion(hops = 1).validity())
    }

    @Test
    fun saltosDesconocidosNoSonValidosSalvoQueSeAcepten() {
        assertEquals(MeasurementValidity.UNKNOWN_HOPS, medicion(hops = null).validity())
        assertEquals(MeasurementValidity.VALID, medicion(hops = null).validity(acceptUnknownHops = true))
    }

    @Test
    fun porMqttNoEsValido() {
        assertEquals(MeasurementValidity.VIA_MQTT, medicion(hops = 0, viaMqtt = true).validity())
    }

    // a 1 km se espera −71,7 dBm; medir −95 dBm son 23,3 dB de pérdida extra
    @Test
    fun comparacionConLaPrediccion() {
        val e = medicion(hops = 0).evaluate(LinkParameters())
        assertEquals(-71.7, e.expectedRssiDbm, 0.1)
        assertEquals(23.3, e.extraLossDb, 0.1)
        assertEquals(21.5, e.margin.worstDb, 0.1)
        assertEquals(LinkLevel.SOLID, e.level)
    }

    @Test
    fun laEvaluacionUsaLosUmbralesRecibidos() {
        val e = medicion(hops = 0).evaluate(LinkParameters(), LinkThresholds(solidDb = 25.0))
        assertEquals(LinkLevel.ACCEPTABLE, e.level)
    }
}

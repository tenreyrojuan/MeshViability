package com.example.meshviability.domain.link

import com.example.meshviability.domain.entities.LinkQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LinkPredictionTest {

    private val tolerance = 0.1

    // Valores por defecto: 20 dBm, 915 MHz, 1 km, SF11, 250 kHz, NF 6 dB
    private val prediction = LinkParameters().predict()

    @Test
    fun ruidoEn250kHz() {
        assertEquals(-120.0, prediction.noiseDbm, tolerance)
    }

    @Test
    fun sensibilidadSf11() {
        assertEquals(-131.5, prediction.sensitivityDbm, tolerance)
    }

    @Test
    fun perdidaEspacioLibre915MHz1km() {
        assertEquals(91.7, prediction.pathLossDb, tolerance)
    }

    @Test
    fun potenciaRecibidaA1km() {
        assertEquals(-71.7, prediction.rxPowerDbm, tolerance)
    }

    // −71,68 − (−131,50) = 59,82 dB ("unos 60 dB")
    @Test
    fun margenA1km() {
        assertEquals(59.8, prediction.marginDb, tolerance)
        assertEquals(prediction.rxPowerDbm - prediction.sensitivityDbm, prediction.marginDb, 1e-9)
    }

    @Test
    fun enElAlcanceMaximoElMargenEsCero() {
        val enElLimite = LinkParameters(distanceKm = prediction.maxRangeKm).predict()
        assertEquals(0.0, enElLimite.marginDb, 1e-6)
    }

    @Test
    fun elFactorDeRuidoEsEditable() {
        val conNf9 = LinkParameters(noiseFigureDb = 9.0).predict()
        assertEquals(-128.5, conNf9.sensitivityDbm, tolerance)
    }

    @Test
    fun limitesDelSemaforo() {
        val t = LinkThresholds()
        assertEquals(LinkLevel.SOLID, t.classify(20.0))
        assertEquals(LinkLevel.ACCEPTABLE, t.classify(19.99))
        assertEquals(LinkLevel.ACCEPTABLE, t.classify(10.0))
        assertEquals(LinkLevel.FRAGILE, t.classify(9.99))
        assertEquals(LinkLevel.FRAGILE, t.classify(0.0))
        assertEquals(LinkLevel.UNRELIABLE, t.classify(-0.01))
    }

    @Test
    fun elUmbralDeSolidoEsConfigurable() {
        assertEquals(LinkLevel.SOLID, LinkThresholds(solidDb = 15.0).classify(16.0))
    }

    @Test
    fun umbralesDesordenadosSeRechazan() {
        assertFailsWith<IllegalArgumentException> { LinkThresholds(solidDb = 5.0, acceptableDb = 10.0) }
    }

    // RSSI −100 → margen 31,5; SNR −5 → margen 12,5: manda el peor
    @Test
    fun elSemaforoUsaElPeorDeLosDosMargenes() {
        val m = LinkQuality(rssiDbm = -100f, snrDb = -5f, hops = 0).margin(LinkParameters())
        assertEquals(31.5, m.rssiMarginDb, tolerance)
        assertEquals(12.5, m.snrMarginDb, tolerance)
        assertEquals(12.5, m.worstDb, tolerance)
        assertEquals(LinkLevel.ACCEPTABLE, m.level())
    }
}

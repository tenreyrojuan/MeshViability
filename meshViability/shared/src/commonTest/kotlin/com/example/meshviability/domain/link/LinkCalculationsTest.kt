package com.example.meshviability.domain.link

import com.example.meshviability.domain.entities.LinkQuality
import kotlin.test.Test
import kotlin.test.assertEquals

class LinkCalculationsTest {

    // Pruebas con ejemplos de la Unidad 3 de comms

    // Ejemplo 12: T = 17 °C, B = 1, Hz = −174 dBm
    @Test
    fun ejemplo12RuidoTermico1Hz() {
        assertEquals(-174.0, ThermalNoise.powerDbm(bandwidthHz = 1.0), 0.05)
    }

    // Ejemplo 13: T = 17 °C, B = 1, MHz = −114 dBm
    @Test
    fun ejemplo13RuidoTermico1MHz() {
        assertEquals(-114.0, ThermalNoise.powerDbm(bandwidthHz = 1e6), 0.05)
    }

    // Sección 4: f = 5,8 GHz, d = 100, m = 87,71 dB
    @Test
    fun perdidaEspacioLibre5800MHz100m() {
        assertEquals(87.71, FreeSpaceLoss.lossDb(frequencyMhz = 5800.0, distanceKm = 0.1), 0.02)
    }

    // Ejemplo 09:
    // RSSI = −65,71 dBm
    // MARGEN = −65,71 − (−82) = 16,29 dB
    @Test
    fun ejemplo09BalanceYMargen() {
        val budget = ejemplo09()
        assertEquals(-65.71, budget.predictedRssiDbm(0.1), 0.02)
        assertEquals(16.29, budget.marginDb(0.1, sensitivityDbm = -82.0), 0.02)
    }

    // Ejemplo 09 (ruido): T = 25 °C, B = 20, MHz = N = −100,8 dBm;
    // SNR ≈ 35 dB
    @Test
    fun ejemplo09RuidoYSnr() {
        assertEquals(-100.8, ThermalNoise.powerDbm(bandwidthHz = 20e6, temperatureC = 25.0), 0.1)
        assertEquals(35.1, ejemplo09().predictedSnrDb(0.1, bandwidthHz = 20e6, temperatureC = 25.0), 0.1)
    }

    // Aplicación a LoRa

    // SRX LongFast = −174 + 10 log(250 000) + 6 − 17,5 ≈ −131,5 dBm
    @Test
    fun sensibilidadLongFast() {
        assertEquals(-131.5, ModemPreset.LONG_FAST.sensitivityDbm(), 0.05)
    }

    // RSSI −95 y SNR 4 → márgenes de ~36,5 y 21,5 dB → sólido
    @Test
    fun margenDeUnaMedicionBuena() {
        val m = LinkQuality(rssiDbm = -95.0f, snrDb = 4f, hops = 0).margin(ModemPreset.LONG_FAST)
        assertEquals(36.5, m.rssiMarginDb, 0.05)
        assertEquals(21.5, m.snrMarginDb, 0.01)
        assertEquals(LinkLevel.SOLID, m.level)
    }

    // SNR −19 con LongFast (mínimo −17,5) => no confiable
    @Test
    fun margenDeUnaMedicionAlLimite() {
        val m = LinkQuality(rssiDbm = -128.0f, snrDb = -19f, hops = 0).margin(ModemPreset.LONG_FAST)
        assertEquals(LinkLevel.UNRELIABLE, m.level)
    }

    // en la distancia máxima, el RSSI predicho queda en SRX + margen de seguridad
    @Test
    fun alcanceMaximoEsCoherente() {
        val budget = LinkBudget(txPowerDbm = 20.0, pathLossExponent = 3.5)
        val srx = ModemPreset.LONG_FAST.sensitivityDbm()
        val d = budget.maxRangeKm(srx)
        assertEquals(srx + LinkBudget.DEFAULT_FADE_MARGIN_DB, budget.predictedRssiDbm(d), 0.01)
    }

    // datos del Ejemplo 09: 20 dBm, cables de 1 dB, antenas de 3 y 1 dBi, 5,8 GHz
    private fun ejemplo09() = LinkBudget(
        txPowerDbm = 20.0,
        txCableLossDb = 1.0,
        txAntennaGainDbi = 3.0,
        rxAntennaGainDbi = 1.0,
        rxCableLossDb = 1.0,
        frequencyMhz = 5800.0,
    )
}
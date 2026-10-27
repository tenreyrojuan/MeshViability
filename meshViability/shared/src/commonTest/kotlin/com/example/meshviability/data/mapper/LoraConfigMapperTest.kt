package com.example.meshviability.data.mapper

import com.example.meshviability.domain.link.LinkParameters
import com.example.meshviability.domain.link.ModemPreset
import com.example.meshviability.domain.link.SpreadingFactor
import com.example.meshviability.domain.link.withNodeSettings
import org.meshtastic.proto.Config.LoRaConfig
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LoraConfigMapperTest {

    @Test
    fun presetLongFast() {
        val s = LoRaConfig(
            use_preset = true,
            modem_preset = LoRaConfig.ModemPreset.LONG_FAST,
            tx_power = 20,
            region = LoRaConfig.RegionCode.US,
        ).toLoraSettings()

        assertEquals(ModemPreset.LONG_FAST, s.preset)
        assertEquals(SpreadingFactor.SF11, s.spreadingFactor)
        assertEquals(250_000.0, s.bandwidthHz)
        assertEquals(20, s.txPowerDbm)
        assertNull(s.frequencyMhz)
        assertEquals("US", s.region)
    }

    @Test
    fun configuracionManual() {
        val s = LoRaConfig(use_preset = false, spread_factor = 10, bandwidth = 125).toLoraSettings()

        assertNull(s.preset)
        assertEquals(SpreadingFactor.SF10, s.spreadingFactor)
        assertEquals(125_000.0, s.bandwidthHz)
    }

    @Test
    fun anchoDeBandaEspecial31() {
        val s = LoRaConfig(use_preset = false, spread_factor = 12, bandwidth = 31).toLoraSettings()
        assertEquals(31_250.0, s.bandwidthHz)
    }

    @Test
    fun potenciaCeroEsLaMaximaLegalYNoSeConoce() {
        assertNull(LoRaConfig(use_preset = true, tx_power = 0).toLoraSettings().txPowerDbm)
    }

    @Test
    fun frecuenciaSoloSiEstaFijadaAMano() {
        val s = LoRaConfig(use_preset = true, override_frequency = 915.5f).toLoraSettings()
        assertEquals(915.5, s.frequencyMhz)
    }

    @Test
    fun loQueElNodoNoInformaSeConservaDeLaUi() {
        val ui = LinkParameters(txPowerDbm = 18.0, frequencyMhz = 916.0)
        val s = LoRaConfig(
            use_preset = true,
            modem_preset = LoRaConfig.ModemPreset.MEDIUM_FAST,
            tx_power = 0,
        ).toLoraSettings()

        val p = ui.withNodeSettings(s)
        assertEquals(18.0, p.txPowerDbm)
        assertEquals(916.0, p.frequencyMhz)
        assertEquals(SpreadingFactor.SF9, p.spreadingFactor)
        assertEquals(250_000.0, p.bandwidthHz)
    }
}

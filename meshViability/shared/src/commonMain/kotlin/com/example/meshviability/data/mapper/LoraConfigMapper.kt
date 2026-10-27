package com.example.meshviability.data.mapper

import com.example.meshviability.domain.entities.LoraSettings
import com.example.meshviability.domain.link.ModemPreset
import com.example.meshviability.domain.link.SpreadingFactor
import org.meshtastic.proto.Config

private const val HZ_PER_KHZ = 1000.0

// Convierte la configuración LoRa de Meshtastic en la del dominio.
fun Config.LoRaConfig.toLoraSettings(): LoraSettings {
    // los nombres de los presets coinciden
    val preset = if (use_preset) ModemPreset.entries.firstOrNull { it.name == modem_preset.name } else null

    return LoraSettings(
        preset = preset,
        spreadingFactor = if (use_preset) preset?.spreadingFactor
            else SpreadingFactor.entries.firstOrNull { it.value == spread_factor },
        bandwidthHz = if (use_preset) preset?.bandwidthHz else manualBandwidthHz(),
        txPowerDbm = tx_power.takeIf { it > 0 },
        frequencyMhz = override_frequency.takeIf { it > 0f }?.toDouble(),
        region = region.name,
    )
}

// 31 significa 31,25 kHz (ver config.proto)
private fun Config.LoRaConfig.manualBandwidthHz(): Double? = when (bandwidth) {
    0 -> null
    31 -> 31.25 * HZ_PER_KHZ
    else -> bandwidth * HZ_PER_KHZ
}

package com.example.meshviability.data.mapper

import com.example.meshviability.domain.entities.LoraSettings
import com.example.meshviability.domain.link.ModemPreset
import org.meshtastic.proto.Config

// Convierte la configuración LoRa de Meshtastic en la del dominio.
fun Config.LoRaConfig.toLoraSettings(): LoraSettings = LoraSettings(
    // los nombres de los presets coinciden
    preset = if (use_preset) ModemPreset.entries.firstOrNull { it.name == modem_preset.name } else null,

    // dBm del Transmisor Tx
    txPowerDbm = tx_power,

    // Region. EN nuestro caso Argentina
    // Como legalmente hay una frecuencia permitida, se usa esto
    // En nuestro caso es 915 MHz
    region = region.name,
)
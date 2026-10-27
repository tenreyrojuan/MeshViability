package com.example.meshviability.domain.entities

import com.example.meshviability.domain.link.ModemPreset
import com.example.meshviability.domain.link.SpreadingFactor

// configuración de radio del nodo; null = el nodo no la informa y se ingresa en la UI
data class LoraSettings(
    val preset: ModemPreset?,   // null si el nodo usa parámetros manuales
    val spreadingFactor: SpreadingFactor?,
    val bandwidthHz: Double?,

    // dBm del Transmisor Tx. null si el nodo usa la máxima legal de la región (tx_power = 0)
    val txPowerDbm: Int?,

    // solo si el nodo fija la frecuencia a mano; si no, la calcula el firmware según región y canal
    val frequencyMhz: Double?,

    val region: String,
)

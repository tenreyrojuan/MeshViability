package com.example.meshviability.domain.entities

import com.example.meshviability.domain.link.ModemPreset

// configuración de radio del nodo
data class LoraSettings(
    val preset: ModemPreset?,   // null si el nodo usa parámetros manuales

    // dBm del Transmisor Tx
    // 0 = la máxima legal de la región
    val txPowerDbm: Int,
    
    // Region. EN nuestro caso Argentina
    // Como legalmente hay una frecuencia permitida, se usa esto
    // En nuestro caso es 915 MHz
    val region: String,
)
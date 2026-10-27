package com.example.meshviability.data.transport

// nodo al que la app se puede conectar: dirección BLE, puerto serie o simulado
data class RadioDevice(val address: String, val name: String)

interface DeviceDiscovery {
    // lanza RadioUnavailableException si falta un permiso o el hardware no está disponible
    suspend fun devices(): List<RadioDevice>
}

class RadioUnavailableException(message: String) : Exception(message)

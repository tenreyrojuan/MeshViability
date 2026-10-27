package com.example.meshviability.data.transport

// nodo al que la app se puede conectar: dirección BLE, puerto serie o simulado
data class RadioDevice(val address: String, val name: String)

interface DeviceDiscovery {
    // lanza RadioUnavailableException si falta un permiso o el hardware no está disponible
    suspend fun devices(): List<RadioDevice>
}

class RadioUnavailableException(message: String) : Exception(message)

// lo encontrado y, aparte, por qué no se pudo buscar en algún medio (Bluetooth, USB)
data class ScanResult(val devices: List<RadioDevice>, val problems: List<String>)

interface NodeScanner {
    suspend fun scan(): ScanResult
}

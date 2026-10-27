package com.example.meshviability.data.transport

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

sealed interface ConnectionState {
    object Connected : ConnectionState
    object Connecting : ConnectionState
    object Disconnected : ConnectionState
    data class ConnectionError(val message : String) : ConnectionState
}

interface RadioTransport {

    // Estado del flujo de la conexion
    val connectionState: StateFlow<ConnectionState>

    // El flujo de datos en si.
    // Es como una corriente en tiempo real
    // de los datos que llegan
    val incoming: Flow<ByteArray>

    suspend fun connect(address: String)
    suspend fun send(bytes: ByteArray)
    suspend fun disconnect()
}
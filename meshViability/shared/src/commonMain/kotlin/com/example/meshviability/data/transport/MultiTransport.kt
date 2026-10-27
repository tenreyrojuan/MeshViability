package com.example.meshviability.data.transport

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onEach

class RadioLink(val transport: RadioTransport, val discovery: DeviceDiscovery)

/** Reúne varios medios (Bluetooth, USB, simulado) y habla por el del nodo elegido. */
class MultiTransport(private val links: List<RadioLink>, scope: CoroutineScope) : RadioTransport, NodeScanner {

    private val owners = mutableMapOf<String, RadioLink>()
    private var active: RadioLink? = null

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    override val incoming: Flow<ByteArray> = merge(*links.map { it.transport.incoming }.toTypedArray())

    init {
        // solo cuenta el estado del medio activo
        links.forEach { link ->
            link.transport.connectionState
                .onEach { if (active === link) _state.value = it }
                .launchIn(scope)
        }
    }

    override suspend fun scan(): ScanResult {
        val devices = mutableListOf<RadioDevice>()
        val problems = mutableListOf<String>()
        for (link in links) {
            try {
                link.discovery.devices().forEach { device ->
                    owners[device.address] = link
                    devices += device
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                problems += e.message ?: "No se pudo buscar nodos"
            }
        }
        return ScanResult(devices, problems)
    }

    override suspend fun connect(address: String) {
        val link = owners[address] ?: throw RadioUnavailableException("Nodo desconocido: buscá los nodos de nuevo")
        val previous = active
        active = link
        if (previous != null && previous !== link) previous.transport.disconnect()
        _state.value = ConnectionState.Connecting
        link.transport.connect(address)
        _state.value = link.transport.connectionState.value
    }

    override suspend fun send(bytes: ByteArray) {
        val link = active ?: throw IllegalStateException("No hay ningún nodo conectado")
        link.transport.send(bytes)
    }

    override suspend fun disconnect() {
        active?.transport?.disconnect()
    }
}

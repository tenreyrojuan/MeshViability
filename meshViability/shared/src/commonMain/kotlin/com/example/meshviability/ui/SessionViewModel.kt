package com.example.meshviability.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meshviability.data.protocol.MeshProtocolClient
import com.example.meshviability.data.repository.LinkSettingsRepository
import com.example.meshviability.data.repository.MeshRepository
import com.example.meshviability.data.transport.MultiTransport
import com.example.meshviability.data.transport.RadioLink
import com.example.meshviability.data.transport.SimulatedNodeTransport
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
fun currentTimeMs(): Long = Clock.System.now().toEpochMilliseconds()

/** Una sesión de medición: conexión con el nodo y datos en memoria. Sobrevive a la rotación. */
class SessionViewModel(platformLinks: List<RadioLink>, now: () -> Long = ::currentTimeMs) : ViewModel() {
    private val simulated = SimulatedNodeTransport(viewModelScope)
    val transport = MultiTransport(platformLinks + RadioLink(simulated, simulated), viewModelScope)
    val repository = MeshRepository(MeshProtocolClient(transport, viewModelScope), viewModelScope, now)
    val settings = LinkSettingsRepository(repository.loraSettings, viewModelScope)
    val sessionStartMs = now()
}

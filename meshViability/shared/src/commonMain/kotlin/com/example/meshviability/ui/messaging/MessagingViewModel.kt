package com.example.meshviability.ui.messaging

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meshviability.data.protocol.MeshProtocolClient
import com.example.meshviability.data.protocol.MeshState
import com.example.meshviability.data.repository.LinkSettingsRepository
import com.example.meshviability.data.repository.MeshRepository
import com.example.meshviability.data.transport.NodeScanner
import com.example.meshviability.data.transport.RadioDevice
import com.example.meshviability.domain.entities.MeshNode
import com.example.meshviability.domain.entities.Message
import com.example.meshviability.domain.entities.MessageStatus
import com.example.meshviability.domain.link.MeasurementValidity
import com.example.meshviability.domain.link.validity
import com.example.meshviability.ui.formatDecimal
import com.example.meshviability.ui.formatElapsed
import com.example.meshviability.ui.nodeLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class DestinationOption(val num: Int, val label: String)

data class MessageItem(
    val id: Int,
    val mine: Boolean,
    val fromLabel: String,
    val toLabel: String,
    val content: String,
    val elapsed: String,
    val status: MessageStatus?,     // solo en los enviados
    val statusLabel: String?,
    val signal: String?,            // "RSSI −95 dBm · SNR 4,3 dB · 0 saltos"
    val validForMeasurement: Boolean,
    val validityNote: String?,      // por qué no sirve para medir
)

data class MessagingUiState(
    val statusText: String = "Desconectado",
    val isConnected: Boolean = false,
    val isBusy: Boolean = false,
    val devices: List<RadioDevice> = emptyList(),
    val firmwareVersion: String? = null,
    val destinations: List<DestinationOption> = emptyList(),
    val destination: Int? = null,
    val draft: String = "",
    val canSend: Boolean = false,
    val messages: List<MessageItem> = emptyList(),
    val invalidPackets: Int = 0,
    val error: String? = null,
)

class MessagingViewModel(
    private val repository: MeshRepository,
    private val settings: LinkSettingsRepository,
    private val scanner: NodeScanner,
    private val sessionStartMs: Long,
) : ViewModel() {

    private data class Local(
        val draft: String = "",
        val selectedDestination: Int? = null,
        val devices: List<RadioDevice> = emptyList(),
        val busy: Boolean = false,
        val error: String? = null,
    )

    private data class Link(
        val state: MeshState,
        val nodes: List<MeshNode>,
        val firmware: String?,
        val invalidPackets: Int,
    )

    private val local = MutableStateFlow(Local())

    private val link = combine(
        repository.state,
        repository.remoteNodes,
        repository.firmwareVersion,
        repository.invalidPackets,
        ::Link,
    )

    val uiState: StateFlow<MessagingUiState> =
        combine(link, repository.messages, settings.acceptUnknownHops, local, ::build)
            .stateIn(viewModelScope, SharingStarted.Eagerly, MessagingUiState())

    private fun build(link: Link, messages: List<Message>, acceptUnknownHops: Boolean, local: Local): MessagingUiState {
        val me = (link.state as? MeshState.Ready)?.myNodeNum
        val destinations = link.nodes.map { DestinationOption(it.num, it.longName.ifBlank { nodeLabel(it.num, link.nodes, me) }) } +
            DestinationOption(MeshProtocolClient.BROADCAST, "Todos (broadcast)")
        // unicast al primer nodo conocido, salvo que el usuario elija otro
        val destination = local.selectedDestination?.takeIf { sel -> destinations.any { it.num == sel } }
            ?: link.nodes.firstOrNull()?.num
        val connected = link.state is MeshState.Ready

        return MessagingUiState(
            statusText = statusText(link.state, local.busy),
            isConnected = connected,
            isBusy = local.busy,
            devices = local.devices,
            firmwareVersion = link.firmware,
            destinations = destinations,
            destination = destination,
            draft = local.draft,
            canSend = connected && destination != null && local.draft.isNotBlank() && !local.busy,
            messages = messages.map { it.toItem(me, link.nodes, acceptUnknownHops) },
            invalidPackets = link.invalidPackets,
            error = local.error,
        )
    }

    private fun statusText(state: MeshState, busy: Boolean): String = when (state) {
        MeshState.Disconnected -> if (busy) "Conectando…" else "Desconectado"
        MeshState.Configuring -> "Leyendo la configuración del nodo…"
        is MeshState.Ready -> "Conectado"
        is MeshState.Failed -> "Error: ${state.reason}"
    }

    private fun Message.toItem(me: Int?, nodes: List<MeshNode>, acceptUnknownHops: Boolean): MessageItem {
        val q = signalQuality
        val validity = q?.validity(acceptUnknownHops)
        val mine = me != null && fromNodeId == me
        return MessageItem(
            id = id,
            mine = mine,
            fromLabel = nodeLabel(fromNodeId, nodes, me),
            toLabel = nodeLabel(toNodeId, nodes, me),
            content = content,
            elapsed = formatElapsed(sessionStartMs, timeStampMs),
            status = status.takeIf { mine },
            statusLabel = if (!mine) null else when (status) {
                MessageStatus.PENDING -> "Pendiente"
                MessageStatus.DELIVERED -> "Entregado"
                MessageStatus.FAILED -> "Fallido"
            },
            signal = q?.let {
                val hops = it.hops?.let { h -> " · $h saltos" } ?: " · saltos desconocidos"
                "RSSI ${formatDecimal(it.rssiDbm.toDouble(), 0)} dBm · SNR ${formatDecimal(it.snrDb.toDouble())} dB$hops"
            },
            validForMeasurement = validity == MeasurementValidity.VALID,
            validityNote = when (validity) {
                null, MeasurementValidity.VALID -> null
                MeasurementValidity.RELAYED -> "No válido para medición: retransmitido, RSSI y SNR son del último salto"
                MeasurementValidity.UNKNOWN_HOPS -> "No válido para medición: el firmware no informa los saltos"
                MeasurementValidity.VIA_MQTT -> "No válido para medición: llegó por MQTT"
            },
        )
    }

    fun onDraftChange(text: String) = local.update { it.copy(draft = text) }

    fun onDestinationSelected(num: Int) = local.update { it.copy(selectedDestination = num) }

    fun dismissError() = local.update { it.copy(error = null) }

    fun scan() = runBusy("No se pudieron buscar nodos") {
        val result = scanner.scan()
        val error = when {
            result.problems.isNotEmpty() -> result.problems.joinToString("\n")
            result.devices.isEmpty() -> "No se encontró ningún nodo"
            else -> null
        }
        local.update { it.copy(devices = result.devices, error = error) }
    }

    fun connect(device: RadioDevice) = runBusy("No se pudo conectar") {
        repository.connect(device.address)
    }

    fun disconnect() = runBusy("No se pudo desconectar") {
        repository.disconnect()
    }

    fun send() {
        val state = uiState.value
        val text = state.draft.trim()
        if (text.isEmpty()) return
        if (repository.state.value !is MeshState.Ready) {
            local.update { it.copy(error = "Nodo desconectado: conectate antes de enviar") }
            return
        }
        val to = state.destination ?: run {
            local.update { it.copy(error = "Elegí un nodo destino") }
            return
        }
        runBusy("No se pudo enviar") {
            repository.sendText(text, to)
            local.update { it.copy(draft = "") }
        }
    }

    private fun runBusy(errorPrefix: String, block: suspend () -> Unit) {
        viewModelScope.launch {
            local.update { it.copy(busy = true, error = null) }
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                local.update { it.copy(error = "$errorPrefix: ${e.message ?: e::class.simpleName}") }
            } finally {
                local.update { it.copy(busy = false) }
            }
        }
    }
}

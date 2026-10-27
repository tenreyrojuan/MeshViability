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
import com.example.meshviability.domain.link.LinkLevel
import com.example.meshviability.domain.link.LinkParameters
import com.example.meshviability.domain.link.LinkThresholds
import com.example.meshviability.domain.link.MeasurementValidity
import com.example.meshviability.domain.link.evaluate
import com.example.meshviability.domain.link.validity
import com.example.meshviability.ui.formatDecimal
import com.example.meshviability.ui.formatElapsed
import com.example.meshviability.ui.measurement.label
import com.example.meshviability.ui.nodeLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class ConnectionKind { DISCONNECTED, CONNECTING, CONNECTED, ERROR }

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
    val signal: String?,            // "RSSI −95 dBm · SNR 4,3 dB · directo"
    val level: LinkLevel?,          // solo si sirve como medición
    val levelLabel: String?,
    val validForMeasurement: Boolean,
    val validityNote: String?,      // por qué no sirve para medir
)

data class MessagingUiState(
    val statusText: String = "Desconectado",
    val detailText: String? = null,
    val connection: ConnectionKind = ConnectionKind.DISCONNECTED,
    val isConnected: Boolean = false,
    val isBusy: Boolean = false,
    val hint: String? = CONNECT_HINT,
    val scanLabel: String = "Buscar nodos",
    val devices: List<RadioDevice> = emptyList(),
    val firmwareVersion: String? = null,
    val destinations: List<DestinationOption> = emptyList(),
    val destination: Int? = null,
    val draft: String = "",
    val composerHint: String = "Mensaje",
    val canSend: Boolean = false,
    val messages: List<MessageItem> = emptyList(),
    val invalidPackets: Int = 0,
    val error: String? = null,
)

private const val CONNECT_HINT =
    "Conectá un nodo Meshtastic por Bluetooth (teléfono) o USB (notebook). Sin placas, usá el nodo simulado."

// lo que la pantalla puede pedir; la implementa el ViewModel (y un objeto vacío en los previews)
interface MessagingActions {
    fun onDraftChange(text: String)
    fun onDestinationSelected(num: Int)
    fun dismissError()
    fun scan()
    fun connect(device: RadioDevice)
    fun disconnect()
    fun send()
}

/** Todo lo que la pantalla de mensajería necesita, en un momento dado. */
internal data class MessagingSnapshot(
    val state: MeshState,
    val nodes: List<MeshNode>,
    val messages: List<Message>,
    val parameters: LinkParameters = LinkParameters(),
    val thresholds: LinkThresholds = LinkThresholds(),
    val acceptUnknownHops: Boolean = false,
    val firmware: String? = null,
    val invalidPackets: Int = 0,
    val draft: String = "",
    val selectedDestination: Int? = null,
    val devices: List<RadioDevice> = emptyList(),
    val busy: Boolean = false,
    val error: String? = null,
    val sessionStartMs: Long = 0L,
)

internal fun MessagingSnapshot.toUiState(): MessagingUiState {
    val me = (state as? MeshState.Ready)?.myNodeNum
    val destinations = nodes.map { DestinationOption(it.num, it.longName.ifBlank { nodeLabel(it.num, nodes, me) }) } +
        DestinationOption(MeshProtocolClient.BROADCAST, "Todos (broadcast)")
    // unicast al primer nodo conocido, salvo que el usuario elija otro
    val destination = selectedDestination?.takeIf { sel -> destinations.any { it.num == sel } }
        ?: nodes.firstOrNull()?.num
    val connected = state is MeshState.Ready

    return MessagingUiState(
        statusText = when (state) {
            MeshState.Disconnected -> if (busy) "Conectando…" else "Desconectado"
            MeshState.Configuring -> "Leyendo la configuración del nodo…"
            is MeshState.Ready -> "Conectado"
            is MeshState.Failed -> "Error de conexión"
        },
        detailText = when (state) {
            is MeshState.Failed -> state.reason
            is MeshState.Ready -> listOfNotNull(
                firmware?.let { "Firmware $it" },
                invalidPackets.takeIf { it > 0 }?.let { "$it paquetes inválidos descartados" },
            ).joinToString(" · ").ifEmpty { null }
            else -> null
        },
        connection = when {
            state is MeshState.Ready -> ConnectionKind.CONNECTED
            state is MeshState.Failed -> ConnectionKind.ERROR
            state is MeshState.Configuring || busy -> ConnectionKind.CONNECTING
            else -> ConnectionKind.DISCONNECTED
        },
        isConnected = connected,
        isBusy = busy,
        hint = if (connected || devices.isNotEmpty()) null else CONNECT_HINT,
        scanLabel = if (devices.isEmpty()) "Buscar nodos" else "Buscar de nuevo",
        devices = devices,
        firmwareVersion = firmware,
        destinations = destinations,
        destination = destination,
        draft = draft,
        composerHint = destinations.firstOrNull { it.num == destination }?.let { "Mensaje para ${it.label}" } ?: "Mensaje",
        canSend = connected && destination != null && draft.isNotBlank() && !busy,
        messages = messages.map { it.toItemWith(this, me) },
        invalidPackets = invalidPackets,
        error = error,
    )
}

private fun hopsLabel(hops: Int?): String = when (hops) {
    null -> "saltos desconocidos"
    0 -> "directo"
    1 -> "1 salto"
    else -> "$hops saltos"
}

private fun Message.toItemWith(snapshot: MessagingSnapshot, me: Int?): MessageItem {
    val q = signalQuality
    val validity = q?.validity(snapshot.acceptUnknownHops)
    val level = q?.takeIf { validity == MeasurementValidity.VALID }?.evaluate(snapshot.parameters, snapshot.thresholds)?.level
    val mine = me != null && fromNodeId == me
    return MessageItem(
        id = id,
        mine = mine,
        fromLabel = nodeLabel(fromNodeId, snapshot.nodes, me),
        toLabel = nodeLabel(toNodeId, snapshot.nodes, me),
        content = content,
        elapsed = formatElapsed(snapshot.sessionStartMs, timeStampMs),
        status = status.takeIf { mine },
        statusLabel = if (!mine) null else when (status) {
            MessageStatus.PENDING -> "Pendiente"
            MessageStatus.DELIVERED -> "Entregado"
            MessageStatus.FAILED -> "Fallido"
        },
        signal = q?.let {
            "RSSI ${formatDecimal(it.rssiDbm.toDouble(), 0)} dBm · SNR ${formatDecimal(it.snrDb.toDouble())} dB · ${hopsLabel(it.hops)}"
        },
        level = level,
        levelLabel = level?.label(),
        validForMeasurement = validity == MeasurementValidity.VALID,
        validityNote = when (validity) {
            null, MeasurementValidity.VALID -> null
            MeasurementValidity.RELAYED -> "No válido para medición: retransmitido, RSSI y SNR son del último salto"
            MeasurementValidity.UNKNOWN_HOPS -> "No válido para medición: el firmware no informa los saltos"
            MeasurementValidity.VIA_MQTT -> "No válido para medición: llegó por MQTT"
        },
    )
}

class MessagingViewModel(
    private val repository: MeshRepository,
    private val settings: LinkSettingsRepository,
    private val scanner: NodeScanner,
    private val sessionStartMs: Long,
) : ViewModel(), MessagingActions {

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

    private data class Criteria(
        val parameters: LinkParameters,
        val thresholds: LinkThresholds,
        val acceptUnknownHops: Boolean,
    )

    private val local = MutableStateFlow(Local())

    private val link = combine(
        repository.state,
        repository.remoteNodes,
        repository.firmwareVersion,
        repository.invalidPackets,
        ::Link,
    )

    private val criteria = combine(settings.parameters, settings.thresholds, settings.acceptUnknownHops, ::Criteria)

    val uiState: StateFlow<MessagingUiState> =
        combine(link, repository.messages, criteria, local) { link, messages, criteria, local ->
            MessagingSnapshot(
                state = link.state,
                nodes = link.nodes,
                messages = messages,
                parameters = criteria.parameters,
                thresholds = criteria.thresholds,
                acceptUnknownHops = criteria.acceptUnknownHops,
                firmware = link.firmware,
                invalidPackets = link.invalidPackets,
                draft = local.draft,
                selectedDestination = local.selectedDestination,
                devices = local.devices,
                busy = local.busy,
                error = local.error,
                sessionStartMs = sessionStartMs,
            ).toUiState()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MessagingUiState())

    override fun onDraftChange(text: String) = local.update { it.copy(draft = text) }

    override fun onDestinationSelected(num: Int) = local.update { it.copy(selectedDestination = num) }

    override fun dismissError() = local.update { it.copy(error = null) }

    override fun scan() = runBusy("No se pudieron buscar nodos") {
        val result = scanner.scan()
        val error = when {
            result.problems.isNotEmpty() -> result.problems.joinToString("\n")
            result.devices.isEmpty() -> "No se encontró ningún nodo"
            else -> null
        }
        local.update { it.copy(devices = result.devices, error = error) }
    }

    override fun connect(device: RadioDevice) = runBusy("No se pudo conectar") {
        repository.connect(device.address)
    }

    override fun disconnect() = runBusy("No se pudo desconectar") {
        repository.disconnect()
    }

    override fun send() {
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

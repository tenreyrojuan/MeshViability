package com.example.meshviability.data.repository

import com.example.meshviability.data.mapper.toDeliveryUpdateOrNull
import com.example.meshviability.data.mapper.toLoraSettings
import com.example.meshviability.data.mapper.toMessageOrNull
import com.example.meshviability.data.protocol.MeshProtocolClient
import com.example.meshviability.data.protocol.MeshState
import com.example.meshviability.domain.entities.DeliveryUpdate
import com.example.meshviability.domain.entities.LoraSettings
import com.example.meshviability.domain.entities.MeshNode
import com.example.meshviability.domain.entities.Message
import com.example.meshviability.domain.entities.MessageStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import org.meshtastic.proto.MeshPacket

/**
 * Única fuente de verdad para la pantalla. Guarda todo en memoria.
 * now devuelve la hora actual en milisegundos; se recibe como parámetro
 * para poder usar una hora fija en los tests.
 */
class MeshRepository(
    private val client: MeshProtocolClient,
    scope: CoroutineScope,
    private val now: () -> Long,
) {

    val state: StateFlow<MeshState> = client.state

    val loraSettings: StateFlow<LoraSettings?> = client.loraConfig
        .map { it?.toLoraSettings() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    // los demás nodos, sin el nuestro
    val remoteNodes: StateFlow<List<MeshNode>> = combine(client.nodes, client.state) { nodes, state ->
        val me = (state as? MeshState.Ready)?.myNodeNum
        nodes.values.filter { it.num != me }.sortedBy { it.longName }
    }.stateIn(scope, SharingStarted.Eagerly, emptyList())

    val firmwareVersion: StateFlow<String?> = client.firmwareVersion
    val invalidPackets: StateFlow<Int> = client.invalidPackets

    private val _messages = MutableStateFlow<List<Message>>(emptyList())
    val messages: StateFlow<List<Message>> = _messages.asStateFlow()

    init {
        client.start()
        client.packets
            .onEach(::handlePacket)
            .launchIn(scope)
    }

    private fun handlePacket(packet: MeshPacket) {
        // ¿es un mensaje de texto? → agregarlo a la lista
        packet.toMessageOrNull(receivedAt = now())?.let { msg ->
            _messages.update { it + msg }
        }

        // ¿es una confirmación? → actualizar el estado del mensaje original
        packet.toDeliveryUpdateOrNull()?.let { update ->
            _messages.update { list ->
                list.map { msg -> if (update.appliesTo(msg)) msg.copy(status = update.status) else msg }
            }
        }
    }

    // En unicast, solo el ACK del destinatario confirma la entrega; el de otro nodo
    // (el nuestro o un repetidor) solo dice que el paquete salió a la malla.
    private fun DeliveryUpdate.appliesTo(msg: Message): Boolean {
        if (msg.id != messageId || msg.status != MessageStatus.PENDING) return false
        if (status == MessageStatus.FAILED) return true
        return msg.toNodeId == MeshProtocolClient.BROADCAST || fromNodeId == msg.toNodeId
    }

    suspend fun connect(address: String) = client.connect(address)

    suspend fun disconnect() = client.disconnect()

    suspend fun sendText(text: String, to: Int = MeshProtocolClient.BROADCAST) {
        val id = client.sendText(text, to)
        val myNodeId = (client.state.value as? MeshState.Ready)?.myNodeNum ?: 0
        val mine = Message(
            id = id,
            fromNodeId = myNodeId,
            toNodeId = to,
            content = text,
            timeStampMs = now(),
            status = MessageStatus.PENDING,
            signalQuality = null,             // los mensajes propios no se miden
        )
        _messages.update { it + mine }
    }

}

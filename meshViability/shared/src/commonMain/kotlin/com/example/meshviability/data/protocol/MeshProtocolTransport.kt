package com.example.meshviability.data.protocol

import com.example.meshviability.data.transport.RadioTransport
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import okio.ByteString.Companion.toByteString
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.ToRadio
import kotlin.random.Random

// estados del protocolo (distinto del estado de la conexión física)
sealed interface MeshState {
    data object Disconnected : MeshState
    data object Configuring : MeshState            // handshake en curso
    data class Ready(val myNodeNum: Int) : MeshState  // listo para enviar
}

/** Habla el protocolo Meshtastic sobre cualquier RadioTransport. */
class MeshProtocolClient(
    private val transport: RadioTransport,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<MeshState>(MeshState.Disconnected)
    val state = _state.asStateFlow()

    // paquetes recibidos de la malla
    private val _packets = MutableSharedFlow<MeshPacket>(extraBufferCapacity = 64)
    val packets = _packets.asSharedFlow()

    // configuración LoRa del nodo (llega durante el handshake)
    private val _loraConfig = MutableStateFlow<Config.LoRaConfig?>(null)
    val loraConfig = _loraConfig.asStateFlow()

    private var configId = 0
    private var myNodeNum: Int? = null

    /** Empieza a escuchar al nodo. Llamar una vez, antes de connect(). */
    fun start() {
        transport.incoming
            .map { FromRadio.ADAPTER.decode(it) }   // bytes -> FromRadio
            .onEach(::handle)                        // procesar cada mensaje
            .launchIn(scope)
    }

    /** Conecta y arranca el handshake pidiendo la configuración. */
    suspend fun connect(address: String) {
        transport.connect(address)
        _state.value = MeshState.Configuring
        configId = Random.nextInt(1, Int.MAX_VALUE)
        transport.send(ToRadio(want_config_id = configId).encode())
    }

    private fun handle(msg: FromRadio) {
        // info de nuestro nodo
        msg.my_info?.let { myNodeNum = it.my_node_num }
        // fin del handshake
        msg.config_complete_id?.let { id ->
            if (id == configId) _state.value = MeshState.Ready(myNodeNum ?: 0)
        }
        // mensaje que llegó por la malla
        msg.packet?.let { _packets.tryEmit(it) }

        // configuración de radio
        msg.config?.lora?.let { _loraConfig.value = it }
    }

    /** Envía un texto. Por defecto a todos (broadcast) en el canal 0. */
    /** Envía un texto y devuelve el ID del paquete. Por defecto a todos (broadcast) en el canal 0. */
    suspend fun sendText(text: String, to: Int = BROADCAST, channel: Int = 0): Int {
        check(_state.value is MeshState.Ready) { "El nodo todavía no terminó el handshake" }
        val id = Random.nextInt(1, Int.MAX_VALUE)
        val packet = MeshPacket(
            to = to,
            channel = channel,
            want_ack = true,
            id = id,
            decoded = Data(
                portnum = PortNum.TEXT_MESSAGE_APP,
                payload = text.encodeToByteArray().toByteString(),
            ),
        )
        transport.send(ToRadio(packet = packet).encode())

        return id
    }

    companion object {
        const val BROADCAST = -1   // 0xFFFFFFFF como Int con signo
    }
}
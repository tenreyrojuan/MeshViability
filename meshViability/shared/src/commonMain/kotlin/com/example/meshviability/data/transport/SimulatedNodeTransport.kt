package com.example.meshviability.data.transport

import com.example.meshviability.domain.link.LinkParameters
import com.example.meshviability.domain.link.predict
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.proto.Config
import org.meshtastic.proto.Data
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import org.meshtastic.proto.ToRadio
import org.meshtastic.proto.User
import kotlin.math.min
import kotlin.random.Random

/**
 * Nodo A con un nodo B a 1 km, para probar la app sin placas. B confirma cada mensaje
 * y contesta con un eco; la señal sale del balance teórico más una pérdida extra al azar,
 * y a veces la respuesta llega retransmitida (no válida para medición).
 */
class SimulatedNodeTransport(
    private val scope: CoroutineScope,
    private val random: Random = Random.Default,
) : RadioTransport, DeviceDiscovery {

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState = _state.asStateFlow()

    private val channel = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: Flow<ByteArray> = channel.receiveAsFlow()

    private val link = LinkParameters()

    override suspend fun devices() = listOf(RadioDevice(ADDRESS, "Nodo simulado (sin hardware)"))

    override suspend fun connect(address: String) {
        _state.value = ConnectionState.Connecting
        delay(CONNECT_DELAY_MS)
        _state.value = ConnectionState.Connected
    }

    override suspend fun disconnect() {
        _state.value = ConnectionState.Disconnected
    }

    override suspend fun send(bytes: ByteArray) {
        if (_state.value != ConnectionState.Connected) return
        val msg = ToRadio.ADAPTER.decode(bytes)
        msg.want_config_id?.let(::handshake)
        msg.packet?.takeIf { it.decoded?.portnum == PortNum.TEXT_MESSAGE_APP }?.let(::answer)
    }

    private fun push(msg: FromRadio) {
        channel.trySend(msg.encode())
    }

    private fun handshake(configId: Int) {
        push(FromRadio(my_info = MyNodeInfo(my_node_num = NODE_A)))
        push(FromRadio(metadata = DeviceMetadata(firmware_version = "simulado")))
        push(FromRadio(node_info = NodeInfo(num = NODE_A, user = User(long_name = "Nodo A (simulado)", short_name = "A"))))
        push(FromRadio(node_info = NodeInfo(num = NODE_B, user = User(long_name = "Nodo B (simulado)", short_name = "B"), hops_away = 0)))
        push(
            FromRadio(
                config = Config(
                    lora = Config.LoRaConfig(
                        use_preset = true,
                        modem_preset = Config.LoRaConfig.ModemPreset.LONG_FAST,
                        tx_power = link.txPowerDbm.toInt(),
                        region = Config.LoRaConfig.RegionCode.US,
                    ),
                ),
            ),
        )
        push(FromRadio(config_complete_id = configId))
    }

    private fun answer(packet: MeshPacket) {
        val text = packet.decoded?.payload?.utf8() ?: return
        scope.launch {
            delay(ACK_DELAY_MS)
            if (packet.to != NODE_B && packet.to != BROADCAST) return@launch
            val lost = random.nextDouble() < LOSS_PROBABILITY
            push(ack(packet.id, from = if (lost) NODE_A else NODE_B, lost = lost))
            if (lost) return@launch

            delay(REPLY_DELAY_MS)
            push(reply("eco: $text"))
        }
    }

    private fun ack(requestId: Int, from: Int, lost: Boolean) = FromRadio(
        packet = MeshPacket(
            from = from,
            to = NODE_A,
            decoded = Data(
                portnum = PortNum.ROUTING_APP,
                request_id = requestId,
                payload = Routing(
                    error_reason = if (lost) Routing.Error.MAX_RETRANSMIT else Routing.Error.NONE,
                ).encodeByteString(),
            ),
        ),
    )

    private fun reply(text: String): FromRadio {
        val expected = link.predict().rxPowerDbm
        val rssi = expected - random.nextDouble(MIN_EXTRA_LOSS_DB, MAX_EXTRA_LOSS_DB)
        // el SNR que informa el SX1262 se satura cerca de +10 dB
        val snr = min(rssi - (link.predict().noiseDbm + link.noiseFigureDb), MAX_SNR_DB) + random.nextDouble(-1.0, 1.0)
        val relayed = random.nextDouble() < RELAY_PROBABILITY
        return FromRadio(
            packet = MeshPacket(
                from = NODE_B,
                to = NODE_A,
                id = random.nextInt(1, Int.MAX_VALUE),
                rx_rssi = rssi.toInt(),
                rx_snr = snr.toFloat(),
                hop_start = HOP_START,
                hop_limit = if (relayed) HOP_START - 1 else HOP_START,
                decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = text.encodeUtf8()),
            ),
        )
    }

    companion object {
        const val ADDRESS = "simulado"
        const val NODE_A = 0x0A0A0A0A
        const val NODE_B = 0x0B0B0B0B
        private const val BROADCAST = -1
        private const val HOP_START = 3
        private const val CONNECT_DELAY_MS = 300L
        private const val ACK_DELAY_MS = 800L
        private const val REPLY_DELAY_MS = 700L
        private const val LOSS_PROBABILITY = 0.1
        private const val RELAY_PROBABILITY = 0.2
        private const val MIN_EXTRA_LOSS_DB = 15.0
        private const val MAX_EXTRA_LOSS_DB = 55.0
        private const val MAX_SNR_DB = 10.0
    }
}

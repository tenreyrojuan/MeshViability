package com.example.meshviability.data.transport

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.meshtastic.proto.Config
import org.meshtastic.proto.DeviceMetadata
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.NodeInfo
import org.meshtastic.proto.ToRadio
import org.meshtastic.proto.User

/**
 * Simulacion del transporte de datos
 */

// los números de nodo de Meshtastic son de 32 bits; se suelen mostrar en hexadecimal
class FakeTransport(
    private val fakeNodeValue: Int = 0x1234,
    private val remoteNodeValue: Int = 0x5678,
    private val connectError: String? = null,
) : RadioTransport {

    // estado inicial de la conexion. Desconectado
    // los dos representan lo mismo, pero por motivos de
    // seguridad y encapsulamiento se crea el valor y
    // la "vista", respectivamente
    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState = _state.asStateFlow()

    // canal que recibe los datos.
    // los dos representan lo mismo, pero siguiendo
    // el mismo principio de arriba
    private val _channel = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: Flow<ByteArray> = _channel.receiveAsFlow()

    val sent = mutableListOf<ToRadio>()

    fun push(msg: FromRadio) {
        _channel.trySend(msg.encode())
    }

    fun pushRaw(bytes: ByteArray) {
        _channel.trySend(bytes)
    }

    // simula que se cae el enlace con el nodo (Bluetooth o USB)
    fun drop(error: String? = null) {
        _state.value = error?.let { ConnectionState.ConnectionError(it) } ?: ConnectionState.Disconnected
    }

    override suspend fun connect(address: String) {
        connectError?.let { throw IllegalStateException(it) }
        _state.value = ConnectionState.Connected
    }

    override suspend fun send(bytes: ByteArray) {
        val msg = ToRadio.ADAPTER.decode(bytes)
        sent += msg

        msg.want_config_id?.let { id ->
            push(FromRadio(my_info = MyNodeInfo(my_node_num = fakeNodeValue)))
            push(FromRadio(metadata = DeviceMetadata(firmware_version = FIRMWARE_VERSION)))
            push(FromRadio(node_info = NodeInfo(num = fakeNodeValue, user = User(long_name = "Nodo A", short_name = "A"))))
            push(
                FromRadio(
                    node_info = NodeInfo(
                        num = remoteNodeValue,
                        user = User(long_name = "Nodo B", short_name = "B"),
                        hops_away = 0,
                    ),
                ),
            )
            push(
                FromRadio(
                    config = Config(
                        lora = Config.LoRaConfig(
                            use_preset = true,
                            modem_preset = Config.LoRaConfig.ModemPreset.LONG_FAST,
                            tx_power = 20,
                            region = Config.LoRaConfig.RegionCode.US,
                        ),
                    ),
                ),
            )
            push(FromRadio(config_complete_id = id))
        }
    }

    override suspend fun disconnect() {
        _state.value = ConnectionState.Disconnected
    }

    companion object {
        const val FIRMWARE_VERSION = "2.6.11.fake"
    }
}

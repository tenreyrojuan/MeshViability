package com.example.meshviability.data.transport

import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import org.meshtastic.proto.Config
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MyNodeInfo
import org.meshtastic.proto.ToRadio

/**
 * Simulacion del transporte de datos
 */

// de momento no tengo idea porque necesito un hexadecimal como valor falso
class FakeTransport(private val fakeNodeValue : Int = 0x1234) : RadioTransport {

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

    override suspend fun connect(address: String) {
        _state.value = ConnectionState.Connected
    }

    override suspend fun send(bytes: ByteArray) {
        val msg = ToRadio.ADAPTER.decode(bytes)
        sent += msg

        msg.want_config_id?.let { id ->
            msg.want_config_id?.let { id ->
                push(FromRadio(my_info = MyNodeInfo(my_node_num = fakeNodeValue)))
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
    }

    override suspend fun disconnect() {
        _state.value = ConnectionState.Disconnected
    }
}
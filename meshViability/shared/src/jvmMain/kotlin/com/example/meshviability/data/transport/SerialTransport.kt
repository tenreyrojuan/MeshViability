package com.example.meshviability.data.transport

import com.fazecast.jSerialComm.SerialPort
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Puertos serie de la PC. La Heltec V3 aparece como CP2102 (/dev/ttyUSB0 en Linux, COMx en Windows). */
object SerialDiscovery : DeviceDiscovery {
    override suspend fun devices(): List<RadioDevice> = withContext(Dispatchers.IO) {
        SerialPort.getCommPorts().map { RadioDevice(it.systemPortPath, "${it.descriptivePortName} (${it.systemPortName})") }
    }
}

/** Notebook ↔ nodo por USB, con la API de stream de Meshtastic a 115200 baudios. */
class SerialTransport : RadioTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    private val channel = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: Flow<ByteArray> = channel.receiveAsFlow()

    @Volatile private var port: SerialPort? = null
    private var reader: Job? = null

    override suspend fun connect(address: String) = withContext(Dispatchers.IO) {
        disconnect()
        _state.value = ConnectionState.Connecting
        val p = SerialPort.getCommPort(address)
        p.setComPortParameters(BAUD_RATE, DATA_BITS, SerialPort.ONE_STOP_BIT, SerialPort.NO_PARITY)
        p.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, READ_TIMEOUT_MS, 0)
        if (!p.openPort()) {
            val reason = "No se pudo abrir $address. ¿Está conectado el cable? En Linux, ¿tu usuario está en el grupo dialout?"
            _state.value = ConnectionState.ConnectionError(reason)
            throw RadioUnavailableException(reason)
        }
        port = p
        write(p, StreamFraming.WAKE)
        reader = scope.launch { readLoop(p) }
        _state.value = ConnectionState.Connected
    }

    private fun CoroutineScope.readLoop(p: SerialPort) {
        val deframer = StreamDeframer()
        val buffer = ByteArray(READ_BUFFER)
        while (isActive) {
            val n = p.readBytes(buffer, buffer.size)
            if (n < 0) {
                if (port === p) {
                    close()
                    _state.value = ConnectionState.ConnectionError("Se perdió la conexión USB con el nodo")
                }
                return
            }
            deframer.feed(buffer, n).forEach { channel.trySend(it) }
        }
    }

    override suspend fun send(bytes: ByteArray) = withContext(Dispatchers.IO) {
        val p = port ?: throw RadioUnavailableException("Nodo desconectado")
        write(p, StreamFraming.frame(bytes))
    }

    private fun write(p: SerialPort, bytes: ByteArray) {
        if (p.writeBytes(bytes, bytes.size) != bytes.size) {
            throw RadioUnavailableException("No se pudo escribir en el puerto USB")
        }
    }

    override suspend fun disconnect() {
        reader?.cancel()
        reader = null
        close()
        _state.value = ConnectionState.Disconnected
    }

    private fun close() {
        port?.closePort()
        port = null
    }

    private companion object {
        const val BAUD_RATE = 115_200
        const val DATA_BITS = 8
        const val READ_TIMEOUT_MS = 200
        const val READ_BUFFER = 1024
    }
}

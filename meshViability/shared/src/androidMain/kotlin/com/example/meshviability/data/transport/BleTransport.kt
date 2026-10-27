package com.example.meshviability.data.transport

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeout
import java.util.UUID

// API Bluetooth de Meshtastic (https://meshtastic.org/docs/development/device/client-api)
internal object MeshtasticBle {
    val SERVICE: UUID = UUID.fromString("6ba1b218-15a8-461f-9fa8-5dcae273eafd")
    val TO_RADIO: UUID = UUID.fromString("f75c76d2-129e-4dad-a1dd-7866124401e7")
    val FROM_RADIO: UUID = UUID.fromString("2c55e69e-4993-11ed-b878-0242ac120002")
    val FROM_NUM: UUID = UUID.fromString("ed9da18c-a800-4f66-a670-aa7547e34453")
    val CCCD: UUID = UUID.fromString("00002902-0000-1000-8000-00805f9b34fb")
}

internal fun bluetoothAdapter(context: Context): BluetoothAdapter {
    if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
        throw RadioUnavailableException("Este dispositivo no tiene Bluetooth LE")
    }
    val adapter = context.getSystemService(BluetoothManager::class.java)?.adapter
        ?: throw RadioUnavailableException("Este dispositivo no tiene Bluetooth")
    if (!adapter.isEnabled) throw RadioUnavailableException("Bluetooth apagado: activalo y volvé a buscar")
    return adapter
}

/**
 * Teléfono ↔ nodo por Bluetooth LE. Se escribe ToRadio; cuando el nodo avisa por FromNum
 * (o después de escribir) se lee FromRadio hasta que vuelve vacío.
 * Los permisos se piden en BleDiscovery, antes de que exista un nodo al que conectarse.
 */
@SuppressLint("MissingPermission")
class BleTransport(private val context: Context) : RadioTransport {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _state = MutableStateFlow<ConnectionState>(ConnectionState.Disconnected)
    override val connectionState: StateFlow<ConnectionState> = _state.asStateFlow()

    private val channel = Channel<ByteArray>(Channel.UNLIMITED)
    override val incoming: Flow<ByteArray> = channel.receiveAsFlow()

    private var gatt: BluetoothGatt? = null
    private var toRadio: BluetoothGattCharacteristic? = null
    private var fromRadio: BluetoothGattCharacteristic? = null

    // Android admite una sola operación GATT a la vez
    private val opMutex = Mutex()
    @Volatile private var pending: CompletableDeferred<ByteArray?>? = null
    @Volatile private var linkUp: CompletableDeferred<Unit>? = null

    private val drainRequests = Channel<Unit>(Channel.CONFLATED)
    private var drainJob: Job? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(g: BluetoothGatt, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED && status == BluetoothGatt.GATT_SUCCESS) {
                linkUp?.complete(Unit)
                return
            }
            if (newState != BluetoothProfile.STATE_DISCONNECTED) return
            val reason = if (status == BluetoothGatt.GATT_SUCCESS) null else "Se perdió la conexión Bluetooth (código $status)"
            val error = RadioUnavailableException(reason ?: "El nodo cerró la conexión")
            linkUp?.completeExceptionally(error)
            pending?.completeExceptionally(error)
            release()
            _state.value = reason?.let { ConnectionState.ConnectionError(it) } ?: ConnectionState.Disconnected
        }

        override fun onMtuChanged(g: BluetoothGatt, mtu: Int, status: Int) = finish(BluetoothGatt.GATT_SUCCESS, null)
        override fun onServicesDiscovered(g: BluetoothGatt, status: Int) = finish(status, null)
        override fun onDescriptorWrite(g: BluetoothGatt, d: BluetoothGattDescriptor, status: Int) = finish(status, null)
        override fun onCharacteristicWrite(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) = finish(status, null)

        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray, status: Int) =
            finish(status, value)

        @Deprecated("Android < 13")
        @Suppress("DEPRECATION")
        override fun onCharacteristicRead(g: BluetoothGatt, c: BluetoothGattCharacteristic, status: Int) =
            finish(status, c.value ?: ByteArray(0))

        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic, value: ByteArray) {
            if (c.uuid == MeshtasticBle.FROM_NUM) drainRequests.trySend(Unit)
        }

        @Deprecated("Android < 13")
        override fun onCharacteristicChanged(g: BluetoothGatt, c: BluetoothGattCharacteristic) {
            if (c.uuid == MeshtasticBle.FROM_NUM) drainRequests.trySend(Unit)
        }
    }

    private fun finish(status: Int, value: ByteArray?) {
        val op = pending ?: return
        pending = null
        if (status == BluetoothGatt.GATT_SUCCESS) op.complete(value)
        else op.completeExceptionally(RadioUnavailableException("Error de Bluetooth (código $status)"))
    }

    private suspend fun gattOp(start: () -> Boolean): ByteArray? = opMutex.withLock {
        val op = CompletableDeferred<ByteArray?>()
        pending = op
        if (!start()) {
            pending = null
            throw RadioUnavailableException("Android rechazó la operación Bluetooth")
        }
        withTimeout(OP_TIMEOUT_MS) { op.await() }
    }

    override suspend fun connect(address: String) {
        disconnect()
        _state.value = ConnectionState.Connecting
        try {
            withTimeout(CONNECT_TIMEOUT_MS) { openGatt(address) }
        } catch (e: TimeoutCancellationException) {
            fail("El nodo no respondió por Bluetooth")
        } catch (e: CancellationException) {
            release()
            throw e
        } catch (e: Exception) {
            fail(e.message ?: "No se pudo conectar por Bluetooth")
        }
        drainJob = scope.launch { for (request in drainRequests) drain() }
        _state.value = ConnectionState.Connected
    }

    private fun fail(reason: String): Nothing {
        release()
        _state.value = ConnectionState.ConnectionError(reason)
        throw RadioUnavailableException(reason)
    }

    private suspend fun openGatt(address: String) {
        val device = bluetoothAdapter(context).getRemoteDevice(address)
        val up = CompletableDeferred<Unit>()
        linkUp = up
        val g = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw RadioUnavailableException("No se pudo abrir la conexión Bluetooth")
        gatt = g
        up.await()

        gattOp { g.requestMtu(MTU) }
        gattOp { g.discoverServices() }
        val service = g.getService(MeshtasticBle.SERVICE)
            ?: throw RadioUnavailableException("El dispositivo no es un nodo Meshtastic")
        val fromNum = service.getCharacteristic(MeshtasticBle.FROM_NUM)
        toRadio = service.getCharacteristic(MeshtasticBle.TO_RADIO)
        fromRadio = service.getCharacteristic(MeshtasticBle.FROM_RADIO)
        if (fromNum == null || toRadio == null || fromRadio == null) {
            throw RadioUnavailableException("El firmware del nodo no tiene la API Bluetooth esperada")
        }

        g.setCharacteristicNotification(fromNum, true)
        val cccd = fromNum.getDescriptor(MeshtasticBle.CCCD)
            ?: throw RadioUnavailableException("El nodo no permite notificaciones")
        gattOp { g.writeDescriptorCompat(cccd, BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE) }
    }

    override suspend fun send(bytes: ByteArray) {
        val g = gatt ?: throw RadioUnavailableException("Nodo desconectado")
        val c = toRadio ?: throw RadioUnavailableException("Nodo desconectado")
        gattOp { g.writeCharacteristicCompat(c, bytes) }
        drainRequests.trySend(Unit)
    }

    private suspend fun drain() {
        val g = gatt ?: return
        val c = fromRadio ?: return
        try {
            while (true) {
                val bytes = gattOp { g.readCharacteristic(c) }
                if (bytes == null || bytes.isEmpty()) break
                channel.trySend(bytes)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // si se cortó el enlace, onConnectionStateChange informa el estado
        }
    }

    override suspend fun disconnect() {
        drainJob?.cancel()
        drainJob = null
        gatt?.disconnect()
        release()
        _state.value = ConnectionState.Disconnected
    }

    private fun release() {
        gatt?.close()
        gatt = null
        toRadio = null
        fromRadio = null
    }

    private fun BluetoothGatt.writeCharacteristicCompat(c: BluetoothGattCharacteristic, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            writeCharacteristic(c, value, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                c.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
                c.value = value
                writeCharacteristic(c)
            }
        }

    private fun BluetoothGatt.writeDescriptorCompat(d: BluetoothGattDescriptor, value: ByteArray): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            writeDescriptor(d, value) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            run {
                d.value = value
                writeDescriptor(d)
            }
        }

    private companion object {
        const val MTU = 512
        const val OP_TIMEOUT_MS = 5_000L
        const val CONNECT_TIMEOUT_MS = 20_000L
    }
}

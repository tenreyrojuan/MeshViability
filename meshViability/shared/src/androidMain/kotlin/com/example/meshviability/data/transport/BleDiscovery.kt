package com.example.meshviability.data.transport

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult as BleScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.ParcelUuid
import kotlinx.coroutines.delay
import java.util.concurrent.ConcurrentHashMap

/** Permisos de Bluetooth. La Activity actual registra cómo pedirlos (cambia al rotar). */
class BlePermissions(private val context: Context) {

    var requester: (suspend (Array<String>) -> Boolean)? = null

    val required: Array<String>
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            // hasta Android 11 buscar dispositivos BLE requiere permiso de ubicación
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    fun granted(): Boolean = required.all { context.checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED }

    suspend fun ensure(): Boolean = granted() || (requester?.invoke(required) == true && granted())
}

/** Busca nodos Meshtastic que anuncian su servicio BLE. Un nodo conectado a otro teléfono no se anuncia. */
@SuppressLint("MissingPermission")
class BleDiscovery(private val context: Context, private val permissions: BlePermissions) : DeviceDiscovery {

    override suspend fun devices(): List<RadioDevice> {
        if (!permissions.ensure()) {
            throw RadioUnavailableException("Falta el permiso de Bluetooth: habilitalo en Ajustes > Apps")
        }
        val scanner = bluetoothAdapter(context).bluetoothLeScanner
            ?: throw RadioUnavailableException("Bluetooth apagado: activalo y volvé a buscar")

        val found = ConcurrentHashMap<String, RadioDevice>()
        var failure: Int? = null
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: BleScanResult) {
                val d = result.device
                found[d.address] = RadioDevice(d.address, result.scanRecord?.deviceName ?: d.name ?: d.address)
            }

            override fun onScanFailed(errorCode: Int) {
                failure = errorCode
            }
        }

        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(MeshtasticBle.SERVICE)).build()
        val settings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        scanner.startScan(listOf(filter), settings, callback)
        try {
            delay(SCAN_DURATION_MS)
        } finally {
            scanner.stopScan(callback)
        }
        failure?.let { throw RadioUnavailableException("Falló la búsqueda por Bluetooth (código $it)") }
        return found.values.sortedBy { it.name }
    }

    private companion object {
        const val SCAN_DURATION_MS = 4_000L
    }
}

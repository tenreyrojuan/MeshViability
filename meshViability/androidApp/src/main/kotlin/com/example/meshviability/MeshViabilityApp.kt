package com.example.meshviability

import android.app.Application
import com.example.meshviability.data.transport.BleDiscovery
import com.example.meshviability.data.transport.BlePermissions
import com.example.meshviability.data.transport.BleTransport
import com.example.meshviability.data.transport.RadioLink

// el Bluetooth vive mientras viva el proceso, no la Activity (que se recrea al rotar)
class MeshViabilityApp : Application() {
    val blePermissions by lazy { BlePermissions(this) }
    val radioLinks by lazy { listOf(RadioLink(BleTransport(this), BleDiscovery(this, blePermissions))) }
}

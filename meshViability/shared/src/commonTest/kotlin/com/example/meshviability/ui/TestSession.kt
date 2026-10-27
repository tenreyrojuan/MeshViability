package com.example.meshviability.ui

import com.example.meshviability.data.protocol.MeshProtocolClient
import com.example.meshviability.data.repository.LinkSettingsRepository
import com.example.meshviability.data.repository.MeshRepository
import com.example.meshviability.data.transport.DeviceDiscovery
import com.example.meshviability.data.transport.FakeTransport
import com.example.meshviability.data.transport.RadioDevice
import kotlinx.coroutines.CoroutineScope
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum

class TestSession(scope: CoroutineScope, connectError: String? = null) {
    val fake = FakeTransport(fakeNodeValue = 0x1234, remoteNodeValue = 0x5678, connectError = connectError)
    val repository = MeshRepository(MeshProtocolClient(fake, scope), scope, now = { 5_000L })
    val settings = LinkSettingsRepository(repository.loraSettings, scope)

    fun pushText(text: String, rssi: Int = -95, snr: Float = 4f, hopStart: Int = 3, hopLimit: Int = 3) {
        fake.push(
            FromRadio(
                packet = MeshPacket(
                    from = 0x5678,
                    to = 0x1234,
                    id = text.hashCode(),
                    rx_rssi = rssi,
                    rx_snr = snr,
                    hop_start = hopStart,
                    hop_limit = hopLimit,
                    decoded = Data(portnum = PortNum.TEXT_MESSAGE_APP, payload = text.encodeUtf8()),
                ),
            ),
        )
    }
}

class TestDiscovery(private val result: () -> List<RadioDevice>) : DeviceDiscovery {
    override suspend fun devices(): List<RadioDevice> = result()
}

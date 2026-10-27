package com.example.meshviability.data.protocol

import com.example.meshviability.data.transport.FakeTransport
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MeshProtocolClientTest {

    @Test
    fun handshakeTerminaEnReady() = runTest {
        val fake = FakeTransport(fakeNodeValue = 0x1234)
        val client = MeshProtocolClient(fake, backgroundScope)

        client.start()
        client.connect("fake")
        runCurrent()

        val state = assertIs<MeshState.Ready>(client.state.value)
        assertEquals(0x1234, state.myNodeNum)
    }

    @Test
    fun sendTextEnviaUnPaqueteDeTexto() = runTest {
        val fake = FakeTransport()
        val client = MeshProtocolClient(fake, backgroundScope)
        client.start()
        client.connect("fake")
        runCurrent()

        client.sendText("hola malla")

        val packet = fake.sent.last().packet!!
        assertEquals(PortNum.TEXT_MESSAGE_APP, packet.decoded!!.portnum)
        assertEquals("hola malla", packet.decoded!!.payload.utf8())
        assertEquals(MeshProtocolClient.BROADCAST, packet.to)
    }

    @Test
    fun paqueteInvalidoNoCortaLaRecepcion() = runTest {
        val fake = FakeTransport()
        val client = MeshProtocolClient(fake, backgroundScope)
        val recibidos = mutableListOf<MeshPacket>()
        backgroundScope.launch { client.packets.collect { recibidos += it } }
        client.start()
        client.connect("fake")
        runCurrent()

        fake.pushRaw(byteArrayOf(0xFF.toByte(), 0xFF.toByte(), 0xFF.toByte()))
        fake.push(FromRadio(packet = MeshPacket(from = 0x5678, id = 1)))
        runCurrent()

        assertEquals(1, client.invalidPackets.value)
        assertEquals(1, recibidos.size)
    }

    @Test
    fun caidaDelEnlacePasaADesconectado() = runTest {
        val fake = FakeTransport()
        val client = MeshProtocolClient(fake, backgroundScope)
        client.start()
        client.connect("fake")
        runCurrent()

        fake.drop()
        runCurrent()

        assertEquals(MeshState.Disconnected, client.state.value)
    }

    @Test
    fun errorDelTransportePasaAFallido() = runTest {
        val fake = FakeTransport()
        val client = MeshProtocolClient(fake, backgroundScope)
        client.start()
        client.connect("fake")
        runCurrent()

        fake.drop(error = "Puerto USB no disponible")
        runCurrent()

        assertEquals(MeshState.Failed("Puerto USB no disponible"), client.state.value)
    }

    @Test
    fun noPoderConectarPasaAFallido() = runTest {
        val client = MeshProtocolClient(FakeTransport(connectError = "Bluetooth apagado"), backgroundScope)
        client.start()
        client.connect("fake")
        runCurrent()

        assertEquals(MeshState.Failed("Bluetooth apagado"), client.state.value)
    }

    @Test
    fun elHandshakeTraeNodosYVersionDeFirmware() = runTest {
        val fake = FakeTransport(fakeNodeValue = 0x1234, remoteNodeValue = 0x5678)
        val client = MeshProtocolClient(fake, backgroundScope)
        client.start()
        client.connect("fake")
        runCurrent()

        assertEquals(FakeTransport.FIRMWARE_VERSION, client.firmwareVersion.value)
        assertEquals(setOf(0x1234, 0x5678), client.nodes.value.keys)
        assertEquals("Nodo B", client.nodes.value.getValue(0x5678).longName)
        assertEquals(0, client.nodes.value.getValue(0x5678).hopsAway)
    }

    @Test
    fun sendTextUnicastVaAlDestino() = runTest {
        val fake = FakeTransport()
        val client = MeshProtocolClient(fake, backgroundScope)
        client.start()
        client.connect("fake")
        runCurrent()

        client.sendText("hola B", to = 0x5678)

        assertEquals(0x5678, fake.sent.last().packet!!.to)
        assertTrue(fake.sent.last().packet!!.want_ack)
    }
}

package com.example.meshviability.data.protocol

import com.example.meshviability.data.transport.FakeTransport
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.meshtastic.proto.PortNum
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

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
}
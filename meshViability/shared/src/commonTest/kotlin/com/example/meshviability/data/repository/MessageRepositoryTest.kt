package com.example.meshviability.data.repository

import com.example.meshviability.data.protocol.MeshProtocolClient
import com.example.meshviability.data.transport.FakeTransport
import com.example.meshviability.domain.entities.MessageStatus
import com.example.meshviability.domain.link.ModemPreset
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import okio.ByteString.Companion.encodeUtf8
import org.meshtastic.proto.Data
import org.meshtastic.proto.FromRadio
import org.meshtastic.proto.MeshPacket
import org.meshtastic.proto.PortNum
import org.meshtastic.proto.Routing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class MeshRepositoryTest {

    // simula un paquete que llega desde la malla
    // eliminar en el futuro (cuando tengamos las placas)
    private fun paquete(portnum: PortNum, texto: String) = FromRadio(
        packet = MeshPacket(
            from = 0x5678,
            to = -1,
            id = 77,
            rx_rssi = -95,
            rx_snr = 4.25f,
            hop_start = 3,
            hop_limit = 3,
            decoded = Data(portnum = portnum, payload = texto.encodeUtf8()),
        ),
    )

    // simulacion de confirmacion. Esto entregaria el nodo
    // remover en el futuro.
    // es para hacer prubeas
    private fun confirmacion(requestId: Int, error: Routing.Error = Routing.Error.NONE, from: Int = 0x1234)
            = FromRadio(
        packet = MeshPacket(
            from = from,
            to = 0x1234,
            decoded = Data(
                portnum = PortNum.ROUTING_APP,
                request_id = requestId,
                payload = Routing(error_reason = error).encodeByteString(),
            ),
        ),
    )

    @Test
    fun mensajeRecibidoApareceEnLaLista() = runTest {
        val fake = FakeTransport()
        val repo = MeshRepository(MeshProtocolClient(fake, backgroundScope), backgroundScope, now = { 1000L })
        repo.connect("fake")
        runCurrent()

        fake.push(paquete(PortNum.TEXT_MESSAGE_APP, "hola desde B"))
        runCurrent()

        val msg = repo.messages.value.single()
        assertEquals("hola desde B", msg.content)
        assertEquals(0x5678, msg.fromNodeId)
        assertEquals(1000L, msg.timeStampMs)
        assertEquals(-95f, msg.signalQuality!!.rssiDbm)
        assertEquals(0, msg.signalQuality!!.hops)   // llegó directo
    }

    @Test
    fun mensajeEnviadoQuedaPendiente() = runTest {
        val fake = FakeTransport()
        val repo = MeshRepository(MeshProtocolClient(fake, backgroundScope), backgroundScope, now = { 1000L })
        repo.connect("fake")
        runCurrent()

        repo.sendText("hola malla")

        val msg = repo.messages.value.single()
        assertEquals("hola malla", msg.content)
        assertEquals(0x1234, msg.fromNodeId)          // el número del nodo falso
        assertEquals(MessageStatus.PENDING, msg.status)
        assertEquals(fake.sent.last().packet!!.id, msg.id)
        assertNull(msg.signalQuality)
    }

    @Test
    fun paquetesQueNoSonTextoSeIgnoran() = runTest {
        val fake = FakeTransport()
        val repo = MeshRepository(MeshProtocolClient(fake, backgroundScope), backgroundScope, now = { 1000L })
        repo.connect("fake")
        runCurrent()

        fake.push(paquete(PortNum.POSITION_APP, "no soy texto"))
        runCurrent()

        assertTrue(repo.messages.value.isEmpty())
    }

    @Test
    fun ackMarcaElMensajeComoEntregado() = runTest {
        val fake = FakeTransport()
        val repo = MeshRepository(MeshProtocolClient(fake, backgroundScope), backgroundScope, now = { 1000L })
        repo.connect("fake")
        runCurrent()

        repo.sendText("hola malla")
        val id = repo.messages.value.single().id

        fake.push(confirmacion(requestId = id))
        runCurrent()

        assertEquals(MessageStatus.DELIVERED, repo.messages.value.single().status)
    }

    @Test
    fun errorDeEntregaMarcaElMensajeComoFallido() = runTest {
        val fake = FakeTransport()
        val repo = MeshRepository(MeshProtocolClient(fake, backgroundScope), backgroundScope, now = { 1000L })
        repo.connect("fake")
        runCurrent()

        repo.sendText("hola malla")
        val id = repo.messages.value.single().id

        fake.push(confirmacion(requestId = id, error = Routing.Error.MAX_RETRANSMIT))
        runCurrent()

        assertEquals(MessageStatus.FAILED, repo.messages.value.single().status)
    }

    @Test
    fun ackDeOtroMensajeNoCambiaNada() = runTest {
        val fake = FakeTransport()
        val repo = MeshRepository(MeshProtocolClient(fake, backgroundScope), backgroundScope, now = { 1000L })
        repo.connect("fake")
        runCurrent()

        repo.sendText("hola malla")
        val id = repo.messages.value.single().id

        fake.push(confirmacion(requestId = id + 1))
        runCurrent()

        assertEquals(MessageStatus.PENDING, repo.messages.value.single().status)
    }

    @Test
    fun laConfiguracionLoraLlegaEnElHandshake() = runTest {
        val fake = FakeTransport()
        val repo = MeshRepository(MeshProtocolClient(fake, backgroundScope), backgroundScope, now = { 1000L })
        repo.connect("fake")
        runCurrent()

        val settings = repo.loraSettings.value
        assertEquals(ModemPreset.LONG_FAST, settings!!.preset)
        assertEquals(20, settings!!.txPowerDbm)
    }

    @Test
    fun unicastSeConfirmaConElAckDelDestinatario() = runTest {
        val fake = FakeTransport()
        val repo = MeshRepository(MeshProtocolClient(fake, backgroundScope), backgroundScope, now = { 1000L })
        repo.connect("fake")
        runCurrent()

        repo.sendText("hola B", to = 0x5678)
        val msg = repo.messages.value.single()
        assertEquals(0x5678, msg.toNodeId)

        fake.push(confirmacion(requestId = msg.id, from = 0x1234))
        runCurrent()
        assertEquals(MessageStatus.PENDING, repo.messages.value.single().status)

        fake.push(confirmacion(requestId = msg.id, from = 0x5678))
        runCurrent()
        assertEquals(MessageStatus.DELIVERED, repo.messages.value.single().status)
    }

    @Test
    fun unicastFallaAunqueElErrorLoInformeNuestroNodo() = runTest {
        val fake = FakeTransport()
        val repo = MeshRepository(MeshProtocolClient(fake, backgroundScope), backgroundScope, now = { 1000L })
        repo.connect("fake")
        runCurrent()

        repo.sendText("hola B", to = 0x5678)
        val id = repo.messages.value.single().id
        fake.push(confirmacion(requestId = id, error = Routing.Error.MAX_RETRANSMIT, from = 0x1234))
        runCurrent()

        assertEquals(MessageStatus.FAILED, repo.messages.value.single().status)
    }

    @Test
    fun confirmacionCorruptaSeIgnora() = runTest {
        val fake = FakeTransport()
        val repo = MeshRepository(MeshProtocolClient(fake, backgroundScope), backgroundScope, now = { 1000L })
        repo.connect("fake")
        runCurrent()

        repo.sendText("hola malla")
        val id = repo.messages.value.single().id
        fake.push(
            FromRadio(
                packet = MeshPacket(
                    from = 0x1234,
                    decoded = Data(
                        portnum = PortNum.ROUTING_APP,
                        request_id = id,
                        payload = okio.ByteString.of(0xFF.toByte(), 0xFF.toByte()),
                    ),
                ),
            ),
        )
        fake.push(paquete(PortNum.TEXT_MESSAGE_APP, "sigo vivo"))
        runCurrent()

        assertEquals(MessageStatus.PENDING, repo.messages.value.first().status)
        assertEquals("sigo vivo", repo.messages.value.last().content)
    }

    @Test
    fun losNodosRemotosNoIncluyenAlNuestro() = runTest {
        val fake = FakeTransport(fakeNodeValue = 0x1234, remoteNodeValue = 0x5678)
        val repo = MeshRepository(MeshProtocolClient(fake, backgroundScope), backgroundScope, now = { 1000L })
        repo.connect("fake")
        runCurrent()

        assertEquals(listOf(0x5678), repo.remoteNodes.value.map { it.num })
    }
}

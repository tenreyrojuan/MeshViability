package com.example.meshviability.data.transport

import com.example.meshviability.data.protocol.MeshProtocolClient
import com.example.meshviability.data.protocol.MeshState
import com.example.meshviability.data.repository.MeshRepository
import com.example.meshviability.domain.entities.MessageStatus
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class MultiTransportTest {

    private class Discovery(private val result: () -> List<RadioDevice>) : DeviceDiscovery {
        override suspend fun devices() = result()
    }

    @Test
    fun unMedioSinPermisoNoOcultaALosDemas() = runTest {
        val ble = RadioLink(FakeTransport(), Discovery { throw RadioUnavailableException("Bluetooth apagado") })
        val sim = SimulatedNodeTransport(backgroundScope)
        val multi = MultiTransport(listOf(ble, RadioLink(sim, sim)), backgroundScope)

        val result = multi.scan()

        assertEquals(listOf(SimulatedNodeTransport.ADDRESS), result.devices.map { it.address })
        assertEquals(listOf("Bluetooth apagado"), result.problems)
    }

    @Test
    fun conectarSinBuscarAntesFalla() = runTest {
        val multi = MultiTransport(emptyList(), backgroundScope)
        val client = MeshProtocolClient(multi, backgroundScope)
        client.start()
        client.connect("AA:BB")
        runCurrent()

        assertIs<MeshState.Failed>(client.state.value)
    }

    @Test
    fun hablaPorElMedioDelNodoElegido() = runTest {
        val usb = FakeTransport()
        val otro = FakeTransport()
        val multi = MultiTransport(
            listOf(
                RadioLink(usb, Discovery { listOf(RadioDevice("/dev/ttyUSB0", "Heltec")) }),
                RadioLink(otro, Discovery { listOf(RadioDevice("AA:BB", "Otro")) }),
            ),
            backgroundScope,
        )
        val client = MeshProtocolClient(multi, backgroundScope)
        client.start()
        multi.scan()
        client.connect("/dev/ttyUSB0")
        runCurrent()

        assertIs<MeshState.Ready>(client.state.value)
        assertTrue(usb.sent.isNotEmpty())
        assertTrue(otro.sent.isEmpty())

        usb.drop()
        runCurrent()
        assertEquals(MeshState.Disconnected, client.state.value)
    }

    @Test
    fun elNodoSimuladoConfirmaYContesta() = runTest {
        val sim = SimulatedNodeTransport(backgroundScope, Random(1))
        val multi = MultiTransport(listOf(RadioLink(sim, sim)), backgroundScope)
        val repo = MeshRepository(MeshProtocolClient(multi, backgroundScope), backgroundScope, now = { 0L })
        multi.scan()
        repo.connect(SimulatedNodeTransport.ADDRESS)
        runCurrent()
        assertIs<MeshState.Ready>(repo.state.value)
        assertEquals(listOf(SimulatedNodeTransport.NODE_B), repo.remoteNodes.value.map { it.num })

        repeat(10) { repo.sendText("hola $it", to = SimulatedNodeTransport.NODE_B) }
        advanceTimeBy(5_000)
        runCurrent()

        val enviados = repo.messages.value.filter { it.fromNodeId == SimulatedNodeTransport.NODE_A }
        val respuestas = repo.messages.value.filter { it.fromNodeId == SimulatedNodeTransport.NODE_B }
        assertTrue(enviados.none { it.status == MessageStatus.PENDING })
        assertEquals(enviados.count { it.status == MessageStatus.DELIVERED }, respuestas.size)
        assertTrue(respuestas.all { it.signalQuality != null && it.content.startsWith("eco: ") })
    }
}

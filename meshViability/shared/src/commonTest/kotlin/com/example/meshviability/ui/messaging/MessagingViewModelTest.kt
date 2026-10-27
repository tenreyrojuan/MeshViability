package com.example.meshviability.ui.messaging

import com.example.meshviability.data.transport.RadioDevice
import com.example.meshviability.data.transport.RadioUnavailableException
import com.example.meshviability.domain.entities.MessageStatus
import com.example.meshviability.ui.TestDiscovery
import com.example.meshviability.ui.TestSession
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MessagingViewModelTest {

    private val nodoB = RadioDevice("fake", "Nodo B")

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun TestScope.viewModel(
        session: TestSession = TestSession(backgroundScope),
        discovery: TestDiscovery = TestDiscovery { listOf(nodoB) },
    ) = MessagingViewModel(session.repository, session.settings, discovery, sessionStartMs = 0L)

    @Test
    fun conectarYEnviarUnicastAlNodoB() = runTest {
        val session = TestSession(backgroundScope)
        val vm = viewModel(session)

        vm.scan()
        runCurrent()
        assertEquals(listOf(nodoB), vm.uiState.value.devices)

        vm.connect(nodoB)
        runCurrent()
        val conectado = vm.uiState.value
        assertTrue(conectado.isConnected)
        assertEquals("Conectado", conectado.statusText)
        assertEquals(0x5678, conectado.destination)
        assertEquals("2.6.11.fake", conectado.firmwareVersion)

        vm.onDraftChange("hola B")
        assertTrue(vm.uiState.value.canSend)
        vm.send()
        runCurrent()

        val msg = vm.uiState.value.messages.single()
        assertTrue(msg.mine)
        assertEquals("Nodo B", msg.toLabel)
        assertEquals(MessageStatus.PENDING, msg.status)
        assertEquals("", vm.uiState.value.draft)
        assertEquals(0x5678, session.fake.sent.last().packet!!.to)
    }

    @Test
    fun enviarSinConexionMuestraError() = runTest {
        val vm = viewModel()
        vm.onDraftChange("hola")
        vm.send()
        runCurrent()

        assertFalse(vm.uiState.value.canSend)
        assertEquals("Nodo desconectado: conectate antes de enviar", vm.uiState.value.error)
    }

    @Test
    fun permisoDenegadoAlBuscarNodos() = runTest {
        val vm = viewModel(discovery = TestDiscovery { throw RadioUnavailableException("Falta el permiso de Bluetooth") })
        vm.scan()
        runCurrent()

        assertEquals("No se pudieron buscar nodos: Falta el permiso de Bluetooth", vm.uiState.value.error)
        assertFalse(vm.uiState.value.isBusy)
    }

    @Test
    fun puertoNoDisponibleAlConectar() = runTest {
        val vm = viewModel(TestSession(backgroundScope, connectError = "Puerto USB no disponible"))
        vm.connect(nodoB)
        runCurrent()

        assertEquals("Error: Puerto USB no disponible", vm.uiState.value.statusText)
        assertFalse(vm.uiState.value.isConnected)
    }

    @Test
    fun mensajeDirectoEsValidoYRetransmitidoNo() = runTest {
        val session = TestSession(backgroundScope)
        val vm = viewModel(session)
        vm.connect(nodoB)
        runCurrent()

        session.pushText("directo", hopStart = 3, hopLimit = 3)
        session.pushText("repetido", hopStart = 3, hopLimit = 2)
        runCurrent()

        val (directo, repetido) = vm.uiState.value.messages
        assertTrue(directo.validForMeasurement)
        assertEquals("RSSI -95 dBm · SNR 4,0 dB · 0 saltos", directo.signal)
        assertFalse(repetido.validForMeasurement)
        assertNotNull(repetido.validityNote)
        assertTrue(repetido.validityNote!!.startsWith("No válido para medición"))
    }

    @Test
    fun broadcastSeOfreceComoOpcion() = runTest {
        val vm = viewModel()
        vm.connect(nodoB)
        runCurrent()

        assertEquals(listOf(0x5678, -1), vm.uiState.value.destinations.map { it.num })
        vm.onDestinationSelected(-1)
        assertEquals(-1, vm.uiState.value.destination)
    }
}

package com.example.meshviability.ui.measurement

import com.example.meshviability.domain.link.LinkLevel
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
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class MeasurementViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private suspend fun TestScope.connected(): Pair<TestSession, MeasurementViewModel> {
        val session = TestSession(backgroundScope)
        val vm = MeasurementViewModel(session.repository, session.settings, sessionStartMs = 0L)
        session.repository.connect("fake")
        runCurrent()
        return session to vm
    }

    // RSSI −95, SNR 4 a 1 km: márgenes 36,5 y 21,5; se esperaban −71,7 dBm
    @Test
    fun medicionDirectaConSemaforoYComparacion() = runTest {
        val (session, vm) = connected()
        session.pushText("hola", rssi = -95, snr = 4f)
        runCurrent()

        val row = vm.uiState.value.rows.single()
        assertEquals("Nodo B", row.fromLabel)
        assertEquals("36,5 dB", row.rssiMargin)
        assertEquals("21,5 dB", row.snrMargin)
        assertEquals("21,5 dB", row.worstMargin)
        assertEquals(LinkLevel.SOLID, row.level)
        assertEquals("Sólido", row.levelLabel)
        assertEquals("-71,7 dBm", row.expectedRssi)
        assertEquals("23,3 dB", row.extraLoss)
        assertEquals("Sólido", vm.uiState.value.overallLevelLabel)
        assertEquals("1 medición válida · 0 descartadas", vm.uiState.value.overallCaption)
        val bar = vm.uiState.value.chart.single()
        assertEquals(LinkLevel.SOLID, bar.level)
        assertEquals(21.5, bar.marginDb, 0.1)
    }

    @Test
    fun retransmitidosYSinSaltosSeDescartan() = runTest {
        val (session, vm) = connected()
        session.pushText("repetido", hopStart = 3, hopLimit = 1)
        session.pushText("viejo", hopStart = 0, hopLimit = 0)
        runCurrent()

        assertTrue(vm.uiState.value.rows.isEmpty())
        assertEquals(2, vm.uiState.value.discarded)

        vm.onAcceptUnknownHopsChange(true)
        assertEquals(1, vm.uiState.value.rows.size)
        assertEquals(1, vm.uiState.value.discarded)
    }

    @Test
    fun elUmbralDeSolidoEsConfigurable() = runTest {
        val (session, vm) = connected()
        session.pushText("hola", rssi = -95, snr = 4f)
        runCurrent()

        vm.onThresholdChange(ThresholdField.SOLID, "25")
        assertEquals(LinkLevel.ACCEPTABLE, vm.uiState.value.rows.single().level)
    }

    @Test
    fun umbralesDesordenadosSeRechazan() = runTest {
        val (session, vm) = connected()
        vm.onThresholdChange(ThresholdField.ACCEPTABLE, "30")

        val s = vm.uiState.value
        assertNotNull(s.thresholdError)
        assertTrue(s.thresholdFields.single { it.field == ThresholdField.ACCEPTABLE }.isError)
        assertEquals(10.0, session.settings.thresholds.value.acceptableDb)
    }

    @Test
    fun exportarLaSesionComoCsv() = runTest {
        val (session, vm) = connected()
        session.pushText("hola")
        runCurrent()

        val lines = vm.exportCsv().trimEnd().split("\r\n")
        assertEquals(2, lines.size)
        assertTrue(lines[1].contains("Nodo B"))
        assertEquals("CSV copiado al portapapeles. Mensajes: 1", vm.uiState.value.exportNotice)
    }
}

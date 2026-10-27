package com.example.meshviability.ui.prediction

import com.example.meshviability.data.repository.LinkSettingsRepository
import com.example.meshviability.domain.entities.LoraSettings
import com.example.meshviability.domain.link.LinkLevel
import com.example.meshviability.domain.link.ModemPreset
import com.example.meshviability.domain.link.SpreadingFactor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class PredictionViewModelTest {

    @BeforeTest
    fun setUp() = Dispatchers.setMain(UnconfinedTestDispatcher())

    @AfterTest
    fun tearDown() = Dispatchers.resetMain()

    private fun PredictionUiState.field(f: LinkField) = fields.single { it.field == f }

    @Test
    fun resultadoConLosValoresPorDefecto() = runTest {
        val node = MutableStateFlow<LoraSettings?>(null)
        val vm = PredictionViewModel(LinkSettingsRepository(node, backgroundScope), node)

        val r = vm.uiState.value.result!!
        assertEquals("-120,0 dBm", r.noise)
        assertEquals("-131,5 dBm", r.sensitivity)
        assertEquals("91,7 dB", r.pathLoss)
        assertEquals("-71,7 dBm", r.rxPower)
        assertEquals("59,8 dB", r.margin)
        assertEquals("1", vm.uiState.value.field(LinkField.DISTANCE).text)
    }

    @Test
    fun cambiarLaDistanciaRecalcula() = runTest {
        val node = MutableStateFlow<LoraSettings?>(null)
        val vm = PredictionViewModel(LinkSettingsRepository(node, backgroundScope), node)

        vm.onFieldChange(LinkField.DISTANCE, "2")
        assertEquals("97,7 dB", vm.uiState.value.result!!.pathLoss)
    }

    @Test
    fun aceptaComaDecimal() = runTest {
        val node = MutableStateFlow<LoraSettings?>(null)
        val vm = PredictionViewModel(LinkSettingsRepository(node, backgroundScope), node)

        vm.onFieldChange(LinkField.NOISE_FIGURE, "9,0")
        assertEquals("9,0", vm.uiState.value.field(LinkField.NOISE_FIGURE).text)
        assertEquals("-128,5 dBm", vm.uiState.value.result!!.sensitivity)
    }

    @Test
    fun valorInvalidoMarcaErrorYOcultaElResultado() = runTest {
        val node = MutableStateFlow<LoraSettings?>(null)
        val vm = PredictionViewModel(LinkSettingsRepository(node, backgroundScope), node)

        vm.onFieldChange(LinkField.DISTANCE, "0")
        assertTrue(vm.uiState.value.field(LinkField.DISTANCE).isError)
        assertNull(vm.uiState.value.result)

        vm.onFieldChange(LinkField.DISTANCE, "abc")
        assertEquals("abc", vm.uiState.value.field(LinkField.DISTANCE).text)
        assertNull(vm.uiState.value.result)
    }

    @Test
    fun laConfiguracionDelNodoReemplazaLoIngresado() = runTest {
        val node = MutableStateFlow<LoraSettings?>(null)
        val vm = PredictionViewModel(LinkSettingsRepository(node, backgroundScope), node)
        vm.onFieldChange(LinkField.TX_POWER, "17")

        node.value = LoraSettings(
            preset = ModemPreset.MEDIUM_FAST,
            spreadingFactor = SpreadingFactor.SF9,
            bandwidthHz = 250_000.0,
            txPowerDbm = 14,
            frequencyMhz = null,
            region = "US",
        )
        runCurrent()

        val s = vm.uiState.value
        assertEquals("14", s.field(LinkField.TX_POWER).text)
        assertTrue(s.field(LinkField.TX_POWER).fromNode)
        assertFalse(s.field(LinkField.FREQUENCY).fromNode)
        assertEquals(SpreadingFactor.SF9, s.spreadingFactor)
        assertTrue(s.spreadingFactorFromNode)
        assertEquals("Configuración del nodo: MEDIUM_FAST · Región US", s.nodeSummary)
        assertEquals("Informado por el nodo", s.field(LinkField.TX_POWER).supportingText)
    }

    @Test
    fun balanceDelEnlaceYNivelPrevisto() = runTest {
        val node = MutableStateFlow<LoraSettings?>(null)
        val vm = PredictionViewModel(LinkSettingsRepository(node, backgroundScope), node)

        val s = vm.uiState.value
        assertEquals(LinkLevel.SOLID, s.result!!.level)
        assertEquals("Margen previsto a 1 km", s.result!!.marginCaption)
        assertEquals("−71,7 dBm", s.budget.single { it.label == "Potencia recibida (Prx)" }.value)
        assertEquals("+59,8 dB", s.budget.last().value)
        assertEquals("−0,0 dB", s.budget.single { it.label == "Pérdida en cables" }.value)
        assertEquals(LinkSection.entries, s.sections.map { it.section })
    }
}

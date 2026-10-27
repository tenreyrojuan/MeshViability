package com.example.meshviability.ui.measurement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meshviability.data.export.SessionCsv
import com.example.meshviability.data.protocol.MeshState
import com.example.meshviability.data.repository.LinkSettingsRepository
import com.example.meshviability.data.repository.MeshRepository
import com.example.meshviability.domain.entities.LinkQuality
import com.example.meshviability.domain.entities.MeshNode
import com.example.meshviability.domain.entities.Message
import com.example.meshviability.domain.link.LinkEvaluation
import com.example.meshviability.domain.link.LinkLevel
import com.example.meshviability.domain.link.LinkParameters
import com.example.meshviability.domain.link.LinkThresholds
import com.example.meshviability.domain.link.MeasurementValidity
import com.example.meshviability.domain.link.evaluate
import com.example.meshviability.domain.link.validity
import com.example.meshviability.ui.formatDecimal
import com.example.meshviability.ui.formatElapsed
import com.example.meshviability.ui.formatInput
import com.example.meshviability.ui.nodeLabel
import com.example.meshviability.ui.parseDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

enum class ThresholdField(val label: String) {
    SOLID("Sólido desde"),
    ACCEPTABLE("Aceptable desde"),
}

data class ThresholdFieldUi(val field: ThresholdField, val text: String, val isError: Boolean)

data class MeasurementRow(
    val messageId: Int,
    val fromLabel: String,
    val elapsed: String,
    val evaluation: LinkEvaluation,
    val rssi: String,
    val snr: String,
    val rssiMargin: String,
    val snrMargin: String,
    val worstMargin: String,
    val level: LinkLevel,
    val levelLabel: String,
    val expectedRssi: String,
    val extraLoss: String,
)

// una barra del gráfico: peor margen de una medición, en orden de llegada
data class ChartBar(val marginDb: Double, val level: LinkLevel)

data class MeasurementUiState(
    val rows: List<MeasurementRow> = emptyList(),   // la más reciente primero
    val discarded: Int = 0,
    val distance: String = "",
    val averageWorstMargin: String? = null,
    val averageExtraLoss: String? = null,
    val overallLevel: LinkLevel? = null,
    val overallLevelLabel: String? = null,
    val overallCaption: String = "Todavía no hay mediciones válidas",
    val chart: List<ChartBar> = emptyList(),
    val chartDescription: String = "",
    val solidDb: Double = LinkThresholds().solidDb,
    val acceptableDb: Double = LinkThresholds().acceptableDb,
    val criteriaSummary: String = "",
    val thresholdFields: List<ThresholdFieldUi> = emptyList(),
    val thresholdError: String? = null,
    val acceptUnknownHops: Boolean = false,
    val canExport: Boolean = false,
    val exportNotice: String? = null,
)

fun LinkLevel.label(): String = when (this) {
    LinkLevel.SOLID -> "Sólido"
    LinkLevel.ACCEPTABLE -> "Aceptable"
    LinkLevel.FRAGILE -> "Frágil"
    LinkLevel.UNRELIABLE -> "No confiable"
}

interface MeasurementActions {
    fun onThresholdChange(field: ThresholdField, text: String)
    fun onAcceptUnknownHopsChange(accept: Boolean)
    fun exportCsv(): String
}

/** Todo lo que la pantalla de medición necesita, en un momento dado. */
internal data class MeasurementSnapshot(
    val messages: List<Message>,
    val nodes: List<MeshNode> = emptyList(),
    val myNodeNum: Int? = null,
    val parameters: LinkParameters = LinkParameters(),
    val thresholds: LinkThresholds = LinkThresholds(),
    val acceptUnknownHops: Boolean = false,
    val drafts: Map<ThresholdField, String> = emptyMap(),
    val thresholdError: Pair<ThresholdField, String>? = null,
    val exportNotice: String? = null,
    val sessionStartMs: Long = 0L,
)

private const val CHART_MAX_BARS = 40

internal fun MeasurementSnapshot.toUiState(): MeasurementUiState {
    val received = messages.mapNotNull { m -> m.signalQuality?.let { m to it } }
    val valid = received.filter { (_, q) -> q.validity(acceptUnknownHops) == MeasurementValidity.VALID }
    val rows = valid.map { (m, q) -> row(m, q) }

    val avgWorst = rows.takeIf { it.isNotEmpty() }?.map { it.evaluation.margin.worstDb }?.average()
    val avgExtra = rows.takeIf { it.isNotEmpty() }?.map { it.evaluation.extraLossDb }?.average()
    val overall = avgWorst?.let { thresholds.classify(it) }
    val chart = rows.takeLast(CHART_MAX_BARS).map { ChartBar(it.evaluation.margin.worstDb, it.level) }

    return MeasurementUiState(
        rows = rows.asReversed(),
        discarded = received.size - valid.size,
        distance = "${formatInput(parameters.distanceKm)}\u00A0km",
        averageWorstMargin = avgWorst?.let { "${formatDecimal(it)} dB" },
        averageExtraLoss = avgExtra?.let { "${formatDecimal(it)} dB" },
        overallLevel = overall,
        overallLevelLabel = overall?.label(),
        overallCaption = if (avgWorst == null) {
            "Todavía no hay mediciones válidas"
        } else {
            "${count(rows.size, "medición válida", "mediciones válidas")} · " +
                count(received.size - valid.size, "descartada", "descartadas")
        },
        chart = chart,
        chartDescription = if (chart.isEmpty()) "" else
            "Margen de las últimas ${chart.size} mediciones, entre ${formatDecimal(chart.minOf { it.marginDb })} " +
                "y ${formatDecimal(chart.maxOf { it.marginDb })} dB",
        solidDb = thresholds.solidDb,
        acceptableDb = thresholds.acceptableDb,
        // espacio irrompible entre número y unidad
        criteriaSummary = "Sólido ≥\u00A0${formatInput(thresholds.solidDb)}\u00A0dB · Aceptable ≥\u00A0${formatInput(thresholds.acceptableDb)}\u00A0dB · " +
            "Frágil ≥\u00A0${formatInput(thresholds.fragileDb)}\u00A0dB",
        thresholdFields = ThresholdField.entries.map { f ->
            val value = when (f) {
                ThresholdField.SOLID -> thresholds.solidDb
                ThresholdField.ACCEPTABLE -> thresholds.acceptableDb
            }
            val text = drafts[f] ?: formatInput(value)
            ThresholdFieldUi(f, text, isError = parseDecimal(text) == null || thresholdError?.first == f)
        },
        thresholdError = thresholdError?.second,
        acceptUnknownHops = acceptUnknownHops,
        canExport = messages.isNotEmpty(),
        exportNotice = exportNotice,
    )
}

private fun count(n: Int, singular: String, plural: String) = "$n ${if (n == 1) singular else plural}"

private fun MeasurementSnapshot.row(m: Message, q: LinkQuality): MeasurementRow {
    val e = q.evaluate(parameters, thresholds)
    return MeasurementRow(
        messageId = m.id,
        fromLabel = nodeLabel(m.fromNodeId, nodes, myNodeNum),
        elapsed = formatElapsed(sessionStartMs, m.timeStampMs),
        evaluation = e,
        rssi = "${formatDecimal(q.rssiDbm.toDouble(), 0)} dBm",
        snr = "${formatDecimal(q.snrDb.toDouble())} dB",
        rssiMargin = "${formatDecimal(e.margin.rssiMarginDb)} dB",
        snrMargin = "${formatDecimal(e.margin.snrMarginDb)} dB",
        worstMargin = "${formatDecimal(e.margin.worstDb)} dB",
        level = e.level,
        levelLabel = e.level.label(),
        expectedRssi = "${formatDecimal(e.expectedRssiDbm)} dBm",
        extraLoss = "${formatDecimal(e.extraLossDb)} dB",
    )
}

class MeasurementViewModel(
    private val repository: MeshRepository,
    private val settings: LinkSettingsRepository,
    private val sessionStartMs: Long,
) : ViewModel(), MeasurementActions {

    private data class Inputs(
        val messages: List<Message>,
        val nodes: List<MeshNode>,
        val myNodeNum: Int?,
    )

    private data class Criteria(
        val parameters: LinkParameters,
        val thresholds: LinkThresholds,
        val acceptUnknownHops: Boolean,
    )

    private val drafts = MutableStateFlow<Map<ThresholdField, String>>(emptyMap())
    private val thresholdError = MutableStateFlow<Pair<ThresholdField, String>?>(null)
    private val exportNotice = MutableStateFlow<String?>(null)

    private val inputs = combine(repository.messages, repository.remoteNodes, repository.state) { m, n, s ->
        Inputs(m, n, (s as? MeshState.Ready)?.myNodeNum)
    }

    private val criteria = combine(settings.parameters, settings.thresholds, settings.acceptUnknownHops, ::Criteria)

    val uiState: StateFlow<MeasurementUiState> =
        combine(inputs, criteria, drafts, thresholdError, exportNotice) { inputs, criteria, drafts, error, notice ->
            MeasurementSnapshot(
                messages = inputs.messages,
                nodes = inputs.nodes,
                myNodeNum = inputs.myNodeNum,
                parameters = criteria.parameters,
                thresholds = criteria.thresholds,
                acceptUnknownHops = criteria.acceptUnknownHops,
                drafts = drafts,
                thresholdError = error,
                exportNotice = notice,
                sessionStartMs = sessionStartMs,
            ).toUiState()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, MeasurementUiState())

    override fun onThresholdChange(field: ThresholdField, text: String) {
        drafts.update { it + (field to text) }
        val value = parseDecimal(text) ?: return
        val current = settings.thresholds.value
        try {
            settings.setThresholds(
                when (field) {
                    ThresholdField.SOLID -> current.copy(solidDb = value)
                    ThresholdField.ACCEPTABLE -> current.copy(acceptableDb = value)
                },
            )
            thresholdError.value = null
        } catch (e: IllegalArgumentException) {
            thresholdError.value = field to "Debe cumplirse: sólido ≥ aceptable ≥ ${formatInput(current.fragileDb)} dB"
        }
    }

    override fun onAcceptUnknownHopsChange(accept: Boolean) = settings.setAcceptUnknownHops(accept)

    // la sesión completa, para pegarla en una planilla
    override fun exportCsv(): String {
        val me = (repository.state.value as? MeshState.Ready)?.myNodeNum
        val nodes = repository.remoteNodes.value
        val messages = repository.messages.value
        exportNotice.value = "CSV copiado al portapapeles. Mensajes: ${messages.size}"
        return SessionCsv.build(
            messages,
            settings.parameters.value,
            settings.thresholds.value,
            settings.acceptUnknownHops.value,
        ) { nodeLabel(it, nodes, me) }
    }
}

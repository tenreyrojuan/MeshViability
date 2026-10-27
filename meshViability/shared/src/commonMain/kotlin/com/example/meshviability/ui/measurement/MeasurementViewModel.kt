package com.example.meshviability.ui.measurement

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
    val rssiDbm: Double,
    val snrDb: Double,
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

data class MeasurementUiState(
    val rows: List<MeasurementRow> = emptyList(),
    val discarded: Int = 0,
    val distance: String = "",
    val averageWorstMargin: String? = null,
    val averageExtraLoss: String? = null,
    val overallLevel: LinkLevel? = null,
    val overallLevelLabel: String? = null,
    val thresholdFields: List<ThresholdFieldUi> = emptyList(),
    val thresholdError: String? = null,
    val acceptUnknownHops: Boolean = false,
)

fun LinkLevel.label(): String = when (this) {
    LinkLevel.SOLID -> "Sólido"
    LinkLevel.ACCEPTABLE -> "Aceptable"
    LinkLevel.FRAGILE -> "Frágil"
    LinkLevel.UNRELIABLE -> "No confiable"
}

class MeasurementViewModel(
    repository: MeshRepository,
    private val settings: LinkSettingsRepository,
    private val sessionStartMs: Long,
) : ViewModel() {

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

    private val inputs = combine(repository.messages, repository.remoteNodes, repository.state) { m, n, s ->
        Inputs(m, n, (s as? MeshState.Ready)?.myNodeNum)
    }

    private val criteria = combine(settings.parameters, settings.thresholds, settings.acceptUnknownHops, ::Criteria)

    val uiState: StateFlow<MeasurementUiState> =
        combine(inputs, criteria, drafts, thresholdError, ::build)
            .stateIn(viewModelScope, SharingStarted.Eagerly, MeasurementUiState())

    fun onThresholdChange(field: ThresholdField, text: String) {
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

    fun onAcceptUnknownHopsChange(accept: Boolean) = settings.setAcceptUnknownHops(accept)

    private fun build(
        inputs: Inputs,
        criteria: Criteria,
        drafts: Map<ThresholdField, String>,
        thresholdError: Pair<ThresholdField, String>?,
    ): MeasurementUiState {
        val received = inputs.messages.mapNotNull { m -> m.signalQuality?.let { m to it } }
        val valid = received.filter { (_, q) -> q.validity(criteria.acceptUnknownHops) == MeasurementValidity.VALID }
        val rows = valid.map { (m, q) -> row(m, q, inputs, criteria) }

        val avgWorst = rows.takeIf { it.isNotEmpty() }?.map { it.evaluation.margin.worstDb }?.average()
        val avgExtra = rows.takeIf { it.isNotEmpty() }?.map { it.evaluation.extraLossDb }?.average()
        val overall = avgWorst?.let { criteria.thresholds.classify(it) }

        return MeasurementUiState(
            rows = rows,
            discarded = received.size - valid.size,
            distance = "${formatInput(criteria.parameters.distanceKm)} km",
            averageWorstMargin = avgWorst?.let { "${formatDecimal(it)} dB" },
            averageExtraLoss = avgExtra?.let { "${formatDecimal(it)} dB" },
            overallLevel = overall,
            overallLevelLabel = overall?.let { "${it.label()} (según el margen promedio)" },
            thresholdFields = ThresholdField.entries.map { f ->
                val value = when (f) {
                    ThresholdField.SOLID -> criteria.thresholds.solidDb
                    ThresholdField.ACCEPTABLE -> criteria.thresholds.acceptableDb
                }
                val text = drafts[f] ?: formatInput(value)
                ThresholdFieldUi(f, text, isError = parseDecimal(text) == null || thresholdError?.first == f)
            },
            thresholdError = thresholdError?.second,
            acceptUnknownHops = criteria.acceptUnknownHops,
        )
    }

    private fun row(m: Message, q: LinkQuality, inputs: Inputs, criteria: Criteria): MeasurementRow {
        val e = q.evaluate(criteria.parameters, criteria.thresholds)
        return MeasurementRow(
            messageId = m.id,
            fromLabel = nodeLabel(m.fromNodeId, inputs.nodes, inputs.myNodeNum),
            elapsed = formatElapsed(sessionStartMs, m.timeStampMs),
            rssiDbm = q.rssiDbm.toDouble(),
            snrDb = q.snrDb.toDouble(),
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
}

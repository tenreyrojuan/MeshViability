package com.example.meshviability.ui.prediction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meshviability.data.repository.LinkSettingsRepository
import com.example.meshviability.domain.entities.LoraSettings
import com.example.meshviability.domain.link.LinkLevel
import com.example.meshviability.domain.link.LinkParameters
import com.example.meshviability.domain.link.LinkThresholds
import com.example.meshviability.domain.link.SpreadingFactor
import com.example.meshviability.domain.link.predict
import com.example.meshviability.ui.formatDecimal
import com.example.meshviability.ui.formatInput
import com.example.meshviability.ui.measurement.label
import com.example.meshviability.ui.parseDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlin.math.abs

private const val HZ_PER_KHZ = 1000.0
private const val NO_NODE_SUMMARY = "Sin nodo conectado: parámetros ingresados a mano"

enum class LinkSection(val title: String) {
    RADIO("Radio"),
    TRANSMITTER("Transmisor (nodo A)"),
    RECEIVER("Receptor (nodo B)"),
    PATH("Trayecto"),
}

enum class LinkField(
    val label: String,
    val unit: String,
    val section: LinkSection,
    val hint: String?,
    val mustBePositive: Boolean,
    val read: (LinkParameters) -> Double,
    val write: (LinkParameters, Double) -> LinkParameters,
) {
    TX_POWER("Potencia", "dBm", LinkSection.TRANSMITTER, "Heltec V3: 21 ± 1 dBm", false, { it.txPowerDbm }, { p, v -> p.copy(txPowerDbm = v) }),
    TX_CABLE_LOSS("Pérdida cable", "dB", LinkSection.TRANSMITTER, null, false, { it.txCableLossDb }, { p, v -> p.copy(txCableLossDb = v) }),
    TX_ANTENNA_GAIN("Ganancia antena", "dBi", LinkSection.TRANSMITTER, null, false, { it.txAntennaGainDbi }, { p, v -> p.copy(txAntennaGainDbi = v) }),
    RX_ANTENNA_GAIN("Ganancia antena", "dBi", LinkSection.RECEIVER, null, false, { it.rxAntennaGainDbi }, { p, v -> p.copy(rxAntennaGainDbi = v) }),
    RX_CABLE_LOSS("Pérdida cable", "dB", LinkSection.RECEIVER, null, false, { it.rxCableLossDb }, { p, v -> p.copy(rxCableLossDb = v) }),
    FREQUENCY("Frecuencia", "MHz", LinkSection.PATH, null, true, { it.frequencyMhz }, { p, v -> p.copy(frequencyMhz = v) }),
    DISTANCE("Distancia", "km", LinkSection.PATH, null, true, { it.distanceKm }, { p, v -> p.copy(distanceKm = v) }),
    BANDWIDTH("Ancho de banda", "kHz", LinkSection.RADIO, null, true, { it.bandwidthHz / HZ_PER_KHZ }, { p, v -> p.copy(bandwidthHz = v * HZ_PER_KHZ) }),
    NOISE_FIGURE("Factor de ruido", "dB", LinkSection.RADIO, "Típico 6 dB · Heltec V3 ≈ 9 dB", false, { it.noiseFigureDb }, { p, v -> p.copy(noiseFigureDb = v) }),
    ;

    fun parse(text: String): Double? = parseDecimal(text)?.takeIf { !mustBePositive || it > 0.0 }
}

data class FieldUi(
    val field: LinkField,
    val text: String,
    val isError: Boolean,
    val fromNode: Boolean,
    val supportingText: String?,
)

data class FieldSectionUi(val section: LinkSection, val fields: List<FieldUi>)

// una línea del balance: Ptx − cables + antenas − L0 = Prx; Prx − S = M
data class BudgetLine(val label: String, val value: String, val isTotal: Boolean = false)

data class PredictionResultUi(
    val noise: String,
    val sensitivity: String,
    val pathLoss: String,
    val rxPower: String,
    val margin: String,
    val maxRange: String,
    val level: LinkLevel,
    val levelLabel: String,
    val marginCaption: String,
)

data class PredictionUiState(
    val sections: List<FieldSectionUi> = emptyList(),
    val spreadingFactor: SpreadingFactor = SpreadingFactor.SF11,
    val spreadingFactorFromNode: Boolean = false,
    val spreadingFactorLabel: String = "Factor de dispersión",
    val nodeSummary: String = NO_NODE_SUMMARY,
    val result: PredictionResultUi? = null,
    val budget: List<BudgetLine> = emptyList(),
) {
    val fields: List<FieldUi> get() = sections.flatMap { it.fields }
}

internal data class PredictionSnapshot(
    val parameters: LinkParameters,
    val drafts: Map<LinkField, String> = emptyMap(),
    val node: LoraSettings? = null,
    val thresholds: LinkThresholds = LinkThresholds(),
)

internal fun PredictionSnapshot.toUiState(): PredictionUiState {
    val fromNode = fieldsFromNode(node)
    val fields = LinkField.entries.map { field ->
        val text = shownText(field, parameters, drafts[field])
        val isError = field.parse(text) == null
        val provided = field in fromNode
        FieldUi(
            field = field,
            text = text,
            isError = isError,
            fromNode = provided,
            supportingText = when {
                isError -> if (field.mustBePositive) "Número mayor que 0" else "Número inválido"
                provided -> "Informado por el nodo"
                else -> field.hint
            },
        )
    }
    val valid = fields.none { it.isError }
    val p = parameters.predict()
    val level = thresholds.classify(p.marginDb)
    return PredictionUiState(
        sections = LinkSection.entries.map { section -> FieldSectionUi(section, fields.filter { it.field.section == section }) },
        spreadingFactor = parameters.spreadingFactor,
        spreadingFactorFromNode = node?.spreadingFactor != null,
        spreadingFactorLabel = if (node?.spreadingFactor != null) "Factor de dispersión (del nodo)" else "Factor de dispersión",
        nodeSummary = node?.let { s ->
            "Configuración del nodo: ${s.preset?.name ?: "parámetros manuales"} · Región ${s.region}"
        } ?: NO_NODE_SUMMARY,
        result = if (!valid) null else PredictionResultUi(
            noise = "${formatDecimal(p.noiseDbm)} dBm",
            sensitivity = "${formatDecimal(p.sensitivityDbm)} dBm",
            pathLoss = "${formatDecimal(p.pathLossDb)} dB",
            rxPower = "${formatDecimal(p.rxPowerDbm)} dBm",
            margin = "${formatDecimal(p.marginDb)} dB",
            maxRange = "${formatDecimal(p.maxRangeKm)} km",
            level = level,
            levelLabel = level.label(),
            marginCaption = "Margen previsto a ${formatInput(parameters.distanceKm)} km",
        ),
        budget = if (!valid) emptyList() else with(parameters) {
            listOf(
                BudgetLine("Potencia de transmisión", "${signed(txPowerDbm)} dBm"),
                BudgetLine("Pérdida en cables", "${loss(txCableLossDb + rxCableLossDb)} dB"),
                BudgetLine("Ganancia de antenas", "${signed(txAntennaGainDbi + rxAntennaGainDbi)} dBi"),
                BudgetLine("Pérdida en espacio libre (L0)", "${loss(p.pathLossDb)} dB"),
                BudgetLine("Potencia recibida (Prx)", "${signed(p.rxPowerDbm)} dBm", isTotal = true),
                BudgetLine("Sensibilidad (S)", "${signed(p.sensitivityDbm)} dBm"),
                BudgetLine("Margen (Prx − S)", "${signed(p.marginDb)} dB", isTotal = true),
            )
        },
    )
}

private fun signed(value: Double): String = (if (value < 0) "−" else "+") + formatDecimal(abs(value))

// una pérdida siempre resta, aunque valga 0
private fun loss(value: Double): String = "−" + formatDecimal(abs(value))

// si el valor cambió por otro lado (por ejemplo, llegó la config del nodo), se muestra el nuevo
private fun shownText(field: LinkField, params: LinkParameters, draft: String?): String {
    val value = field.read(params)
    if (draft == null) return formatInput(value)
    val parsed = field.parse(draft) ?: return draft
    return if (parsed == value) draft else formatInput(value)
}

private fun fieldsFromNode(node: LoraSettings?): Set<LinkField> = buildSet {
    if (node == null) return@buildSet
    if (node.txPowerDbm != null) add(LinkField.TX_POWER)
    if (node.frequencyMhz != null) add(LinkField.FREQUENCY)
    if (node.bandwidthHz != null) add(LinkField.BANDWIDTH)
}

interface PredictionActions {
    fun onFieldChange(field: LinkField, text: String)
    fun onSpreadingFactorChange(sf: SpreadingFactor)
}

class PredictionViewModel(
    private val settings: LinkSettingsRepository,
    nodeSettings: StateFlow<LoraSettings?>,
) : ViewModel(), PredictionActions {

    // lo que el usuario escribió, aunque todavía no sea un número válido
    private val drafts = MutableStateFlow<Map<LinkField, String>>(emptyMap())

    val uiState: StateFlow<PredictionUiState> =
        combine(settings.parameters, drafts, nodeSettings, settings.thresholds) { params, drafts, node, thresholds ->
            PredictionSnapshot(params, drafts, node, thresholds).toUiState()
        }.stateIn(viewModelScope, SharingStarted.Eagerly, PredictionUiState())

    override fun onFieldChange(field: LinkField, text: String) {
        drafts.update { it + (field to text) }
        field.parse(text)?.let { value -> settings.updateParameters { field.write(it, value) } }
    }

    override fun onSpreadingFactorChange(sf: SpreadingFactor) = settings.updateParameters { it.copy(spreadingFactor = sf) }
}

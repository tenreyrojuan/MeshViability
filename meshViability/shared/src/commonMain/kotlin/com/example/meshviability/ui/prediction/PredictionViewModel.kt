package com.example.meshviability.ui.prediction

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.meshviability.data.repository.LinkSettingsRepository
import com.example.meshviability.domain.entities.LoraSettings
import com.example.meshviability.domain.link.LinkParameters
import com.example.meshviability.domain.link.SpreadingFactor
import com.example.meshviability.domain.link.predict
import com.example.meshviability.ui.formatDecimal
import com.example.meshviability.ui.formatInput
import com.example.meshviability.ui.parseDecimal
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update

private const val HZ_PER_KHZ = 1000.0
private const val NO_NODE_SUMMARY = "Sin nodo conectado: parámetros ingresados a mano"

enum class LinkField(
    val label: String,
    val unit: String,
    val hint: String?,
    val mustBePositive: Boolean,
    val read: (LinkParameters) -> Double,
    val write: (LinkParameters, Double) -> LinkParameters,
) {
    TX_POWER("Potencia Tx", "dBm", "Heltec V3: 21 ± 1 dBm", false, { it.txPowerDbm }, { p, v -> p.copy(txPowerDbm = v) }),
    TX_CABLE_LOSS("Pérdida cable Tx", "dB", null, false, { it.txCableLossDb }, { p, v -> p.copy(txCableLossDb = v) }),
    TX_ANTENNA_GAIN("Ganancia antena Tx", "dBi", null, false, { it.txAntennaGainDbi }, { p, v -> p.copy(txAntennaGainDbi = v) }),
    RX_ANTENNA_GAIN("Ganancia antena Rx", "dBi", null, false, { it.rxAntennaGainDbi }, { p, v -> p.copy(rxAntennaGainDbi = v) }),
    RX_CABLE_LOSS("Pérdida cable Rx", "dB", null, false, { it.rxCableLossDb }, { p, v -> p.copy(rxCableLossDb = v) }),
    FREQUENCY("Frecuencia", "MHz", null, true, { it.frequencyMhz }, { p, v -> p.copy(frequencyMhz = v) }),
    DISTANCE("Distancia", "km", null, true, { it.distanceKm }, { p, v -> p.copy(distanceKm = v) }),
    BANDWIDTH("Ancho de banda", "kHz", null, true, { it.bandwidthHz / HZ_PER_KHZ }, { p, v -> p.copy(bandwidthHz = v * HZ_PER_KHZ) }),
    NOISE_FIGURE("Factor de ruido NF", "dB", "Típico 6 dB; con la hoja de datos de la Heltec V3 ≈ 9 dB", false, { it.noiseFigureDb }, { p, v -> p.copy(noiseFigureDb = v) }),
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

data class PredictionResultUi(
    val noise: String,
    val sensitivity: String,
    val pathLoss: String,
    val rxPower: String,
    val margin: String,
    val maxRange: String,
)

data class PredictionUiState(
    val fields: List<FieldUi> = emptyList(),
    val spreadingFactor: SpreadingFactor = SpreadingFactor.SF11,
    val spreadingFactorFromNode: Boolean = false,
    val spreadingFactorLabel: String = "Factor de dispersión",
    val nodeSummary: String = NO_NODE_SUMMARY,
    val result: PredictionResultUi? = null,
)

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
        combine(settings.parameters, drafts, nodeSettings, ::build)
            .stateIn(viewModelScope, SharingStarted.Eagerly, PredictionUiState())

    override fun onFieldChange(field: LinkField, text: String) {
        drafts.update { it + (field to text) }
        field.parse(text)?.let { value -> settings.updateParameters { field.write(it, value) } }
    }

    override fun onSpreadingFactorChange(sf: SpreadingFactor) = settings.updateParameters { it.copy(spreadingFactor = sf) }

    private fun build(params: LinkParameters, drafts: Map<LinkField, String>, node: LoraSettings?): PredictionUiState {
        val fields = LinkField.entries.map { field ->
            val text = shownText(field, params, drafts[field])
            val isError = field.parse(text) == null
            val fromNode = field in fieldsFromNode(node)
            FieldUi(
                field = field,
                text = text,
                isError = isError,
                fromNode = fromNode,
                supportingText = when {
                    isError -> if (field.mustBePositive) "Número mayor que 0" else "Número inválido"
                    fromNode -> "Informado por el nodo"
                    else -> field.hint
                },
            )
        }
        val p = params.predict()
        return PredictionUiState(
            fields = fields,
            spreadingFactor = params.spreadingFactor,
            spreadingFactorFromNode = node?.spreadingFactor != null,
            spreadingFactorLabel = if (node?.spreadingFactor != null) "Factor de dispersión (del nodo)" else "Factor de dispersión",
            nodeSummary = node?.let { s ->
                "Configuración del nodo: ${s.preset?.name ?: "parámetros manuales"} · Región ${s.region}"
            } ?: NO_NODE_SUMMARY,
            result = if (fields.any { it.isError }) null else PredictionResultUi(
                noise = "${formatDecimal(p.noiseDbm)} dBm",
                sensitivity = "${formatDecimal(p.sensitivityDbm)} dBm",
                pathLoss = "${formatDecimal(p.pathLossDb)} dB",
                rxPower = "${formatDecimal(p.rxPowerDbm)} dBm",
                margin = "${formatDecimal(p.marginDb)} dB",
                maxRange = "${formatDecimal(p.maxRangeKm)} km",
            ),
        )
    }

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
}

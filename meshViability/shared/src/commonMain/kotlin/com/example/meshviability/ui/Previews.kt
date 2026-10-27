package com.example.meshviability.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.example.meshviability.AppScaffold
import androidx.compose.ui.tooling.preview.Preview
import com.example.meshviability.data.transport.RadioDevice
import com.example.meshviability.domain.entities.LinkQuality
import com.example.meshviability.domain.entities.MessageStatus
import com.example.meshviability.domain.link.LinkParameters
import com.example.meshviability.domain.link.LinkThresholds
import com.example.meshviability.domain.link.SpreadingFactor
import com.example.meshviability.domain.link.evaluate
import com.example.meshviability.domain.link.predict
import com.example.meshviability.ui.measurement.MeasurementActions
import com.example.meshviability.ui.measurement.MeasurementContent
import com.example.meshviability.ui.measurement.MeasurementRow
import com.example.meshviability.ui.measurement.MeasurementUiState
import com.example.meshviability.ui.measurement.ThresholdField
import com.example.meshviability.ui.measurement.ThresholdFieldUi
import com.example.meshviability.ui.measurement.label
import com.example.meshviability.ui.messaging.DestinationOption
import com.example.meshviability.ui.messaging.MessageItem
import com.example.meshviability.ui.messaging.MessagingActions
import com.example.meshviability.ui.messaging.MessagingContent
import com.example.meshviability.ui.messaging.MessagingUiState
import com.example.meshviability.ui.prediction.FieldUi
import com.example.meshviability.ui.prediction.LinkField
import com.example.meshviability.ui.prediction.PredictionActions
import com.example.meshviability.ui.prediction.PredictionContent
import com.example.meshviability.ui.prediction.PredictionResultUi
import com.example.meshviability.ui.prediction.PredictionUiState

// Previews sin ViewModels: el renderizador del IDE no tiene Dispatchers.Main, que usa viewModelScope.

private object NoMessagingActions : MessagingActions {
    override fun onDraftChange(text: String) = Unit
    override fun onDestinationSelected(num: Int) = Unit
    override fun dismissError() = Unit
    override fun scan() = Unit
    override fun connect(device: RadioDevice) = Unit
    override fun disconnect() = Unit
    override fun send() = Unit
}

private object NoPredictionActions : PredictionActions {
    override fun onFieldChange(field: LinkField, text: String) = Unit
    override fun onSpreadingFactorChange(sf: SpreadingFactor) = Unit
}

private object NoMeasurementActions : MeasurementActions {
    override fun onThresholdChange(field: ThresholdField, text: String) = Unit
    override fun onAcceptUnknownHopsChange(accept: Boolean) = Unit
    override fun exportCsv(): String = ""
}

private fun sampleMessage(id: Int, mine: Boolean, text: String, status: MessageStatus? = null, signal: String? = null, note: String? = null) =
    MessageItem(
        id = id,
        mine = mine,
        fromLabel = if (mine) "Yo" else "Nodo B",
        toLabel = if (mine) "Nodo B" else "Yo",
        content = text,
        elapsed = "+00:${(id * 4).toString().padStart(2, '0')}",
        status = status,
        statusLabel = when (status) {
            MessageStatus.PENDING -> "Pendiente"
            MessageStatus.DELIVERED -> "Entregado"
            MessageStatus.FAILED -> "Fallido"
            null -> null
        },
        signal = signal,
        validForMeasurement = note == null,
        validityNote = note,
    )

private val sampleMessaging = MessagingUiState(
    statusText = "Conectado",
    isConnected = true,
    firmwareVersion = "2.6.11",
    destinations = listOf(DestinationOption(0x5678, "Nodo B"), DestinationOption(-1, "Todos (broadcast)")),
    destination = 0x5678,
    draft = "hola",
    canSend = true,
    messages = listOf(
        sampleMessage(1, mine = true, text = "hola B", status = MessageStatus.DELIVERED),
        sampleMessage(2, mine = false, text = "hola A", signal = "RSSI -97 dBm · SNR 9,8 dB · 0 saltos"),
        sampleMessage(3, mine = true, text = "¿me escuchás?", status = MessageStatus.FAILED),
        sampleMessage(
            4, mine = false, text = "repetido", signal = "RSSI -112 dBm · SNR 1,5 dB · 1 saltos",
            note = "No válido para medición: retransmitido, RSSI y SNR son del último salto",
        ),
        sampleMessage(5, mine = true, text = "última", status = MessageStatus.PENDING),
    ),
)

private fun samplePrediction(): PredictionUiState {
    val params = LinkParameters()
    val p = params.predict()
    return PredictionUiState(
        fields = LinkField.entries.map { f ->
            FieldUi(f, formatInput(f.read(params)), isError = false, fromNode = f == LinkField.TX_POWER, supportingText = f.hint)
        },
        nodeSummary = "Configuración del nodo: LONG_FAST · Región US",
        result = PredictionResultUi(
            noise = "${formatDecimal(p.noiseDbm)} dBm",
            sensitivity = "${formatDecimal(p.sensitivityDbm)} dBm",
            pathLoss = "${formatDecimal(p.pathLossDb)} dB",
            rxPower = "${formatDecimal(p.rxPowerDbm)} dBm",
            margin = "${formatDecimal(p.marginDb)} dB",
            maxRange = "${formatDecimal(p.maxRangeKm)} km",
        ),
    )
}

private fun sampleRow(id: Int, rssi: Float, snr: Float): MeasurementRow {
    val e = LinkQuality(rssi, snr, hops = 0).evaluate(LinkParameters())
    return MeasurementRow(
        messageId = id,
        fromLabel = "Nodo B",
        elapsed = "+00:0$id",
        rssiDbm = rssi.toDouble(),
        snrDb = snr.toDouble(),
        evaluation = e,
        rssi = "${formatDecimal(rssi.toDouble(), 0)} dBm",
        snr = "${formatDecimal(snr.toDouble())} dB",
        rssiMargin = "${formatDecimal(e.margin.rssiMarginDb)} dB",
        snrMargin = "${formatDecimal(e.margin.snrMarginDb)} dB",
        worstMargin = "${formatDecimal(e.margin.worstDb)} dB",
        level = e.level,
        levelLabel = e.level.label(),
        expectedRssi = "${formatDecimal(e.expectedRssiDbm)} dBm",
        extraLoss = "${formatDecimal(e.extraLossDb)} dB",
    )
}

private fun sampleMeasurement(): MeasurementUiState {
    val rows = listOf(sampleRow(1, -97f, 9.8f), sampleRow(2, -110f, 2.1f), sampleRow(3, -124f, -10f))
    val overall = rows.map { it.evaluation.margin.worstDb }.average()
    val level = LinkThresholds().classify(overall)
    return MeasurementUiState(
        rows = rows,
        discarded = 1,
        distance = "1 km",
        averageWorstMargin = "${formatDecimal(overall)} dB",
        averageExtraLoss = "${formatDecimal(rows.map { it.evaluation.extraLossDb }.average())} dB",
        overallLevel = level,
        overallLevelLabel = "${level.label()} (según el margen promedio)",
        thresholdFields = listOf(
            ThresholdFieldUi(ThresholdField.SOLID, "20", isError = false),
            ThresholdFieldUi(ThresholdField.ACCEPTABLE, "10", isError = false),
        ),
        canExport = true,
    )
}

@Composable
private fun PreviewTheme(content: @Composable () -> Unit) {
    MaterialTheme { Surface(content = content) }
}

@Preview
@Composable
fun MessagingPreview() = PreviewTheme { MessagingContent(sampleMessaging, NoMessagingActions) }

@Preview
@Composable
fun MessagingDisconnectedPreview() = PreviewTheme {
    MessagingContent(
        MessagingUiState(
            statusText = "Error: Bluetooth apagado: activalo y volvé a buscar",
            devices = listOf(RadioDevice("simulado", "Nodo simulado (sin hardware)")),
        ),
        NoMessagingActions,
    )
}

@Preview
@Composable
fun PredictionPreview() = PreviewTheme { PredictionContent(samplePrediction(), NoPredictionActions) }

@Preview
@Composable
fun MeasurementPreview() = PreviewTheme { MeasurementContent(sampleMeasurement(), NoMeasurementActions) }

// la app completa; en el modo interactivo del preview se puede cambiar de pestaña
@Preview
@Composable
fun AppPreview() {
    var selected by remember { mutableIntStateOf(0) }
    AppScaffold(selected, onSelected = { selected = it }) { modifier ->
        when (selected) {
            0 -> MessagingContent(sampleMessaging, NoMessagingActions, modifier)
            1 -> PredictionContent(samplePrediction(), NoPredictionActions, modifier)
            else -> MeasurementContent(sampleMeasurement(), NoMeasurementActions, modifier)
        }
    }
}

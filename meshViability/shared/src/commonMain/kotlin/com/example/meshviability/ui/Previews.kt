package com.example.meshviability.ui

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_NIGHT_YES
import androidx.compose.ui.tooling.preview.AndroidUiModes.UI_MODE_TYPE_NORMAL
import androidx.compose.ui.tooling.preview.Preview
import com.example.meshviability.AppScaffold
import com.example.meshviability.data.protocol.MeshState
import com.example.meshviability.data.transport.RadioDevice
import com.example.meshviability.domain.entities.LinkQuality
import com.example.meshviability.domain.entities.LoraSettings
import com.example.meshviability.domain.entities.MeshNode
import com.example.meshviability.domain.entities.Message
import com.example.meshviability.domain.entities.MessageStatus
import com.example.meshviability.domain.link.LinkParameters
import com.example.meshviability.domain.link.ModemPreset
import com.example.meshviability.domain.link.SpreadingFactor
import com.example.meshviability.ui.measurement.MeasurementActions
import com.example.meshviability.ui.measurement.MeasurementContent
import com.example.meshviability.ui.measurement.MeasurementSnapshot
import com.example.meshviability.ui.measurement.ThresholdField
import com.example.meshviability.ui.measurement.toUiState
import com.example.meshviability.ui.messaging.MessagingActions
import com.example.meshviability.ui.messaging.MessagingContent
import com.example.meshviability.ui.messaging.MessagingSnapshot
import com.example.meshviability.ui.messaging.toUiState
import com.example.meshviability.ui.prediction.LinkField
import com.example.meshviability.ui.prediction.PredictionActions
import com.example.meshviability.ui.prediction.PredictionContent
import com.example.meshviability.ui.prediction.PredictionSnapshot
import com.example.meshviability.ui.prediction.toUiState
import com.example.meshviability.ui.theme.MeshTheme

// Los previews arman el estado con las mismas funciones que los ViewModels, a partir de datos
// de dominio, pero sin ViewModels: el renderizador del IDE no tiene Dispatchers.Main.
// apiLevel 36 coincide con el layoutlib del IDE aunque el proyecto compile con 37.

@Preview(
    name = "Claro", widthDp = 360, heightDp = 780, showBackground = true, apiLevel = 36)
@Preview(
    name = "Oscuro", widthDp = 360, heightDp = 780, showBackground = true, apiLevel = 36,
    uiMode = UI_MODE_NIGHT_YES or UI_MODE_TYPE_NORMAL,
)
private annotation class PhonePreviews

private const val ME = 0x1234
private const val NODE_B = 0x5678
private const val SECOND_MS = 1000L

private val nodes = listOf(MeshNode(NODE_B, "Nodo B", "B", hopsAway = 0))

private fun sent(id: Int, atSeconds: Int, text: String, status: MessageStatus) =
    Message(id, ME, NODE_B, atSeconds * SECOND_MS, text, status, signalQuality = null)

private fun received(id: Int, atSeconds: Int, text: String, rssi: Float, snr: Float, hops: Int? = 0) =
    Message(id, NODE_B, ME, atSeconds * SECOND_MS, text, MessageStatus.DELIVERED, LinkQuality(rssi, snr, hops))

private val conversation = listOf(
    sent(1, 3, "hola B, ¿me escuchás?", MessageStatus.DELIVERED),
    received(2, 5, "fuerte y claro", rssi = -97f, snr = 9.8f),
    sent(3, 12, "probando desde la terraza", MessageStatus.DELIVERED),
    received(4, 14, "llega bien", rssi = -112f, snr = 1.5f),
    received(5, 20, "esto vino retransmitido", rssi = -104f, snr = 4f, hops = 1),
    sent(6, 31, "último antes de movernos", MessageStatus.FAILED),
    sent(7, 40, "¿seguís ahí?", MessageStatus.PENDING),
)

private val measurements = listOf(
    received(11, 4, "m1", rssi = -92f, snr = 9.5f),
    received(12, 9, "m2", rssi = -101f, snr = 7.8f),
    received(13, 15, "m3", rssi = -108f, snr = 3.1f),
    received(14, 21, "m4", rssi = -99f, snr = 8.2f),
    received(15, 27, "m5", rssi = -117f, snr = -4.5f),
    received(16, 33, "m6", rssi = -126f, snr = -12f),
    received(17, 38, "m7", rssi = -110f, snr = 1.2f, hops = 2),
    received(18, 45, "m8", rssi = -103f, snr = 6.4f),
)

private val nodeSettings = LoraSettings(
    preset = ModemPreset.LONG_FAST,
    spreadingFactor = SpreadingFactor.SF11,
    bandwidthHz = 250_000.0,
    txPowerDbm = 20,
    frequencyMhz = null,
    region = "US",
)

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

private fun connectedMessaging() = MessagingSnapshot(
    state = MeshState.Ready(ME),
    nodes = nodes,
    messages = conversation,
    firmware = "2.6.11",
    draft = "vamos a 2 km",
).toUiState()

private fun prediction(drafts: Map<LinkField, String> = emptyMap()) =
    PredictionSnapshot(LinkParameters(), drafts, nodeSettings).toUiState()

private fun measurement(messages: List<Message> = measurements) =
    MeasurementSnapshot(messages, nodes, myNodeNum = ME).toUiState()

@Composable
private fun PreviewSurface(content: @Composable () -> Unit) {
    MeshTheme { Surface(color = MaterialTheme.colorScheme.surface, content = content) }
}

@PhonePreviews
@Composable
private fun AppPreview() {
    var selected by remember { mutableIntStateOf(0) }
    AppScaffold(selected, onSelected = { selected = it }) { modifier ->
        when (selected) {
            0 -> MessagingContent(connectedMessaging(), NoMessagingActions, modifier)
            1 -> PredictionContent(prediction(), NoPredictionActions, modifier)
            else -> MeasurementContent(measurement(), NoMeasurementActions, modifier)
        }
    }
}

@PhonePreviews
@Composable
private fun MessagingPreview() = PreviewSurface { MessagingContent(connectedMessaging(), NoMessagingActions) }

@PhonePreviews
@Composable
private fun MessagingDisconnectedPreview() = PreviewSurface {
    val state = MessagingSnapshot(
        state = MeshState.Failed("Bluetooth apagado: activalo y volvé a buscar"),
        nodes = emptyList(),
        messages = emptyList(),
        devices = listOf(
            RadioDevice("simulado", "Nodo simulado (sin hardware)"),
            RadioDevice("48:CA:43:5A:10:2F", "Meshtastic_102f"),
        ),
    ).toUiState()
    MessagingContent(state, NoMessagingActions)
}

@PhonePreviews
@Composable
private fun PredictionPreview() = PreviewSurface { PredictionContent(prediction(), NoPredictionActions) }

@PhonePreviews
@Composable
private fun PredictionInvalidPreview() = PreviewSurface {
    PredictionContent(prediction(mapOf(LinkField.DISTANCE to "abc")), NoPredictionActions)
}

@PhonePreviews
@Composable
private fun MeasurementPreview() = PreviewSurface { MeasurementContent(measurement(), NoMeasurementActions) }

@PhonePreviews
@Composable
private fun MeasurementEmptyPreview() = PreviewSurface { MeasurementContent(measurement(emptyList()), NoMeasurementActions) }

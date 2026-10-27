package com.example.meshviability

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meshviability.data.transport.RadioLink
import com.example.meshviability.ui.SessionViewModel
import com.example.meshviability.ui.theme.MeshTheme
import com.example.meshviability.ui.measurement.MeasurementScreen
import com.example.meshviability.ui.measurement.MeasurementViewModel
import com.example.meshviability.ui.messaging.MessagingScreen
import com.example.meshviability.ui.messaging.MessagingViewModel
import com.example.meshviability.ui.prediction.PredictionScreen
import com.example.meshviability.ui.prediction.PredictionViewModel

private val tabs = listOf("Mensajería", "Predicción", "Medición")

// platformLinks: Bluetooth en Android, USB en escritorio. El nodo simulado se agrega siempre.
@Composable
fun App(platformLinks: List<RadioLink> = emptyList()) {
    val session = viewModel { SessionViewModel(platformLinks) }
    val messaging = viewModel {
        MessagingViewModel(session.repository, session.settings, session.transport, session.sessionStartMs)
    }
    val prediction = viewModel { PredictionViewModel(session.settings, session.repository.loraSettings) }
    val measurement = viewModel { MeasurementViewModel(session.repository, session.settings, session.sessionStartMs) }

    var selected by rememberSaveable { mutableIntStateOf(0) }

    AppScaffold(selected, onSelected = { selected = it }) { modifier ->
        when (selected) {
            0 -> MessagingScreen(messaging, modifier)
            1 -> PredictionScreen(prediction, modifier)
            else -> MeasurementScreen(measurement, modifier)
        }
    }
}

// pestañas sin ViewModels, para poder usarlas en los previews
@Composable
fun AppScaffold(selected: Int, onSelected: (Int) -> Unit, content: @Composable (Modifier) -> Unit) {
    MeshTheme {
        Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                Column(Modifier.padding(start = 20.dp, end = 20.dp, top = 16.dp, bottom = 8.dp)) {
                    Text("meshViability", style = MaterialTheme.typography.titleLarge)
                    Text(
                        "Viabilidad de enlaces LoRa con Meshtastic",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                PrimaryTabRow(selectedTabIndex = selected, containerColor = MaterialTheme.colorScheme.surface) {
                    tabs.forEachIndexed { i, title ->
                        Tab(selected = i == selected, onClick = { onSelected(i) }, text = { Text(title) })
                    }
                }
                content(Modifier.weight(1f))
            }
        }
    }
}

package com.example.meshviability

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.ui.tooling.preview.Preview
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.meshviability.data.transport.RadioLink
import com.example.meshviability.ui.SessionViewModel
import com.example.meshviability.ui.measurement.MeasurementScreen
import com.example.meshviability.ui.measurement.MeasurementViewModel
import com.example.meshviability.ui.messaging.MessagingScreen
import com.example.meshviability.ui.messaging.MessagingViewModel
import com.example.meshviability.ui.prediction.PredictionScreen
import com.example.meshviability.ui.prediction.PredictionViewModel

private val tabs = listOf("Mensajería", "Predicción", "Medición")

// platformLinks: Bluetooth en Android, USB en escritorio. El nodo simulado se agrega siempre.
@Composable
@Preview
fun App(platformLinks: List<RadioLink> = emptyList()) {
    val session = viewModel { SessionViewModel(platformLinks) }
    val messaging = viewModel {
        MessagingViewModel(session.repository, session.settings, session.transport, session.sessionStartMs)
    }
    val prediction = viewModel { PredictionViewModel(session.settings, session.repository.loraSettings) }
    val measurement = viewModel { MeasurementViewModel(session.repository, session.settings, session.sessionStartMs) }

    var selected by rememberSaveable { mutableIntStateOf(0) }

    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().safeDrawingPadding()) {
                PrimaryTabRow(selectedTabIndex = selected) {
                    tabs.forEachIndexed { i, title ->
                        Tab(selected = i == selected, onClick = { selected = i }, text = { Text(title) })
                    }
                }
                when (selected) {
                    0 -> MessagingScreen(messaging, Modifier.weight(1f))
                    1 -> PredictionScreen(prediction, Modifier.weight(1f))
                    else -> MeasurementScreen(measurement, Modifier.weight(1f))
                }
            }
        }
    }
}

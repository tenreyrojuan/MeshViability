package com.example.meshviability.ui.measurement

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.meshviability.ui.components.EmptyState
import com.example.meshviability.ui.components.LevelBadge
import com.example.meshviability.ui.components.SectionCard
import com.example.meshviability.ui.components.SignalBars
import com.example.meshviability.ui.components.StatTile
import com.example.meshviability.ui.theme.color

@Composable
fun MeasurementScreen(viewModel: MeasurementViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MeasurementContent(state, viewModel, modifier)
}

@Composable
fun MeasurementContent(state: MeasurementUiState, actions: MeasurementActions, modifier: Modifier = Modifier) {
    LazyColumn(
        modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { SummaryCard(state, actions) }
        if (state.chart.isNotEmpty()) {
            item {
                SectionCard(title = "Margen por medición") {
                    MarginChart(state)
                    Text(
                        "Peor margen entre RSSI y SNR de cada mensaje directo. Líneas: umbrales de Sólido y Aceptable.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
        item { CriteriaCard(state, actions) }
        if (state.rows.isEmpty()) {
            item {
                EmptyState(
                    title = "Sin mediciones válidas",
                    body = "Solo cuentan los mensajes recibidos directo del otro nodo (0 saltos). Los retransmitidos se descartan.",
                )
            }
        }
        items(state.rows) { MeasurementCard(it) }
    }
}

@Suppress("DEPRECATION") // LocalClipboard pide un ClipEntry distinto por plataforma
@Composable
private fun SummaryCard(state: MeasurementUiState, actions: MeasurementActions) {
    val clipboard = LocalClipboardManager.current
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            SignalBars(state.overallLevel, 44.dp, description = state.overallLevelLabel)
            Column(Modifier.weight(1f)) {
                Text(state.overallLevelLabel ?: "Sin datos", style = MaterialTheme.typography.headlineSmall)
                Text(state.overallCaption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatTile("Margen promedio", state.averageWorstMargin ?: "—", Modifier.weight(1f), caption = "peor de RSSI y SNR")
            StatTile("Pérdida extra", state.averageExtraLoss ?: "—", Modifier.weight(1f), caption = "vs. espacio libre a ${state.distance}")
        }
        OutlinedButton(
            onClick = { clipboard.setText(AnnotatedString(actions.exportCsv())) },
            enabled = state.canExport,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Copiar sesión como CSV") }
        state.exportNotice?.let {
            Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CriteriaCard(state: MeasurementUiState, actions: MeasurementActions) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    val open = expanded || state.thresholdError != null
    SectionCard(
        title = "Criterios del semáforo",
        action = { TextButton(onClick = { expanded = !open }) { Text(if (open) "Listo" else "Editar") } },
    ) {
        Text(state.criteriaSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        AnimatedVisibility(open) {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    state.thresholdFields.forEach { f ->
                        OutlinedTextField(
                            value = f.text,
                            onValueChange = { actions.onThresholdChange(f.field, it) },
                            modifier = Modifier.weight(1f),
                            label = { Text(f.field.label) },
                            suffix = { Text("dB") },
                            isError = f.isError,
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        )
                    }
                }
                state.thresholdError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Contar paquetes con saltos desconocidos (firmware anterior a 2.3)",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Switch(checked = state.acceptUnknownHops, onCheckedChange = actions::onAcceptUnknownHopsChange)
                }
            }
        }
    }
}

@Composable
private fun MeasurementCard(row: MeasurementRow) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            Box(Modifier.width(5.dp).fillMaxHeight().background(row.level.color()))
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LevelBadge(row.level, row.levelLabel)
                    Text(
                        "${row.fromLabel} · ${row.elapsed}",
                        Modifier.weight(1f),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("RSSI", row.rssi, "margen ${row.rssiMargin}", Modifier.weight(1f))
                    Metric("SNR", row.snr, "margen ${row.snrMargin}", Modifier.weight(1f))
                    Metric("Margen real", row.worstMargin, "el peor de los dos", Modifier.weight(1f))
                }
                Text(
                    "Esperado ${row.expectedRssi} · pérdida extra ${row.extraLoss}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, caption: String, modifier: Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium)
        Text(caption, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

package com.example.meshviability.ui.measurement

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.meshviability.domain.link.LinkLevel
import com.example.meshviability.ui.color

@Composable
fun MeasurementScreen(viewModel: MeasurementViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LazyColumn(
        modifier.fillMaxSize().padding(horizontal = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item { SummaryCard(state, viewModel) }
        item { CriteriaCard(state, viewModel) }
        if (state.rows.isEmpty()) {
            item {
                Text(
                    "Todavía no hay mediciones válidas. Solo cuentan los mensajes recibidos directo (0 saltos).",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
        items(state.rows) { MeasurementCard(it) }
    }
}

@Composable
private fun LevelDot(level: LinkLevel) {
    Box(Modifier.size(16.dp).clip(CircleShape).background(level.color()))
}

@Suppress("DEPRECATION") // LocalClipboard pide un ClipEntry distinto por plataforma
@Composable
private fun SummaryCard(state: MeasurementUiState, viewModel: MeasurementViewModel) {
    val clipboard = LocalClipboardManager.current
    Card(Modifier.fillMaxWidth().padding(top = 12.dp)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.overallLevel?.let { LevelDot(it) }
                Text(state.overallLevelLabel ?: "Sin mediciones", style = MaterialTheme.typography.titleMedium)
            }
            Text("Mediciones válidas: ${state.rows.size} · descartadas: ${state.discarded}")
            state.averageWorstMargin?.let { Text("Margen promedio (peor de RSSI y SNR): $it") }
            state.averageExtraLoss?.let { Text("Pérdida extra promedio vs. espacio libre a ${state.distance}: $it") }
            OutlinedButton(onClick = { clipboard.setText(AnnotatedString(viewModel.exportCsv())) }, enabled = state.canExport) {
                Text("Copiar sesión como CSV")
            }
            state.exportNotice?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

@Composable
private fun CriteriaCard(state: MeasurementUiState, viewModel: MeasurementViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Criterios del semáforo (dB)", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                state.thresholdFields.forEach { f ->
                    OutlinedTextField(
                        value = f.text,
                        onValueChange = { viewModel.onThresholdChange(f.field, it) },
                        modifier = Modifier.weight(1f),
                        label = { Text(f.field.label) },
                        isError = f.isError,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    )
                }
            }
            state.thresholdError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Contar paquetes con saltos desconocidos (firmware < 2.3)", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                Switch(checked = state.acceptUnknownHops, onCheckedChange = viewModel::onAcceptUnknownHopsChange)
            }
        }
    }
}

@Composable
private fun MeasurementCard(row: MeasurementRow) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                LevelDot(row.level)
                Text(row.levelLabel, fontWeight = FontWeight.Bold, color = row.level.color())
                Text("${row.fromLabel} · ${row.elapsed}", Modifier.weight(1f), style = MaterialTheme.typography.labelSmall)
            }
            ValueRow("RSSI ${row.rssi}", "margen ${row.rssiMargin}")
            ValueRow("SNR ${row.snr}", "margen ${row.snrMargin}")
            ValueRow("Margen real (peor)", row.worstMargin)
            ValueRow("RSSI esperado", row.expectedRssi)
            ValueRow("Pérdida extra", row.extraLoss)
        }
    }
}

@Composable
private fun ValueRow(label: String, value: String) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

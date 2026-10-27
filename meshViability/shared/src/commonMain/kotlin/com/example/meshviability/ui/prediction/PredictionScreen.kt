package com.example.meshviability.ui.prediction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.meshviability.domain.link.SpreadingFactor

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PredictionScreen(viewModel: PredictionViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        ResultCard(state.result)
        Text(state.nodeSummary, style = MaterialTheme.typography.bodySmall)

        Text(state.spreadingFactorLabel, style = MaterialTheme.typography.labelLarge)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            SpreadingFactor.entries.forEach { sf ->
                FilterChip(
                    selected = sf == state.spreadingFactor,
                    onClick = { viewModel.onSpreadingFactorChange(sf) },
                    label = { Text(sf.name) },
                )
            }
        }

        state.fields.forEach { f ->
            OutlinedTextField(
                value = f.text,
                onValueChange = { viewModel.onFieldChange(f.field, it) },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("${f.field.label} (${f.field.unit})") },
                isError = f.isError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                supportingText = f.supportingText?.let { text -> { Text(text) } },
            )
        }
    }
}

@Composable
private fun ResultCard(result: PredictionResultUi?) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("Predicción (espacio libre)", style = MaterialTheme.typography.titleMedium)
            if (result == null) {
                Text("Corregí los parámetros marcados para ver el resultado", color = MaterialTheme.colorScheme.error)
                return@Column
            }
            ResultRow("Ruido N", result.noise)
            ResultRow("Sensibilidad S", result.sensitivity)
            ResultRow("Pérdida L0", result.pathLoss)
            ResultRow("Potencia recibida Prx", result.rxPower)
            ResultRow("Margen M", result.margin, bold = true)
            ResultRow("Alcance máximo (M = 0)", result.maxRange, bold = true)
        }
    }
}

@Composable
private fun ResultRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f))
        Text(value, fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal)
    }
}

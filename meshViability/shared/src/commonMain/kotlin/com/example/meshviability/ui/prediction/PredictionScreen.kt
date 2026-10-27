package com.example.meshviability.ui.prediction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.meshviability.domain.link.SpreadingFactor
import com.example.meshviability.ui.components.LevelBadge
import com.example.meshviability.ui.components.SectionCard
import com.example.meshviability.ui.components.StatTile

@Composable
fun PredictionScreen(viewModel: PredictionViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PredictionContent(state, viewModel, modifier)
}

@Composable
fun PredictionContent(state: PredictionUiState, actions: PredictionActions, modifier: Modifier = Modifier) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        ResultHero(state.result)
        state.result?.let { r ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Sensibilidad S", r.sensitivity, Modifier.weight(1f))
                StatTile("Potencia recibida", r.rxPower, Modifier.weight(1f))
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatTile("Ruido N", r.noise, Modifier.weight(1f))
                StatTile("Pérdida L0", r.pathLoss, Modifier.weight(1f))
            }
        }
        if (state.budget.isNotEmpty()) BudgetCard(state.budget)

        Text(state.nodeSummary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        state.sections.forEach { section ->
            SectionCard(title = section.section.title) {
                if (section.section == LinkSection.RADIO) SpreadingFactorPicker(state, actions)
                section.fields.chunked(2).forEach { pair ->
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        pair.forEach { FieldInput(it, actions, Modifier.weight(1f)) }
                        if (pair.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun ResultHero(result: PredictionResultUi?) {
    val ink = MaterialTheme.colorScheme.onPrimaryContainer
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.primaryContainer) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (result == null) {
                Text("Predicción en espacio libre", style = MaterialTheme.typography.titleMedium, color = ink)
                Text(
                    "Corregí los parámetros marcados para ver el resultado.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.error,
                )
                return@Column
            }
            Text(result.marginCaption, style = MaterialTheme.typography.labelLarge, color = ink)
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(result.margin, style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = ink)
                LevelBadge(result.level, result.levelLabel)
            }
            Text("Alcance teórico (margen 0): ${result.maxRange}", style = MaterialTheme.typography.bodyMedium, color = ink)
        }
    }
}

@Composable
private fun BudgetCard(lines: List<BudgetLine>) {
    SectionCard(title = "Balance del enlace") {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            lines.forEach { line ->
                if (line.isTotal) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                val style = if (line.isTotal) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium
                Row(Modifier.fillMaxWidth()) {
                    Text(
                        line.label,
                        Modifier.weight(1f),
                        style = style,
                        color = if (line.isTotal) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(line.value, style = style)
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SpreadingFactorPicker(state: PredictionUiState, actions: PredictionActions) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(state.spreadingFactorLabel, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SpreadingFactor.entries.forEach { sf ->
                FilterChip(
                    selected = sf == state.spreadingFactor,
                    onClick = { actions.onSpreadingFactorChange(sf) },
                    label = { Text(sf.name) },
                )
            }
        }
    }
}

@Composable
private fun FieldInput(f: FieldUi, actions: PredictionActions, modifier: Modifier) {
    OutlinedTextField(
        value = f.text,
        onValueChange = { actions.onFieldChange(f.field, it) },
        modifier = modifier,
        label = { Text(f.field.label, maxLines = 1) },
        suffix = { Text(f.field.unit) },
        isError = f.isError,
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        supportingText = f.supportingText?.let { text -> { Text(text) } },
    )
}

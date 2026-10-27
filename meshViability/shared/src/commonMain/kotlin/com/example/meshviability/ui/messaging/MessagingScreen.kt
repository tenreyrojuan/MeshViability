package com.example.meshviability.ui.messaging

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.meshviability.ui.color

@Composable
fun MessagingScreen(viewModel: MessagingViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    Column(modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        ConnectionCard(state, viewModel)
        state.error?.let { ErrorBanner(it, viewModel::dismissError) }
        if (state.isConnected) DestinationPicker(state, viewModel::onDestinationSelected)
        MessageList(state.messages, Modifier.weight(1f))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = viewModel::onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text("Mensaje") },
                singleLine = true,
            )
            Button(onClick = viewModel::send, enabled = state.canSend) { Text("Enviar") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ConnectionCard(state: MessagingUiState, viewModel: MessagingViewModel) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(state.statusText, style = MaterialTheme.typography.titleMedium)
            state.firmwareVersion?.let { Text("Firmware $it", style = MaterialTheme.typography.bodySmall) }
            if (state.invalidPackets > 0) {
                Text("Paquetes inválidos descartados: ${state.invalidPackets}", style = MaterialTheme.typography.bodySmall)
            }
            if (state.isConnected) {
                OutlinedButton(onClick = viewModel::disconnect, enabled = !state.isBusy) { Text("Desconectar") }
            } else {
                OutlinedButton(onClick = viewModel::scan, enabled = !state.isBusy) { Text("Buscar nodos") }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.devices.forEach { device ->
                        Button(onClick = { viewModel.connect(device) }, enabled = !state.isBusy) { Text(device.name) }
                    }
                }
            }
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(start = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onDismiss) { Text("OK") }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DestinationPicker(state: MessagingUiState, onSelected: (Int) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
        Text("Para:", Modifier.align(Alignment.CenterVertically))
        state.destinations.forEach { option ->
            FilterChip(
                selected = option.num == state.destination,
                onClick = { onSelected(option.num) },
                label = { Text(option.label) },
            )
        }
    }
}

@Composable
private fun MessageList(messages: List<MessageItem>, modifier: Modifier) {
    val listState = rememberLazyListState()
    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }
    LazyColumn(modifier.fillMaxWidth(), state = listState, verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(messages) { MessageCard(it) }
    }
}

@Composable
private fun MessageCard(item: MessageItem) {
    val container = if (item.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row {
                Text("${item.fromLabel} → ${item.toLabel}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
                Text(item.elapsed, style = MaterialTheme.typography.labelSmall)
            }
            Text(item.content, style = MaterialTheme.typography.bodyLarge)
            if (item.status != null && item.statusLabel != null) {
                Text(item.statusLabel, color = item.status.color(), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.labelMedium)
            }
            item.signal?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            item.validityNote?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }
    }
}

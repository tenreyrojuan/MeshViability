package com.example.meshviability.ui.messaging

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.meshviability.data.transport.RadioDevice
import com.example.meshviability.ui.components.EmptyState
import com.example.meshviability.ui.components.SectionCard
import com.example.meshviability.ui.components.SignalBars
import com.example.meshviability.ui.components.StatusDot
import com.example.meshviability.ui.theme.color

@Composable
fun MessagingScreen(viewModel: MessagingViewModel, modifier: Modifier = Modifier) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    MessagingContent(state, viewModel, modifier)
}

@Composable
fun MessagingContent(state: MessagingUiState, actions: MessagingActions, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize()) {
        Column(
            Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            ConnectionCard(state, actions)
            state.error?.let { ErrorBanner(it, actions::dismissError) }
            if (state.isConnected) DestinationPicker(state, actions::onDestinationSelected)
        }
        if (state.messages.isEmpty()) {
            EmptyState(
                title = "Sin mensajes",
                body = if (state.isConnected) {
                    "Enviá el primero. Cada mensaje que llega directo del otro nodo es una medición del enlace."
                } else {
                    "Cuando te conectes a un nodo, acá vas a ver la conversación y la señal de cada mensaje."
                },
                modifier = Modifier.weight(1f),
            )
        } else {
            MessageList(state.messages, Modifier.weight(1f))
        }
        Composer(state, actions)
    }
}

@Composable
private fun ConnectionCard(state: MessagingUiState, actions: MessagingActions) {
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusDot(state.connection.color(), 12.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(state.statusText, style = MaterialTheme.typography.titleMedium)
                state.detailText?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            when {
                state.isBusy -> CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp)
                state.isConnected -> TextButton(onClick = actions::disconnect) { Text("Desconectar") }
            }
        }
        if (!state.isConnected) {
            state.hint?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            state.devices.forEach { device -> DeviceRow(device, enabled = !state.isBusy) { actions.connect(device) } }
            FilledTonalButton(onClick = actions::scan, enabled = !state.isBusy, modifier = Modifier.fillMaxWidth()) {
                Text(state.scanLabel)
            }
        }
    }
}

@Composable
private fun DeviceRow(device: RadioDevice, enabled: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        enabled = enabled,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(device.name, style = MaterialTheme.typography.bodyLarge)
                Text(device.address, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text("Conectar", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ErrorBanner(message: String, onDismiss: () -> Unit) {
    Surface(Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.medium, color = MaterialTheme.colorScheme.errorContainer) {
        Row(Modifier.padding(start = 16.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                message,
                Modifier.weight(1f).padding(vertical = 8.dp),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onErrorContainer,
            )
            TextButton(onClick = onDismiss) { Text("Cerrar", color = MaterialTheme.colorScheme.onErrorContainer) }
        }
    }
}

@Composable
private fun DestinationPicker(state: MessagingUiState, onSelected: (Int) -> Unit) {
    Row(
        Modifier.horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("Para", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
    LazyColumn(
        modifier.fillMaxWidth(),
        state = listState,
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(messages) { MessageBubble(it) }
    }
}

@Composable
private fun MessageBubble(item: MessageItem) {
    val shape = if (item.mine) RoundedCornerShape(18.dp, 18.dp, 4.dp, 18.dp) else RoundedCornerShape(18.dp, 18.dp, 18.dp, 4.dp)
    val container = if (item.mine) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh
    val ink = if (item.mine) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
    val muted = if (item.mine) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f) else MaterialTheme.colorScheme.onSurfaceVariant

    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (item.mine) Arrangement.End else Arrangement.Start) {
        Surface(shape = shape, color = container, modifier = Modifier.widthIn(max = 320.dp)) {
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                if (!item.mine) Text(item.fromLabel, style = MaterialTheme.typography.labelMedium, color = muted)
                Text(item.content, style = MaterialTheme.typography.bodyLarge, color = ink)
                item.signal?.let { signal ->
                    HorizontalDivider(Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SignalBars(item.level, 14.dp, description = item.levelLabel ?: "No válido para medición")
                        Text(signal, style = MaterialTheme.typography.labelMedium, color = muted)
                    }
                    item.levelLabel?.let {
                        Text("Enlace $it", style = MaterialTheme.typography.labelMedium, color = ink)
                    }
                }
                item.validityNote?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                }
                Row(
                    Modifier.align(Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(item.elapsed, style = MaterialTheme.typography.labelSmall, color = muted)
                    if (item.status != null && item.statusLabel != null) {
                        StatusDot(item.status.color(), 6.dp)
                        Text(item.statusLabel, style = MaterialTheme.typography.labelSmall, color = muted)
                    }
                }
            }
        }
    }
}

@Composable
private fun Composer(state: MessagingUiState, actions: MessagingActions) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainer) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = state.draft,
                onValueChange = actions::onDraftChange,
                modifier = Modifier.weight(1f),
                placeholder = { Text(state.composerHint) },
                enabled = state.isConnected,
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
            )
            Button(onClick = actions::send, enabled = state.canSend, shape = CircleShape) { Text("Enviar") }
        }
    }
}

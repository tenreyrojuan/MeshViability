package com.example.meshviability.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.meshviability.domain.entities.MessageStatus
import com.example.meshviability.domain.link.LinkLevel
import com.example.meshviability.ui.messaging.ConnectionKind

private val LightColors = lightColorScheme(
    primary = Color(0xFF1F5FAF),
    onPrimary = Color(0xFFFFFFFF),
    primaryContainer = Color(0xFFD7E3FF),
    onPrimaryContainer = Color(0xFF001B3F),
    secondary = Color(0xFF006A6A),
    onSecondary = Color(0xFFFFFFFF),
    secondaryContainer = Color(0xFFB4EEED),
    onSecondaryContainer = Color(0xFF002020),
    tertiary = Color(0xFF6B5778),
    onTertiary = Color(0xFFFFFFFF),
    tertiaryContainer = Color(0xFFF2DAFF),
    onTertiaryContainer = Color(0xFF251431),
    background = Color(0xFFF8F9FC),
    onBackground = Color(0xFF191C20),
    surface = Color(0xFFF8F9FC),
    onSurface = Color(0xFF191C20),
    surfaceVariant = Color(0xFFE0E2EC),
    onSurfaceVariant = Color(0xFF44474E),
    outline = Color(0xFF74777F),
    outlineVariant = Color(0xFFC4C6D0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF2F3F7),
    surfaceContainer = Color(0xFFECEEF2),
    surfaceContainerHigh = Color(0xFFE6E8EC),
    surfaceContainerHighest = Color(0xFFE1E2E6),
    error = Color(0xFFBA1A1A),
    onError = Color(0xFFFFFFFF),
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFABC7FF),
    onPrimary = Color(0xFF002F65),
    primaryContainer = Color(0xFF00468E),
    onPrimaryContainer = Color(0xFFD7E3FF),
    secondary = Color(0xFF80D5D4),
    onSecondary = Color(0xFF003737),
    secondaryContainer = Color(0xFF004F4F),
    onSecondaryContainer = Color(0xFFB4EEED),
    tertiary = Color(0xFFD6BEE4),
    onTertiary = Color(0xFF3B2948),
    tertiaryContainer = Color(0xFF523F5F),
    onTertiaryContainer = Color(0xFFF2DAFF),
    background = Color(0xFF111318),
    onBackground = Color(0xFFE1E2E9),
    surface = Color(0xFF111318),
    onSurface = Color(0xFFE1E2E9),
    surfaceVariant = Color(0xFF44474E),
    onSurfaceVariant = Color(0xFFC4C6D0),
    outline = Color(0xFF8E9099),
    outlineVariant = Color(0xFF44474E),
    surfaceContainerLowest = Color(0xFF0C0E13),
    surfaceContainerLow = Color(0xFF191C20),
    surfaceContainer = Color(0xFF1D2024),
    surfaceContainerHigh = Color(0xFF282A2F),
    surfaceContainerHighest = Color(0xFF33353A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
)

private val MeshShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

@Composable
fun MeshTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val base = Typography()
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        shapes = MeshShapes,
        typography = base.copy(
            titleLarge = base.titleLarge.copy(fontWeight = FontWeight.SemiBold),
            titleMedium = base.titleMedium.copy(fontWeight = FontWeight.SemiBold),
            titleSmall = base.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        ),
        content = content,
    )
}

// Colores de estado: los mismos en claro y oscuro, y siempre junto a una etiqueta de texto.
object StatusColors {
    val good = Color(0xFF0CA30C)
    val warning = Color(0xFFFAB219)
    val serious = Color(0xFFEC835A)
    val critical = Color(0xFFD03B3B)
}

fun LinkLevel.color(): Color = when (this) {
    LinkLevel.SOLID -> StatusColors.good
    LinkLevel.ACCEPTABLE -> StatusColors.warning
    LinkLevel.FRAGILE -> StatusColors.serious
    LinkLevel.UNRELIABLE -> StatusColors.critical
}

@Composable
fun MessageStatus.color(): Color = when (this) {
    MessageStatus.PENDING -> MaterialTheme.colorScheme.outline
    MessageStatus.DELIVERED -> StatusColors.good
    MessageStatus.FAILED -> StatusColors.critical
}

@Composable
fun ConnectionKind.color(): Color = when (this) {
    ConnectionKind.DISCONNECTED -> MaterialTheme.colorScheme.outline
    ConnectionKind.CONNECTING -> StatusColors.warning
    ConnectionKind.CONNECTED -> StatusColors.good
    ConnectionKind.ERROR -> StatusColors.critical
}

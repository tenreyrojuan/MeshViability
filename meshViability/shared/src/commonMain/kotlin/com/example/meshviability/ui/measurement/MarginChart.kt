package com.example.meshviability.ui.measurement

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import com.example.meshviability.ui.formatInput
import com.example.meshviability.ui.theme.color
import kotlin.math.max
import kotlin.math.min

private const val HEADROOM_DB = 6.0

/** Peor margen de cada medición, con las líneas de los umbrales del semáforo. */
@Composable
fun MarginChart(state: MeasurementUiState, modifier: Modifier = Modifier) {
    val measurer = rememberTextMeasurer()
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val grid = MaterialTheme.colorScheme.outlineVariant
    val baseline = MaterialTheme.colorScheme.outline
    val bars = state.chart

    Canvas(modifier.fillMaxWidth().height(160.dp).semantics { contentDescription = state.chartDescription }) {
        if (bars.isEmpty()) return@Canvas
        val gutter = 44.dp.toPx()
        val plotWidth = size.width - gutter
        val top = max(bars.maxOf { it.marginDb }, state.solidDb) + HEADROOM_DB
        val bottom = min(bars.minOf { it.marginDb }, 0.0).let { if (it < 0) it - HEADROOM_DB else it }
        fun y(db: Double) = (size.height * (top - db) / (top - bottom)).toFloat()

        val dash = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx()))
        guide(y(state.solidDb), plotWidth, grid, dash, measurer, "${formatInput(state.solidDb)} dB", labelStyle)
        guide(y(state.acceptableDb), plotWidth, grid, dash, measurer, "${formatInput(state.acceptableDb)} dB", labelStyle)
        guide(y(0.0), plotWidth, baseline, null, measurer, "0 dB", labelStyle)

        val slot = plotWidth / bars.size
        val barWidth = min(slot * 0.6f, 14.dp.toPx())
        val radius = CornerRadius(min(4.dp.toPx(), barWidth / 2))
        val zero = y(0.0)
        bars.forEachIndexed { i, bar ->
            val left = i * slot + (slot - barWidth) / 2
            val end = y(bar.marginDb)
            // extremo redondeado del lado del dato, recto contra la línea de 0 dB
            val rect = if (bar.marginDb >= 0) {
                RoundRect(left, end, left + barWidth, zero, topLeftCornerRadius = radius, topRightCornerRadius = radius)
            } else {
                RoundRect(left, zero, left + barWidth, end, bottomLeftCornerRadius = radius, bottomRightCornerRadius = radius)
            }
            drawPath(Path().apply { addRoundRect(rect) }, bar.level.color())
        }
    }
}

private fun DrawScope.guide(
    y: Float,
    width: Float,
    color: Color,
    effect: PathEffect?,
    measurer: TextMeasurer,
    label: String,
    style: TextStyle,
) {
    drawLine(color, Offset(0f, y), Offset(width, y), strokeWidth = 1.dp.toPx(), pathEffect = effect)
    val text = measurer.measure(label, style)
    drawText(text, topLeft = Offset(width + 6.dp.toPx(), y - text.size.height / 2f))
}

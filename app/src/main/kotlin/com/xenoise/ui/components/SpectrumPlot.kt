package com.xenoise.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xenoise.model.NoiseColors
import com.xenoise.model.Presets
import com.xenoise.model.Slopes
import com.xenoise.ui.theme.XenoiseColors
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.min

private const val MIN_HZ = 20f
private const val MAX_HZ = 20_000f
private val TOTAL_OCTAVES = (ln(MAX_HZ / MIN_HZ) / ln(2f))

/** Vertical span of the chart in dB. A line at the steepest slope just reaches the corners. */
private const val DB_SPAN = 90f

/** Grabbing closer than this to the pivot would make the slope jump around. */
private const val MIN_GRAB_OCTAVES = 0.6f

private val GRID_HZ = floatArrayOf(20f, 50f, 100f, 200f, 500f, 1_000f, 2_000f, 5_000f, 10_000f, 20_000f)
private val MAJOR_HZ = setOf(100f, 1_000f, 10_000f)
private val LABELED_HZ = listOf(20f to "20", 100f to "100", 1_000f to "1k", 10_000f to "10k", 20_000f to "20k")
private val GRID_DB = floatArrayOf(-30f, -15f, 0f, 15f, 30f)

/**
 * The spectrum as a straight line: frequency on a log scale across, level in dB up.
 * Faint lines show the five presets. When [onSlopeChange] is set, touching or dragging
 * anywhere on the chart rotates the line around its center to follow the finger. The raw
 * slope under the finger is reported; snapping to presets is up to the caller.
 */
@Composable
fun SpectrumPlot(
    slope: Float,
    color: Color,
    modifier: Modifier = Modifier,
    onSlopeChange: ((Float) -> Unit)? = null,
) {
    val textMeasurer = rememberTextMeasurer(cacheSize = 32)
    val localDensity = LocalDensity.current
    val labelStyle = MaterialTheme.typography.labelSmall.copy(letterSpacing = 0.sp)
    val currentSlope by rememberUpdatedState(slope)
    val onChange by rememberUpdatedState(onSlopeChange)
    var dragging by remember { mutableStateOf(false) }

    val interaction = if (onSlopeChange == null) Modifier else Modifier.pointerInput(Unit) {
        awaitEachGesture {
            val down = awaitFirstDown()
            dragging = true
            try {
                var position = down.position
                down.consume()
                while (true) {
                    val plot = plotArea(Size(size.width.toFloat(), size.height.toFloat()), localDensity)
                    val raw = slopeAt(position, plot)
                    // Only report moves that change the rounded, snapped slope.
                    if (raw != null && Slopes.snap(raw) != currentSlope) onChange?.invoke(raw)
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    if (!change.pressed) break
                    position = change.position
                    change.consume()
                }
            } finally {
                dragging = false
            }
        }
    }

    Canvas(
        modifier
            .clip(RoundedCornerShape(24.dp))
            .background(XenoiseColors.Surface)
            .semantics { contentDescription = "Spectrum line, ${Slopes.format(slope)} dB per octave" }
            .then(interaction)
    ) {
        val plot = plotArea(size, this)
        drawGrid(plot)
        drawFrequencyLabels(plot, textMeasurer, labelStyle)

        // The five presets as faint guides, labeled at the right edge.
        for (preset in Presets.all) {
            val presetColor = Color(NoiseColors.forSlope(preset.slope))
            val (a, b) = lineEnds(preset.slope, plot)
            drawLine(
                color = presetColor.copy(alpha = 0.3f),
                start = a,
                end = b,
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(4.dp.toPx(), 4.dp.toPx())),
            )
            val label = textMeasurer.measure(preset.name.lowercase(), labelStyle)
            drawText(
                textLayoutResult = label,
                color = presetColor.copy(alpha = 0.75f),
                topLeft = Offset(plot.right + 6.dp.toPx(), b.y - label.size.height / 2f),
            )
        }

        val (a, b) = lineEnds(slope, plot)
        clipRect(plot.left, plot.top, plot.right, plot.bottom) {
            val fill = Path().apply {
                moveTo(a.x, a.y)
                lineTo(b.x, b.y)
                lineTo(plot.right, plot.bottom)
                lineTo(plot.left, plot.bottom)
                close()
            }
            drawPath(
                path = fill,
                brush = Brush.verticalGradient(
                    0f to color.copy(alpha = 0.32f),
                    1f to color.copy(alpha = 0.02f),
                    startY = min(a.y, b.y),
                    endY = plot.bottom,
                ),
            )
        }
        drawLine(color.copy(alpha = 0.22f), a, b, strokeWidth = 12.dp.toPx(), cap = StrokeCap.Round)
        drawLine(color, a, b, strokeWidth = 3.dp.toPx(), cap = StrokeCap.Round)

        val handle = if (dragging) 10.dp.toPx() else 7.dp.toPx()
        for (end in listOf(a, b)) {
            drawCircle(color, radius = handle, center = end)
            drawCircle(XenoiseColors.Surface, radius = handle * 0.42f, center = end)
        }
        drawCircle(XenoiseColors.Muted, radius = 3.dp.toPx(), center = plot.center)
    }
}

/** The chart area inside the card, leaving room for labels below and to the right. */
private fun plotArea(size: Size, density: Density): Rect = with(density) {
    Rect(
        left = 14.dp.toPx(),
        top = 18.dp.toPx(),
        right = size.width - 50.dp.toPx(),
        bottom = size.height - 30.dp.toPx(),
    )
}

/** Where the line for [slope] meets the left and right edges of [plot]. */
private fun lineEnds(slope: Float, plot: Rect): Pair<Offset, Offset> {
    val edgeDb = slope * TOTAL_OCTAVES / 2f
    val dy = edgeDb / DB_SPAN * plot.height
    return Offset(plot.left, plot.center.y + dy) to Offset(plot.right, plot.center.y - dy)
}

/** The slope whose line passes under the finger, or null too close to the pivot. */
private fun slopeAt(position: Offset, plot: Rect): Float? {
    val octaves = (position.x - plot.center.x) / plot.width * TOTAL_OCTAVES
    if (abs(octaves) < MIN_GRAB_OCTAVES) return null
    val db = (plot.center.y - position.y) / plot.height * DB_SPAN
    return db / octaves
}

private fun xFor(hz: Float, plot: Rect): Float =
    plot.left + (ln(hz / MIN_HZ) / ln(2f)) / TOTAL_OCTAVES * plot.width

private fun yFor(db: Float, plot: Rect): Float =
    plot.center.y - db / DB_SPAN * plot.height

private fun DrawScope.drawGrid(plot: Rect) {
    for (hz in GRID_HZ) {
        val x = xFor(hz, plot)
        val major = hz in MAJOR_HZ
        drawLine(
            color = if (major) XenoiseColors.Outline else XenoiseColors.Outline.copy(alpha = 0.5f),
            start = Offset(x, plot.top),
            end = Offset(x, plot.bottom),
            strokeWidth = if (major) 1.dp.toPx() else 0.5.dp.toPx(),
        )
    }
    for (db in GRID_DB) {
        val y = yFor(db, plot)
        drawLine(
            color = if (db == 0f) XenoiseColors.Outline else XenoiseColors.Outline.copy(alpha = 0.5f),
            start = Offset(plot.left, y),
            end = Offset(plot.right, y),
            strokeWidth = if (db == 0f) 1.dp.toPx() else 0.5.dp.toPx(),
        )
    }
}

private fun DrawScope.drawFrequencyLabels(
    plot: Rect,
    textMeasurer: TextMeasurer,
    style: TextStyle,
) {
    for ((hz, text) in LABELED_HZ) {
        val layout = textMeasurer.measure(text, style)
        val x = (xFor(hz, plot) - layout.size.width / 2f)
            .coerceIn(plot.left, maxOf(plot.left, plot.right - layout.size.width))
        drawText(
            textLayoutResult = layout,
            color = XenoiseColors.Faint,
            topLeft = Offset(x, plot.bottom + 8.dp.toPx()),
        )
    }
    val hz = textMeasurer.measure("Hz", style)
    drawText(
        textLayoutResult = hz,
        color = XenoiseColors.Faint,
        topLeft = Offset(plot.right + 6.dp.toPx(), plot.bottom + 8.dp.toPx()),
    )
}

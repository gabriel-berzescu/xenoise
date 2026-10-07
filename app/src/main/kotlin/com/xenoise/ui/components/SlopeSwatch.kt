package com.xenoise.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.xenoise.model.NoiseColors
import com.xenoise.model.Slopes
import com.xenoise.ui.theme.XenoiseColors
import kotlin.math.max

/** A small disc in the noise's color with its spectrum line drawn across it. */
@Composable
fun SlopeSwatch(
    slope: Float,
    diameter: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val color = Color(NoiseColors.forSlope(slope))
    Canvas(modifier.size(diameter)) {
        val ring = 2.dp.toPx()
        val gap = 3.dp.toPx()
        val r = size.minDimension / 2f - ring - gap
        val c = center
        if (selected) {
            drawCircle(color = color, radius = r + gap + ring / 2f, center = c, style = Stroke(ring))
        }
        drawCircle(
            brush = Brush.radialGradient(
                0f to lerp(color, Color.White, 0.2f),
                0.5f to color,
                1f to lerp(color, XenoiseColors.Ink, 0.45f),
                center = c + Offset(-r * 0.3f, -r * 0.35f),
                radius = r * 1.7f,
            ),
            radius = r,
            center = c,
        )
        // Same orientation as the spectrum chart: deep noises fall to the right.
        val dx = r * 0.62f
        val dy = (slope / Slopes.MAX) * r * 0.55f
        drawLine(
            color = XenoiseColors.Ink.copy(alpha = 0.6f),
            start = c + Offset(-dx, dy),
            end = c + Offset(dx, -dy),
            strokeWidth = max(1.5.dp.toPx(), r * 0.09f),
            cap = StrokeCap.Round,
        )
    }
}

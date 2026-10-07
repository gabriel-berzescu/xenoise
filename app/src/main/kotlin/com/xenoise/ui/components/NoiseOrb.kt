package com.xenoise.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.xenoise.ui.theme.XenoiseColors
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/** Orb radius as a fraction of the composable's size; the rest is room for the glow. */
private const val ORB_RADIUS = 0.34f
private const val GRAIN_FRAMES = 8

/**
 * The big play button: a glowing orb in the noise's color, alive with grain while playing.
 * Deep noises get coarse, slow grain and bright ones fine, quick sparkle, so the texture
 * hints at the sound.
 */
@Composable
fun NoiseOrb(
    color: Color,
    slope: Float,
    playing: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val grain = remember(slope.roundToInt()) { GrainSpec.forSlope(slope) }
    val frame = remember { mutableIntStateOf(0) }

    LaunchedEffect(playing, grain.fps) {
        if (!playing) return@LaunchedEffect
        val interval = 1_000_000_000L / grain.fps
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (now - last >= interval) {
                    last = now
                    frame.intValue++
                }
            }
        }
    }

    val activity = animateFloatAsState(if (playing) 1f else 0f, tween(900), label = "orbActivity")

    // A slow breath while playing. When paused it settles and stops, so nothing redraws.
    val breath = remember { Animatable(0f) }
    LaunchedEffect(playing) {
        if (playing) {
            while (true) {
                breath.animateTo(1f, tween(2_400, easing = FastOutSlowInEasing))
                breath.animateTo(0f, tween(2_400, easing = FastOutSlowInEasing))
            }
        } else {
            breath.animateTo(0f, tween(900))
        }
    }
    val currentColor = rememberUpdatedState(color)

    Box(modifier, contentAlignment = Alignment.Center) {
        Spacer(
            Modifier
                .fillMaxSize()
                .drawWithCache {
                    val radius = size.minDimension * ORB_RADIUS
                    val c = Offset(size.width / 2f, size.height / 2f)
                    val grainRadius = radius * 0.96f
                    val pools = List(GRAIN_FRAMES) { i -> grainPoints(i, grain.count, c, grainRadius) }
                    val dot = grain.dotDp.dp.toPx()
                    onDrawBehind {
                        val col = currentColor.value
                        val act = activity.value
                        val r = radius * (1f + 0.025f * breath.value * act)

                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to col.copy(alpha = 0.10f + 0.22f * act),
                                0.55f to col.copy(alpha = 0.04f + 0.08f * act),
                                1f to Color.Transparent,
                                center = c,
                                radius = r * 1.5f,
                            ),
                            radius = r * 1.5f,
                            center = c,
                        )
                        drawCircle(
                            brush = Brush.radialGradient(
                                0f to lerp(col, Color.White, 0.25f),
                                0.45f to col,
                                1f to lerp(col, XenoiseColors.Ink, 0.55f),
                                center = c + Offset(-r * 0.3f, -r * 0.35f),
                                radius = r * 1.6f,
                            ),
                            radius = r,
                            center = c,
                        )
                        val f = frame.intValue
                        val grainAlpha = 0.14f + 0.22f * act
                        drawPoints(
                            points = pools[f % GRAIN_FRAMES],
                            pointMode = PointMode.Points,
                            color = XenoiseColors.Ink,
                            strokeWidth = dot,
                            cap = StrokeCap.Round,
                            alpha = grainAlpha,
                        )
                        drawPoints(
                            points = pools[(f + GRAIN_FRAMES / 2) % GRAIN_FRAMES],
                            pointMode = PointMode.Points,
                            color = Color.White,
                            strokeWidth = dot,
                            cap = StrokeCap.Round,
                            alpha = grainAlpha * 0.8f,
                        )
                    }
                }
        )
        Box(
            Modifier
                .fillMaxSize(ORB_RADIUS * 2f)
                .clip(CircleShape)
                .clickable(
                    onClickLabel = if (playing) "Pause" else "Play",
                    role = Role.Button,
                    onClick = onClick,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (playing) XenoiseIcons.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pause" else "Play",
                tint = XenoiseColors.Ink.copy(alpha = 0.8f),
                modifier = Modifier.size(64.dp),
            )
        }
    }
}

private class GrainSpec(val count: Int, val dotDp: Float, val fps: Int) {
    companion object {
        fun forSlope(slope: Float): GrainSpec {
            val t = ((slope + 9f) / 18f).coerceIn(0f, 1f)
            return GrainSpec(
                count = (120 + 520 * t).roundToInt(),
                dotDp = 3.2f - 2.0f * t,
                fps = (6 + 18 * t).roundToInt(),
            )
        }
    }
}

private fun grainPoints(seed: Int, count: Int, center: Offset, radius: Float): List<Offset> {
    val random = Random(seed * 7_919 + count)
    return List(count) {
        val r = radius * sqrt(random.nextFloat())
        val a = random.nextFloat() * (2f * Math.PI.toFloat())
        Offset(center.x + r * cos(a), center.y + r * sin(a))
    }
}

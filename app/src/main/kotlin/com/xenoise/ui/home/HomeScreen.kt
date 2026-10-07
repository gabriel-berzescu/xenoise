package com.xenoise.ui.home

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xenoise.model.Noise
import com.xenoise.model.NoiseColors
import com.xenoise.model.Presets
import com.xenoise.model.Slopes
import com.xenoise.model.TimerOptions
import com.xenoise.playback.PlayerState
import com.xenoise.playback.SleepTimer
import com.xenoise.ui.components.NoiseOrb
import com.xenoise.ui.components.SlopeSwatch
import com.xenoise.ui.components.TimerSheet
import com.xenoise.ui.components.XenoiseIcons
import com.xenoise.ui.theme.XenoiseColors
import kotlinx.coroutines.delay

@Composable
fun HomeScreen(
    state: PlayerState,
    customNoises: List<Noise>,
    onTogglePlay: () -> Unit,
    onPlayNoise: (Noise) -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeChangeFinished: () -> Unit,
    onEditNoise: (Noise) -> Unit,
    onNewNoise: () -> Unit,
    onStartTimer: (Long) -> Unit,
    onCancelTimer: () -> Unit,
) {
    val noise = state.noise
    val color by animateColorAsState(Color(NoiseColors.forSlope(noise.slope)), tween(700), label = "noiseColor")
    val remaining = rememberRemaining(state.timer)
    val remainingText = remaining?.let { TimerOptions.formatRemaining(it) }
    var showTimer by rememberSaveable { mutableStateOf(false) }

    Box(
        Modifier
            .fillMaxSize()
            .background(XenoiseColors.Ink)
            .drawBehind {
                drawRect(
                    Brush.radialGradient(
                        0f to color.copy(alpha = 0.16f),
                        1f to Color.Transparent,
                        center = Offset(size.width / 2f, size.height * 0.25f),
                        radius = size.width * 0.95f,
                    )
                )
            }
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .windowInsetsPadding(WindowInsets.safeDrawing)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Header(
                accent = color,
                remainingText = remainingText,
                onTimerClick = { showTimer = true },
            )

            NoiseOrb(
                color = color,
                slope = noise.slope,
                playing = state.isPlaying,
                onClick = onTogglePlay,
                modifier = Modifier
                    .widthIn(max = 360.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )

            Text(noise.displayName, style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(4.dp))
            Text(
                "${Slopes.format(noise.slope)} dB per octave",
                style = MaterialTheme.typography.bodyMedium,
                color = color,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                Slopes.describe(noise.slope),
                style = MaterialTheme.typography.bodyMedium,
                color = XenoiseColors.Muted,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(28.dp))
            VolumeControl(
                volume = state.volume,
                accent = color,
                onChange = onVolumeChange,
                onChangeFinished = onVolumeChangeFinished,
            )

            Spacer(Modifier.height(28.dp))
            SectionLabel("Colors")
            Row(Modifier.fillMaxWidth()) {
                Presets.all.forEach { preset ->
                    PresetButton(
                        preset = preset,
                        selected = !state.isPreviewing && preset.id == noise.id,
                        onClick = { onPlayNoise(preset) },
                        modifier = Modifier.weight(1f),
                    )
                }
            }

            Spacer(Modifier.height(28.dp))
            SectionLabel("Your noises")
            if (customNoises.isEmpty()) {
                Text(
                    "Make your own: start from pink and tilt the line until it sounds right.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = XenoiseColors.Muted,
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                )
            }
            customNoises.forEach { custom ->
                CustomNoiseRow(
                    noise = custom,
                    selected = !state.isPreviewing && custom.id == noise.id,
                    onClick = { onPlayNoise(custom) },
                    onEdit = { onEditNoise(custom) },
                )
                Spacer(Modifier.height(6.dp))
            }
            OutlinedButton(
                onClick = onNewNoise,
                modifier = Modifier.fillMaxWidth().height(52.dp),
                shape = RoundedCornerShape(18.dp),
                border = BorderStroke(1.dp, XenoiseColors.Outline),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, tint = XenoiseColors.Text)
                Spacer(Modifier.width(8.dp))
                Text("New noise", color = XenoiseColors.Text)
            }
            Spacer(Modifier.height(32.dp))
        }
    }

    if (showTimer) {
        TimerSheet(
            accent = color,
            remainingText = remainingText,
            onPick = onStartTimer,
            onCancelTimer = onCancelTimer,
            onDismiss = { showTimer = false },
        )
    }
}

@Composable
private fun Header(accent: Color, remainingText: String?, onTimerClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(top = 12.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "xenoise",
            style = MaterialTheme.typography.titleLarge.copy(
                fontWeight = FontWeight.Light,
                letterSpacing = 6.sp,
            ),
            color = XenoiseColors.Text,
        )
        Surface(
            onClick = onTimerClick,
            shape = CircleShape,
            color = XenoiseColors.SurfaceHigh,
            border = if (remainingText != null) BorderStroke(1.dp, accent.copy(alpha = 0.6f)) else null,
        ) {
            Row(
                Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    XenoiseIcons.Moon,
                    contentDescription = null,
                    tint = if (remainingText != null) accent else XenoiseColors.Muted,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    remainingText ?: "Sleep timer",
                    style = MaterialTheme.typography.labelLarge,
                    color = XenoiseColors.Text,
                )
            }
        }
    }
}

@Composable
private fun VolumeControl(
    volume: Float,
    accent: Color,
    onChange: (Float) -> Unit,
    onChangeFinished: () -> Unit,
) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Icon(XenoiseIcons.VolumeLow, contentDescription = null, tint = XenoiseColors.Muted, modifier = Modifier.size(20.dp))
        Slider(
            value = volume,
            onValueChange = onChange,
            onValueChangeFinished = onChangeFinished,
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            colors = SliderDefaults.colors(
                thumbColor = accent,
                activeTrackColor = accent,
                inactiveTrackColor = XenoiseColors.Outline,
            ),
        )
        Icon(XenoiseIcons.VolumeHigh, contentDescription = null, tint = XenoiseColors.Muted, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = XenoiseColors.Faint,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 12.dp),
    )
}

@Composable
private fun PresetButton(
    preset: Noise,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClickLabel = "Play ${preset.displayName}", onClick = onClick)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        SlopeSwatch(slope = preset.slope, diameter = 54.dp, selected = selected)
        Spacer(Modifier.height(6.dp))
        Text(
            preset.name,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) XenoiseColors.Text else XenoiseColors.Muted,
        )
    }
}

@Composable
private fun CustomNoiseRow(
    noise: Noise,
    selected: Boolean,
    onClick: () -> Unit,
    onEdit: () -> Unit,
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(18.dp),
        color = if (selected) XenoiseColors.SurfaceHigh else XenoiseColors.Surface,
        border = if (selected) BorderStroke(1.dp, Color(NoiseColors.forSlope(noise.slope)).copy(alpha = 0.5f)) else null,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            Modifier.padding(start = 10.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SlopeSwatch(slope = noise.slope, diameter = 44.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(noise.name, style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "${Slopes.format(noise.slope)} dB per octave",
                    style = MaterialTheme.typography.bodySmall,
                    color = XenoiseColors.Muted,
                )
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Rounded.Edit, contentDescription = "Edit ${noise.name}", tint = XenoiseColors.Muted)
            }
        }
    }
}

/** Milliseconds left on the timer, ticking once a second, or null without a timer. */
@Composable
private fun rememberRemaining(timer: SleepTimer?): Long? {
    val remaining by produceState<Long?>(initialValue = null, timer) {
        if (timer == null) {
            value = null
            return@produceState
        }
        while (true) {
            val left = timer.endsAtElapsedMs - SystemClock.elapsedRealtime()
            value = left.coerceAtLeast(0)
            if (left <= 0) break
            // Wake right after the displayed second changes.
            delay((left - 1) % 1000 + 1)
        }
    }
    return remaining
}

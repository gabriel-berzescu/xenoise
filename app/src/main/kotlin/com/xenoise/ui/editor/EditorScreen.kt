package com.xenoise.ui.editor

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.xenoise.model.Noise
import com.xenoise.model.NoiseColors
import com.xenoise.model.Presets
import com.xenoise.model.Slopes
import com.xenoise.ui.components.SpectrumPlot
import com.xenoise.ui.components.XenoiseIcons
import com.xenoise.ui.theme.XenoiseColors
import java.util.UUID

/**
 * Shape a custom noise by tilting its line. The sound follows the line live: while this
 * screen is open, the player previews the draft instead of the selected noise.
 *
 * @param existing the saved noise being edited, or null to create a new one.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(
    existing: Noise?,
    defaultName: String,
    isPlaying: Boolean,
    onPreview: (Noise) -> Unit,
    onEndPreview: () -> Unit,
    onTogglePlay: () -> Unit,
    onSave: (Noise) -> Unit,
    onDelete: (Noise) -> Unit,
    onClose: () -> Unit,
) {
    // What the screen opened with, so a delete does not retitle it during the exit animation.
    val original = remember { existing }
    val id = rememberSaveable { existing?.id ?: UUID.randomUUID().toString() }
    var name by rememberSaveable { mutableStateOf(existing?.name ?: defaultName) }
    var slope by rememberSaveable { mutableFloatStateOf(existing?.slope ?: Presets.Pink.slope) }
    var confirmDelete by remember { mutableStateOf(false) }
    val draft = Noise(id = id, name = name.trim().ifEmpty { defaultName }, slope = slope)
    val color by animateColorAsState(Color(NoiseColors.forSlope(slope)), tween(250), label = "draftColor")
    val haptics = LocalHapticFeedback.current

    val endPreview by rememberUpdatedState(onEndPreview)
    DisposableEffect(Unit) {
        onDispose { endPreview() }
    }
    LaunchedEffect(draft) { onPreview(draft) }

    fun setSlope(value: Float) {
        val next = Slopes.snap(value)
        if (next == slope) return
        if (Presets.matching(next) != null) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        slope = next
    }

    Scaffold(
        containerColor = XenoiseColors.Ink,
        topBar = {
            TopAppBar(
                title = { Text(if (original == null) "New noise" else "Edit noise") },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (original != null) {
                        IconButton(onClick = { confirmDelete = true }) {
                            Icon(Icons.Rounded.Delete, contentDescription = "Delete", tint = XenoiseColors.Muted)
                        }
                    }
                    TextButton(onClick = { onSave(draft) }) {
                        Text("Save", color = color, style = MaterialTheme.typography.titleMedium)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = XenoiseColors.Ink,
                    scrolledContainerColor = XenoiseColors.Ink,
                ),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(padding)
                .padding(horizontal = 20.dp),
        ) {
            SpectrumPlot(
                slope = slope,
                color = color,
                onSlopeChange = ::setSlope,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(300.dp),
            )
            Text(
                "Drag anywhere on the chart to tilt the line.",
                style = MaterialTheme.typography.bodySmall,
                color = XenoiseColors.Faint,
                modifier = Modifier.padding(top = 8.dp, start = 4.dp),
            )

            Spacer(Modifier.height(20.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            Slopes.format(slope),
                            style = MaterialTheme.typography.displayMedium,
                            color = XenoiseColors.Text,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "dB per octave",
                            style = MaterialTheme.typography.bodyMedium,
                            color = XenoiseColors.Muted,
                            modifier = Modifier.padding(bottom = 10.dp),
                        )
                    }
                    Text(
                        Slopes.describe(slope),
                        style = MaterialTheme.typography.bodyMedium,
                        color = color,
                    )
                }
                Spacer(Modifier.width(12.dp))
                ListenButton(playing = isPlaying, color = color, onClick = onTogglePlay)
            }

            Spacer(Modifier.height(12.dp))
            Slider(
                value = slope,
                onValueChange = ::setSlope,
                valueRange = Slopes.MIN..Slopes.MAX,
                colors = SliderDefaults.colors(
                    thumbColor = color,
                    activeTrackColor = XenoiseColors.Outline,
                    inactiveTrackColor = XenoiseColors.Outline,
                ),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Deeper", style = MaterialTheme.typography.labelMedium, color = XenoiseColors.Faint)
                Text("Brighter", style = MaterialTheme.typography.labelMedium, color = XenoiseColors.Faint)
            }

            Spacer(Modifier.height(24.dp))
            Text(
                "START FROM",
                style = MaterialTheme.typography.labelSmall,
                color = XenoiseColors.Faint,
            )
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Presets.all.forEach { preset ->
                    PresetPill(
                        preset = preset,
                        active = Presets.matching(slope) == preset,
                        onClick = { slope = preset.slope },
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
            OutlinedTextField(
                value = name,
                onValueChange = { name = it.take(MAX_NAME_LENGTH) },
                label = { Text("Name") },
                singleLine = true,
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = color,
                    unfocusedBorderColor = XenoiseColors.Outline,
                    focusedLabelColor = color,
                    cursorColor = color,
                ),
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(Modifier.height(32.dp))
        }
    }

    if (confirmDelete && original != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete “${original.name}”?") },
            text = { Text("This noise will be gone for good.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    onDelete(original)
                }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Keep") }
            },
            containerColor = XenoiseColors.SurfaceHigh,
        )
    }
}

private const val MAX_NAME_LENGTH = 32

@Composable
private fun ListenButton(playing: Boolean, color: Color, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = color,
        modifier = Modifier.size(64.dp),
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = if (playing) XenoiseIcons.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (playing) "Pause" else "Listen",
                tint = XenoiseColors.Ink,
                modifier = Modifier.size(30.dp),
            )
        }
    }
}

@Composable
private fun PresetPill(preset: Noise, active: Boolean, onClick: () -> Unit) {
    val presetColor = Color(NoiseColors.forSlope(preset.slope))
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = if (active) presetColor.copy(alpha = 0.16f) else XenoiseColors.SurfaceHigh,
        border = BorderStroke(1.dp, if (active) presetColor else XenoiseColors.Outline),
    ) {
        Row(
            Modifier.padding(horizontal = 14.dp, vertical = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(10.dp)
                    .background(presetColor, CircleShape)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                preset.name,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                color = XenoiseColors.Text,
            )
        }
    }
}

package com.xenoise.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.xenoise.model.TimerOptions
import com.xenoise.ui.theme.XenoiseColors
import kotlinx.coroutines.launch

/** Bottom sheet for choosing how long the noise plays before fading out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TimerSheet(
    accent: Color,
    remainingText: String?,
    onPick: (Long) -> Unit,
    onCancelTimer: () -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun closeThen(action: () -> Unit) {
        scope.launch { sheetState.hide() }.invokeOnCompletion {
            action()
            onDismiss()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = XenoiseColors.Surface,
    ) {
        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 28.dp)) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            Text(
                text = if (remainingText != null) {
                    "Stops in $remainingText. Pick a new time or turn it off."
                } else {
                    "The noise fades out gently over the last minute."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = XenoiseColors.Muted,
            )
            Spacer(Modifier.height(20.dp))
            TimerOptions.all.chunked(3).forEach { row ->
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    row.forEach { option ->
                        Surface(
                            onClick = { closeThen { onPick(option.durationMs) } },
                            modifier = Modifier.weight(1f).height(52.dp),
                            shape = RoundedCornerShape(16.dp),
                            color = XenoiseColors.SurfaceHigh,
                            border = BorderStroke(1.dp, XenoiseColors.Outline),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(option.label, style = MaterialTheme.typography.titleMedium)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (remainingText != null) {
                TextButton(
                    onClick = { closeThen(onCancelTimer) },
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                ) {
                    Text("Turn off timer", color = accent)
                }
            }
        }
    }
}

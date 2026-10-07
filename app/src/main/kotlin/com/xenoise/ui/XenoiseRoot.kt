package com.xenoise.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.xenoise.data.NoiseLibrary
import com.xenoise.playback.NoisePlayer
import com.xenoise.ui.editor.EditorScreen
import com.xenoise.ui.home.HomeScreen

/** Route value for the editor when it creates a new noise rather than editing one. */
private const val NEW_NOISE = "new"

@Composable
fun XenoiseRoot(player: NoisePlayer, library: NoiseLibrary) {
    val state by player.state.collectAsStateWithLifecycle()
    val customNoises by library.customNoises.collectAsStateWithLifecycle()

    // null shows the home screen; otherwise the editor for NEW_NOISE or a saved noise id.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    val askForNotifications = rememberNotificationPermissionAsker()
    val play = {
        askForNotifications()
        player.play()
    }
    val togglePlay = {
        if (state.isPlaying) player.pause() else play()
    }

    BackHandler(enabled = editing != null) { editing = null }

    AnimatedContent(
        targetState = editing,
        transitionSpec = {
            if (targetState != null) {
                (slideInHorizontally { it / 5 } + fadeIn()) togetherWith fadeOut()
            } else {
                fadeIn() togetherWith (slideOutHorizontally { it / 5 } + fadeOut())
            }
        },
        label = "screen",
    ) { target ->
        if (target == null) {
            HomeScreen(
                state = state,
                customNoises = customNoises,
                onTogglePlay = togglePlay,
                onPlayNoise = { noise ->
                    player.select(noise)
                    if (!state.isPlaying) play()
                },
                onVolumeChange = { player.setVolume(it, persist = false) },
                onVolumeChangeFinished = { player.setVolume(state.volume, persist = true) },
                onEditNoise = { editing = it.id },
                onNewNoise = { editing = NEW_NOISE },
                onStartTimer = { durationMs ->
                    askForNotifications()
                    player.startTimer(durationMs)
                },
                onCancelTimer = player::cancelTimer,
            )
        } else {
            val existing = customNoises.firstOrNull { it.id == target }
            EditorScreen(
                existing = existing,
                defaultName = library.nextDefaultName(),
                isPlaying = state.isPlaying,
                onPreview = player::previewDraft,
                onEndPreview = player::endPreview,
                onTogglePlay = togglePlay,
                onSave = { draft ->
                    library.save(draft)
                    library.find(draft.id)?.let(player::select)
                    editing = null
                },
                onDelete = { noise ->
                    library.delete(noise.id)
                    editing = null
                },
                onClose = { editing = null },
            )
        }
    }
}

/**
 * Android 13+ needs permission to show the playback notification. Ask once per app run,
 * the first time the user starts the sound. Playback works either way.
 */
@Composable
private fun rememberNotificationPermissionAsker(): () -> Unit {
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    return {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !asked) {
            asked = true
            val granted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
            if (!granted) launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

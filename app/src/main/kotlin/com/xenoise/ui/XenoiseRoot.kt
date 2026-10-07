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
import java.util.UUID

/**
 * One opening of the editor, encoded as a string so it survives rotation. The key is fresh
 * every time, so an editor that is still animating out is never reused by the next opening.
 */
private data class EditorRoute(val noiseId: String, val isNew: Boolean) {
    companion object {
        fun forNew(): String = encode(UUID.randomUUID().toString(), isNew = true)
        fun forExisting(noiseId: String): String = encode(noiseId, isNew = false)

        private fun encode(noiseId: String, isNew: Boolean): String =
            listOf(if (isNew) "new" else "edit", noiseId, UUID.randomUUID().toString()).joinToString("|")

        fun decode(route: String): EditorRoute {
            val parts = route.split("|")
            return EditorRoute(noiseId = parts[1], isNew = parts[0] == "new")
        }
    }
}

@Composable
fun XenoiseRoot(player: NoisePlayer, library: NoiseLibrary) {
    val state by player.state.collectAsStateWithLifecycle()
    val customNoises by library.customNoises.collectAsStateWithLifecycle()

    // null shows the home screen; otherwise an encoded EditorRoute.
    var editing by rememberSaveable { mutableStateOf<String?>(null) }

    val askForNotifications = rememberNotificationPermissionAsker()
    val play = {
        askForNotifications()
        player.play()
    }
    // Read the player directly: Compose state can lag a frame behind a quick double tap.
    val togglePlay = {
        if (player.state.value.isPlaying) player.pause() else play()
    }
    val closeEditor = { keepPlaying: Boolean ->
        editing?.let { player.endPreview(it, keepPlaying) }
        editing = null
    }

    BackHandler(enabled = editing != null) { closeEditor(false) }

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
                    if (!player.state.value.isPlaying) play()
                },
                onVolumeChange = { player.setVolume(it, persist = false) },
                onVolumeChangeFinished = { player.setVolume(player.state.value.volume, persist = true) },
                onEditNoise = { editing = EditorRoute.forExisting(it.id) },
                onNewNoise = { editing = EditorRoute.forNew() },
                onStartTimer = { durationMs ->
                    askForNotifications()
                    player.startTimer(durationMs)
                },
                onCancelTimer = player::cancelTimer,
            )
        } else {
            val route = EditorRoute.decode(target)
            // Only the editor on screen may act; one that is animating out ignores taps.
            val isCurrent = { editing == target }
            EditorScreen(
                noiseId = route.noiseId,
                existing = if (route.isNew) null else customNoises.firstOrNull { it.id == route.noiseId },
                isNew = route.isNew,
                active = editing == target,
                defaultName = library.nextDefaultName(),
                isPlaying = state.isPlaying,
                onPreview = { draft -> if (isCurrent()) player.previewDraft(target, draft) },
                onEndPreview = { player.endPreview(target) },
                onTogglePlay = { if (isCurrent()) togglePlay() },
                onSave = { draft ->
                    if (isCurrent()) {
                        library.save(draft)
                        library.find(draft.id)?.let(player::select)
                        closeEditor(true)
                    }
                },
                onDelete = { noise ->
                    if (isCurrent()) {
                        closeEditor(false)
                        library.delete(noise.id)
                    }
                },
                onClose = { if (isCurrent()) closeEditor(false) },
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

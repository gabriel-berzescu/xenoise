package com.xenoise.playback

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.xenoise.audio.NoiseEngine
import com.xenoise.data.NoiseLibrary
import com.xenoise.model.Noise
import com.xenoise.model.Presets
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.min

data class SleepTimer(
    /** When playback stops, on the [SystemClock.elapsedRealtime] clock. */
    val endsAtElapsedMs: Long,
    val durationMs: Long,
)

data class PlayerState(
    /** What is sounding: the selected noise, or the editor's draft while previewing. */
    val noise: Noise,
    val isPlaying: Boolean = false,
    /** True from the first play until stop. The notification lives exactly this long. */
    val isSessionActive: Boolean = false,
    val volume: Float = NoiseLibrary.DEFAULT_VOLUME,
    val timer: SleepTimer? = null,
    val isPreviewing: Boolean = false,
)

/**
 * Single source of truth for playback, shared by the UI and [PlaybackService].
 * Call every method from the main thread.
 */
class NoisePlayer(
    private val context: Context,
    private val library: NoiseLibrary,
) {
    private val scope = MainScope()
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    private var engine: NoiseEngine? = null
    private var releaseJob: Job? = null

    private var selected: Noise = library.find(library.selectedId) ?: Presets.Pink
    private var preview: Noise? = null

    private var timerJob: Job? = null
    private var timerFading = false

    private var hasFocus = false
    private var resumeOnFocusGain = false

    private val _state = MutableStateFlow(PlayerState(noise = selected, volume = library.volume))
    val state: StateFlow<PlayerState> = _state.asStateFlow()

    private val focusRequest: AudioFocusRequest =
        AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setOnAudioFocusChangeListener({ change -> onFocusChange(change) }, Handler(Looper.getMainLooper()))
            .build()

    init {
        // Keep the selection in sync when a saved noise is edited or deleted.
        scope.launch {
            library.customNoises.collect { saved ->
                if (selected.isPreset) return@collect
                val fresh = saved.firstOrNull { it.id == selected.id }
                when {
                    fresh == null -> select(Presets.Pink)
                    fresh != selected -> select(fresh)
                }
            }
        }
    }

    fun play() {
        if (timerFading) cancelTimer()
        if (!hasFocus) {
            val result = audioManager.requestAudioFocus(focusRequest)
            if (result != AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
                Log.i(TAG, "Audio focus denied, not starting")
                return
            }
            hasFocus = true
        }
        releaseJob?.cancel()
        releaseJob = null
        val e = engine ?: NoiseEngine(context).also { created ->
            created.onError = { error -> scope.launch { onEngineError(error) } }
            engine = created
        }
        e.setSlope(current().slope)
        e.setVolume(_state.value.volume)
        e.play(FADE_IN_MS)
        resumeOnFocusGain = false
        _state.update { it.copy(isPlaying = true, isSessionActive = true) }
        PlaybackService.ensureRunning(context)
    }

    fun pause() {
        engine?.pause(FADE_OUT_MS)
        resumeOnFocusGain = false
        _state.update { it.copy(isPlaying = false) }
    }

    fun togglePlayPause() {
        if (_state.value.isPlaying) pause() else play()
    }

    /** Fades out, ends the session (removing the notification) and frees the audio track. */
    fun stop() {
        cancelTimer(resume = false)
        val e = engine
        e?.pause(FADE_OUT_MS)
        resumeOnFocusGain = false
        abandonFocus()
        _state.update { it.copy(isPlaying = false, isSessionActive = false) }
        releaseJob?.cancel()
        releaseJob = scope.launch {
            delay(FADE_OUT_MS + 250L)
            if (engine === e && !_state.value.isPlaying) {
                e?.release()
                engine = null
            }
        }
    }

    /** Makes [noise] the current noise and remembers it for next time. */
    fun select(noise: Noise) {
        selected = noise
        library.selectedId = noise.id
        if (preview == null) {
            engine?.setSlope(noise.slope)
            _state.update { it.copy(noise = noise) }
        }
    }

    /** Lets the editor take over the sound with its unsaved draft. */
    fun previewDraft(draft: Noise) {
        preview = draft
        engine?.setSlope(draft.slope)
        _state.update { it.copy(noise = draft, isPreviewing = true) }
    }

    /** Hands the sound back to the selected noise when the editor closes. */
    fun endPreview() {
        if (preview == null) return
        preview = null
        engine?.setSlope(selected.slope)
        _state.update { it.copy(noise = selected, isPreviewing = false) }
    }

    fun setVolume(volume: Float, persist: Boolean) {
        val v = volume.coerceIn(0f, 1f)
        engine?.setVolume(v)
        _state.update { it.copy(volume = v) }
        if (persist) library.volume = v
    }

    /** Plays for [durationMs], fading out gently over the last minute, then stops. */
    fun startTimer(durationMs: Long) {
        cancelTimer(resume = true)
        val endsAt = SystemClock.elapsedRealtime() + durationMs
        _state.update { it.copy(timer = SleepTimer(endsAt, durationMs)) }
        if (!_state.value.isPlaying) play()
        val fadeMs = min(TIMER_FADE_MS, durationMs / 4)
        timerJob = scope.launch {
            waitUntil(endsAt - fadeMs)
            timerFading = true
            engine?.pause(fadeMs.toInt())
            waitUntil(endsAt)
            timerFading = false
            timerJob = null
            stop()
        }
    }

    fun cancelTimer() = cancelTimer(resume = true)

    private fun cancelTimer(resume: Boolean) {
        timerJob?.cancel()
        timerJob = null
        if (timerFading) {
            timerFading = false
            // The timer had started its fade; bring the sound back up.
            if (resume && _state.value.isPlaying) engine?.play(FADE_IN_MS)
        }
        _state.update { it.copy(timer = null) }
    }

    private fun current(): Noise = preview ?: selected

    private fun onFocusChange(change: Int) {
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS -> {
                // Another app took over for good. Pause and let the user resume.
                pause()
                abandonFocus()
            }
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                // A call or a voice assistant. Pause and come back afterwards.
                if (_state.value.isPlaying) {
                    pause()
                    resumeOnFocusGain = true
                }
            }
            AudioManager.AUDIOFOCUS_GAIN -> {
                if (resumeOnFocusGain) {
                    resumeOnFocusGain = false
                    play()
                }
            }
            // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: the system lowers our volume by itself.
        }
    }

    private fun abandonFocus() {
        if (hasFocus) {
            audioManager.abandonAudioFocusRequest(focusRequest)
            hasFocus = false
        }
    }

    private fun onEngineError(error: Throwable) {
        Log.e(TAG, "Playback failed", error)
        engine?.release()
        engine = null
        _state.update { it.copy(isPlaying = false) }
    }

    private suspend fun waitUntil(elapsedRealtimeMs: Long) {
        while (true) {
            val left = elapsedRealtimeMs - SystemClock.elapsedRealtime()
            if (left <= 0) return
            // Re-check the wall clock regularly in case the device slept in between.
            delay(min(left, 5_000L))
        }
    }

    companion object {
        private const val TAG = "NoisePlayer"
        const val FADE_IN_MS = 1_200
        const val FADE_OUT_MS = 400
        const val TIMER_FADE_MS = 60_000L
    }
}

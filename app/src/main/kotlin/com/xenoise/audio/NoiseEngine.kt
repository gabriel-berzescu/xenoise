package com.xenoise.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.os.Process
import android.util.Log
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.max
import kotlin.math.min

/**
 * Streams [NoiseSynth] output to an [AudioTrack] from a dedicated audio thread.
 *
 * The thread starts on the first [play] and lives until [release]. When a pause has
 * faded all the way out, the track drains and the thread parks, so a paused engine uses
 * no CPU. Every level change (play, pause, volume, the sleep timer's long fade) is ramped
 * per sample, so nothing ever clicks.
 *
 * All public methods are safe to call from any thread.
 */
class NoiseEngine(context: Context) {

    private val sampleRate = outputSampleRate(context)

    private val lock = ReentrantLock()
    private val wake = lock.newCondition()

    // Guarded by lock.
    private var wantPlaying = false
    private var fadeMs = DEFAULT_FADE_MS
    private var released = false
    private var thread: Thread? = null

    @Volatile private var slope = 0f
    @Volatile private var volume = 0.7f

    /** Called on the audio thread if playback fails. The engine can be played again afterwards. */
    @Volatile var onError: ((Throwable) -> Unit)? = null

    fun setSlope(value: Float) {
        slope = value
    }

    fun setVolume(value: Float) {
        volume = value.coerceIn(0f, 1f)
    }

    /** Starts or resumes playback, fading in over [fadeInMs]. */
    fun play(fadeInMs: Int = DEFAULT_FADE_MS) = lock.withLock {
        check(!released) { "Engine was released" }
        wantPlaying = true
        fadeMs = fadeInMs
        if (thread == null) {
            thread = Thread(::runAudioLoop, "xenoise-audio").also { it.start() }
        }
        wake.signalAll()
    }

    /** Fades out over [fadeOutMs], then parks the audio thread. */
    fun pause(fadeOutMs: Int = DEFAULT_FADE_MS) = lock.withLock {
        wantPlaying = false
        fadeMs = fadeOutMs
        wake.signalAll()
    }

    /** Stops immediately and frees the audio track. The engine cannot be used afterwards. */
    fun release() = lock.withLock {
        released = true
        wantPlaying = false
        thread = null
        wake.signalAll()
    }

    private fun runAudioLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO)
        var track: AudioTrack? = null
        try {
            val synth = NoiseSynth(sampleRate, slope)
            val audio = buildTrack(synth.hopSize)
            track = audio
            val block = FloatArray(synth.hopSize * 2)
            var envelope = 0f
            var smoothedGain = Levels.volumeToGain(volume)

            while (true) {
                val playing: Boolean
                val rampMs: Int
                lock.withLock {
                    while (!released && !wantPlaying && envelope == 0f) {
                        // Fully faded out: let the track play what it has, then sleep.
                        if (audio.playState == AudioTrack.PLAYSTATE_PLAYING) audio.stop()
                        wake.await()
                    }
                    if (released) return
                    playing = wantPlaying
                    rampMs = fadeMs
                }
                if (audio.playState != AudioTrack.PLAYSTATE_PLAYING) audio.play()

                synth.targetSlope = slope
                synth.render(block)

                val target = if (playing) 1f else 0f
                val step = 1f / max(1f, rampMs * sampleRate / 1000f)
                val gainTarget = Levels.volumeToGain(volume)
                var j = 0
                for (i in 0 until synth.hopSize) {
                    envelope = if (envelope < target) min(target, envelope + step) else max(target, envelope - step)
                    smoothedGain += (gainTarget - smoothedGain) * GAIN_SMOOTHING
                    // Squaring the envelope makes long fades sound even to the ear.
                    val g = smoothedGain * envelope * envelope
                    block[j] = Levels.softClip(block[j] * g)
                    block[j + 1] = Levels.softClip(block[j + 1] * g)
                    j += 2
                }

                var written = 0
                while (written < block.size) {
                    val n = audio.write(block, written, block.size - written, AudioTrack.WRITE_BLOCKING)
                    if (n < 0) error("AudioTrack.write failed with code $n")
                    written += n
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "Audio thread failed", t)
            lock.withLock {
                wantPlaying = false
                if (thread === Thread.currentThread()) thread = null
            }
            onError?.invoke(t)
        } finally {
            track?.let {
                runCatching { it.pause(); it.flush() }
                it.release()
            }
        }
    }

    private fun buildTrack(hopSize: Int): AudioTrack {
        val channelMask = AudioFormat.CHANNEL_OUT_STEREO
        val encoding = AudioFormat.ENCODING_PCM_FLOAT
        val minBytes = AudioTrack.getMinBufferSize(sampleRate, channelMask, encoding)
        val blockBytes = hopSize * 2 * Float.SIZE_BYTES
        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build()
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setSampleRate(sampleRate)
                    .setEncoding(encoding)
                    .setChannelMask(channelMask)
                    .build()
            )
            .setTransferMode(AudioTrack.MODE_STREAM)
            .setBufferSizeInBytes(max(minBytes, blockBytes * 2))
            .build()
    }

    companion object {
        private const val TAG = "NoiseEngine"
        const val DEFAULT_FADE_MS = 400

        /** One-pole smoothing per sample, about a 40 ms time constant at 48 kHz. */
        private const val GAIN_SMOOTHING = 0.0005f

        private fun outputSampleRate(context: Context): Int {
            val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            return audioManager?.getProperty(AudioManager.PROPERTY_OUTPUT_SAMPLE_RATE)
                ?.toIntOrNull()
                ?.takeIf { it in 8_000..192_000 }
                ?: 48_000
        }
    }
}

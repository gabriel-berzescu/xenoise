package com.xenoise.audio

import kotlin.math.abs
import kotlin.math.tanh

/** Volume curve and the output limiter, kept free of Android types so they can be unit tested. */
object Levels {
    /** Above this level the limiter starts rounding peaks off. */
    const val KNEE = 0.85f

    /** Slider position to amplitude. Squaring gives a natural-feeling volume curve. */
    fun volumeToGain(volume: Float): Float {
        val v = volume.coerceIn(0f, 1f)
        return v * v
    }

    /**
     * Transparent below [KNEE], then a smooth tanh curve that never goes past full scale.
     * At full volume the bass-heavy slopes touch the knee a few times per second at most.
     */
    fun softClip(x: Float): Float {
        val a = abs(x)
        if (a <= KNEE) return x
        val y = KNEE + (1f - KNEE) * tanh((a - KNEE) / (1f - KNEE))
        return if (x < 0f) -y else y
    }
}

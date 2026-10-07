package com.xenoise.model

import kotlin.math.abs

/**
 * A noise is a straight line on the spectrum, so its slope in dB per octave is all
 * there is to it. Presets are the five classic colors; custom noises are saved by the user.
 */
data class Noise(
    val id: String,
    val name: String,
    val slope: Float,
    val isPreset: Boolean = false,
) {
    /** "Pink noise" for presets, the user's own name for custom noises. */
    val displayName: String get() = if (isPreset) "$name noise" else name
}

object Presets {
    val Brown = Noise("brown", "Brown", -6f, isPreset = true)
    val Pink = Noise("pink", "Pink", -3f, isPreset = true)
    val White = Noise("white", "White", 0f, isPreset = true)
    val Blue = Noise("blue", "Blue", 3f, isPreset = true)
    val Violet = Noise("violet", "Violet", 6f, isPreset = true)

    /** Ordered from the deepest to the brightest. */
    val all: List<Noise> = listOf(Brown, Pink, White, Blue, Violet)

    fun byId(id: String?): Noise? = all.firstOrNull { it.id == id }

    /** The preset that has exactly this slope, if there is one. */
    fun matching(slope: Float): Noise? = all.firstOrNull { abs(it.slope - slope) < 0.05f }
}

package com.xenoise.model

import java.text.DecimalFormatSymbols
import kotlin.math.abs
import kotlin.math.roundToInt

/** Range, rounding, snapping and wording for slopes in dB per octave. */
object Slopes {
    const val MIN = -9f
    const val MAX = 9f

    /** Within this distance of a preset, the editor snaps onto the preset slope. */
    const val SNAP_DISTANCE = 0.3f

    /** Clamps to the allowed range and rounds to one decimal. */
    fun normalize(slope: Float): Float =
        (slope.coerceIn(MIN, MAX) * 10f).roundToInt() / 10f

    /** Like [normalize], but pulls values close to a preset onto it. */
    fun snap(slope: Float): Float {
        val value = normalize(slope)
        val nearest = Presets.all.minBy { abs(it.slope - value) }
        return if (abs(nearest.slope - value) <= SNAP_DISTANCE) nearest.slope else value
    }

    /** "−4.5", "0", "+3", with a true minus sign and the locale's decimal separator. */
    fun format(slope: Float): String {
        val tenths = (slope * 10f).roundToInt()
        if (tenths == 0) return "0"
        val sign = if (tenths < 0) "−" else "+"
        val whole = abs(tenths) / 10
        val fraction = abs(tenths) % 10
        if (fraction == 0) return "$sign$whole"
        val separator = DecimalFormatSymbols.getInstance().decimalSeparator
        return "$sign$whole$separator$fraction"
    }

    /** A short phrase describing how a slope sounds. */
    fun describe(slope: Float): String {
        Presets.matching(slope)?.let { return PRESET_DESCRIPTIONS.getValue(it.id) }
        val presets = Presets.all
        if (slope < presets.first().slope) return "Deeper than brown, a low rumble"
        if (slope > presets.last().slope) return "Brighter than violet, a fine hiss"
        val lower = presets.last { it.slope < slope }
        val upper = presets.first { it.slope > slope }
        val low = lower.name.lowercase()
        val high = upper.name.lowercase()
        val middle = (lower.slope + upper.slope) / 2f
        return when {
            abs(slope - middle) < 0.05f -> "Halfway between $low and $high"
            slope < middle -> "Between $low and $high, leaning $low"
            else -> "Between $low and $high, leaning $high"
        }
    }

    private val PRESET_DESCRIPTIONS = mapOf(
        "brown" to "Deep and rumbling, like a distant waterfall",
        "pink" to "Soft and balanced, like steady rain",
        "white" to "Bright and even, like a fan on high",
        "blue" to "Airy and crisp, like a fine spray",
        "violet" to "Thin and fizzy, like hissing steam",
    )
}

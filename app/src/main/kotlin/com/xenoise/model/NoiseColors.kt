package com.xenoise.model

/**
 * Maps a slope to a color that matches its name: brown, pink, white, blue, violet,
 * blending smoothly in between and deepening past the brown and violet presets.
 * Colors are ARGB ints so both Compose and the notification artwork can use them.
 */
object NoiseColors {
    private val anchorSlopes = floatArrayOf(-9f, -6f, -3f, 0f, 3f, 6f, 9f)
    private val anchorColors = longArrayOf(
        0xFF8A5634, // deeper than brown
        0xFFC98B5E, // brown
        0xFFF29CC0, // pink
        0xFFF0F1F6, // white
        0xFF7FB2FF, // blue
        0xFFB18CFF, // violet
        0xFF8257FF, // brighter than violet
    )

    fun forSlope(slope: Float): Int {
        val s = slope.coerceIn(anchorSlopes.first(), anchorSlopes.last())
        var i = 0
        while (i < anchorSlopes.size - 2 && s > anchorSlopes[i + 1]) i++
        val t = (s - anchorSlopes[i]) / (anchorSlopes[i + 1] - anchorSlopes[i])
        return blend(anchorColors[i].toInt(), anchorColors[i + 1].toInt(), t)
    }

    private fun blend(a: Int, b: Int, t: Float): Int {
        fun channel(shift: Int): Int {
            val ca = (a ushr shift) and 0xFF
            val cb = (b ushr shift) and 0xFF
            return (ca + (cb - ca) * t + 0.5f).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
    }
}

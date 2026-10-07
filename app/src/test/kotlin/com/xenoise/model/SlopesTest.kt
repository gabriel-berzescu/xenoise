package com.xenoise.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SlopesTest {

    @Test
    fun snapsOntoPresetsOnlyWhenClose() {
        assertEquals(-3f, Slopes.snap(-3.2f), 0f)
        assertEquals(-3f, Slopes.snap(-2.8f), 0f)
        assertEquals(-3.5f, Slopes.snap(-3.5f), 0f)
        assertEquals(Slopes.MAX, Slopes.snap(12f), 0f)
        assertEquals(1.3f, Slopes.snap(1.27f), 1e-6f)
    }

    @Test
    fun formatsWithSignAndOneDecimal() {
        val previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
        try {
            assertEquals("0", Slopes.format(0f))
            assertEquals("+3", Slopes.format(3f))
            assertEquals("−4.5", Slopes.format(-4.5f))
            assertEquals("+0.1", Slopes.format(0.1f))
        } finally {
            Locale.setDefault(previous)
        }
    }

    @Test
    fun describesPresetsAndInBetweens() {
        assertEquals("Soft and balanced, like steady rain", Slopes.describe(-3f))
        assertEquals("Halfway between brown and pink", Slopes.describe(-4.5f))
        assertEquals("Between pink and white, leaning pink", Slopes.describe(-2f))
        assertEquals("Deeper than brown, a low rumble", Slopes.describe(-7f))
        assertEquals("Brighter than violet, a fine hiss", Slopes.describe(8f))
    }

    @Test
    fun colorsBlendBetweenPresets() {
        val pink = NoiseColors.forSlope(-3f)
        val white = NoiseColors.forSlope(0f)
        val between = NoiseColors.forSlope(-1.5f)
        assertEquals(0xFFF29CC0.toInt(), pink)
        assertEquals(0xFFF0F1F6.toInt(), white)
        assertNotEquals(pink, between)
        assertNotEquals(white, between)
        assertEquals(NoiseColors.forSlope(-9f), NoiseColors.forSlope(-20f))
        assertTrue(between ushr 24 == 0xFF)
    }

    @Test
    fun countdownRoundsUp() {
        assertEquals("0:00", TimerOptions.formatRemaining(0))
        assertEquals("0:01", TimerOptions.formatRemaining(1))
        assertEquals("15:00", TimerOptions.formatRemaining(15 * 60_000L))
        assertEquals("1:05:09", TimerOptions.formatRemaining((3_600 + 309) * 1000L))
    }
}

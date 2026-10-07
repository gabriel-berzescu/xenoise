package com.xenoise.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelsTest {

    @Test
    fun limiterIsTransparentBelowTheKnee() {
        for (x in listOf(-0.85f, -0.5f, 0f, 0.3f, 0.85f)) {
            assertEquals(x, Levels.softClip(x), 0f)
        }
    }

    @Test
    fun limiterIsSmoothSymmetricAndBounded() {
        var previous = Levels.softClip(Levels.KNEE)
        var x = Levels.KNEE
        while (x < 4f) {
            x += 0.01f
            val y = Levels.softClip(x)
            assertTrue("monotonic at $x", y >= previous)
            assertTrue("bounded at $x", y <= 1f)
            assertTrue("no jumps at $x", y - previous < 0.011f)
            assertEquals(-y, Levels.softClip(-x), 0f)
            previous = y
        }
    }

    @Test
    fun volumeCurve() {
        assertEquals(0f, Levels.volumeToGain(0f), 0f)
        assertEquals(0.25f, Levels.volumeToGain(0.5f), 1e-6f)
        assertEquals(1f, Levels.volumeToGain(1f), 0f)
        assertEquals(1f, Levels.volumeToGain(3f), 0f)
    }
}

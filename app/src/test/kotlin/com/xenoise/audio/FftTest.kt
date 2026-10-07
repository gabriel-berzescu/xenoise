package com.xenoise.audio

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

class FftTest {

    @Test
    fun matchesNaiveDft() {
        val n = 64
        val random = Random(7)
        val re = FloatArray(n) { random.nextFloat() * 2f - 1f }
        val im = FloatArray(n) { random.nextFloat() * 2f - 1f }
        val expectedRe = DoubleArray(n)
        val expectedIm = DoubleArray(n)
        for (k in 0 until n) {
            for (t in 0 until n) {
                val a = -2.0 * PI * k * t / n
                expectedRe[k] += re[t] * cos(a) - im[t] * sin(a)
                expectedIm[k] += re[t] * sin(a) + im[t] * cos(a)
            }
        }
        Fft(n).transform(re, im)
        for (k in 0 until n) {
            assertEquals("re[$k]", expectedRe[k], re[k].toDouble(), 1e-3)
            assertEquals("im[$k]", expectedIm[k], im[k].toDouble(), 1e-3)
        }
    }

    @Test
    fun singleToneLandsInOneBin() {
        val n = 1024
        val bin = 37
        val re = FloatArray(n) { cos(2.0 * PI * bin * it / n).toFloat() }
        val im = FloatArray(n)
        Fft(n).transform(re, im)
        assertEquals(n / 2.0, re[bin].toDouble(), 1e-2)
        assertEquals(n / 2.0, re[n - bin].toDouble(), 1e-2)
        assertEquals(0.0, re[bin + 1].toDouble(), 1e-2)
    }
}

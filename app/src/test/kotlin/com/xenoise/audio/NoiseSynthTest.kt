package com.xenoise.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.ln
import kotlin.math.sqrt

class NoiseSynthTest {

    private val sampleRate = 48_000

    /** Renders [seconds] of audio and returns the left and right channels. */
    private fun render(slope: Float, seconds: Int, seed: Long = 1L): Pair<FloatArray, FloatArray> {
        val synth = NoiseSynth(sampleRate, slope, seed)
        val block = FloatArray(synth.hopSize * 2)
        val blocks = sampleRate * seconds / synth.hopSize
        val left = FloatArray(blocks * synth.hopSize)
        val right = FloatArray(blocks * synth.hopSize)
        for (b in 0 until blocks) {
            synth.render(block)
            for (i in 0 until synth.hopSize) {
                left[b * synth.hopSize + i] = block[2 * i]
                right[b * synth.hopSize + i] = block[2 * i + 1]
            }
        }
        return left to right
    }

    /** Averaged power spectrum (Hann-windowed periodograms), one value per bin. */
    private fun powerSpectrum(x: FloatArray, n: Int): DoubleArray {
        val fft = Fft(n)
        val re = FloatArray(n)
        val im = FloatArray(n)
        val sum = DoubleArray(n / 2)
        var segments = 0
        var start = 0
        while (start + n <= x.size) {
            for (i in 0 until n) {
                val w = 0.5 - 0.5 * kotlin.math.cos(2.0 * Math.PI * i / n)
                re[i] = (x[start + i] * w).toFloat()
                im[i] = 0f
            }
            fft.transform(re, im)
            for (k in 0 until n / 2) sum[k] += (re[k] * re[k] + im[k] * im[k]).toDouble()
            segments++
            start += n / 2
        }
        return DoubleArray(n / 2) { sum[it] / segments }
    }

    /** Least-squares slope of the spectrum in dB per octave, over 1/3-octave bands. */
    private fun measuredSlope(x: FloatArray): Double {
        val n = 8192
        val power = powerSpectrum(x, n)
        val octaves = ArrayList<Double>()
        val levels = ArrayList<Double>()
        var lo = 50.0
        while (lo * 1.26 <= 16_000.0) {
            val hi = lo * 1.26
            val kLo = (lo * n / sampleRate).toInt().coerceAtLeast(1)
            val kHi = (hi * n / sampleRate).toInt()
            if (kHi > kLo) {
                var p = 0.0
                for (k in kLo until kHi) p += power[k] / (kHi - kLo)
                octaves += ln(sqrt(lo * hi) / 1000.0) / ln(2.0)
                levels += 10 * log10(p)
            }
            lo = hi
        }
        val mx = octaves.average()
        val my = levels.average()
        var num = 0.0
        var den = 0.0
        for (i in octaves.indices) {
            num += (octaves[i] - mx) * (levels[i] - my)
            den += (octaves[i] - mx) * (octaves[i] - mx)
        }
        return num / den
    }

    private fun rms(x: FloatArray): Double = sqrt(x.sumOf { it.toDouble() * it } / x.size)

    @Test
    fun spectrumFollowsTheRequestedSlope() {
        for (slope in listOf(-9f, -6f, -3f, 0f, 3f, 6f, 9f)) {
            val (left, _) = render(slope, seconds = 20)
            val measured = measuredSlope(left)
            assertEquals("slope $slope", slope.toDouble(), measured, 0.25)
        }
    }

    @Test
    fun presetsAreLoudnessMatched() {
        // Measure K-weighted loudness from the spectrum, the same weighting the synth targets.
        val n = 8192
        val levels = listOf(-6f, -3f, 0f, 3f, 6f).map { slope ->
            val (left, _) = render(slope, seconds = 10)
            val power = powerSpectrum(left, n)
            var weighted = 0.0
            for (k in 1 until n / 2) {
                weighted += power[k] * NoiseSynth.kWeightingPower(k.toDouble() * sampleRate / n)
            }
            10 * log10(weighted)
        }
        val spread = levels.max() - levels.min()
        assertTrue("loudness spread $spread dB", spread < 0.5)
    }

    @Test
    fun channelsAreIndependent() {
        val (left, right) = render(-3f, seconds = 10)
        var cross = 0.0
        for (i in left.indices) cross += left[i].toDouble() * right[i]
        val correlation = cross / left.size / (rms(left) * rms(right))
        assertTrue("correlation $correlation", abs(correlation) < 0.05)
    }

    @Test
    fun peaksRarelyReachTheLimiterAndNeverPassFullScale() {
        for (slope in listOf(-9f, -6f, -3f, 0f, 6f)) {
            val (left, right) = render(slope, seconds = 20)
            val samples = left + right
            val overKnee = samples.count { abs(it) > Levels.KNEE }.toDouble() / samples.size
            assertTrue("slope $slope: ${overKnee * 100}% of samples over the knee", overKnee < 2e-4)
            val limitedPeak = samples.maxOf { abs(Levels.softClip(it)) }
            assertTrue("slope $slope limited peak $limitedPeak", limitedPeak < 1f)
        }
    }

    @Test
    fun levelIsSteady() {
        for (slope in listOf(-6f, 0f, 6f)) {
            val (left, _) = render(slope, seconds = 20)
            // The first and second halves should have the same level.
            val a = rms(left.copyOfRange(0, left.size / 2))
            val b = rms(left.copyOfRange(left.size / 2, left.size))
            assertEquals("slope $slope halves", 0.0, 20 * log10(a / b), 0.5)
        }
    }

    @Test
    fun glidesToANewSlope() {
        val synth = NoiseSynth(sampleRate, initialSlope = -6f, seed = 3L)
        val block = FloatArray(synth.hopSize * 2)
        synth.targetSlope = 6f
        synth.render(block)
        assertTrue("first step moves part way", synth.currentSlope > -6f && synth.currentSlope < 6f)
        repeat(20) { synth.render(block) }
        assertEquals(6f, synth.currentSlope, 0f)
    }

    @Test
    fun targetIsClampedToTheRange() {
        val synth = NoiseSynth(sampleRate, initialSlope = 30f)
        assertEquals(NoiseSynth.MAX_SLOPE, synth.targetSlope, 0f)
        synth.targetSlope = -30f
        assertEquals(NoiseSynth.MIN_SLOPE, synth.targetSlope, 0f)
    }
}

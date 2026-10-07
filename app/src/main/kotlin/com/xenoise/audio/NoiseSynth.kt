package com.xenoise.audio

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Endless stereo noise whose spectrum is a straight line on a chart with frequency on a
 * log scale and level in decibels. The line is described by its slope in dB per octave:
 * -6 brown, -3 pink, 0 white, +3 blue, +6 violet.
 *
 * How it works: overlap-add of random-phase frames. Each frame gets a spectrum with the
 * target magnitude and uniformly random phases, goes through one complex FFT, is windowed,
 * and is added into the output with 75% overlap. The real part of the FFT output feeds the
 * left channel and the imaginary part the right one. Because the magnitude is the same at
 * +f and -f, the two channels are uncorrelated noises with the same spectrum, which sounds
 * wide and natural on headphones.
 *
 * The window is a sine window scaled so its squares add up to exactly 1 across the
 * overlapping frames. Frames are independent, so their powers add, and the output level is
 * perfectly steady over time.
 *
 * Every slope is level matched with a K-weighted loudness estimate, the same weighting
 * that LUFS loudness meters use, so switching colors does not jump in perceived volume.
 * A cap on the raw level keeps very bass-heavy slopes from clipping.
 *
 * Threading: call [render] from a single thread. [targetSlope] can be written from any thread.
 */
class NoiseSynth(
    val sampleRate: Int,
    initialSlope: Float = 0f,
    seed: Long = System.nanoTime(),
) {
    /** FFT frame length: about 170 ms, long enough for clean bass down to 20 Hz. */
    val frameSize: Int = if (sampleRate > 50_000) 16_384 else 8_192

    /** Samples per channel produced by each [render] call. */
    val hopSize: Int = frameSize / 4

    /** Slope the generator glides toward, in dB per octave. Safe to set from any thread. */
    @Volatile
    var targetSlope: Float = initialSlope.coerceIn(MIN_SLOPE, MAX_SLOPE)
        set(value) {
            field = value.coerceIn(MIN_SLOPE, MAX_SLOPE)
        }

    /** Slope used for the most recent frame. Read it from the rendering thread only. */
    var currentSlope: Float = targetSlope
        private set

    private val half = frameSize / 2
    private val fft = Fft(frameSize)
    private val re = FloatArray(frameSize)
    private val im = FloatArray(frameSize)
    private val accLeft = FloatArray(frameSize)
    private val accRight = FloatArray(frameSize)

    private val window = FloatArray(frameSize) {
        (sin(PI * (it + 0.5) / frameSize) / sqrt(2.0)).toFloat()
    }

    private val phaseCos = FloatArray(PHASES) { cos(2.0 * PI * it / PHASES).toFloat() }
    private val phaseSin = FloatArray(PHASES) { sin(2.0 * PI * it / PHASES).toFloat() }

    /** ln(f / 1 kHz) for each bin inside the audible band. */
    private val logRatio = DoubleArray(half)
    private val inBand = BooleanArray(half)

    /** Power of the K-weighting filter at each bin. */
    private val loudnessWeight = DoubleArray(half)

    private val shape = DoubleArray(half)
    private val binGain = FloatArray(half)

    private var rngState = seed

    init {
        val topFrequency = min(MAX_FREQUENCY, sampleRate * 0.45)
        for (k in 1 until half) {
            val f = k.toDouble() * sampleRate / frameSize
            if (f >= MIN_FREQUENCY && f <= topFrequency) {
                inBand[k] = true
                logRatio[k] = ln(f / REFERENCE_FREQUENCY)
                loudnessWeight[k] = kWeightingPower(f)
            }
        }
        computeGains(currentSlope)
        // Fill the overlap buffers so the very first block already has a steady level.
        repeat(OVERLAP - 1) {
            synthesizeFrame()
            shiftAccumulators()
        }
    }

    /**
     * Writes [hopSize] stereo frames, interleaved left and right, into [out] starting at index 0.
     * The level is about [TARGET_LOUDNESS] K-weighted RMS, before any volume is applied.
     */
    fun render(out: FloatArray) {
        require(out.size >= hopSize * 2) { "Output needs room for ${hopSize * 2} samples" }
        glideTowardTarget()
        synthesizeFrame()
        var j = 0
        for (i in 0 until hopSize) {
            out[j] = accLeft[i]
            out[j + 1] = accRight[i]
            j += 2
        }
        shiftAccumulators()
    }

    private fun glideTowardTarget() {
        val target = targetSlope
        val diff = target - currentSlope
        if (diff == 0f) return
        val next = if (abs(diff) < SLOPE_SNAP) target else currentSlope + diff * SLOPE_GLIDE
        currentSlope = next
        computeGains(next)
    }

    private fun computeGains(slope: Float) {
        val exponent = slope / DB_PER_OCTAVE_PER_UNIT
        var raw = 0.0
        var weighted = 0.0
        for (k in 1 until half) {
            if (!inBand[k]) {
                shape[k] = 0.0
                continue
            }
            val g = exp(exponent * logRatio[k])
            shape[k] = g
            val power = g * g
            raw += power
            weighted += power * loudnessWeight[k]
        }
        // Output variance per channel is the sum of squared bin gains over positive bins.
        val byLoudness = TARGET_LOUDNESS / sqrt(weighted)
        val byHeadroom = MAX_RMS / sqrt(raw)
        val scale = min(byLoudness, byHeadroom)
        for (k in 1 until half) binGain[k] = (shape[k] * scale).toFloat()
    }

    private fun synthesizeFrame() {
        val n = frameSize
        re[0] = 0f; im[0] = 0f
        re[half] = 0f; im[half] = 0f
        for (k in 1 until half) {
            val g = binGain[k]
            val mirror = n - k
            if (g == 0f) {
                re[k] = 0f; im[k] = 0f
                re[mirror] = 0f; im[mirror] = 0f
                continue
            }
            val r = nextRandom()
            val p = (r ushr (64 - PHASE_BITS)).toInt()
            val q = (r ushr (32 - PHASE_BITS)).toInt() and (PHASES - 1)
            re[k] = g * phaseCos[p]; im[k] = g * phaseSin[p]
            re[mirror] = g * phaseCos[q]; im[mirror] = g * phaseSin[q]
        }
        // A forward FFT is used as the inverse: with random phases the result is
        // statistically identical, and it saves a second transform implementation.
        fft.transform(re, im)
        for (i in 0 until n) {
            val w = window[i]
            accLeft[i] += re[i] * w
            accRight[i] += im[i] * w
        }
    }

    private fun shiftAccumulators() {
        val keep = frameSize - hopSize
        System.arraycopy(accLeft, hopSize, accLeft, 0, keep)
        System.arraycopy(accRight, hopSize, accRight, 0, keep)
        accLeft.fill(0f, keep, frameSize)
        accRight.fill(0f, keep, frameSize)
    }

    /** SplitMix64: tiny, fast, and statistically solid for audio noise. */
    private fun nextRandom(): Long {
        rngState += -0x61c8864680b583ebL
        var z = rngState
        z = (z xor (z ushr 30)) * -0x40a7b892e31b1a47L
        z = (z xor (z ushr 27)) * -0x6b2fb644ecceee15L
        return z xor (z ushr 31)
    }

    companion object {
        const val MIN_SLOPE = -9f
        const val MAX_SLOPE = 9f

        const val MIN_FREQUENCY = 20.0
        const val MAX_FREQUENCY = 20_000.0
        const val REFERENCE_FREQUENCY = 1_000.0

        /** K-weighted RMS of the output before volume, about -17 dBFS. */
        const val TARGET_LOUDNESS = 0.14

        /**
         * Ceiling on raw RMS. Only the steepest bass slopes reach it; their rare peaks above
         * full scale are rounded off by the engine's soft limiter.
         */
        const val MAX_RMS = 0.22

        /** 20 * log10(2): dB per octave for an amplitude that scales as frequency^1. */
        private const val DB_PER_OCTAVE_PER_UNIT = 6.0206f

        private const val OVERLAP = 4
        private const val PHASE_BITS = 12
        private const val PHASES = 1 shl PHASE_BITS
        private const val SLOPE_GLIDE = 0.35f
        private const val SLOPE_SNAP = 0.02f

        /**
         * Power response of the ITU-R BS.1770 K-weighting filter (high shelf plus high-pass),
         * using the reference 48 kHz coefficients evaluated at [frequency].
         */
        fun kWeightingPower(frequency: Double): Double {
            val w = 2.0 * PI * frequency / 48_000.0
            val shelf = biquadPower(
                w,
                1.53512485958697, -2.69169618940638, 1.19839281085285,
                -1.69065929318241, 0.73248077421585,
            )
            val highPass = biquadPower(
                w,
                1.0, -2.0, 1.0,
                -1.99004745483398, 0.99007225036621,
            )
            return shelf * highPass
        }

        private fun biquadPower(
            w: Double,
            b0: Double, b1: Double, b2: Double,
            a1: Double, a2: Double,
        ): Double {
            val c1 = cos(w); val s1 = sin(w)
            val c2 = cos(2 * w); val s2 = sin(2 * w)
            val nr = b0 + b1 * c1 + b2 * c2
            val ni = -(b1 * s1 + b2 * s2)
            val dr = 1.0 + a1 * c1 + a2 * c2
            val di = -(a1 * s1 + a2 * s2)
            return (nr * nr + ni * ni) / (dr * dr + di * di)
        }
    }
}

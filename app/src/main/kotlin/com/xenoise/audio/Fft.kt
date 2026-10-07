package com.xenoise.audio

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * In-place iterative radix-2 complex FFT of a fixed power-of-two size.
 *
 * Computes X[k] = sum over n of x[n] * e^(-2 pi i k n / size). Tables are built once,
 * so [transform] allocates nothing and is safe to call from the audio thread.
 */
class Fft(val size: Int) {

    init {
        require(size >= 2 && size and (size - 1) == 0) { "FFT size must be a power of two, was $size" }
    }

    private val bits = Integer.numberOfTrailingZeros(size)
    private val cosTable = FloatArray(size / 2) { cos(2.0 * PI * it / size).toFloat() }
    private val sinTable = FloatArray(size / 2) { sin(2.0 * PI * it / size).toFloat() }
    private val bitReversed = IntArray(size) { Integer.reverse(it) ushr (32 - bits) }

    fun transform(re: FloatArray, im: FloatArray) {
        require(re.size == size && im.size == size) { "Arrays must have length $size" }

        for (i in 0 until size) {
            val j = bitReversed[i]
            if (j > i) {
                val tr = re[i]; re[i] = re[j]; re[j] = tr
                val ti = im[i]; im[i] = im[j]; im[j] = ti
            }
        }

        var span = 2
        while (span <= size) {
            val half = span / 2
            val tableStep = size / span
            var start = 0
            while (start < size) {
                var t = 0
                for (a in start until start + half) {
                    val b = a + half
                    val c = cosTable[t]
                    val s = sinTable[t]
                    // (re[b] + i im[b]) * (c - i s)
                    val xr = re[b] * c + im[b] * s
                    val xi = im[b] * c - re[b] * s
                    re[b] = re[a] - xr
                    im[b] = im[a] - xi
                    re[a] += xr
                    im[a] += xi
                    t += tableStep
                }
                start += span
            }
            span *= 2
        }
    }
}

package com.xenoise.playback

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import com.xenoise.model.NoiseColors
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

/**
 * Square artwork for the media notification and lock screen: a glowing orb in the
 * noise's color, dusted with grain that is coarse for deep noises and fine for bright ones.
 */
object Artwork {
    private const val SIZE = 384
    private const val BACKGROUND = 0xFF09090E.toInt()

    private var cachedSlope = Float.NaN
    private var cached: Bitmap? = null

    fun forSlope(slope: Float): Bitmap {
        cached?.let { if (slope == cachedSlope) return it }
        val bitmap = render(slope)
        cached = bitmap
        cachedSlope = slope
        return bitmap
    }

    private fun render(slope: Float): Bitmap {
        val color = NoiseColors.forSlope(slope)
        val bitmap = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(BACKGROUND)

        val c = SIZE / 2f
        val radius = SIZE * 0.34f
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        paint.shader = RadialGradient(
            c, c, radius * 1.6f,
            intArrayOf(withAlpha(color, 0x66), withAlpha(color, 0x1A), 0),
            floatArrayOf(0f, 0.6f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(c, c, radius * 1.6f, paint)

        paint.shader = RadialGradient(
            c - radius * 0.3f, c - radius * 0.35f, radius * 1.6f,
            intArrayOf(mix(color, 0xFFFFFFFF.toInt(), 0.25f), color, mix(color, BACKGROUND, 0.55f)),
            floatArrayOf(0f, 0.45f, 1f),
            Shader.TileMode.CLAMP,
        )
        canvas.drawCircle(c, c, radius, paint)
        paint.shader = null

        val t = ((slope + 9f) / 18f).coerceIn(0f, 1f)
        val count = (160 + 640 * t).toInt()
        val dot = 5f - 3.2f * t
        val random = Random(42)
        repeat(count) { i ->
            val r = radius * 0.97f * sqrt(random.nextFloat())
            val a = random.nextFloat() * 2f * Math.PI.toFloat()
            paint.color = if (i % 2 == 0) withAlpha(BACKGROUND, 0x40) else withAlpha(0xFFFFFFFF.toInt(), 0x40)
            canvas.drawCircle(c + r * cos(a), c + r * sin(a), dot / 2f, paint)
        }
        return bitmap
    }

    private fun withAlpha(color: Int, alpha: Int): Int = (color and 0x00FFFFFF) or (alpha shl 24)

    private fun mix(a: Int, b: Int, t: Float): Int {
        fun ch(shift: Int): Int {
            val x = (a ushr shift) and 0xFF
            val y = (b ushr shift) and 0xFF
            return (x + (y - x) * t).toInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }
}

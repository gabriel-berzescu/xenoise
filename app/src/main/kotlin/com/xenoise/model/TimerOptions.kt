package com.xenoise.model

import java.util.Locale

/** Sleep timer choices and countdown formatting. */
object TimerOptions {
    data class Option(val label: String, val minutes: Int) {
        val durationMs: Long get() = minutes * 60_000L
    }

    val all: List<Option> = listOf(
        Option("15 min", 15),
        Option("30 min", 30),
        Option("45 min", 45),
        Option("1 h", 60),
        Option("1 h 30", 90),
        Option("2 h", 120),
        Option("3 h", 180),
        Option("6 h", 360),
        Option("8 h", 480),
    )

    /** "1:05:09" or "23:41". Rounds up, so the display reads 0:00 only at the very end. */
    fun formatRemaining(ms: Long): String {
        val totalSeconds = ((ms.coerceAtLeast(0) + 999) / 1000)
        val h = totalSeconds / 3600
        val m = (totalSeconds % 3600) / 60
        val s = totalSeconds % 60
        return if (h > 0) {
            String.format(Locale.ROOT, "%d:%02d:%02d", h, m, s)
        } else {
            String.format(Locale.ROOT, "%d:%02d", m, s)
        }
    }
}

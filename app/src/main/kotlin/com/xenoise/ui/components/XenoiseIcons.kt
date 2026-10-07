package com.xenoise.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * The few icons the core Material set lacks, drawn from the Material Design path data.
 * Like any icon they are tinted by [androidx.compose.material3.Icon].
 */
object XenoiseIcons {
    val Pause: ImageVector = icon("Pause", "M6 19h4V5H6v14zm8-14v14h4V5h-4z")

    val Moon: ImageVector = icon(
        "Moon",
        "M12.34 2.02C6.59 1.82 2 6.42 2 12c0 5.52 4.48 10 10 10 3.71 0 6.93-2.02 8.66-5.02" +
            "-7.51-0.25-12.09-8.43-8.32-14.96z",
    )

    val VolumeLow: ImageVector = icon(
        "VolumeLow",
        "M18.5 12c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-0.73 2.5-2.25 2.5-4.02zM5 9v6h4l5 5V4L9 9H5z",
    )

    val VolumeHigh: ImageVector = icon(
        "VolumeHigh",
        "M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-0.73 2.5-2.25 2.5-4.02z" +
            "M14 3.23v2.06c2.89 0.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-0.91 7-4.49 7-8.77" +
            "s-2.99-7.86-7-8.77z",
    )

    private fun icon(name: String, pathData: String): ImageVector =
        ImageVector.Builder(
            name = name,
            defaultWidth = 24.dp,
            defaultHeight = 24.dp,
            viewportWidth = 24f,
            viewportHeight = 24f,
        ).addPath(
            pathData = addPathNodes(pathData),
            fill = SolidColor(Color.Black),
        ).build()
}

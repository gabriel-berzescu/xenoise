package com.xenoise.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** A deep night palette. The color of the current noise provides every accent. */
object XenoiseColors {
    val Ink = Color(0xFF09090E)
    val Surface = Color(0xFF111118)
    val SurfaceHigh = Color(0xFF191923)
    val Outline = Color(0xFF292936)
    val Text = Color(0xFFECECF4)
    val Muted = Color(0xFF8E8EA4)
    val Faint = Color(0xFF55556B)
}

private val ColorScheme = darkColorScheme(
    primary = Color(0xFFE7E5F7),
    onPrimary = XenoiseColors.Ink,
    primaryContainer = XenoiseColors.SurfaceHigh,
    onPrimaryContainer = XenoiseColors.Text,
    secondary = XenoiseColors.Muted,
    onSecondary = XenoiseColors.Ink,
    background = XenoiseColors.Ink,
    onBackground = XenoiseColors.Text,
    surface = XenoiseColors.Surface,
    onSurface = XenoiseColors.Text,
    surfaceVariant = XenoiseColors.SurfaceHigh,
    onSurfaceVariant = XenoiseColors.Muted,
    surfaceContainerLowest = XenoiseColors.Ink,
    surfaceContainerLow = XenoiseColors.Surface,
    surfaceContainer = XenoiseColors.Surface,
    surfaceContainerHigh = XenoiseColors.SurfaceHigh,
    surfaceContainerHighest = XenoiseColors.SurfaceHigh,
    outline = XenoiseColors.Outline,
    outlineVariant = XenoiseColors.Outline,
    error = Color(0xFFFF8A80),
)

private val BaseTypography = Typography()

private val XenoiseTypography = BaseTypography.copy(
    displayMedium = BaseTypography.displayMedium.copy(
        fontWeight = FontWeight.Light,
        letterSpacing = (-1).sp,
    ),
    headlineMedium = BaseTypography.headlineMedium.copy(fontWeight = FontWeight.Normal),
    titleLarge = BaseTypography.titleLarge.copy(fontWeight = FontWeight.Normal),
    labelSmall = BaseTypography.labelSmall.copy(
        fontWeight = FontWeight.Medium,
        letterSpacing = 1.6.sp,
    ),
)

@Composable
fun XenoiseTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ColorScheme,
        typography = XenoiseTypography,
        content = content,
    )
}

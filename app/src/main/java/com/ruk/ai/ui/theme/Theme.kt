package com.ruk.ai.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.unit.dp

private val RukColorScheme = darkColorScheme(
    primary = RukColors.Purple,
    onPrimary = RukColors.White,
    primaryContainer = RukColors.PurpleDeep,
    onPrimaryContainer = RukColors.White,
    secondary = RukColors.PurpleLight,
    onSecondary = RukColors.White,
    tertiary = RukColors.AccentCyan,
    onTertiary = RukColors.Black,
    background = RukColors.Black,
    onBackground = RukColors.White,
    surface = RukColors.Surface,
    onSurface = RukColors.White,
    surfaceVariant = RukColors.SurfaceHigh,
    onSurfaceVariant = RukColors.Grey,
    outline = RukColors.SurfaceBorder,
    error = RukColors.Error,
    onError = RukColors.Black
)

val RukShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

/** The signature purple gradient used on primary buttons and the Pro card. */
val PurpleGradient = Brush.horizontalGradient(
    listOf(RukColors.PurpleDeep, RukColors.Purple, RukColors.PurpleLight)
)

val PurpleGradientVertical = Brush.verticalGradient(
    listOf(RukColors.Purple, RukColors.PurpleDeep)
)

/** Subtle glow used behind hero elements. */
val GlowGradient = Brush.radialGradient(
    listOf(RukColors.PurpleGlow, RukColors.Black)
)

@Composable
fun RukAiTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = RukColorScheme,
        typography = RukTypography,
        shapes = RukShapes,
        content = content
    )
}

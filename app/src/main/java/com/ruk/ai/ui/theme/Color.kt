package com.ruk.ai.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * RUK AI brand palette — black, dark navy, purple, white.
 * Mirrors res/values/colors.xml so Compose and XML stay in step.
 */
object RukColors {
    val Black = Color(0xFF000000)
    val Navy = Color(0xFF0A0E1A)
    val NavyDeep = Color(0xFF06080F)
    val Surface = Color(0xFF12121A)
    val SurfaceHigh = Color(0xFF1A1A24)
    val SurfaceBorder = Color(0xFF2A2A38)

    val Purple = Color(0xFF7C3AED)
    val PurpleLight = Color(0xFF9B5DE5)
    val PurpleDeep = Color(0xFF4C1D95)
    val PurpleGlow = Color(0x667C3AED)

    val White = Color(0xFFFFFFFF)
    val Grey = Color(0xFFA0A0A0)
    val GreyDim = Color(0xFF6B6B7B)
    val Gold = Color(0xFFD6AD60)

    val Success = Color(0xFF34D399)
    val Warning = Color(0xFFFBBF24)
    val Error = Color(0xFFF87171)

    // Accents used by the dashboard cards, matching the reference design.
    val AccentBlue = Color(0xFF60A5FA)
    val AccentLavender = Color(0xFFA78BFA)
    val AccentPink = Color(0xFFF472B6)
    val AccentCyan = Color(0xFF22D3EE)
    val AccentRed = Color(0xFFFB7185)
    val AccentGreen = Color(0xFF34D399)
    val AccentMagenta = Color(0xFFE879F9)
}

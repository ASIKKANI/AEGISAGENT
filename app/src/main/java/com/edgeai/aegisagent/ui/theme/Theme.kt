package com.edgeai.aegisagent.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Color Tokens (Futuristic Glassmorphic Obsidian / Dark Cyberpunk theme)
val ObsidianBg = Color(0xFF0C0C0E)
val GlassSurface = Color(0x221D1D22)
val GlassBorder = Color(0x337A8299)
val CyberCyan = Color(0xFF00F0FF)
val ElectricPurple = Color(0xFFBD53FF)
val ActiveGreen = Color(0xFF00F5A0)
val WarningAmber = Color(0xFFFFB000)
val TextPrimary = Color(0xFFE2E8F0)
val TextSecondary = Color(0xFF94A3B8)

private val DarkColorScheme = darkColorScheme(
    primary = CyberCyan,
    secondary = ElectricPurple,
    background = ObsidianBg,
    surface = GlassSurface,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = TextPrimary,
    onSurface = TextPrimary
)

// Typography
val MonospaceFamily = FontFamily.Monospace

val CyberTypography = Typography(
    bodyLarge = TextStyle(
        fontFamily = MonospaceFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        letterSpacing = 0.5.sp,
        color = TextPrimary
    ),
    titleLarge = TextStyle(
        fontFamily = MonospaceFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        letterSpacing = 1.sp,
        color = CyberCyan
    ),
    labelMedium = TextStyle(
        fontFamily = MonospaceFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        color = TextSecondary
    )
)

@Composable
fun AegisTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = CyberTypography,
        content = content
    )
}

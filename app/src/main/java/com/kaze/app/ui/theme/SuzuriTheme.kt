package com.kaze.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Option B — Suzuri Studio & Persimmon (硯・柿) Palette
 */
object SuzuriColors {
    val Base = Color(0xFF141413)               // OLED Inkstone canvas
    val Surface = Color(0xFF1D1C1A)            // Smoked Hinoki card surface
    val SurfaceAlt = Color(0xFF262522)         // Recessed badge / secondary surface
    val Hairline = Color(0xFF2E2C28)           // 1dp technical rule
    val HairlineStrong = Color(0xFF3D3A34)     // Focused / elevated hairline

    val Ivory = Color(0xFFF2EFE9)              // Primary warm ivory ink
    val Mist = Color(0xFF9A958B)               // Secondary muted ink
    val Faint = Color(0xFF6A665E)              // Micro-labels & telemetry hints

    val Persimmon = Color(0xFFE05A3A)          // Kaki persimmon primary pulse & CTA
    val PersimmonSoft = Color(0x24E05A3A)      // Soft persimmon wash
    val PersimmonHighlight = Color(0xFFF8B49E) // Gloss crescent highlight on Kaki motif
    val Celadon = Color(0xFF7FA88E)            // Calyx (heta) & verified LAN signal
    val CeladonSoft = Color(0x227FA88E)        // Soft celadon wash
}

private val SuzuriDarkColorScheme = darkColorScheme(
    primary = SuzuriColors.Persimmon,
    onPrimary = SuzuriColors.Base,
    secondary = SuzuriColors.Celadon,
    onSecondary = SuzuriColors.Base,
    background = SuzuriColors.Base,
    onBackground = SuzuriColors.Ivory,
    surface = SuzuriColors.Surface,
    onSurface = SuzuriColors.Ivory,
    surfaceVariant = SuzuriColors.SurfaceAlt,
    onSurfaceVariant = SuzuriColors.Mist,
    outline = SuzuriColors.Hairline,
    error = SuzuriColors.Persimmon,
    onError = SuzuriColors.Base
)

val SuzuriTypography = Typography(
    headlineSmall = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 19.sp,
        letterSpacing = 2.6.sp,
        color = SuzuriColors.Ivory
    ),
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        letterSpacing = 0.4.sp,
        color = SuzuriColors.Ivory
    ),
    bodyMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 19.sp,
        color = SuzuriColors.Mist
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        color = SuzuriColors.Ivory
    ),
    labelMedium = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        letterSpacing = 0.6.sp,
        color = SuzuriColors.Mist
    ),
    labelSmall = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        letterSpacing = 0.8.sp,
        color = SuzuriColors.Faint
    )
)

@Composable
fun KazeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = SuzuriDarkColorScheme,
        typography = SuzuriTypography,
        content = content
    )
}

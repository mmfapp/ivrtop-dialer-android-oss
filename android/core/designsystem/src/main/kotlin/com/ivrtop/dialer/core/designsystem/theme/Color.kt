package com.ivrtop.dialer.core.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color

/**
 * Brand palette sampled from the original אהבת ישראל artwork (branding/original):
 *   teal  #10B3B5 (letters)  ·  deep teal #229D99 (letter shading)
 *   ocean #0A78A6            ·  indigo #5B5FA8  ·  magenta #A3418F (gradient end)
 * Text-bearing roles use darker/lighter tones that pass WCAG AA (4.5:1).
 * Design tokens are mirrored in contracts/design-tokens.json for iOS.
 */
object Brand {
    val Teal = Color(0xFF10B3B5)
    val TealDeep = Color(0xFF229D99)
    val Ocean = Color(0xFF0A78A6)
    val Indigo = Color(0xFF5B5FA8)
    val Magenta = Color(0xFFA3418F)

    val CallGreen = Color(0xFF1F9D55)
    val CallGreenDark = Color(0xFF3CCB7F)
    val HangupRed = Color(0xFFE5484D)
    val Missed = Color(0xFFD93A4A)
    val MissedDark = Color(0xFFFF7A85)
    val Warning = Color(0xFFE8A317)

    /** Signature diagonal gradient (logo background). */
    val gradient = Brush.linearGradient(listOf(Color(0xFF13B5B8), Ocean, Indigo, Magenta))
    val gradientSoft = Brush.linearGradient(listOf(Color(0xFF13B5B8).copy(alpha = 0.16f), Indigo.copy(alpha = 0.10f), Magenta.copy(alpha = 0.14f)))
}

internal val LightColors = lightColorScheme(
    primary = Color(0xFF00696B),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB2F0EF),
    onPrimaryContainer = Color(0xFF002021),
    secondary = Color(0xFF0A6A93),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFCDE6FF),
    onSecondaryContainer = Color(0xFF001E30),
    tertiary = Color(0xFF8E3A80),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFFFD7F3),
    onTertiaryContainer = Color(0xFF390035),
    error = Color(0xFFBA1A1A),
    onError = Color.White,
    errorContainer = Color(0xFFFFDAD6),
    onErrorContainer = Color(0xFF410002),
    background = Color(0xFFF7FAFA),
    onBackground = Color(0xFF161D1D),
    surface = Color(0xFFF7FAFA),
    onSurface = Color(0xFF161D1D),
    surfaceVariant = Color(0xFFDAE4E4),
    onSurfaceVariant = Color(0xFF3F4949),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF1F5F5),
    surfaceContainer = Color(0xFFEBF0F0),
    surfaceContainerHigh = Color(0xFFE5EAEA),
    surfaceContainerHighest = Color(0xFFDFE4E4),
    outline = Color(0xFF6F7979),
    outlineVariant = Color(0xFFBEC8C8),
    inverseSurface = Color(0xFF2B3232),
    inverseOnSurface = Color(0xFFECF2F1),
    inversePrimary = Color(0xFF4FD8DA),
)

internal val DarkColors = darkColorScheme(
    primary = Color(0xFF4FD8DA),
    onPrimary = Color(0xFF003738),
    primaryContainer = Color(0xFF004F51),
    onPrimaryContainer = Color(0xFFB2F0EF),
    secondary = Color(0xFF8FCDFF),
    onSecondary = Color(0xFF00344F),
    secondaryContainer = Color(0xFF004C70),
    onSecondaryContainer = Color(0xFFCDE6FF),
    tertiary = Color(0xFFF7AEE6),
    onTertiary = Color(0xFF570A4F),
    tertiaryContainer = Color(0xFF722367),
    onTertiaryContainer = Color(0xFFFFD7F3),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
    errorContainer = Color(0xFF93000A),
    onErrorContainer = Color(0xFFFFDAD6),
    background = Color(0xFF0E1415),
    onBackground = Color(0xFFDDE4E3),
    surface = Color(0xFF0E1415),
    onSurface = Color(0xFFDDE4E3),
    surfaceVariant = Color(0xFF3F4949),
    onSurfaceVariant = Color(0xFFBEC8C8),
    surfaceContainerLowest = Color(0xFF090F10),
    surfaceContainerLow = Color(0xFF161D1D),
    surfaceContainer = Color(0xFF1A2121),
    surfaceContainerHigh = Color(0xFF252B2B),
    surfaceContainerHighest = Color(0xFF2F3636),
    outline = Color(0xFF889392),
    outlineVariant = Color(0xFF3F4949),
    inverseSurface = Color(0xFFDDE4E3),
    inverseOnSurface = Color(0xFF2B3232),
    inversePrimary = Color(0xFF00696B),
)

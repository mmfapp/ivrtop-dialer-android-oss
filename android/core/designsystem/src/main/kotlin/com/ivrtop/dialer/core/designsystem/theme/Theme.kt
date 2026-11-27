package com.ivrtop.dialer.core.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

/** Semantic colours not covered by Material roles. */
@Immutable
data class ExtendedColors(
    val callGreen: Color,
    val hangupRed: Color,
    val missed: Color,
    val warning: Color,
    val online: Color,
    val isDark: Boolean,
)

val LocalExtendedColors = staticCompositionLocalOf {
    ExtendedColors(Brand.CallGreen, Brand.HangupRed, Brand.Missed, Brand.Warning, Brand.CallGreen, false)
}

val IvrtopShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * App theme. Dynamic colour is optional (Settings › Display) and only re-tints neutral roles –
 * the brand primary is kept so the product always looks like אהבת ישראל.
 */
/**
 * Uses the stable [MaterialTheme]: the Material 3 Expressive APIs (MaterialExpressiveTheme,
 * MotionScheme) are still internal in the Compose BOM in use. [reduceMotion] is kept for callers
 * and for switching to the expressive motion scheme once it is public.
 */
@Suppress("UNUSED_PARAMETER")
@Composable
fun IvrtopTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    dynamicColor: Boolean = false,
    reduceMotion: Boolean = false,
    content: @Composable () -> Unit,
) {
    val base = if (darkTheme) DarkColors else LightColors
    val scheme: ColorScheme = if (dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val ctx = LocalContext.current
        val dyn = if (darkTheme) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx)
        dyn.copy(primary = base.primary, onPrimary = base.onPrimary, primaryContainer = base.primaryContainer, onPrimaryContainer = base.onPrimaryContainer)
    } else base

    val ext = if (darkTheme) {
        ExtendedColors(Brand.CallGreenDark, Brand.HangupRed, Brand.MissedDark, Brand.Warning, Brand.CallGreenDark, true)
    } else {
        ExtendedColors(Brand.CallGreen, Brand.HangupRed, Brand.Missed, Brand.Warning, Brand.CallGreen, false)
    }

    CompositionLocalProvider(LocalExtendedColors provides ext) {
        MaterialTheme(
            colorScheme = scheme,
            typography = IvrtopTypography,
            shapes = IvrtopShapes,
            content = content,
        )
    }
}

object IvrtopThemeExt {
    val colors: ExtendedColors
        @Composable get() = LocalExtendedColors.current
}

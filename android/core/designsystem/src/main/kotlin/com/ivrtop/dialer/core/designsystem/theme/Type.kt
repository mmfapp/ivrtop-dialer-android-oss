package com.ivrtop.dialer.core.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.ivrtop.dialer.core.designsystem.R

/**
 * Rubik (SIL OFL 1.1, branding/RUBIK_OFL.txt) – a rounded geometric face with first-class Hebrew
 * support that echoes the block letterforms of the אהבת ישראל logo. Bundled as a variable font.
 */
private fun rubik(weight: Int) = Font(
    R.font.rubik_variable,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Rubik = FontFamily(rubik(400), rubik(500), rubik(600), rubik(700), rubik(800))

/** Dial-pad digits and timers use tabular figures so they don't jitter. */
val NumberStyle = TextStyle(fontFamily = Rubik, fontWeight = FontWeight.Normal, fontFeatureSettings = "tnum")

internal val IvrtopTypography = Typography(
    displayLarge = TextStyle(fontFamily = Rubik, fontWeight = FontWeight(600), fontSize = 54.sp, lineHeight = 60.sp, letterSpacing = (-0.02).em),
    displayMedium = TextStyle(fontFamily = Rubik, fontWeight = FontWeight(600), fontSize = 42.sp, lineHeight = 50.sp),
    displaySmall = TextStyle(fontFamily = Rubik, fontWeight = FontWeight(500), fontSize = 34.sp, lineHeight = 42.sp, fontFeatureSettings = "tnum"),
    headlineLarge = TextStyle(fontFamily = Rubik, fontWeight = FontWeight(700), fontSize = 30.sp, lineHeight = 38.sp),
    headlineMedium = TextStyle(fontFamily = Rubik, fontWeight = FontWeight(600), fontSize = 26.sp, lineHeight = 34.sp),
    headlineSmall = TextStyle(fontFamily = Rubik, fontWeight = FontWeight(600), fontSize = 22.sp, lineHeight = 30.sp),
    titleLarge = TextStyle(fontFamily = Rubik, fontWeight = FontWeight(600), fontSize = 20.sp, lineHeight = 28.sp),
    titleMedium = TextStyle(fontFamily = Rubik, fontWeight = FontWeight(600), fontSize = 16.sp, lineHeight = 24.sp),
    titleSmall = TextStyle(fontFamily = Rubik, fontWeight = FontWeight(500), fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontFamily = Rubik, fontWeight = FontWeight(400), fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontFamily = Rubik, fontWeight = FontWeight(400), fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontFamily = Rubik, fontWeight = FontWeight(400), fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Rubik, fontWeight = FontWeight(600), fontSize = 14.sp, lineHeight = 20.sp),
    labelMedium = TextStyle(fontFamily = Rubik, fontWeight = FontWeight(500), fontSize = 12.sp, lineHeight = 16.sp),
    labelSmall = TextStyle(fontFamily = Rubik, fontWeight = FontWeight(500), fontSize = 11.sp, lineHeight = 16.sp),
)

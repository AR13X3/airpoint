package io.github.ar13x3.airpoint.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import io.github.ar13x3.airpoint.R

/**
 * Sora (the wordmark's face) sets display text, titles and numbers. Body copy stays in the
 * platform font (Roboto, or SamsungOne on Galaxy devices) so long text feels native.
 */
private fun sora(weight: Int) = Font(
    R.font.sora,
    weight = FontWeight(weight),
    variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
)

val Sora = FontFamily(sora(400), sora(500), sora(600), sora(700))

private val tight = (-0.025).em

val AirTypography = Typography(
    displayLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 44.sp, lineHeight = 48.sp, letterSpacing = (-0.035).em),
    displayMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 36.sp, lineHeight = 42.sp, letterSpacing = (-0.03).em),
    headlineLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = tight),
    headlineMedium = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 24.sp, lineHeight = 30.sp, letterSpacing = tight),
    headlineSmall = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 26.sp, letterSpacing = (-0.015).em),
    titleLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp, letterSpacing = (-0.01).em),
    titleMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    bodySmall = TextStyle(fontSize = 12.sp, lineHeight = 16.sp),
    labelLarge = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 20.sp, letterSpacing = (-0.005).em),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 13.sp, lineHeight = 16.sp, letterSpacing = 0.01.em),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 14.sp, letterSpacing = 0.08.em),
)

/** Tabular figures, so changing numbers don't jitter sideways. */
val Numeric = TextStyle(fontFamily = Sora, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum")

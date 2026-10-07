package io.github.ar13x3.airpoint.ui.theme

import android.provider.Settings
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import io.github.ar13x3.airpoint.core.ThemeMode

/**
 * Airpoint's colour tokens. Ink and Paper are the brand neutrals; Electric is the one accent.
 * The interactive accent is #3B69FF, a hair deeper than the brand's #3D6BFF, so white labels
 * on it pass WCAG AA (4.5:1). Every text token clears 4.5:1 on every surface it's used on, and
 * status colours differ in lightness as well as hue so they still read for colour-blind users.
 */
@Immutable
data class AirpointColors(
    val isDark: Boolean,
    val background: Color,
    val surface: Color,
    val surfaceRaised: Color,
    val surfaceSunken: Color,
    /** Background for icon tiles: lifts off cards in dark, sinks into them in light. */
    val tile: Color,
    val outline: Color,
    val outlineStrong: Color,
    val text: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val accent: Color,
    val onAccent: Color,
    val accentSoft: Color,
    val accentText: Color,
    val live: Color,
    val liveSoft: Color,
    val warn: Color,
    val warnSoft: Color,
    val danger: Color,
    val dangerSoft: Color,
)

val DarkColors = AirpointColors(
    isDark = true,
    background = Color(0xFF0D0F14),
    surface = Color(0xFF15181F),
    surfaceRaised = Color(0xFF1D2129),
    surfaceSunken = Color(0xFF090B0F),
    tile = Color(0xFF1D2129),
    outline = Color(0xFF242934),
    outlineStrong = Color(0xFF353B48),
    text = Color(0xFFF4F4F1),
    textSecondary = Color(0xFFA3A9B5),
    textTertiary = Color(0xFF828894),
    accent = Color(0xFF3B69FF),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFF1A2547),
    accentText = Color(0xFF8DA8FF),
    live = Color(0xFF3DD68C),
    liveSoft = Color(0xFF12301F),
    warn = Color(0xFFFFB547),
    warnSoft = Color(0xFF33270F),
    danger = Color(0xFFFF6B5E),
    dangerSoft = Color(0xFF3A1814),
)

val LightColors = AirpointColors(
    isDark = false,
    background = Color(0xFFF4F4F1),
    surface = Color(0xFFFFFFFF),
    surfaceRaised = Color(0xFFFFFFFF),
    surfaceSunken = Color(0xFFEBEBE6),
    tile = Color(0xFFEFEFEA),
    outline = Color(0xFFE2E2DC),
    outlineStrong = Color(0xFFCFCFC8),
    text = Color(0xFF0D0F14),
    textSecondary = Color(0xFF525865),
    textTertiary = Color(0xFF656A75),
    accent = Color(0xFF3B69FF),
    onAccent = Color(0xFFFFFFFF),
    accentSoft = Color(0xFFE5EBFF),
    accentText = Color(0xFF2B55E6),
    live = Color(0xFF0B7B49),
    liveSoft = Color(0xFFDAF3E6),
    warn = Color(0xFF985D00),
    warnSoft = Color(0xFFFBEED6),
    danger = Color(0xFFC13528),
    dangerSoft = Color(0xFFFBE3E0),
)

object Radii {
    val sm = RoundedCornerShape(12.dp)
    val md = RoundedCornerShape(18.dp)
    val lg = RoundedCornerShape(26.dp)
    val xl = RoundedCornerShape(34.dp)
}

val LocalAirColors = staticCompositionLocalOf { DarkColors }

/** True when the user turned animations off (Settings › Accessibility › Remove animations). */
val LocalReduceMotion = staticCompositionLocalOf { false }

object Air {
    val colors: AirpointColors
        @Composable get() = LocalAirColors.current
    val reduceMotion: Boolean
        @Composable get() = LocalReduceMotion.current
}

@Composable
fun AirpointTheme(mode: ThemeMode = ThemeMode.System, content: @Composable () -> Unit) {
    val dark = when (mode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val c = if (dark) DarkColors else LightColors
    val context = LocalContext.current
    val reduceMotion = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    // Material components (switches, dialogs, text fields) pick up the same tokens.
    val scheme = if (dark) {
        darkColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accentText,
            background = c.background, onBackground = c.text, surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surfaceRaised, onSurfaceVariant = c.textSecondary, outline = c.outlineStrong,
            outlineVariant = c.outline, error = c.danger, surfaceContainerHigh = c.surfaceRaised,
            surfaceContainer = c.surface, surfaceContainerHighest = c.surfaceRaised,
        )
    } else {
        lightColorScheme(
            primary = c.accent, onPrimary = c.onAccent, primaryContainer = c.accentSoft, onPrimaryContainer = c.accentText,
            background = c.background, onBackground = c.text, surface = c.surface, onSurface = c.text,
            surfaceVariant = c.surfaceSunken, onSurfaceVariant = c.textSecondary, outline = c.outlineStrong,
            outlineVariant = c.outline, error = c.danger, surfaceContainerHigh = c.surface,
            surfaceContainer = c.surface, surfaceContainerHighest = c.surfaceSunken,
        )
    }
    CompositionLocalProvider(LocalAirColors provides c, LocalReduceMotion provides reduceMotion) {
        MaterialTheme(colorScheme = scheme, typography = AirTypography, content = content)
    }
}

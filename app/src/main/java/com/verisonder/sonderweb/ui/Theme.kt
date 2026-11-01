package com.verisonder.sonderweb.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import com.verisonder.sonderweb.R

/**
 * Plain browser colours, close to Chromium's: grey toolbar, a rounded grey address field, one blue.
 * The app ships its own font (Inter) so it looks the same whatever font the phone uses.
 */
@Immutable
data class Palette(
    val dark: Boolean,
    val bg: Color,
    val bar: Color,
    val field: Color,
    val card: Color,
    val line: Color,
    val text: Color,
    val mute: Color,
    val faint: Color,
    val accent: Color,
    val privateAccent: Color,
    val danger: Color,
)

val DarkPalette = Palette(
    dark = true,
    bg = Color(0xFF202124),
    bar = Color(0xFF202124),
    field = Color(0xFF303134),
    card = Color(0xFF292A2D),
    line = Color(0xFF3C4043),
    text = Color(0xFFE8EAED),
    mute = Color(0xFFBDC1C6),
    faint = Color(0xFF9AA0A6),
    accent = Color(0xFF8AB4F8),
    privateAccent = Color(0xFFC58AF9),
    danger = Color(0xFFF28B82),
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFFFFFFF),
    bar = Color(0xFFFFFFFF),
    field = Color(0xFFF1F3F4),
    card = Color(0xFFF8F9FA),
    line = Color(0xFFDADCE0),
    text = Color(0xFF202124),
    mute = Color(0xFF5F6368),
    faint = Color(0xFF80868B),
    accent = Color(0xFF1A73E8),
    privateAccent = Color(0xFF9334E6),
    danger = Color(0xFFD93025),
)

/** Private tabs: always dark, like an incognito window. */
val PrivatePalette = DarkPalette.copy(
    bg = Color(0xFF292A2D),
    bar = Color(0xFF292A2D),
    field = Color(0xFF3C4043),
    accent = Color(0xFFC58AF9),
)

val Inter = FontFamily(
    Font(R.font.inter_extralight, FontWeight.ExtraLight),
    Font(R.font.inter_light, FontWeight.Light),
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_semibold, FontWeight.Bold),
)

/** Text centred in its line box, so short labels sit in the middle of what holds them. */
val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

@Composable
fun isDark(theme: Int): Boolean = when (theme) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

private fun typography(): Typography {
    val base = Typography()
    fun TextStyle.inter() = copy(fontFamily = Inter)
    return Typography(
        displayLarge = base.displayLarge.inter(),
        displayMedium = base.displayMedium.inter(),
        displaySmall = base.displaySmall.inter(),
        headlineLarge = base.headlineLarge.inter(),
        headlineMedium = base.headlineMedium.inter(),
        headlineSmall = base.headlineSmall.inter(),
        titleLarge = base.titleLarge.inter(),
        titleMedium = base.titleMedium.inter(),
        titleSmall = base.titleSmall.inter(),
        bodyLarge = base.bodyLarge.inter(),
        bodyMedium = base.bodyMedium.inter(),
        bodySmall = base.bodySmall.inter(),
        labelLarge = base.labelLarge.inter(),
        labelMedium = base.labelMedium.inter(),
        labelSmall = base.labelSmall.inter(),
    )
}

@Composable
fun SonderTheme(palette: Palette, content: @Composable () -> Unit) {
    val scheme = if (palette.dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color(0xFF202124),
            background = palette.bg,
            surface = palette.card,
            surfaceContainer = palette.card,
            surfaceContainerLow = palette.card,
            surfaceContainerHigh = palette.field,
            surfaceContainerHighest = palette.field,
            onBackground = palette.text,
            onSurface = palette.text,
            onSurfaceVariant = palette.mute,
            outline = palette.line,
            outlineVariant = palette.line,
            error = palette.danger,
        )
    } else {
        lightColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.bg,
            surface = palette.bg,
            surfaceContainer = palette.bg,
            surfaceContainerLow = palette.bg,
            surfaceContainerHigh = palette.card,
            surfaceContainerHighest = palette.field,
            onBackground = palette.text,
            onSurface = palette.text,
            onSurfaceVariant = palette.mute,
            outline = palette.line,
            outlineVariant = palette.line,
            error = palette.danger,
        )
    }
    CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, typography = typography(), content = content)
    }
}

/** Text and icons on an accent-coloured fill. */
val Palette.onAccent: Color get() = if (dark) Color(0xFF202124) else Color.White

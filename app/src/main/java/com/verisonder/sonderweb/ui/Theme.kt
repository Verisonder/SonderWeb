package com.verisonder.sonderweb.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** SonderWeb's colours: near-black / white, one blue accent, violet for private tabs. */
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
    bg = Color(0xFF0B0B0C),
    bar = Color(0xFF0B0B0C),
    field = Color(0xFF1C1C1F),
    card = Color(0xFF161618),
    line = Color(0xFF2A2A2E),
    text = Color(0xFFEDEDED),
    mute = Color(0xFFA0A0A6),
    faint = Color(0xFF6B6B72),
    accent = Color(0xFF3B82FF),
    privateAccent = Color(0xFFA78BFA),
    danger = Color(0xFFFF5A5F),
)

val LightPalette = Palette(
    dark = false,
    bg = Color(0xFFFFFFFF),
    bar = Color(0xFFFFFFFF),
    field = Color(0xFFF1F1F3),
    card = Color(0xFFF6F6F8),
    line = Color(0xFFE4E4E8),
    text = Color(0xFF111113),
    mute = Color(0xFF5F5F66),
    faint = Color(0xFF9A9AA2),
    accent = Color(0xFF2563EB),
    privateAccent = Color(0xFF7C3AED),
    danger = Color(0xFFDC2626),
)

/** Private tabs always use a dark, violet-tinted bar so you can tell at a glance. */
val PrivatePalette = DarkPalette.copy(
    bar = Color(0xFF15111F),
    field = Color(0xFF241D33),
    accent = Color(0xFFA78BFA),
)

val LocalPalette = staticCompositionLocalOf { DarkPalette }

@Composable
fun isDark(theme: Int): Boolean = when (theme) {
    1 -> false
    2 -> true
    else -> isSystemInDarkTheme()
}

@Composable
fun SonderTheme(palette: Palette, content: @Composable () -> Unit) {
    val scheme = if (palette.dark) {
        darkColorScheme(
            primary = palette.accent,
            onPrimary = Color.White,
            background = palette.bg,
            surface = palette.card,
            surfaceContainer = palette.card,
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
            surfaceContainer = palette.card,
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
    androidx.compose.runtime.CompositionLocalProvider(LocalPalette provides palette) {
        MaterialTheme(colorScheme = scheme, content = content)
    }
}

package com.robertotorino.gallery.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class AppThemeColors(
    val background: Color,
    val cardBackground: Color,
    val boxBackground: Color,
    val boxBorder: Color,
    val boxText: Color,
    val accent: Color,
    val textPrimary: Color,
    val textSecondary: Color
)

val BlueTheme = AppThemeColors(
    background = Color(0xFF003791),
    cardBackground = Color(0xFF0072CE).copy(alpha = 0.8f),
    boxBackground = Color(0xFF0072CE),
    boxBorder = Color.White,
    boxText = Color.White,
    accent = Color(0xFF4FC3F7),
    textPrimary = Color.White,
    textSecondary = Color.White.copy(alpha = 0.7f)
)

val LocalAppThemeColors = staticCompositionLocalOf {
    BlueTheme // Default to BlueTheme
}

object AppTheme {
    val colors: AppThemeColors
        @Composable
        @ReadOnlyComposable
        get() = LocalAppThemeColors.current
}

@Composable
fun GalleryTheme(
    colors: AppThemeColors = BlueTheme,
    content: @Composable () -> Unit
) {
    val materialColorScheme = darkColorScheme(
        primary = colors.accent,
        background = colors.background,
        surface = colors.background,
        onBackground = colors.textPrimary,
        onSurface = colors.textPrimary,
        onPrimary = Color.Black // Contrast for accent
    )

    CompositionLocalProvider(LocalAppThemeColors provides colors) {
        MaterialTheme(
            colorScheme = materialColorScheme,
            typography = Typography,
            content = content
        )
    }
}

package com.scrollmeter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import com.scrollmeter.app.domain.models.ThemePreference

private val LightColorScheme = lightColorScheme(
    primary = ElectricPurplePrimary,
    onPrimary = Color.White,
    primaryContainer = ElectricPurpleContainerLight,
    onPrimaryContainer = Color(0xFF312E81),
    secondary = InstagramReelsMagenta,
    onSecondary = Color.White,
    tertiary = YouTubeShortsRed,
    background = SoftWhiteBackground,
    onBackground = LightTextPrimary,
    surface = LightCardSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    outline = LightBorderSubtle
)

private val DarkColorScheme = darkColorScheme(
    primary = ElectricPurplePrimary,
    onPrimary = Color.White,
    primaryContainer = ElectricPurpleContainerDark,
    onPrimaryContainer = Color(0xFFE0E7FF),
    secondary = InstagramReelsMagenta,
    onSecondary = Color.White,
    tertiary = YouTubeShortsRed,
    background = DeepCharcoalBackground,
    onBackground = DarkTextPrimary,
    surface = DarkCardSurface,
    onSurface = DarkTextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = DarkTextSecondary,
    outline = DarkBorderSubtle
)

@Composable
fun ScrollMeterTheme(
    themePreference: ThemePreference = ThemePreference.SYSTEM,
    content: @Composable () -> Unit
) {
    val useDarkTheme = when (themePreference) {
        ThemePreference.LIGHT -> false
        ThemePreference.DARK -> true
        ThemePreference.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (useDarkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = ScrollMeterTypography,
        content = content
    )
}

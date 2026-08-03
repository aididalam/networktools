package com.nexdevlab.networktools.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val ColorWhite = Color(0xFFFFFFFF)

private val LightColors = lightColorScheme(
    primary = LightPrimary,
    onPrimary = ColorWhite,
    primaryContainer = Color(0xFFCDEAE2),
    onPrimaryContainer = Color(0xFF12362F),
    secondary = LightSecondary,
    secondaryContainer = Color(0xFFDCE8EE),
    onSecondaryContainer = Color(0xFF243A45),
    tertiary = LightTertiary,
    tertiaryContainer = Color(0xFFFFDDA5),
    onTertiaryContainer = Color(0xFF2A1800),
    background = LightBackground,
    onBackground = LightText,
    surface = LightSurface,
    onSurface = LightText,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = Color(0xFF44514B),
    outline = Color(0xFF74817B),
    outlineVariant = Color(0xFFCBD4CF),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = DarkPrimary,
    onPrimary = Color(0xFF00382F),
    primaryContainer = Color(0xFF1C5046),
    onPrimaryContainer = Color(0xFFCBEAE2),
    secondary = DarkSecondary,
    secondaryContainer = Color(0xFF344A55),
    onSecondaryContainer = Color(0xFFD5E7F0),
    tertiary = DarkTertiary,
    tertiaryContainer = Color(0xFF5F4100),
    onTertiaryContainer = Color(0xFFFFDEA6),
    background = DarkBackground,
    onBackground = DarkText,
    surface = DarkSurface,
    onSurface = DarkText,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = Color(0xFFBEC9C3),
    outline = Color(0xFF89948E),
    outlineVariant = Color(0xFF39433E),
    error = Color(0xFFFFB4AB),
)

enum class ThemeChoice(val label: String) {
    LIGHT("Light"),
    DARK("Dark"),
    SYSTEM("System default"),
}

@Composable
fun NetworkToolsTheme(
    themeChoice: ThemeChoice = ThemeChoice.SYSTEM,
    content: @Composable () -> Unit,
) {
    val dark = when (themeChoice) {
        ThemeChoice.LIGHT -> false
        ThemeChoice.DARK -> true
        ThemeChoice.SYSTEM -> isSystemInDarkTheme()
    }
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = NetworkTypography,
        content = content,
    )
}

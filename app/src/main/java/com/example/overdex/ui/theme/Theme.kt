package com.example.overdex.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat

private val TerminalColorScheme = darkColorScheme(
    primary = TerminalGreen,
    secondary = TerminalPurple,
    tertiary = TerminalDimGreen,
    background = TerminalBlack,
    surface = TerminalBlack,
    onPrimary = TerminalBlack,
    onSecondary = TerminalBlack,
    onTertiary = TerminalGreen,
    onBackground = TerminalGreen,
    onSurface = TerminalGreen
)

@Composable
fun OverdexTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is disabled to maintain retro theme
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val colorScheme = TerminalColorScheme
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colorScheme.background.toArgb()
            WindowCompat.getInsetsController(window, view).isAppearanceLightStatusBars = false
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}

package com.atari.launcher.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val AtariColorScheme = darkColorScheme(
    primary = Color(0xFF00D4FF),
    onPrimary = Color(0xFF000000),
    secondary = Color(0xFFCC2200),
    onSecondary = Color(0xFFFFFFFF),
    background = Color(0xFF0A0A12),
    onBackground = Color(0xFFE0E0E0),
    surface = Color(0xFF1A1A2E),
    onSurface = Color(0xFFE0E0E0),
    error = Color(0xFFFF5555),
)

@Composable
fun AtariLauncherTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AtariColorScheme,
        content = content
    )
}

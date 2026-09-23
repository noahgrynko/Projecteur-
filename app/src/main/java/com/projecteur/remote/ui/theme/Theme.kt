package com.projecteur.remote.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PowerRed = Color(0xFFD93025)
val OkGreen = Color(0xFF1E8E3E)
val WarnAmber = Color(0xFFF29900)
val ErrorRed = Color(0xFFC5221F)

private val Dark = darkColorScheme(
    primary = Color(0xFF8AB4F8),
    secondary = Color(0xFFA8C7FA),
    background = Color(0xFF111318),
    surface = Color(0xFF1B1E24),
    surfaceVariant = Color(0xFF2A2E36),
)
private val Light = lightColorScheme(
    primary = Color(0xFF1A73E8),
    secondary = Color(0xFF3C4A63),
)

@Composable
fun ProjecteurTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

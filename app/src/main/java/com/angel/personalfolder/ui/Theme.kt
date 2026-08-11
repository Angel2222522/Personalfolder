package com.angel.personalfolder.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = Color(0xFF496A7D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD0E6F1),
    onPrimaryContainer = Color(0xFF0A1E28),
    secondary = Color(0xFF6A5F74),
    secondaryContainer = Color(0xFFEDE1F2),
    surface = Color(0xFFF8FAFC),
    surfaceContainer = Color(0xFFEEF2F5),
    background = Color(0xFFF8FAFC),
    error = Color(0xFFB3261E)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB0D0E1),
    onPrimary = Color(0xFF17313E),
    primaryContainer = Color(0xFF304C5B),
    onPrimaryContainer = Color(0xFFD0E6F1),
    secondary = Color(0xFFD5C5DC),
    secondaryContainer = Color(0xFF4A4051),
    surface = Color(0xFF101416),
    surfaceContainer = Color(0xFF1C2225),
    background = Color(0xFF101416)
)

@Composable
fun PersonalFolderTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) DarkColors else LightColors,
        content = content
    )
}

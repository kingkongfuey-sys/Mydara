package com.example.mydara

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MydaraColors = darkColorScheme(
    primary = Color(0xFF7EE8A2),
    onPrimary = Color(0xFF0B2A3A),
    background = Color(0xFF0B2A3A),
    surface = Color(0xFF0B2A3A),
    onBackground = Color(0xFFD4E6EE),
    onSurface = Color(0xFFD4E6EE)
)

@Composable
fun MydaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MydaraColors, content = content)
}

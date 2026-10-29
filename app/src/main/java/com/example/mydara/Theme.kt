package com.example.mydara

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val MydaraColors = darkColorScheme(
    primary = Color(0xFF2ECC40),
    background = Color(0xFF111111),
    surface = Color(0xFF111111),
    onBackground = Color.White,
    onSurface = Color.White
)

@Composable
fun MydaraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = MydaraColors, content = content)
}

package com.dendy.app.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val WarmLightScheme = lightColorScheme(
    primary = Color(0xFF245C7A),
    onPrimary = Color.White,
    secondary = Color(0xFF4D8F7D),
    tertiary = Color(0xFFCB7A2B),
    background = Color(0xFFF5F0E7),
    surface = Color(0xFFFFFAF2),
)

@Composable
fun DendyTheme(
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = WarmLightScheme,
        content = content,
    )
}

package com.dendy.app.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val WarmLightScheme = lightColorScheme(
    primary = Color(0xFF245C7A),
    onPrimary = Color.White,
    secondary = Color(0xFF4D8F7D),
    tertiary = Color(0xFFCB7A2B),
    background = Color(0xFFF5F0E7),
    surface = Color(0xFFFFFAF2),
)

private val WarmDarkScheme = darkColorScheme(
    primary = Color(0xFF7CC7F3),
    secondary = Color(0xFF86D5B8),
    tertiary = Color(0xFFFFB26A),
    background = Color(0xFF0A1118),
    surface = Color(0xFF121C25),
)

@Composable
fun DendyTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val colorScheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && darkTheme -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !darkTheme -> dynamicLightColorScheme(context)
        darkTheme -> WarmDarkScheme
        else -> WarmLightScheme
    }

    MaterialTheme(
        colorScheme = colorScheme,
        content = content,
    )
}


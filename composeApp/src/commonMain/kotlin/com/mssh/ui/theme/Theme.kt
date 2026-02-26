package com.mssh.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFF4A9EFF),
    onPrimary = Color.White,
    primaryContainer = Color(0xFF003D7A),
    onPrimaryContainer = Color(0xFFD5E3FF),
    secondary = Color(0xFFE94560),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF5C1127),
    onSecondaryContainer = Color(0xFFFFDADF),
    tertiary = Color(0xFF00CC99),
    onTertiary = Color.White,
    background = Color(0xFF1A1A2E),
    onBackground = Color(0xFFE6E1E5),
    surface = Color(0xFF1A1A2E),
    onSurface = Color(0xFFE6E1E5),
    surfaceVariant = Color(0xFF2D2D3F),
    onSurfaceVariant = Color(0xFFCAC4D0),
    error = Color(0xFFFF5555),
    onError = Color.White,
    outline = Color(0xFF3D3D52)
)

@Composable
fun MsshTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        content = content
    )
}

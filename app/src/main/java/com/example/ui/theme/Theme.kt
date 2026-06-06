package com.example.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val DarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    secondary = NeonAmber,
    tertiary = NeonTeal,
    background = MidnightBg,
    surface = SolidSurfaceBg,
    onBackground = CompliantWhite,
    onSurface = CompliantWhite,
    onPrimary = MidnightBg,
    outline = BorderGrey
)

@Composable
fun MyApplicationTheme(
    content: @Composable () -> Unit
) {
    MaterialTheme(
        colorScheme = DarkColorScheme,
        typography = Typography,
        content = content
    )
}

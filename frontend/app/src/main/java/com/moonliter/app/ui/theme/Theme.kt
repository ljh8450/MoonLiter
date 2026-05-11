package com.moonliter.app.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

private val MoonLiterColorScheme: ColorScheme = darkColorScheme(
    primary = LampOrange,
    onPrimary = MidnightNavy,
    background = MidnightNavy,
    onBackground = WarmCream,
    surface = MidnightNavy,
    onSurface = WarmCream,
    secondary = RainBlue,
    tertiary = SoftBrown
)

@Composable
fun MoonLiterTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = MoonLiterColorScheme,
        content = content
    )
}

package com.zhipu.herbreview.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val LightColors = lightColorScheme(
    primary = HerbBrown,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFF0DECF),
    onPrimaryContainer = Color(0xFF2A160C),
    secondary = HerbBrownDeep,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFF4E8DC),
    onSecondaryContainer = Color(0xFF29160C),
    background = WoodLight,
    surface = WoodSurface,
    surfaceVariant = Color(0xFFF6EDE3),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFD7B79A),
    onPrimary = Color(0xFF3A2114),
    primaryContainer = Color(0xFF5A3522),
    onPrimaryContainer = Color(0xFFF6E5D5),
    secondary = Color(0xFFC89F7D),
    onSecondary = Color(0xFF321C11),
    secondaryContainer = Color(0xFF553323),
    onSecondaryContainer = Color(0xFFF3E0D1),
    background = Color(0xFF1C1612),
    surface = Color(0xFF28201A),
)

@Composable
fun HerbReviewTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    val colors: ColorScheme = if (darkTheme) DarkColors else LightColors
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        content = content,
    )
}

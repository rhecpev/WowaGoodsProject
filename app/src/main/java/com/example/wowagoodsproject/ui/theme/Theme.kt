package com.example.wowagoodsproject.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Gold = Color(0xFFF3E85A)
private val GoldLight = Color(0xFFE8C97A) // 밝은 금색
private val GoldDark = Color(0xFF9A7420)  // 어두운 금색
private val Black = Color(0xFF000000)
private val DarkSurface = Color(0xFF1A1A1A)
private val DarkSurfaceVariant = Color(0xFF2A2A2A)
private val OnDark = Color(0xFFE0E0E0)

private val DarkColorScheme = darkColorScheme(
    primary = Color(0xFFF3E85A),        // 밝은 금색
    onPrimary = Color(0xFF000000),      // 검정
    primaryContainer = Color(0xFF9A7420),
    onPrimaryContainer = Color(0xFFF3E85A),
    secondary = Color(0xFFE8C97A),
    onSecondary = Color(0xFF000000),
    tertiary = Color(0xFF9A7420),
    onTertiary = Color(0xFF000000),
    background = Color(0xFF0A0A0A),     // 진한 검정
    onBackground = Color(0xFFEEEEEE),   // 밝은 회색 글자
    surface = Color(0xFF141414),        // 약간 밝은 검정
    onSurface = Color(0xFFEEEEEE),
    surfaceVariant = Color(0xFF1E1E1E),
    onSurfaceVariant = Color(0xFFAAAAAA),
    outline = Color(0xFF4A4A4A),
    error = Color(0xFFCF6679)
)

private val LightColorScheme = lightColorScheme(
    primary = Color(0xFFB8860B),        // 어두운 금색
    onPrimary = Color(0xFFFFFAF0),      // 아이보리
    primaryContainer = Color(0xFFDAA520),
    onPrimaryContainer = Color(0xFF3D2B00),
    secondary = Color(0xFFB8860B),
    onSecondary = Color(0xFFFFFAF0),
    tertiary = Color(0xFFDAA520),
    onTertiary = Color(0xFF3D2B00),
    background = Color(0xFFFFFAF0),     // 아이보리 배경
    onBackground = Color(0xFF3D2B00),   // 진한 갈색 글자
    surface = Color(0xFFFFF8E7),        // 살짝 노란 아이보리
    onSurface = Color(0xFF3D2B00),
    surfaceVariant = Color(0xFFEDE0C8), // 베이지
    onSurfaceVariant = Color(0xFF5C4A1E),
    outline = Color(0xFFB8860B),
    error = Color(0xFFB3261E)
)

@Composable
fun WowaGoodsProjectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
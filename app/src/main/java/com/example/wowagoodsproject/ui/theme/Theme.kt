package com.example.wowagoodsproject.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import android.app.Activity
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
    outlineVariant = Color(0xFF2C2C2C),
    error = Color(0xFFCF6679),
    // 아래 값을 비워 두면 M3 기본 보라 톤이 다이얼로그/칩 등에 섞여 나온다.
    secondaryContainer = Color(0xFF3A3314),
    onSecondaryContainer = Color(0xFFF5EBA8),
    surfaceDim = Color(0xFF0A0A0A),
    surfaceBright = Color(0xFF2A2A2A),
    surfaceContainerLowest = Color(0xFF0A0A0A),
    surfaceContainerLow = Color(0xFF111111),
    surfaceContainer = Color(0xFF171717),
    surfaceContainerHigh = Color(0xFF1E1E1E),
    surfaceContainerHighest = Color(0xFF262626),
    inverseSurface = Color(0xFFEEEEEE),
    inverseOnSurface = Color(0xFF141414),
    inversePrimary = Color(0xFF9A7420)
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
    outlineVariant = Color(0xFFE6D6B2),
    error = Color(0xFFB3261E),
    secondaryContainer = Color(0xFFF6E4B0),
    onSecondaryContainer = Color(0xFF3D2B00),
    surfaceDim = Color(0xFFE8DCC2),
    surfaceBright = Color(0xFFFFFAF0),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFFFF6E6),
    surfaceContainer = Color(0xFFFBF0DA),
    surfaceContainerHigh = Color(0xFFF7EAD0),
    surfaceContainerHighest = Color(0xFFF1E3C6),
    inverseSurface = Color(0xFF3D2B00),
    inverseOnSurface = Color(0xFFFFF8E7),
    inversePrimary = Color(0xFFF3E85A)
)

@Composable
fun WowaGoodsProjectTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme
    val view = LocalView.current

    // 상태바/내비게이션바 아이콘 명암을 테마에 맞춘다.
    // (바 배경은 enableEdgeToEdge() 로 투명하게 두고 앱 배경이 그대로 비치게 한다)
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !darkTheme
                isAppearanceLightNavigationBars = !darkTheme
            }
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
package com.example.wowagoodsproject.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import android.app.Activity
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp

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

/**
 * 어두운 테마 한 벌을 만든다.
 * 배경색을 흰색 쪽으로 조금씩 섞어 surface 단계(컨테이너 명암)를 자동으로 뽑는다.
 * @param primary 강조색(메인 색상)
 * @param deep primary 의 어두운 버전. 컨테이너/inversePrimary 에 쓴다.
 * @param muted 보조 글자색
 */
private fun darkSchemeOf(
    primary: Color,
    onPrimary: Color,
    secondary: Color,
    deep: Color,
    background: Color,
    onBackground: Color,
    muted: Color
): ColorScheme {
    val up = Color.White
    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = deep,
        onPrimaryContainer = primary,
        secondary = secondary,
        onSecondary = onPrimary,
        tertiary = deep,
        onTertiary = onPrimary,
        background = background,
        onBackground = onBackground,
        surface = lerp(background, up, 0.04f),
        onSurface = onBackground,
        surfaceVariant = lerp(background, up, 0.08f),
        onSurfaceVariant = muted,
        outline = lerp(background, up, 0.26f),
        outlineVariant = lerp(background, up, 0.12f),
        error = Color(0xFFCF6679),
        secondaryContainer = lerp(deep, background, 0.45f),
        onSecondaryContainer = lerp(primary, up, 0.2f),
        surfaceDim = background,
        surfaceBright = lerp(background, up, 0.16f),
        surfaceContainerLowest = background,
        surfaceContainerLow = lerp(background, up, 0.03f),
        surfaceContainer = lerp(background, up, 0.06f),
        surfaceContainerHigh = lerp(background, up, 0.10f),
        surfaceContainerHighest = lerp(background, up, 0.14f),
        inverseSurface = onBackground,
        inverseOnSurface = background,
        inversePrimary = deep
    )
}

/**
 * 밝은 테마 한 벌을 만든다.
 * 배경색을 글자색(onBackground) 쪽으로 조금씩 섞어 surface 단계를 뽑는다.
 */
private fun lightSchemeOf(
    primary: Color,
    onPrimary: Color,
    secondary: Color,
    container: Color,
    background: Color,
    onBackground: Color,
    muted: Color
): ColorScheme {
    val down = onBackground
    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = container,
        onPrimaryContainer = onBackground,
        secondary = secondary,
        onSecondary = onPrimary,
        tertiary = container,
        onTertiary = onBackground,
        background = background,
        onBackground = onBackground,
        surface = lerp(background, Color.White, 0.35f),
        onSurface = onBackground,
        surfaceVariant = lerp(background, down, 0.12f),
        onSurfaceVariant = muted,
        outline = primary,
        outlineVariant = lerp(background, down, 0.14f),
        error = Color(0xFFB3261E),
        secondaryContainer = lerp(container, background, 0.6f),
        onSecondaryContainer = onBackground,
        surfaceDim = lerp(background, down, 0.14f),
        surfaceBright = background,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = lerp(background, Color.White, 0.5f),
        surfaceContainer = lerp(background, down, 0.04f),
        surfaceContainerHigh = lerp(background, down, 0.07f),
        surfaceContainerHighest = lerp(background, down, 0.11f),
        inverseSurface = onBackground,
        inverseOnSurface = background,
        inversePrimary = lerp(primary, Color.White, 0.55f)
    )
}

private val MidnightColorScheme = darkSchemeOf(
    primary = Color(0xFF7CA9FF),
    onPrimary = Color(0xFF06122B),
    secondary = Color(0xFF9FC1FF),
    deep = Color(0xFF274B8C),
    background = Color(0xFF0B1120),
    onBackground = Color(0xFFE6EDF8),
    muted = Color(0xFF9AA8C2)
)

private val SepiaColorScheme = lightSchemeOf(
    primary = Color(0xFF8B5E34),
    onPrimary = Color(0xFFFBF3E4),
    secondary = Color(0xFFA9793F),
    container = Color(0xFFE7D2AF),
    background = Color(0xFFF5ECD9),
    onBackground = Color(0xFF4A3520),
    muted = Color(0xFF7A6247)
)

private val OceanColorScheme = lightSchemeOf(
    primary = Color(0xFF0E7490),
    onPrimary = Color(0xFFF2FAFD),
    secondary = Color(0xFF2E9CB8),
    container = Color(0xFFB9E3EE),
    background = Color(0xFFF1F8FB),
    onBackground = Color(0xFF0C2F3C),
    muted = Color(0xFF4A7183)
)

private val ForestColorScheme = darkSchemeOf(
    primary = Color(0xFF5FD39B),
    onPrimary = Color(0xFF04150D),
    secondary = Color(0xFF8FE3BA),
    deep = Color(0xFF1F5C41),
    background = Color(0xFF0C1710),
    onBackground = Color(0xFFE4F1E9),
    muted = Color(0xFF93AC9F)
)

private val RoseColorScheme = lightSchemeOf(
    primary = Color(0xFFC2185B),
    onPrimary = Color(0xFFFFF5F8),
    secondary = Color(0xFFE05A87),
    container = Color(0xFFF7C6D6),
    background = Color(0xFFFFF5F7),
    onBackground = Color(0xFF3F1023),
    muted = Color(0xFF8A5468)
)

private val LavenderColorScheme = darkSchemeOf(
    primary = Color(0xFFB69CFF),
    onPrimary = Color(0xFF15102A),
    secondary = Color(0xFFCFBCFF),
    deep = Color(0xFF45348A),
    background = Color(0xFF141021),
    onBackground = Color(0xFFEAE4FA),
    muted = Color(0xFFA79FC0)
)

private val SlateColorScheme = lightSchemeOf(
    primary = Color(0xFF475569),
    onPrimary = Color(0xFFF8FAFC),
    secondary = Color(0xFF64748B),
    container = Color(0xFFCBD5E1),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF1E293B),
    muted = Color(0xFF64748B)
)

/**
 * 선택 가능한 화면 테마.
 * [id] 는 SharedPreferences("theme_mode") 에 저장하는 값이라 한 번 정하면 바꾸지 않는다.
 * (기존 저장값과의 호환 때문에 0=시스템, 1=라이트, 2=다크 는 그대로 둔다)
 *
 * @param swatch 설정 화면 동그라미에 칠할 메인 색상
 * @param swatchBackground 동그라미 바탕(테마 배경색)
 * @param swatchBackgroundAlt 값이 있으면 바탕을 반반으로 나눠 칠한다(시스템 테마용)
 */
enum class AppTheme(
    val id: Int,
    val label: String,
    val swatch: Color,
    val swatchBackground: Color,
    val isDark: Boolean,
    val swatchBackgroundAlt: Color? = null
) {
    SYSTEM(
        id = 0,
        label = "시스템",
        swatch = Color(0xFFD9B23C),
        swatchBackground = Color(0xFFFFFAF0),
        isDark = false,
        swatchBackgroundAlt = Color(0xFF0A0A0A)
    ),
    LIGHT(1, "라이트", Color(0xFFB8860B), Color(0xFFFFFAF0), false),
    DARK(2, "다크", Color(0xFFF3E85A), Color(0xFF0A0A0A), true),
    MIDNIGHT(3, "미드나잇", Color(0xFF7CA9FF), Color(0xFF0B1120), true),
    SEPIA(4, "세피아", Color(0xFF8B5E34), Color(0xFFF5ECD9), false),
    OCEAN(5, "오션", Color(0xFF0E7490), Color(0xFFF1F8FB), false),
    FOREST(6, "포레스트", Color(0xFF5FD39B), Color(0xFF0C1710), true),
    ROSE(7, "로즈", Color(0xFFC2185B), Color(0xFFFFF5F7), false),
    LAVENDER(8, "라벤더", Color(0xFFB69CFF), Color(0xFF141021), true),
    SLATE(9, "슬레이트", Color(0xFF475569), Color(0xFFF6F8FA), false);

    /** 시스템 테마일 때는 기기 설정을 따른다. */
    fun resolveDark(systemDark: Boolean): Boolean = if (this == SYSTEM) systemDark else isDark

    internal fun scheme(systemDark: Boolean): ColorScheme = when (this) {
        SYSTEM -> if (systemDark) DarkColorScheme else LightColorScheme
        LIGHT -> LightColorScheme
        DARK -> DarkColorScheme
        MIDNIGHT -> MidnightColorScheme
        SEPIA -> SepiaColorScheme
        OCEAN -> OceanColorScheme
        FOREST -> ForestColorScheme
        ROSE -> RoseColorScheme
        LAVENDER -> LavenderColorScheme
        SLATE -> SlateColorScheme
    }

    companion object {
        /** 저장된 값이 깨졌거나 예전 값이면 시스템으로 되돌린다. */
        fun fromId(id: Int): AppTheme = entries.firstOrNull { it.id == id } ?: SYSTEM
    }
}

@Composable
fun WowaGoodsProjectTheme(
    theme: AppTheme = AppTheme.SYSTEM,
    content: @Composable () -> Unit
) {
    val systemDark = isSystemInDarkTheme()
    val darkTheme = theme.resolveDark(systemDark)
    val colorScheme = theme.scheme(systemDark)
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

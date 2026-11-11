package com.jij6303.sidecoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

/** 라이트: 흰 카드 + 남색 강조. 아이콘처럼 남색 바탕 위 노란색은 히어로 카드에만 쓴다. */
private val LightColorScheme = lightColorScheme(
    primary = Navy800,
    onPrimary = Color.White,
    primaryContainer = Navy100,
    onPrimaryContainer = Navy900,
    secondary = Sun500,
    onSecondary = Navy900,
    secondaryContainer = Sun100,
    onSecondaryContainer = Color(0xFF5C3F00),
    tertiary = Sprout600,
    onTertiary = Color.White,
    background = Navy50,
    onBackground = Ink900,
    surface = Color.White,
    onSurface = Ink900,
    surfaceVariant = Ink100,
    onSurfaceVariant = Ink500,
    outline = Navy200,
    outlineVariant = Ink200,
    error = LightSignalColors.brake.onContainer,
)

/** 다크: 아이콘 바탕 남색을 그대로 화면 바탕으로, 강조는 아이콘 얼굴의 노란색. */
private val DarkColorScheme = darkColorScheme(
    primary = Sun500,
    onPrimary = Navy900,
    primaryContainer = Navy700,
    onPrimaryContainer = Navy100,
    secondary = Sun500,
    onSecondary = Navy900,
    secondaryContainer = Color(0xFF3A2C05),
    onSecondaryContainer = Color(0xFFFFE6A3),
    tertiary = Sprout400,
    onTertiary = Navy950,
    background = Navy950,
    onBackground = Navy50,
    surface = Navy900,
    onSurface = Navy50,
    surfaceVariant = Color(0xFF12305C),
    onSurfaceVariant = Navy300,
    outline = Navy600,
    outlineVariant = Color(0xFF173561),
    error = DarkSignalColors.brake.accent,
)

/** 모서리 크기. 아이콘의 둥근 사각형 느낌으로 큼직하게 통일한다. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(12.dp),
    small = RoundedCornerShape(16.dp),
    medium = RoundedCornerShape(24.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

/**
 * 앱 테마. 차량 화면에서 신호 색의 의미가 흔들리지 않도록 동적 색상(Material You)은 쓰지 않는다.
 */
@Composable
fun SideCoachTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalSignalColors provides if (darkTheme) DarkSignalColors else LightSignalColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColorScheme else LightColorScheme,
            typography = Typography,
            shapes = AppShapes,
            content = content,
        )
    }
}

object SideCoachTheme {
    val signal: SignalColors
        @Composable get() = LocalSignalColors.current
}

package com.jij6303.sidecoach.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

private val LightColorScheme = lightColorScheme(
    primary = Blue700,
    onPrimary = Ink50,
    primaryContainer = LightSignalColors.neutral.container,
    onPrimaryContainer = LightSignalColors.neutral.onContainer,
    background = Ink100,
    onBackground = Ink900,
    surface = Color.White,
    onSurface = Ink900,
    surfaceVariant = Ink100,
    onSurfaceVariant = Ink700,
    outline = Ink200,
    error = LightSignalColors.brake.onContainer,
)

private val DarkColorScheme = darkColorScheme(
    primary = Blue300,
    onPrimary = Ink900,
    primaryContainer = DarkSignalColors.neutral.container,
    onPrimaryContainer = DarkSignalColors.neutral.onContainer,
    background = Ink900,
    onBackground = Ink50,
    surface = Ink800,
    onSurface = Ink50,
    surfaceVariant = Ink700,
    onSurfaceVariant = Ink400,
    outline = Ink700,
    error = DarkSignalColors.brake.accent,
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
            content = content,
        )
    }
}

object SideCoachTheme {
    val signal: SignalColors
        @Composable get() = LocalSignalColors.current
}

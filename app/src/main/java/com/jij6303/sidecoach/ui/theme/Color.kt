package com.jij6303.sidecoach.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 브랜드 색: 앱 아이콘(S2)의 남색 바탕, 노란 핸들 얼굴, 새싹 초록
val Navy950 = Color(0xFF051327)
val Navy900 = Color(0xFF071C3A)
val Navy800 = Color(0xFF0B2A55) // 아이콘 바탕
val Navy700 = Color(0xFF163A6E)
val Navy600 = Color(0xFF24518F)
val Navy500 = Color(0xFF3D6AAE)
val Navy300 = Color(0xFF8FB2E8)
val Navy200 = Color(0xFFC9D8F0)
val Navy100 = Color(0xFFE4EBF6)
val Navy50 = Color(0xFFF3F6FB)

val Sun500 = Color(0xFFFFC845) // 아이콘 얼굴
val Sun600 = Color(0xFFF2B01E)
val Sun100 = Color(0xFFFFF3D3)

val Sprout600 = Color(0xFF3CB371) // 새싹 줄기
val Sprout400 = Color(0xFF5BD68A)
val Sprout200 = Color(0xFF9BE8B5)

// 글자·선용 회색 (남색 쪽으로 살짝 기운 회색)
val Ink900 = Color(0xFF0E1A2E)
val Ink700 = Color(0xFF3A4A63)
val Ink500 = Color(0xFF5B6B84)
val Ink400 = Color(0xFF8C9AB0)
val Ink200 = Color(0xFFDCE3EE)
val Ink100 = Color(0xFFEDF1F7)

/**
 * 신호·코칭 유형별 색. 배경([container])과 그 위 글자([onContainer])는 대비 4.5:1 이상이 되도록 짝지었다.
 * [accent]는 아이콘·막대처럼 큰 면적에 쓰는 강조색.
 */
@Immutable
data class SignalTone(val accent: Color, val container: Color, val onContainer: Color)

@Immutable
data class SignalColors(
    val turn: SignalTone,
    val brake: SignalTone,
    val praise: SignalTone,
    val neutral: SignalTone,
    val inactive: Color,
)

val LightSignalColors = SignalColors(
    turn = SignalTone(accent = Sun600, container = Sun100, onContainer = Color(0xFF5C3F00)),
    brake = SignalTone(accent = Color(0xFFEF4444), container = Color(0xFFFEE2E2), onContainer = Color(0xFF7F1D1D)),
    praise = SignalTone(accent = Sprout600, container = Color(0xFFE2F6EA), onContainer = Color(0xFF0F4A2A)),
    neutral = SignalTone(accent = Navy600, container = Navy100, onContainer = Navy900),
    inactive = Ink200,
)

val DarkSignalColors = SignalColors(
    turn = SignalTone(accent = Sun500, container = Color(0xFF3A2C05), onContainer = Color(0xFFFFE6A3)),
    brake = SignalTone(accent = Color(0xFFF87171), container = Color(0xFF450A0A), onContainer = Color(0xFFFECACA)),
    praise = SignalTone(accent = Sprout400, container = Color(0xFF0A3320), onContainer = Sprout200),
    neutral = SignalTone(accent = Navy300, container = Navy700, onContainer = Navy100),
    inactive = Color(0xFF2A4470),
)

val LocalSignalColors = staticCompositionLocalOf { LightSignalColors }

package com.jij6303.sidecoach.ui.theme

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

// 기본 색 (차분한 파랑 계열)
val Blue700 = Color(0xFF1D4ED8)
val Blue500 = Color(0xFF3B82F6)
val Blue300 = Color(0xFF93C5FD)

val Ink900 = Color(0xFF0F172A)
val Ink800 = Color(0xFF1E293B)
val Ink700 = Color(0xFF334155)
val Ink500 = Color(0xFF64748B)
val Ink400 = Color(0xFF94A3B8)
val Ink200 = Color(0xFFE2E8F0)
val Ink100 = Color(0xFFF1F5F9)
val Ink50 = Color(0xFFF8FAFC)

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
    turn = SignalTone(accent = Color(0xFFF59E0B), container = Color(0xFFFEF3C7), onContainer = Color(0xFF78350F)),
    brake = SignalTone(accent = Color(0xFFEF4444), container = Color(0xFFFEE2E2), onContainer = Color(0xFF7F1D1D)),
    praise = SignalTone(accent = Color(0xFF22C55E), container = Color(0xFFDCFCE7), onContainer = Color(0xFF14532D)),
    neutral = SignalTone(accent = Blue500, container = Color(0xFFDBEAFE), onContainer = Color(0xFF1E3A8A)),
    inactive = Ink200,
)

val DarkSignalColors = SignalColors(
    turn = SignalTone(accent = Color(0xFFFBBF24), container = Color(0xFF451A03), onContainer = Color(0xFFFDE68A)),
    brake = SignalTone(accent = Color(0xFFF87171), container = Color(0xFF450A0A), onContainer = Color(0xFFFECACA)),
    praise = SignalTone(accent = Color(0xFF4ADE80), container = Color(0xFF052E16), onContainer = Color(0xFFBBF7D0)),
    neutral = SignalTone(accent = Blue300, container = Color(0xFF172554), onContainer = Color(0xFFBFDBFE)),
    inactive = Ink700,
)

val LocalSignalColors = staticCompositionLocalOf { LightSignalColors }

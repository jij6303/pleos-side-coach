package com.jij6303.sidecoach.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * 차량 화면용 글자 크기. 가이드 최소 12sp보다 넉넉하게 잡고, 줄간격은 약 1.5배.
 * 서체는 가이드 권장 Asta Sans 적용 전까지 기본 서체를 쓴다.
 */
val Typography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 88.sp, lineHeight = 96.sp),
    displayMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 56.sp, lineHeight = 64.sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 40.sp, lineHeight = 56.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Bold, fontSize = 32.sp, lineHeight = 44.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 26.sp, lineHeight = 36.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 22.sp, lineHeight = 32.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 22.sp, lineHeight = 32.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 18.sp, lineHeight = 26.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 18.sp, lineHeight = 24.sp),
)

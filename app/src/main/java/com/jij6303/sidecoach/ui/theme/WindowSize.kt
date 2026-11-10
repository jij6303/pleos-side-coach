package com.jij6303.sidecoach.ui.theme

/**
 * 2열 배치로 바꾸는 앱 창 너비 (dp).
 *
 * Pleos 화면은 고정 주행 영역 + 앱 창 1개 또는 2개다. 에뮬레이터 실측(#33):
 * 창 1개 = 841×1210dp, 창 2개 = 1682×1210dp (160dpi).
 * 창 1개에서는 1열, 창 2개에서는 2열로 그린다.
 */
const val WIDE_MIN_DP = 1200

package com.jij6303.sidecoach.domain.situation

/** 미리 알려줄 도로 상황 (concept ①). */
enum class SituationType(val label: String) {
    ROUNDABOUT("회전교차로"),
    SCHOOL_ZONE("어린이보호구역"),
    UNPROTECTED_LEFT("비보호좌회전"),
    MERGE("합류"),
}

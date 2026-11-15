package com.jij6303.sidecoach.data.navi

/**
 * 내비 경로에서 알아낸 도로 상황의 종류 (concept ②).
 * 언제 예고할지는 코칭 정책이라 feature/coach/situation/PreviewWindow가 정한다.
 */
enum class SituationType(val label: String) {
    // 규칙 안내 (시연 경로)
    ROUNDABOUT("회전교차로"),
    SCHOOL_ZONE("어린이보호구역"),
    UNPROTECTED_LEFT("비보호좌회전"),
    MERGE("합류"),

    // 차로 준비. 내비 TBT의 다음 회전 종류로 정한다
    LANE_LEFT_TURN("좌회전"),
    LANE_RIGHT_TURN("우회전"),
    LANE_UTURN("유턴"),
    LANE_KEEP_LEFT("왼쪽 길"),
    LANE_KEEP_RIGHT("오른쪽 길"),
    LANE_STRAIGHT("직진"),

    // 연속 회전. 앞 회전 바로 뒤에 이어지는 회전
    CHAIN_LEFT_TURN("이어서 좌회전"),
    CHAIN_RIGHT_TURN("이어서 우회전"),
    CHAIN_UTURN("이어서 유턴"),
    CHAIN_KEEP_LEFT("이어서 왼쪽 길"),
    CHAIN_KEEP_RIGHT("이어서 오른쪽 길"),
}

package com.jij6303.sidecoach.data.navi

/**
 * 미리 알려줄 도로 상황 (concept ①).
 * [previewFarM]~[previewNearM] 구간에서 한 번 예고한다. 차로 안내는 차선을 옮길 시간이 필요해 더 일찍 말한다.
 */
enum class SituationType(
    val label: String,
    val previewFarM: Float = 300f,
    val previewNearM: Float = 150f,
) {
    ROUNDABOUT("회전교차로"),
    SCHOOL_ZONE("어린이보호구역"),
    UNPROTECTED_LEFT("비보호좌회전"),
    MERGE("합류"),

    // 차로 사전 안내 (#65). 내비 TBT의 다음 회전 종류로 정한다. 💭 500~200m
    LANE_LEFT_TURN("좌회전", LANE_FAR_M, LANE_NEAR_M),
    LANE_RIGHT_TURN("우회전", LANE_FAR_M, LANE_NEAR_M),
    LANE_UTURN("유턴", LANE_FAR_M, LANE_NEAR_M),
    LANE_KEEP_LEFT("왼쪽 길", LANE_FAR_M, LANE_NEAR_M),
    LANE_KEEP_RIGHT("오른쪽 길", LANE_FAR_M, LANE_NEAR_M),
    LANE_STRAIGHT("직진", LANE_FAR_M, LANE_NEAR_M),

    // 연속 회전 (#67). 앞 회전 바로 뒤에 이어지는 회전을, **앞 회전까지** 150~30m에서 미리 알린다. 💭
    CHAIN_LEFT_TURN("이어서 좌회전", CHAIN_FAR_M, CHAIN_NEAR_M),
    CHAIN_RIGHT_TURN("이어서 우회전", CHAIN_FAR_M, CHAIN_NEAR_M),
    CHAIN_UTURN("이어서 유턴", CHAIN_FAR_M, CHAIN_NEAR_M),
    CHAIN_KEEP_LEFT("이어서 왼쪽 길", CHAIN_FAR_M, CHAIN_NEAR_M),
    CHAIN_KEEP_RIGHT("이어서 오른쪽 길", CHAIN_FAR_M, CHAIN_NEAR_M),
}

private const val LANE_FAR_M = 500f
private const val LANE_NEAR_M = 200f
private const val CHAIN_FAR_M = 150f
private const val CHAIN_NEAR_M = 30f

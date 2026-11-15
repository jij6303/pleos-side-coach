package com.jij6303.sidecoach.data.navi


/**
 * NaviHelper 상수 이름 → 앱 상황 유형 (#65). SDK enum의 `name`을 받아 SDK 없이 테스트할 수 있게 한다.
 * 상수 목록은 navi-data 3.0.4 바이트코드로 확인(#63, dev-architecture 0.1).
 */
object TbtMapping {

    /** `ai.pleos.playground.navi.constants.TurnType` 이름. 안내하지 않는 유형(터널, 톨게이트, 도착 등)은 null. */
    fun turn(turnTypeName: String): SituationType? = when {
        turnTypeName.startsWith("TURN_ENTER_ROUNDABOUT") -> SituationType.ROUNDABOUT
        else -> when (turnTypeName) {
            "TURN_LEFT", "TURN_SHARP_L" -> SituationType.LANE_LEFT_TURN
            "TURN_RIGHT", "TURN_SHARP_R" -> SituationType.LANE_RIGHT_TURN
            "TURN_UTURN" -> SituationType.LANE_UTURN
            "TURN_KEEP_L", "TURN_ENTER_IC_L", "TURN_EXIT_IC_L" -> SituationType.LANE_KEEP_LEFT
            "TURN_KEEP_R", "TURN_ENTER_IC_R", "TURN_EXIT_IC_R" -> SituationType.LANE_KEEP_RIGHT
            "TURN_STRAIGHT" -> SituationType.LANE_STRAIGHT
            else -> null
        }
    }

    /** 앞 회전 바로 뒤에 이어지는 회전 [next]를 미리 알릴 유형 (#67). 직진·회전교차로 등은 null. */
    fun chain(next: SituationType): SituationType? = when (next) {
        SituationType.LANE_LEFT_TURN -> SituationType.CHAIN_LEFT_TURN
        SituationType.LANE_RIGHT_TURN -> SituationType.CHAIN_RIGHT_TURN
        SituationType.LANE_UTURN -> SituationType.CHAIN_UTURN
        SituationType.LANE_KEEP_LEFT -> SituationType.CHAIN_KEEP_LEFT
        SituationType.LANE_KEEP_RIGHT -> SituationType.CHAIN_KEEP_RIGHT
        else -> null
    }

    /** TBT 한 항목. [turnTypeName]은 SDK `TurnType`의 이름, [distanceM]은 이전 지점부터의 구간 거리(#63 실측). */
    data class Segment(val turnTypeName: String, val distanceM: Double, val description: String)

    /**
     * TBT 목록 → 경로. 구간 거리를 누적해 지점 위치를 만들고, 안내하지 않는 유형은 뺀다.
     * 키에 받은 시각을 넣는다. 세션 복원으로 "예고한 지점"이 남아 있어도 새 목록의 같은 순번과 겹치지 않게 (#67 실측).
     */
    fun route(segments: List<Segment>, receivedAtMs: Long): NaviRoute {
        var at = 0.0
        val points = segments.mapIndexedNotNull { i, seg ->
            at += seg.distanceM
            turn(seg.turnTypeName)?.let { RoutePoint("tbt-$receivedAtMs-$i", it, at.toFloat()) }
        }
        return NaviRoute(id = "tbt-$receivedAtMs", points = points)
    }
}

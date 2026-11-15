package com.jij6303.sidecoach.data.navi

import com.jij6303.sidecoach.domain.situation.SituationType

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

    /** `ai.pleos.playground.navi.constants.CautionPointType` 이름. */
    fun caution(cautionTypeName: String): SituationType? = when (cautionTypeName) {
        "SCHOOL_ZONE" -> SituationType.SCHOOL_ZONE
        else -> null
    }
}

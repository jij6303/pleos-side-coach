package com.jij6303.sidecoach.feature.coach

import com.jij6303.sidecoach.R
import com.jij6303.sidecoach.data.navi.SituationType

/** 실시간 코칭·상황 예고·차로 확인 문구 → 녹음 클립. 문구가 바뀌면 클립도 다시 만들어야 한다. */
object CoachClips {
    val all: Map<String, Int> = mapOf(
        MessageCatalog.blinkerOmission() to R.raw.coach_blinker_omission,
        MessageCatalog.hardBrake() to R.raw.coach_hard_brake,
        MessageCatalog.praise() to R.raw.coach_praise,
        MessageCatalog.blinkerLeftOn() to R.raw.coach_blinker_left_on,
        MessageCatalog.turnSpeed() to R.raw.coach_turn_speed,
        MessageCatalog.gearShiftMoving() to R.raw.coach_gear_shift_moving,
        MessageCatalog.preview(SituationType.ROUNDABOUT) to R.raw.preview_roundabout,
        MessageCatalog.preview(SituationType.SCHOOL_ZONE) to R.raw.preview_school_zone,
        MessageCatalog.preview(SituationType.UNPROTECTED_LEFT) to R.raw.preview_unprotected_left,
        MessageCatalog.preview(SituationType.MERGE) to R.raw.preview_merge,
        MessageCatalog.preview(SituationType.LANE_LEFT_TURN) to R.raw.lane_left_turn,
        MessageCatalog.preview(SituationType.LANE_RIGHT_TURN) to R.raw.lane_right_turn,
        MessageCatalog.preview(SituationType.LANE_UTURN) to R.raw.lane_uturn,
        MessageCatalog.preview(SituationType.LANE_KEEP_LEFT) to R.raw.lane_keep_left,
        MessageCatalog.preview(SituationType.LANE_KEEP_RIGHT) to R.raw.lane_keep_right,
        MessageCatalog.preview(SituationType.LANE_STRAIGHT) to R.raw.lane_straight,
        MessageCatalog.preview(SituationType.CHAIN_LEFT_TURN) to R.raw.chain_left_turn,
        MessageCatalog.preview(SituationType.CHAIN_RIGHT_TURN) to R.raw.chain_right_turn,
        MessageCatalog.preview(SituationType.CHAIN_UTURN) to R.raw.chain_uturn,
        MessageCatalog.preview(SituationType.CHAIN_KEEP_LEFT) to R.raw.chain_keep_left,
        MessageCatalog.preview(SituationType.CHAIN_KEEP_RIGHT) to R.raw.chain_keep_right,
        // 지금 차로를 아는 경우 (#69)
        MessageCatalog.preview(SituationType.LANE_LEFT_TURN, 1) to R.raw.lane_left_l1,
        MessageCatalog.preview(SituationType.LANE_LEFT_TURN, 2) to R.raw.lane_left_l2,
        MessageCatalog.preview(SituationType.LANE_LEFT_TURN, 3) to R.raw.lane_left_l3,
        MessageCatalog.preview(SituationType.LANE_LEFT_TURN, 4) to R.raw.lane_left_l4,
        MessageCatalog.preview(SituationType.LANE_UTURN, 1) to R.raw.lane_uturn_l1,
        MessageCatalog.preview(SituationType.LANE_UTURN, 2) to R.raw.lane_uturn_l2,
        MessageCatalog.preview(SituationType.LANE_UTURN, 3) to R.raw.lane_uturn_l3,
        MessageCatalog.preview(SituationType.LANE_UTURN, 4) to R.raw.lane_uturn_l4,
        MessageCatalog.preview(SituationType.LANE_STRAIGHT, 1) to R.raw.lane_straight_l1,
        MessageCatalog.preview(SituationType.LANE_STRAIGHT, 2) to R.raw.lane_straight_l2,
        MessageCatalog.preview(SituationType.LANE_KEEP_LEFT, 1) to R.raw.lane_keep_left_l1,
        MessageCatalog.laneSet(1) to R.raw.lane_set_1,
        MessageCatalog.laneSet(2) to R.raw.lane_set_2,
        MessageCatalog.laneSet(3) to R.raw.lane_set_3,
        MessageCatalog.laneSet(4) to R.raw.lane_set_4,
    )
}

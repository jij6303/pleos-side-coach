package com.jij6303.sidecoach.feature.coach.situation

import com.jij6303.sidecoach.data.navi.SituationType

/**
 * 상황을 몇 m 전에 예고할지 (코칭 정책). 남은 거리가 [nearM]~[farM]일 때 한 번 말한다.
 * [nearM]보다 가까워진 뒤에 처음 알게 된 지점은 늦었으므로 말하지 않는다.
 */
data class PreviewWindow(val farM: Float, val nearM: Float) {

    operator fun contains(distanceM: Float): Boolean = distanceM in nearM..farM

    companion object {
        /** 💭 규칙 안내: 통행 규칙을 들을 시간. */
        val RULE = PreviewWindow(farM = 300f, nearM = 150f)

        /** 💭 차로 준비: 차선을 옮길 시간이 필요해 더 일찍 말한다. */
        val LANE = PreviewWindow(farM = 500f, nearM = 200f)

        /** 💭 연속 회전: 앞 회전 바로 전에, 돌 때 들어갈 차로를 말한다. */
        val CHAIN = PreviewWindow(farM = 150f, nearM = 30f)

        fun of(type: SituationType): PreviewWindow = when (type) {
            SituationType.ROUNDABOUT,
            SituationType.SCHOOL_ZONE,
            SituationType.UNPROTECTED_LEFT,
            SituationType.MERGE -> RULE

            SituationType.LANE_LEFT_TURN,
            SituationType.LANE_RIGHT_TURN,
            SituationType.LANE_UTURN,
            SituationType.LANE_KEEP_LEFT,
            SituationType.LANE_KEEP_RIGHT,
            SituationType.LANE_STRAIGHT -> LANE

            SituationType.CHAIN_LEFT_TURN,
            SituationType.CHAIN_RIGHT_TURN,
            SituationType.CHAIN_UTURN,
            SituationType.CHAIN_KEEP_LEFT,
            SituationType.CHAIN_KEEP_RIGHT -> CHAIN
        }
    }
}

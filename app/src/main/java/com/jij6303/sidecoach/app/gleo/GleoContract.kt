package com.jij6303.sidecoach.app.gleo

/** Gleo Interface 상수 (dev-architecture 7.2, assets/schema.json과 이름이 같아야 함). */
object GleoContract {
    const val EXTRA_PARAM = "ACTION_PARAM"
    const val ACTION_RESPONSE = "ai.pleos.playground.intent.action.APP_ACTION_RESPONSE"
    const val EXTRA_PACKAGE = "packageName"
    const val EXTRA_RESPONSE = "response"

    const val START_DRIVING_COACH = "START_DRIVING_COACH"
    const val ASK_CAR_TIP = "ASK_CAR_TIP"
    const val SET_CURRENT_LANE = "SET_CURRENT_LANE"

    const val PARAM_TOPIC = "topic"
    const val PARAM_LANE_NUMBER = "laneNumber"

    /** Gleo는 이 시간 안에 응답이 없으면 취소한다. */
    const val RESPONSE_TIMEOUT_MS = 7_000L
}

package com.jij6303.sidecoach.gleo

import com.jij6303.sidecoach.domain.tips.CarTip
import com.jij6303.sidecoach.ui.UiMode

/**
 * Gleo 명령 처리 (dev-architecture 7.2). Android에 의존하지 않도록 동작은 람다로 받는다.
 * 처리하지 않는 액션이면 null.
 */
class GleoActionHandler(
    private val startCoaching: () -> Unit,
    /** 차량 상태가 들어온 뒤의 화면 모드. 끝내 모르면 null. */
    private val currentMode: suspend () -> UiMode?,
    /** 도움말을 음성으로 말한다. */
    private val speakTip: (CarTip) -> Unit = {},
    /** 정차 중일 때 도움말 화면을 연다. */
    private val openTip: (CarTip) -> Unit = {},
    /** 운전자가 알려준 차로를 기억하고 음성으로 확인한다(#69). 범위 밖이면 false. */
    private val setLane: (Int) -> Boolean = { false },
) {
    suspend fun handle(action: String?, params: Map<String, String>): GleoResponse? = when (action) {
        GleoContract.START_DRIVING_COACH -> startCoach()
        GleoContract.ASK_CAR_TIP -> askTip(params)
        GleoContract.SET_CURRENT_LANE -> setCurrentLane(params)
        else -> null
    }

    private fun setCurrentLane(params: Map<String, String>): GleoResponse {
        // Gleo가 숫자를 2.0처럼 보낼 수도 있어 소수도 받는다 (💭)
        val lane = params[GleoContract.PARAM_LANE_NUMBER]?.toDoubleOrNull()?.takeIf { it % 1.0 == 0.0 }?.toInt()
        if (lane == null || !setLane(lane)) {
            return GleoResponse(
                functionName = GleoContract.SET_CURRENT_LANE,
                status = GleoResponse.Status.FAIL,
                message = "Please tell me a lane number from 1 to 4, counting from the leftmost lane.",
            )
        }
        // 앱이 "N차로로 기억할게요"라고 직접 말하므로 Gleo는 읽지 않게 한다 (이중 낭독 방지)
        return GleoResponse(
            functionName = GleoContract.SET_CURRENT_LANE,
            status = GleoResponse.Status.SUCCESS,
            message = "The app will remember that you are in lane $lane.",
            mode = GleoResponse.Mode.SILENT,
            data = """[{"laneNumber":$lane}]""",
        )
    }

    private fun startCoach(): GleoResponse {
        startCoaching()
        return GleoResponse(
            functionName = GleoContract.START_DRIVING_COACH,
            status = GleoResponse.Status.SUCCESS,
            message = "The driving coach session has started.",
        )
    }

    private suspend fun askTip(params: Map<String, String>): GleoResponse {
        val tip = CarTip.entries.firstOrNull { it.name == params[GleoContract.PARAM_TOPIC] }
            ?: return GleoResponse(
                functionName = GleoContract.ASK_CAR_TIP,
                status = GleoResponse.Status.FAIL,
                message = "There is no help for that topic yet.",
            )
        // 주행 중에도 음성으로 답한다. 화면은 정차 중일 때만 연다.
        speakTip(tip)
        if (currentMode() == UiMode.PARKED_FULL) openTip(tip)
        // 앱이 직접 말하므로 Gleo는 읽지 않게 한다 (dev-architecture 6.2, 이중 낭독 방지)
        return GleoResponse(
            functionName = GleoContract.ASK_CAR_TIP,
            status = GleoResponse.Status.SUCCESS,
            message = "The app is explaining how to use that vehicle feature.",
            mode = GleoResponse.Mode.SILENT,
            data = """[{"topic":"${tip.name}"}]""",
        )
    }
}

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
    private val startTutorial: () -> Unit,
    /** 도움말을 음성으로 말한다. */
    private val speakTip: (CarTip) -> Unit = {},
    /** 정차 중일 때 도움말 화면을 연다. */
    private val openTip: (CarTip) -> Unit = {},
) {
    suspend fun handle(action: String?, params: Map<String, String>): GleoResponse? = when (action) {
        GleoContract.START_DRIVING_COACH -> startCoach()
        GleoContract.RUN_VEHICLE_TUTORIAL -> runTutorial()
        GleoContract.ASK_CAR_TIP -> askTip(params)
        else -> null
    }

    private fun startCoach(): GleoResponse {
        startCoaching()
        return GleoResponse(
            functionName = GleoContract.START_DRIVING_COACH,
            status = GleoResponse.Status.SUCCESS,
            message = "The driving coach session has started.",
        )
    }

    private suspend fun runTutorial(): GleoResponse {
        // 주행 중에는 튜토리얼에 들어갈 수 없다 (dev-architecture 5.2)
        if (currentMode() != UiMode.PARKED_FULL) {
            return GleoResponse(
                functionName = GleoContract.RUN_VEHICLE_TUTORIAL,
                status = GleoResponse.Status.FAIL,
                message = "The tutorial is available only while the vehicle is parked.",
            )
        }
        startTutorial()
        return GleoResponse(
            functionName = GleoContract.RUN_VEHICLE_TUTORIAL,
            status = GleoResponse.Status.SUCCESS,
            message = "The vehicle tutorial has been opened.",
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

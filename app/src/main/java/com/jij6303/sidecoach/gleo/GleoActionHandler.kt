package com.jij6303.sidecoach.gleo

import com.jij6303.sidecoach.domain.coach.ExperienceLevel
import com.jij6303.sidecoach.ui.UiMode

/**
 * Gleo 명령 처리 (dev-architecture 7.2). Android에 의존하지 않도록 동작은 람다로 받는다.
 * 처리하지 않는 액션이면 null.
 */
class GleoActionHandler(
    private val startCoaching: (ExperienceLevel) -> Unit,
    /** 차량 상태가 들어온 뒤의 화면 모드. 끝내 모르면 null. */
    private val currentMode: suspend () -> UiMode?,
    private val startTutorial: () -> Unit,
) {
    suspend fun handle(action: String?, params: Map<String, String>): GleoResponse? = when (action) {
        GleoContract.START_DRIVING_COACH -> startCoach(params)
        GleoContract.RUN_VEHICLE_TUTORIAL -> runTutorial()
        else -> null
    }

    private fun startCoach(params: Map<String, String>): GleoResponse {
        val raw = params[GleoContract.PARAM_EXPERIENCE_LEVEL]
        val level = ExperienceLevel.entries.firstOrNull { it.name == raw } ?: ExperienceLevel.BEGINNER
        startCoaching(level)
        return GleoResponse(
            functionName = GleoContract.START_DRIVING_COACH,
            status = GleoResponse.Status.SUCCESS,
            message = "The driving coach session has started.",
            data = """[{"experienceLevel":"${level.name}"}]""",
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
}

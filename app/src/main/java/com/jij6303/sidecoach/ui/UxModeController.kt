package com.jij6303.sidecoach.ui

import com.jij6303.sidecoach.data.ux.UxState
import com.jij6303.sidecoach.data.vehicle.GearPosition
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged

/** 주행 규제 상태별 화면 모드 (dev-architecture 5.1). */
enum class UiMode {
    /** 정차 + P단: 전체 UI (성적표, 튜토리얼, 설정). */
    PARKED_FULL,

    /** 규제는 없지만 P단이 아님 (신호 대기 등): 간단한 상태 화면. */
    IDLE_LIMITED,

    /** 주행 규제 중: 큰 코칭 카드 1장, 음성 위주. */
    DRIVING_VOICE,
}

/** 규제 + 기어 → 화면 모드. */
class UxModeController(ux: Flow<UxState>, gear: Flow<GearPosition>) {

    val mode: Flow<UiMode> = combine(ux, gear.distinctUntilChanged(), ::decide).distinctUntilChanged()

    companion object {
        fun decide(ux: UxState, gear: GearPosition): UiMode = when {
            ux.requiresDistractionOptimization -> UiMode.DRIVING_VOICE
            gear == GearPosition.PARK -> UiMode.PARKED_FULL
            else -> UiMode.IDLE_LIMITED
        }
    }
}

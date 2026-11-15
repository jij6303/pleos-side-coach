package com.jij6303.sidecoach.data.ux

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * 주행 규제 상태 (AAOS CarUxRestrictions를 감싼 순수 Kotlin 모델).
 * [activeRestrictions]는 CarUxRestrictions 비트 플래그.
 */
data class UxState(
    val requiresDistractionOptimization: Boolean,
    val activeRestrictions: Int,
    /** false면 규제 정보를 받지 못한 상태. 안전하게 주행 중으로 취급한다. */
    val available: Boolean = true,
) {
    companion object {
        /** Car 서비스에 연결하지 못했을 때. 가장 엄격한 상태로 둔다. */
        val Unavailable = UxState(requiresDistractionOptimization = true, activeRestrictions = 0x1ff, available = false)
        val Parked = UxState(requiresDistractionOptimization = false, activeRestrictions = 0)
    }
}

interface UxRestrictionSource {
    /** collect하면 현재 값을 먼저 내보내고, 바뀔 때마다 내보낸다. */
    val state: Flow<UxState>
}

/** 테스트·데모용. [set]으로 규제를 바꾼다. */
class FakeUxRestrictionSource(initial: UxState = UxState.Parked) : UxRestrictionSource {
    private val flow = MutableStateFlow(initial)
    override val state: Flow<UxState> = flow
    fun set(value: UxState) {
        flow.value = value
    }
}

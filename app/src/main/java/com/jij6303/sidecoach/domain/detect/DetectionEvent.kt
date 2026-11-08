package com.jij6303.sidecoach.domain.detect

import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.domain.DrivingSnapshot

/** 디텍터가 판정한 사건. */
sealed interface DetectionEvent {
    val timestampMs: Long

    /** 차선 변경으로 보이는 조향 패턴이 끝남. [signalled]가 false면 깜빡이 누락. */
    data class LaneChange(
        val direction: TurnSignalState,
        val signalled: Boolean,
        override val timestampMs: Long,
    ) : DetectionEvent

    /** 급제동. [decelKmhPerSec]는 1초 기준 감속도. */
    data class HardBrake(
        val decelKmhPerSec: Float,
        val fromSpeedKmh: Float,
        override val timestampMs: Long,
    ) : DetectionEvent
}

/** 스냅샷을 하나씩 받아 사건을 판정한다. 상태를 가질 수 있다. */
interface Detector {
    fun onSnapshot(s: DrivingSnapshot): DetectionEvent?
    fun reset()
}

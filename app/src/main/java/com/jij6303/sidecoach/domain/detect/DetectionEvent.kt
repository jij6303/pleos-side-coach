package com.jij6303.sidecoach.domain.detect

import com.jij6303.sidecoach.data.vehicle.GearPosition
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

    /** 조향을 마친 뒤에도 깜빡이를 켠 채 계속 달림 (#49). */
    data class BlinkerLeftOn(
        val direction: TurnSignalState,
        override val timestampMs: Long,
    ) : DetectionEvent

    /** 핸들을 크게 꺾은 채 빠르게 달림 (#49). */
    data class TurnTooFast(
        val speedKmh: Float,
        val steeringDeg: Float,
        override val timestampMs: Long,
    ) : DetectionEvent

    /** 움직이는 중 기어 변경 (#49). [speedKmh]는 변속 직전 차속. */
    data class GearShiftWhileMoving(
        val from: GearPosition,
        val to: GearPosition,
        val speedKmh: Float,
        override val timestampMs: Long,
    ) : DetectionEvent
}

/** 스냅샷을 하나씩 받아 사건을 판정한다. 상태를 가질 수 있다. */
interface Detector {
    fun onSnapshot(s: DrivingSnapshot): DetectionEvent?
    fun reset()
}

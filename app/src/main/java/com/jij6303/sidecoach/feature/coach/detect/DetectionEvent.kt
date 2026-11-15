package com.jij6303.sidecoach.feature.coach.detect

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.feature.coach.CoachMessage
import com.jij6303.sidecoach.feature.coach.CoachType
import com.jij6303.sidecoach.feature.coach.MessageCatalog
import com.jij6303.sidecoach.state.DrivingSnapshot

/** 디텍터가 판정한 사건. 각 사건은 자기를 교정하는 코칭 메시지를 안다. */
sealed interface DetectionEvent {
    val timestampMs: Long

    /** 이 사건을 교정하는 메시지. 교정할 것이 없으면(깜빡이를 켠 차선 변경) null. */
    fun correction(): CoachMessage?

    /** 차선 변경으로 보이는 조향 패턴이 끝남. [signalled]가 false면 깜빡이 누락. */
    data class LaneChange(
        val direction: TurnSignalState,
        val signalled: Boolean,
        override val timestampMs: Long,
    ) : DetectionEvent {
        override fun correction() = if (signalled) null else CoachMessage(CoachType.BLINKER_OMISSION, MessageCatalog.blinkerOmission(), timestampMs)
    }

    /** 급제동. [decelKmhPerSec]는 1초 기준 감속도. */
    data class HardBrake(
        val decelKmhPerSec: Float,
        val fromSpeedKmh: Float,
        override val timestampMs: Long,
    ) : DetectionEvent {
        override fun correction() = CoachMessage(CoachType.HARD_BRAKE, MessageCatalog.hardBrake(), timestampMs)
    }

    /** 조향을 마친 뒤에도 깜빡이를 켠 채 계속 달림 (#49). */
    data class BlinkerLeftOn(
        val direction: TurnSignalState,
        override val timestampMs: Long,
    ) : DetectionEvent {
        override fun correction() = CoachMessage(CoachType.BLINKER_LEFT_ON, MessageCatalog.blinkerLeftOn(), timestampMs)
    }

    /** 핸들을 크게 꺾은 채 빠르게 달림 (#49). */
    data class TurnTooFast(
        val speedKmh: Float,
        val steeringDeg: Float,
        override val timestampMs: Long,
    ) : DetectionEvent {
        override fun correction() = CoachMessage(CoachType.TURN_SPEED, MessageCatalog.turnSpeed(), timestampMs)
    }

    /** 움직이는 중 기어 변경 (#49). [speedKmh]는 변속 직전 차속. */
    data class GearShiftWhileMoving(
        val from: GearPosition,
        val to: GearPosition,
        val speedKmh: Float,
        override val timestampMs: Long,
    ) : DetectionEvent {
        override fun correction() = CoachMessage(CoachType.GEAR_SHIFT_MOVING, MessageCatalog.gearShiftMoving(), timestampMs)
    }
}

/** 스냅샷을 하나씩 받아 사건을 판정한다. 상태를 가질 수 있다. */
interface Detector {
    fun onSnapshot(s: DrivingSnapshot): DetectionEvent?
    fun reset()
}

package com.jij6303.sidecoach.feature.coach

import com.jij6303.sidecoach.feature.coach.detect.DetectionEvent

/**
 * 판정 결과 → 말할 코칭 메시지. 문구는 [MessageCatalog]에서 가져온다.
 * 깜빡이를 켠 차선 변경은 교정할 것이 없어서, 칭찬할 때([praise])만 메시지가 된다.
 */
object CoachMessageFactory {

    fun from(event: DetectionEvent, praise: Boolean = false): CoachMessage? {
        val ts = event.timestampMs
        return when (event) {
            is DetectionEvent.LaneChange -> when {
                !event.signalled -> CoachMessage(CoachType.BLINKER_OMISSION, MessageCatalog.blinkerOmission(), ts)
                praise -> CoachMessage(CoachType.PRAISE, MessageCatalog.praise(), ts)
                else -> null
            }
            is DetectionEvent.HardBrake -> CoachMessage(CoachType.HARD_BRAKE, MessageCatalog.hardBrake(), ts)
            is DetectionEvent.BlinkerLeftOn -> CoachMessage(CoachType.BLINKER_LEFT_ON, MessageCatalog.blinkerLeftOn(), ts)
            is DetectionEvent.TurnTooFast -> CoachMessage(CoachType.TURN_SPEED, MessageCatalog.turnSpeed(), ts)
            is DetectionEvent.GearShiftWhileMoving ->
                CoachMessage(CoachType.GEAR_SHIFT_MOVING, MessageCatalog.gearShiftMoving(), ts)
        }
    }
}

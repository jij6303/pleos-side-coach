package com.jij6303.sidecoach.feature.coach

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.feature.coach.detect.DetectionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachMessageFactoryTest {

    private val left = TurnSignalState.LEFT

    @Test
    fun `차선 변경은 누락이면 교정, 칭찬할 때면 칭찬, 그 외는 말하지 않는다`() {
        assertEquals(CoachType.BLINKER_OMISSION, CoachMessageFactory.from(DetectionEvent.LaneChange(left, false, 1))?.type)
        assertEquals(CoachType.PRAISE, CoachMessageFactory.from(DetectionEvent.LaneChange(left, true, 1), praise = true)?.type)
        assertNull(CoachMessageFactory.from(DetectionEvent.LaneChange(left, true, 1)))
    }

    @Test
    fun `판정 종류마다 맞는 코칭 유형과 문구, 시각을 쓴다`() {
        val brake = CoachMessageFactory.from(DetectionEvent.HardBrake(12f, 60f, 7))!!
        assertEquals(CoachType.HARD_BRAKE, brake.type)
        assertEquals(MessageCatalog.hardBrake(), brake.text)
        assertEquals(7L, brake.createdAtMs)
        assertEquals(CoachType.BLINKER_LEFT_ON, CoachMessageFactory.from(DetectionEvent.BlinkerLeftOn(left, 1))?.type)
        assertEquals(CoachType.TURN_SPEED, CoachMessageFactory.from(DetectionEvent.TurnTooFast(40f, 120f, 1))?.type)
        assertEquals(
            CoachType.GEAR_SHIFT_MOVING,
            CoachMessageFactory.from(DetectionEvent.GearShiftWhileMoving(GearPosition.DRIVE, GearPosition.REVERSE, 20f, 1))?.type,
        )
    }
}

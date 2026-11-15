package com.jij6303.sidecoach.feature.report.trip

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.feature.coach.detect.DetectionEvent
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class TripRecorderTest {

    @Test
    fun `새 코칭 3종을 횟수로 센다 (#53)`() {
        val r = TripRecorder()
        assertFalse(r.record(DetectionEvent.BlinkerLeftOn(TurnSignalState.LEFT, 0)))
        r.record(DetectionEvent.BlinkerLeftOn(TurnSignalState.RIGHT, 1))
        r.record(DetectionEvent.TurnTooFast(40f, 120f, 2))
        r.record(DetectionEvent.GearShiftWhileMoving(GearPosition.DRIVE, GearPosition.REVERSE, 20f, 3))
        assertEquals(TripStats(blinkerLeftOn = 2, turnTooFast = 1, gearShiftsMoving = 1), r.stats.value)
    }
}

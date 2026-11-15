package com.jij6303.sidecoach.feature.report.trip

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.feature.coach.detect.DetectionEvent
import com.jij6303.sidecoach.state.DrivingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TripRecorderTest {

    private fun snap(ts: Long, gear: GearPosition, kmh: Float) = DrivingSnapshot(timestampMs = ts, gear = gear, speedKmh = kmh)

    @Test
    fun `새 코칭 3종을 횟수로 센다 (#53)`() {
        val r = TripRecorder()
        r.record(
            listOf(
                DetectionEvent.BlinkerLeftOn(TurnSignalState.LEFT, 0),
                DetectionEvent.BlinkerLeftOn(TurnSignalState.RIGHT, 1),
                DetectionEvent.TurnTooFast(40f, 120f, 2),
                DetectionEvent.GearShiftWhileMoving(GearPosition.DRIVE, GearPosition.REVERSE, 20f, 3),
            ),
            nowMs = 3,
        )
        assertEquals(TripStats(blinkerLeftOn = 2, turnTooFast = 1, gearShiftsMoving = 1), r.stats.value)
    }

    @Test
    fun `출발하면 통계를 비우고, 끝나는 스냅샷의 판정까지 넣어 성적표를 만든다`() {
        val r = TripRecorder()
        r.record(listOf(DetectionEvent.HardBrake(12f, 60f, 0)), nowMs = 0) // 지난 주행의 기록
        assertEquals(TripSession.Event.Started, r.onSnapshot(snap(0, GearPosition.DRIVE, 40f)))
        assertEquals(TripStats(), r.stats.value)

        r.onSnapshot(snap(10_000, GearPosition.DRIVE, 20f))
        val gear = DetectionEvent.GearShiftWhileMoving(GearPosition.DRIVE, GearPosition.PARK, 20f, 11_000)
        r.onSnapshot(snap(11_000, GearPosition.PARK, 20f))
        assertNull("판정을 넣기 전에는 성적표가 없다", r.lastReport.value)
        r.record(listOf(gear), nowMs = 11_000)
        assertEquals(1, r.lastReport.value!!.stats.gearShiftsMoving)
    }

    @Test
    fun `P에 잠깐 넣었다 다시 출발하면 성적표만 닫고 통계는 이어간다`() {
        val r = TripRecorder()
        r.onSnapshot(snap(0, GearPosition.DRIVE, 40f))
        r.record(listOf(DetectionEvent.HardBrake(12f, 60f, 1_000)), nowMs = 1_000)
        r.onSnapshot(snap(10_000, GearPosition.DRIVE, 0f))
        r.onSnapshot(snap(11_000, GearPosition.PARK, 0f))
        r.record(emptyList(), nowMs = 11_000)

        assertEquals(TripSession.Event.Resumed, r.onSnapshot(snap(20_000, GearPosition.DRIVE, 10f)))
        assertNull(r.lastReport.value)
        assertEquals(1, r.stats.value.hardBrakes)
    }
}

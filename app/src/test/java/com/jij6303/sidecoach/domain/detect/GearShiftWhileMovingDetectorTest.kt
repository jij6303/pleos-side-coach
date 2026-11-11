package com.jij6303.sidecoach.domain.detect

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.GearPosition.DRIVE
import com.jij6303.sidecoach.data.vehicle.GearPosition.NEUTRAL
import com.jij6303.sidecoach.data.vehicle.GearPosition.PARK
import com.jij6303.sidecoach.data.vehicle.GearPosition.REVERSE
import com.jij6303.sidecoach.domain.DrivingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GearShiftWhileMovingDetectorTest {

    private val detector = GearShiftWhileMovingDetector()

    private fun snap(ts: Long, gear: GearPosition, speed: Float) =
        DrivingSnapshot(timestampMs = ts, speedKmh = speed, gear = gear)

    /** [from]으로 [speed]로 달리다 [to]로 바꾼다. 기어 신호에는 아직 이전 차속이 남아 있다. */
    private fun shift(from: GearPosition, to: GearPosition, speed: Float): DetectionEvent? {
        detector.onSnapshot(snap(0, from, speed))
        return detector.onSnapshot(snap(1_000, to, speed))
    }

    @Test
    fun `달리는 중 D에서 R로 바꾸면 알림`() {
        assertEquals(DetectionEvent.GearShiftWhileMoving(DRIVE, REVERSE, 20f, 1_000), shift(DRIVE, REVERSE, 20f))
    }

    @Test
    fun `달리는 중 P에 넣으면 알림`() {
        assertEquals(DetectionEvent.GearShiftWhileMoving(DRIVE, PARK, 10f, 1_000), shift(DRIVE, PARK, 10f))
    }

    @Test
    fun `후진 중 D로 바꾸면 알림`() {
        assertEquals(DetectionEvent.GearShiftWhileMoving(REVERSE, DRIVE, 5f, 1_000), shift(REVERSE, DRIVE, 5f))
    }

    @Test
    fun `멈춘 뒤 바꾸면 알리지 않음`() {
        assertNull(shift(DRIVE, REVERSE, 0f))
        detector.reset()
        assertNull(shift(DRIVE, PARK, 2f))
    }

    @Test
    fun `N 관련 변속과 P에서 출발은 판정하지 않음`() {
        assertNull(shift(DRIVE, NEUTRAL, 30f))
        detector.reset()
        assertNull(shift(NEUTRAL, DRIVE, 30f))
        detector.reset()
        assertNull(shift(PARK, DRIVE, 0f))
    }

    @Test
    fun `같은 기어 유지 중에는 알리지 않음`() {
        detector.onSnapshot(snap(0, DRIVE, 50f))
        assertNull(detector.onSnapshot(snap(1_000, DRIVE, 60f)))
    }

    @Test
    fun `콘솔처럼 변속 순간 차속 0이 먼저 와도 알림`() {
        detector.onSnapshot(snap(0, DRIVE, 25f))
        detector.onSnapshot(snap(300, DRIVE, 0f))
        assertEquals(DetectionEvent.GearShiftWhileMoving(DRIVE, REVERSE, 25f, 300), detector.onSnapshot(snap(300, REVERSE, 0f)))
    }

    @Test
    fun `멈추고 잠시 뒤 바꾸면 알리지 않음`() {
        detector.onSnapshot(snap(0, DRIVE, 25f))
        detector.onSnapshot(snap(1_000, DRIVE, 0f))
        assertNull(detector.onSnapshot(snap(2_000, REVERSE, 0f)))
    }

    @Test
    fun `같은 차속으로 오래 달린 뒤 바꿔도 알림`() {
        detector.onSnapshot(snap(0, DRIVE, 40f))
        assertEquals(DetectionEvent.GearShiftWhileMoving(DRIVE, PARK, 40f, 5_000), detector.onSnapshot(snap(5_000, PARK, 40f)))
    }
}

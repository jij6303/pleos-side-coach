package com.jij6303.sidecoach.domain.detect

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.domain.DrivingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TurnSpeedDetectorTest {

    private val detector = TurnSpeedDetector()

    private fun snap(ts: Long, steering: Float, speed: Float, gear: GearPosition = GearPosition.DRIVE) =
        DrivingSnapshot(timestampMs = ts, speedKmh = speed, gear = gear, steeringDeg = steering)

    private fun hold(from: Long, to: Long, steering: Float, speed: Float) =
        (from..to step 250).mapNotNull { detector.onSnapshot(snap(it, steering, speed)) }

    @Test
    fun `크게 꺾은 채 빠르게 1초 넘게 달리면 알림 (오른쪽 조향도)`() {
        val events = hold(0, 2_000, steering = -120f, speed = 40f)
        assertEquals(listOf(DetectionEvent.TurnTooFast(40f, -120f, 1_000)), events)
    }

    @Test
    fun `천천히 돌면 알리지 않음`() {
        assertTrue(hold(0, 3_000, steering = 200f, speed = 20f).isEmpty())
    }

    @Test
    fun `차선 변경 정도의 조향은 알리지 않음`() {
        assertTrue(hold(0, 3_000, steering = 30f, speed = 80f).isEmpty())
    }

    @Test
    fun `1초가 안 되게 스치면 알리지 않음`() {
        val events = hold(0, 750, steering = 120f, speed = 40f) + hold(1_000, 3_000, steering = 0f, speed = 40f)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `쿨다운 15초 동안 다시 알리지 않음`() {
        val first = hold(0, 2_000, steering = 120f, speed = 40f)
        hold(2_250, 3_000, steering = 0f, speed = 40f)
        val second = hold(4_000, 6_000, steering = 120f, speed = 40f)
        val third = hold(17_000, 19_000, steering = 120f, speed = 40f)
        assertEquals(1, first.size)
        assertTrue(second.isEmpty())
        assertEquals(1, third.size)
    }

    @Test
    fun `후진 중에는 판정하지 않음`() {
        val events = (0L..2_000L step 250).mapNotNull {
            detector.onSnapshot(snap(it, 300f, 35f, GearPosition.REVERSE))
        }
        assertTrue(events.isEmpty())
    }
}

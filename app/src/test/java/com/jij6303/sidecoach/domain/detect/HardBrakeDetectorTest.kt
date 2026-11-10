package com.jij6303.sidecoach.domain.detect

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.domain.DrivingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class HardBrakeDetectorTest {

    private val detector = HardBrakeDetector()

    private fun snap(ts: Long, speed: Float, brake: Boolean = false) =
        DrivingSnapshot(timestampMs = ts, speedKmh = speed, brakePressed = brake)

    private fun feed(vararg s: DrivingSnapshot) = s.mapNotNull { detector.onSnapshot(it) }

    @Test
    fun `T6 60에서 30으로 1초에 줄이며 브레이크를 밟으면 급제동`() {
        val events = feed(
            snap(0, 60f),
            snap(100, 60f, brake = true),
            snap(1_000, 30f, brake = true),
        )
        val e = events.single() as DetectionEvent.HardBrake
        assertEquals(60f, e.fromSpeedKmh, 0f)
        assertTrue(e.decelKmhPerSec >= 30f)
    }

    @Test
    fun `T6 쿨다운 10초 안에는 다시 판정하지 않는다`() {
        val first = feed(snap(0, 60f, true), snap(1_000, 30f, true))
        assertEquals(1, first.size)
        val again = feed(snap(5_000, 60f, true), snap(6_000, 30f, true))
        assertEquals(0, again.size)
        val afterCooldown = feed(snap(12_000, 60f, true), snap(13_000, 30f, true))
        assertEquals(1, afterCooldown.size)
    }

    @Test
    fun `후진 중 정지는 급제동으로 보지 않는다 (주차 코치가 맡음)`() {
        val r = { ts: Long, speed: Float -> DrivingSnapshot(timestampMs = ts, speedKmh = speed, brakePressed = true, gear = GearPosition.REVERSE) }
        assertEquals(0, feed(r(0, 25f), r(1_000, 0f)).size)
    }

    @Test
    fun `완만한 감속은 급제동이 아니다`() {
        val events = feed(snap(0, 60f, true), snap(1_000, 55f, true), snap(2_000, 50f, true))
        assertEquals(0, events.size)
    }

    @Test
    fun `브레이크를 밟지 않은 감속은 제외`() {
        val events = feed(snap(0, 60f), snap(1_000, 30f))
        assertEquals(0, events.size)
    }

    @Test
    fun `정지까지 가는 급제동도 잡는다`() {
        val events = feed(snap(0, 45f, true), snap(1_000, 0f, true))
        assertEquals(1, events.size)
    }

    @Test
    fun `저속에서 시작한 감속은 제외`() {
        val events = feed(snap(0, 12f, true), snap(500, 0f, true))
        assertEquals(0, events.size)
    }

    @Test
    fun `차속 이벤트가 드물어도 1초 전 속도를 기준으로 계산`() {
        // 45 km/h가 5초간 유지되다(이벤트 없음) 0.5초 만에 30으로 떨어짐 → 1초 기준 15 km/h/s
        val events = feed(snap(0, 45f), snap(5_000, 45f, true), snap(5_500, 30f, true))
        val e = events.single() as DetectionEvent.HardBrake
        assertEquals(15f, e.decelKmhPerSec, 0.01f)
    }

    @Test
    fun `첫 샘플만으로는 판정하지 않는다`() {
        assertNull(detector.onSnapshot(snap(0, 60f, true)))
    }
}

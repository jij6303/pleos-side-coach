package com.jij6303.sidecoach.domain.detect

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.domain.DrivingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BlinkerLeftOnDetectorTest {

    private val detector = BlinkerLeftOnDetector()
    private val left = TurnSignalState.LEFT

    private fun snap(ts: Long, steering: Float, signal: TurnSignalState = left, speed: Float = 40f) =
        DrivingSnapshot(timestampMs = ts, speedKmh = speed, gear = GearPosition.DRIVE, steeringDeg = steering, turnSignal = signal)

    /** 깜빡이 켜고 왼쪽으로 꺾었다가 1초에 가운데로 돌아온 뒤, [untilMs]까지 1초 간격으로 달린다. */
    private fun turnThenDrive(untilMs: Long, speed: Float = 40f, signalAt: (Long) -> TurnSignalState = { left }): List<DetectionEvent> {
        val out = mutableListOf<DetectionEvent>()
        detector.onSnapshot(snap(0, 0f))?.let(out::add)
        detector.onSnapshot(snap(500, 30f))?.let(out::add)
        var ts = 1_000L
        while (ts <= untilMs) {
            detector.onSnapshot(snap(ts, 0f, signalAt(ts), speed))?.let(out::add)
            ts += 1_000
        }
        return out
    }

    @Test
    fun `조향 후 깜빡이를 켠 채 8초 넘게 달리면 한 번 알림`() {
        val events = turnThenDrive(untilMs = 20_000)
        assertEquals(listOf(DetectionEvent.BlinkerLeftOn(left, 9_000)), events)
    }

    @Test
    fun `조향 후 깜빡이를 끄면 알리지 않음`() {
        val events = turnThenDrive(untilMs = 20_000) { ts -> if (ts >= 3_000) TurnSignalState.OFF else left }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `정차 중에는 시간을 세지 않음`() {
        val events = turnThenDrive(untilMs = 20_000, speed = 0f)
        assertTrue(events.isEmpty())
    }

    @Test
    fun `핸들을 꺾지 않고 켜만 둔 경우는 판정하지 않음`() {
        val events = (0L..20_000L step 1_000).mapNotNull { detector.onSnapshot(snap(it, 0f)) }
        assertTrue(events.isEmpty())
    }

    @Test
    fun `깜빡이를 다시 켜면 다시 판정`() {
        turnThenDrive(untilMs = 10_000)
        detector.onSnapshot(snap(11_000, 0f, TurnSignalState.OFF))
        detector.onSnapshot(snap(12_000, 0f))
        detector.onSnapshot(snap(12_500, 30f))
        val events = (13_000L..25_000L step 1_000).mapNotNull { detector.onSnapshot(snap(it, 0f)) }
        assertEquals(listOf(DetectionEvent.BlinkerLeftOn(left, 21_000)), events)
    }
}

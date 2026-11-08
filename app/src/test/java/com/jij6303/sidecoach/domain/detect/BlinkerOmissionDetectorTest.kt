package com.jij6303.sidecoach.domain.detect

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.domain.DrivingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class BlinkerOmissionDetectorTest {

    private val detector = BlinkerOmissionDetector()

    private fun snap(
        ts: Long,
        steering: Float,
        signal: TurnSignalState = TurnSignalState.OFF,
        speed: Float = 50f,
        gear: GearPosition = GearPosition.DRIVE,
    ) = DrivingSnapshot(
        timestampMs = ts, speedKmh = speed, gear = gear, steeringDeg = steering, turnSignal = signal,
    )

    /** 조향 시작 → 반대로 되돌림 → 중립. [steps]는 (시각, 조향각, 깜빡이). */
    private fun run(vararg steps: Triple<Long, Float, TurnSignalState>, speed: Float = 50f): List<DetectionEvent> =
        steps.mapNotNull { (ts, deg, sig) -> detector.onSnapshot(snap(ts, deg, sig, speed)) }

    private val off = TurnSignalState.OFF
    private val left = TurnSignalState.LEFT
    private val right = TurnSignalState.RIGHT

    @Test
    fun `T2 깜빡이 없이 왼쪽 차선 변경하면 누락`() {
        val events = run(
            Triple(0L, 0f, off),
            Triple(1_000L, 20f, off),
            Triple(2_500L, -20f, off),
            Triple(4_000L, 0f, off),
        )
        assertEquals(listOf(DetectionEvent.LaneChange(left, signalled = false, timestampMs = 4_000)), events)
    }

    @Test
    fun `T3 조향 전에 같은 방향 깜빡이를 켰으면 정상`() {
        val events = run(
            Triple(0L, 0f, right),        // 오른쪽 깜빡이 먼저
            Triple(1_000L, -20f, right),  // 오른쪽(−)으로 조향
            Triple(2_500L, 20f, off),
            Triple(4_000L, 0f, off),
        )
        assertEquals(listOf(DetectionEvent.LaneChange(right, signalled = true, timestampMs = 4_000)), events)
    }

    @Test
    fun `깜빡이를 조향 시작 3초 전에 끄고 바꿔도 인정`() {
        val events = run(
            Triple(0L, 0f, left),
            Triple(1_000L, 0f, off),      // 1초에 끔
            Triple(3_500L, 20f, off),     // 2.5초 뒤 조향 시작 (3초 이내)
            Triple(5_000L, -20f, off),
            Triple(6_500L, 0f, off),
        )
        assertEquals(true, (events.single() as DetectionEvent.LaneChange).signalled)
    }

    @Test
    fun `조향 시작 1초 안에 늦게 켜도 인정, 그 뒤면 누락`() {
        val late = run(
            Triple(0L, 20f, off),
            Triple(800L, 22f, left),
            Triple(2_000L, -20f, off),
            Triple(3_500L, 0f, off),
        )
        assertEquals(true, (late.single() as DetectionEvent.LaneChange).signalled)

        val tooLate = run(
            Triple(10_000L, 20f, off),
            Triple(11_500L, 22f, left),
            Triple(12_000L, -20f, off),
            Triple(13_500L, 0f, off),
        )
        assertEquals(false, (tooLate.single() as DetectionEvent.LaneChange).signalled)
    }

    @Test
    fun `반대 방향 깜빡이는 인정하지 않는다`() {
        val events = run(
            Triple(0L, 0f, right),
            Triple(1_000L, 20f, right),  // 왼쪽(+)으로 조향인데 오른쪽 깜빡이
            Triple(2_500L, -20f, off),
            Triple(4_000L, 0f, off),
        )
        assertEquals(false, (events.single() as DetectionEvent.LaneChange).signalled)
    }

    @Test
    fun `T5 저속에서는 판정하지 않는다`() {
        val events = run(
            Triple(0L, 20f, off),
            Triple(1_500L, -20f, off),
            Triple(3_000L, 0f, off),
            speed = 20f,
        )
        assertEquals(emptyList<DetectionEvent>(), events)
    }

    @Test
    fun `D단이 아니면 판정하지 않는다`() {
        assertNull(detector.onSnapshot(snap(0, 20f, gear = GearPosition.REVERSE)))
        assertNull(detector.onSnapshot(snap(1_500, -20f, gear = GearPosition.REVERSE)))
        assertNull(detector.onSnapshot(snap(3_000, 0f, gear = GearPosition.REVERSE)))
    }

    @Test
    fun `한쪽으로만 꺾고 돌아오면(커브·회전) 차선 변경이 아니다`() {
        val events = run(
            Triple(0L, 30f, off),
            Triple(2_000L, 30f, off),
            Triple(4_000L, 0f, off),
        )
        assertEquals(emptyList<DetectionEvent>(), events)
    }

    @Test
    fun `8초보다 길면 차선 변경이 아니다`() {
        val events = run(
            Triple(0L, 20f, off),
            Triple(5_000L, -20f, off),
            Triple(9_000L, 0f, off),
        )
        assertEquals(emptyList<DetectionEvent>(), events)
    }

    @Test
    fun `2초보다 짧은 흔들림은 무시`() {
        val events = run(
            Triple(0L, 20f, off),
            Triple(500L, -20f, off),
            Triple(1_000L, 0f, off),
        )
        assertEquals(emptyList<DetectionEvent>(), events)
    }

    @Test
    fun `회전 지점 근처에서는 억제`() {
        val d = BlinkerOmissionDetector(isNearTurn = { true })
        listOf(snap(0, 20f), snap(1_500, -20f), snap(3_000, 0f)).forEach { assertNull(d.onSnapshot(it)) }
    }
}

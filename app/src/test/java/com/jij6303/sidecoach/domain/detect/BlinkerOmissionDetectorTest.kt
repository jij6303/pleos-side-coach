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
    fun `한쪽으로 꺾었다 돌아오는 조향도 2~8초면 차선 변경 (#57)`() {
        val events = run(
            Triple(0L, 30f, off),
            Triple(2_000L, 30f, off),
            Triple(4_000L, 0f, off),
        )
        assertEquals(listOf(DetectionEvent.LaneChange(left, signalled = false, timestampMs = 4_000)), events)
    }

    @Test
    fun `실측 재현 - 100km에서 깜빡이 켜고 +51도 3_8초 뒤 복귀하면 정상 차선 변경 (#57)`() {
        val events = listOf(
            Triple(0L, 0f, left),          // 깜빡이 먼저
            Triple(2_700L, 44f, left),
            Triple(2_840L, 51f, left),
            Triple(2_980L, 55f, left),
            Triple(3_400L, 51f, left),
            Triple(6_480L, 0f, left),
        ).mapNotNull { (ts, deg, sig) -> detector.onSnapshot(snap(ts, deg, sig, speed = 99f)) }
        assertEquals(listOf(DetectionEvent.LaneChange(left, signalled = true, timestampMs = 6_480)), events)
    }

    @Test
    fun `차선 변경 직후 반대쪽 되돌림 조향은 한 번 더 세지 않는다 (#57)`() {
        val events = run(
            Triple(0L, 0f, off),
            Triple(500L, -25f, off),
            Triple(3_000L, 0f, off),      // 오른쪽 차선 변경 판정
            Triple(3_500L, 15f, off),     // 되돌림
            Triple(5_000L, 0f, off),
            Triple(8_000L, 0f, off),
        )
        assertEquals(listOf(DetectionEvent.LaneChange(right, signalled = false, timestampMs = 3_000)), events)
    }

    @Test
    fun `중립을 거쳐 반대로 꺾는 S자도 한 번으로 센다 (#57)`() {
        val events = run(
            Triple(0L, 0f, off),
            Triple(300L, 20f, off),
            Triple(1_200L, 0f, off),      // 2초 전에 중립 통과
            Triple(1_500L, -20f, off),    // 반대쪽 되돌림 → S자
            Triple(3_000L, 0f, off),
        )
        assertEquals(listOf(DetectionEvent.LaneChange(left, signalled = false, timestampMs = 3_000)), events)
    }

    @Test
    fun `90도 넘게 꺾으면 회전이라 차선 변경이 아니다 (#57)`() {
        val events = run(
            Triple(0L, 30f, off),
            Triple(1_000L, 120f, off),
            Triple(3_000L, 30f, off),
            Triple(4_000L, 0f, off),
        )
        assertEquals(emptyList<DetectionEvent>(), events)
    }

    @Test
    fun `실측 재현 - 100도까지 꺾었다 천천히 풀면 푸는 동안도 세지 않는다 (#57)`() {
        val events = listOf(
            0L to 0f, 1_000L to 100f, 1_290L to 92f, 1_430L to 85f, 1_840L to 77f,
            1_940L to 66f, 2_630L to 51f, 5_840L to 0f,
        ).mapNotNull { (ts, deg) -> detector.onSnapshot(snap(ts, deg, speed = 90f)) }
        assertEquals(emptyList<DetectionEvent>(), events)
    }

    @Test
    fun `8초 넘게 꺾은 커브는 핸들을 풀 때도 세지 않는다 (#57)`() {
        val events = run(
            Triple(0L, 30f, off),
            Triple(5_000L, 30f, off),
            Triple(9_000L, 30f, off),     // 8초 초과: 커브
            Triple(12_000L, 30f, off),
            Triple(13_000L, 0f, off),
        )
        assertEquals(emptyList<DetectionEvent>(), events)
    }

    @Test
    fun `되돌림이 아닌 다음 차선 변경은 따로 센다 (#57)`() {
        val events = run(
            Triple(0L, 25f, off),
            Triple(3_000L, 0f, off),
            Triple(10_000L, 25f, off),    // 7초 뒤 같은 방향으로 또 변경
            Triple(13_000L, 0f, off),
        )
        assertEquals(2, events.size)
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

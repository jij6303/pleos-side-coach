package com.jij6303.sidecoach.domain.report

import com.jij6303.sidecoach.domain.trip.TripMetrics
import com.jij6303.sidecoach.domain.trip.TripStats
import org.junit.Assert.assertEquals
import org.junit.Test

class ReportGeneratorTest {

    private val metrics = TripMetrics(startedAtMs = 0, endedAtMs = 60_000, movingMs = 50_000, distanceKm = 0.5f, maxSpeedKmh = 50f)

    private fun gen(lane: Int, signalled: Int, brakes: Int) =
        ReportGenerator.generate(metrics, TripStats(laneChanges = lane, signalledLaneChanges = signalled, hardBrakes = brakes))

    @Test
    fun `실수가 없으면 100점, 아주 잘했어요`() {
        val r = gen(lane = 3, signalled = 3, brakes = 0)
        assertEquals(100, r.score)
        assertEquals(Grade.GREAT, r.grade)
        assertEquals(NextTask.KEEP_GOING, r.nextTask)
    }

    @Test
    fun `깜빡이 누락과 급제동은 1회당 10점씩 뺀다`() {
        val r = gen(lane = 4, signalled = 2, brakes = 1)
        assertEquals(2, r.missedBlinkers)
        assertEquals(70, r.score)
        assertEquals(Grade.GOOD, r.grade)
    }

    @Test
    fun `점수는 0점 아래로 내려가지 않는다`() {
        val r = gen(lane = 8, signalled = 0, brakes = 5)
        assertEquals(0, r.score)
        assertEquals(Grade.PRACTICE, r.grade)
    }

    @Test
    fun `과제는 깜빡이 누락이 급제동보다 먼저`() {
        assertEquals(NextTask.BLINKER, gen(lane = 1, signalled = 0, brakes = 2).nextTask)
        assertEquals(NextTask.SMOOTH_BRAKE, gen(lane = 2, signalled = 2, brakes = 1).nextTask)
    }

    @Test
    fun `차선 변경이 없었으면 차선 변경 연습을 제안`() {
        assertEquals(NextTask.TRY_LANE_CHANGE, gen(lane = 0, signalled = 0, brakes = 0).nextTask)
    }
}

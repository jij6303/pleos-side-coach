package com.jij6303.sidecoach.data.navi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TbtRouteTrackerTest {

    private val tracker = TbtRouteTracker()

    /** #63 에뮬레이터 실측 목록 (2.5 km 경로). */
    private val measured = listOf(
        TbtRouteTracker.Segment("TURN_LEFT", 80.0, "기업지원허브 방면"),
        TbtRouteTracker.Segment("TURN_LEFT", 390.0, "성남시청 방면"),
        TbtRouteTracker.Segment("TURN_LEFT", 1500.0, "고등IC 방면"),
        TbtRouteTracker.Segment("TURN_RIGHT", 150.0, "왕남초등학교 방면"),
        TbtRouteTracker.Segment("TURN_RIGHT", 320.0, "하나로마트 방면"),
        TbtRouteTracker.Segment("TURN_ARRIVE", 8.0, "도착"),
    )

    private fun snap(ts: Long, kmh: Float) = VehicleMotion(timestampMs = ts, speedKmh = kmh)

    private fun List<NaviEvent>.situations() = map { it as NaviEvent.Situation }

    @Test
    fun `구간 거리를 누적해 다음 회전 하나만 보낸다`() {
        val first = tracker.setRoute(measured, 0).situations()
        assertEquals(1, first.size)
        assertEquals(80f, first[0].distanceM)
        assertEquals(SituationType.LANE_LEFT_TURN, first[0].type)
        assertTrue(tracker.hasRoute)
    }

    @Test
    fun `차속을 적분해 첫 회전을 지나면 다음 회전까지 남은 거리를 보낸다`() {
        tracker.setRoute(measured, 0)
        tracker.onMotion(snap(0, 36f)) // 10 m/s
        val e = tracker.advance(10_000).situations() // 100 m 이동 → 첫 회전(80 m) 지남
        assertEquals(1, e.size)
        assertEquals(370f, e[0].distanceM, 0.01f) // 470 - 100
        assertEquals("tbt-0-1", e[0].key)
    }

    @Test
    fun `다음 회전 바로 뒤에 회전이 있으면 앞 회전까지 남은 거리로 연속 회전을 함께 보낸다`() {
        tracker.setRoute(measured, 0)
        tracker.onMotion(snap(0, 36f))
        // 1850 m 이동: 세 번째 좌회전(1970 m)까지 120 m, 그 뒤 150 m에 우회전(2120 m)
        val e = tracker.advance(185_000).situations()
        assertEquals(listOf(SituationType.LANE_LEFT_TURN, SituationType.CHAIN_RIGHT_TURN), e.map { it.type })
        assertEquals(listOf(120f, 120f), e.map { it.distanceM })
        assertEquals("tbt-0-3-chain", e[1].key)
    }

    @Test
    fun `뒤 회전이 300m보다 멀면 연속 회전으로 보지 않는다`() {
        tracker.setRoute(measured, 0)
        tracker.onMotion(snap(0, 36f))
        // 2050 m: 우회전(2120 m) 다음 우회전(2440 m)은 320 m 뒤
        val e = tracker.advance(205_000).situations()
        assertEquals(listOf(SituationType.LANE_RIGHT_TURN), e.map { it.type })
    }

    @Test
    fun `같은 목록이 다시 오면 이동 거리를 유지한다`() {
        tracker.setRoute(measured, 0)
        tracker.onMotion(snap(0, 36f))
        tracker.advance(5_000) // 50 m
        assertTrue(tracker.setRoute(measured, 5_000).isEmpty())
        assertEquals(10f, tracker.advance(7_000).situations().single().distanceM, 0.01f) // 80 - 70
    }

    @Test
    fun `다른 목록이 오면 지금 위치를 새 출발점으로 본다`() {
        tracker.setRoute(measured, 0)
        tracker.onMotion(snap(0, 36f))
        tracker.advance(5_000) // 50 m
        val rerouted = listOf(TbtRouteTracker.Segment("TURN_UTURN", 300.0, "유턴"))
        val e = tracker.setRoute(rerouted, 5_000).situations().single()
        assertEquals(SituationType.LANE_UTURN, e.type)
        assertEquals(300f, e.distanceM, 0.01f)
    }

    @Test
    fun `안내하지 않는 유형은 건너뛰고 지평선 밖은 보내지 않는다`() {
        val route = listOf(
            TbtRouteTracker.Segment("TURN_TUNNEL", 100.0, "터널"),
            TbtRouteTracker.Segment("TURN_RIGHT", 700.0, "우회전"),
        )
        assertTrue(tracker.setRoute(route, 0).isEmpty()) // 우회전이 800 m 앞 (600 m 밖)
        tracker.onMotion(snap(0, 36f))
        val e = tracker.advance(30_000).situations().single() // 300 m 이동
        assertEquals(SituationType.LANE_RIGHT_TURN, e.type)
        assertEquals(500f, e.distanceM, 0.01f)
    }

    @Test
    fun `경로가 끝나면 아무것도 보내지 않는다`() {
        tracker.setRoute(measured, 0)
        tracker.clear()
        assertFalse(tracker.hasRoute)
        tracker.onMotion(snap(0, 36f))
        assertTrue(tracker.advance(10_000).isEmpty())
    }
}

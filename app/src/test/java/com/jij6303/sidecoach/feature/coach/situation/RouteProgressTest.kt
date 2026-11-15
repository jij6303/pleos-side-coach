package com.jij6303.sidecoach.feature.coach.situation

import com.jij6303.sidecoach.data.navi.FakeRouteNaviEventSource
import com.jij6303.sidecoach.data.navi.SituationType
import com.jij6303.sidecoach.data.navi.TbtMapping
import com.jij6303.sidecoach.data.vehicle.GearPosition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RouteProgressTest {

    private val progress = RouteProgress()

    /** #63 에뮬레이터 실측 목록 (2.5 km 경로). */
    private val measured = TbtMapping.route(
        listOf(
            TbtMapping.Segment("TURN_LEFT", 80.0, "기업지원허브 방면"),
            TbtMapping.Segment("TURN_LEFT", 390.0, "성남시청 방면"),
            TbtMapping.Segment("TURN_LEFT", 1500.0, "고등IC 방면"),
            TbtMapping.Segment("TURN_RIGHT", 150.0, "왕남초등학교 방면"),
            TbtMapping.Segment("TURN_RIGHT", 320.0, "하나로마트 방면"),
            TbtMapping.Segment("TURN_ARRIVE", 8.0, "도착"),
        ),
        receivedAtMs = 0,
    )

    /** 0초부터 [ms]까지 36 km/h(10 m/s)로 달린다. */
    private fun driveTo(ms: Long) {
        progress.onMotion(0, 36f, GearPosition.DRIVE)
        progress.advance(ms)
    }

    @Test
    fun `구간 거리를 누적해 다음 회전 하나만 알린다`() {
        progress.setRoute(measured, 0)
        val first = progress.upcoming().single()
        assertEquals(80f, first.remainingM)
        assertEquals(SituationType.LANE_LEFT_TURN, first.type)
        assertTrue(progress.hasRoute)
    }

    @Test
    fun `차속을 적분해 첫 회전을 지나면 다음 회전까지 남은 거리를 알린다`() {
        progress.setRoute(measured, 0)
        driveTo(10_000) // 100 m → 첫 회전(80 m) 지남
        val next = progress.upcoming().single()
        assertEquals(370f, next.remainingM, 0.01f) // 470 - 100
        assertEquals("tbt-0-1", next.key)
    }

    @Test
    fun `다음 회전 바로 뒤에 회전이 있으면 앞 회전까지 남은 거리로 연속 회전을 함께 알린다`() {
        progress.setRoute(measured, 0)
        driveTo(185_000) // 1850 m: 세 번째 좌회전(1970 m)까지 120 m, 그 뒤 150 m에 우회전
        val e = progress.upcoming()
        assertEquals(listOf(SituationType.LANE_LEFT_TURN, SituationType.CHAIN_RIGHT_TURN), e.map { it.type })
        assertEquals(listOf(120f, 120f), e.map { it.remainingM })
        assertEquals("tbt-0-3-chain", e[1].key)
    }

    @Test
    fun `뒤 회전이 300m보다 멀면 연속 회전으로 보지 않는다`() {
        progress.setRoute(measured, 0)
        driveTo(205_000) // 2050 m: 우회전(2120 m) 다음 우회전(2440 m)은 320 m 뒤
        assertEquals(listOf(SituationType.LANE_RIGHT_TURN), progress.upcoming().map { it.type })
    }

    @Test
    fun `같은 경로가 다시 오면 위치를 유지하고, 다른 경로가 오면 지금 자리를 출발점으로 본다`() {
        progress.setRoute(measured, 0)
        driveTo(5_000) // 50 m
        progress.setRoute(measured, 5_000)
        assertEquals(30f, progress.upcoming().single().remainingM, 0.01f)

        val rerouted = TbtMapping.route(listOf(TbtMapping.Segment("TURN_UTURN", 300.0, "유턴")), receivedAtMs = 5_000)
        progress.setRoute(rerouted, 5_000)
        val e = progress.upcoming().single()
        assertEquals(SituationType.LANE_UTURN, e.type)
        assertEquals(300f, e.remainingM, 0.01f)
    }

    @Test
    fun `지평선(600m) 밖 지점은 알리지 않는다`() {
        val far = TbtMapping.route(
            listOf(TbtMapping.Segment("TURN_TUNNEL", 100.0, "터널"), TbtMapping.Segment("TURN_RIGHT", 700.0, "우회전")),
            receivedAtMs = 0,
        )
        progress.setRoute(far, 0)
        assertTrue(progress.upcoming().isEmpty()) // 800 m 앞
        driveTo(30_000)
        assertEquals(500f, progress.upcoming().single().remainingM, 0.01f)
    }

    @Test
    fun `경로가 끝나면 아무것도 알리지 않는다`() {
        progress.setRoute(measured, 0)
        progress.clearRoute()
        assertFalse(progress.hasRoute)
        assertTrue(progress.upcoming().isEmpty())
    }

    @Test
    fun `시연 경로는 P에서 출발할 때마다 처음부터 따라간다`() {
        progress.setRoute(FakeRouteNaviEventSource.DEMO_ROUTE, 0)
        progress.onMotion(0, 72f, GearPosition.DRIVE)   // 20 m/s
        progress.advance(25_000)                        // 500 m: 회전교차로(400 m) 지남
        assertTrue(progress.upcoming().none { it.type == SituationType.ROUNDABOUT })
        progress.onMotion(26_000, 0f, GearPosition.PARK)
        progress.onMotion(30_000, 0f, GearPosition.DRIVE)
        assertEquals(400f, progress.upcoming().single().remainingM, 0.01f)
    }

    @Test
    fun `저장한 위치는 같은 경로가 다시 오면 이어가고, 내려가 있던 시간은 이동으로 치지 않는다`() {
        progress.setRoute(FakeRouteNaviEventSource.DEMO_ROUTE, 0)
        driveTo(15_000) // 150 m
        val saved = progress.exportState()!!

        val restored = RouteProgress()
        restored.restore(saved, nowMs = 100_000)
        assertNull("경로가 오기 전에는 위치가 없다", restored.exportState())
        restored.setRoute(FakeRouteNaviEventSource.DEMO_ROUTE, 100_000)
        assertEquals(250f, restored.upcoming().single().remainingM, 0.01f)
    }
}

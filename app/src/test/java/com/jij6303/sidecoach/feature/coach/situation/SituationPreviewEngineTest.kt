package com.jij6303.sidecoach.feature.coach.situation

import com.jij6303.sidecoach.data.navi.NaviEvent
import com.jij6303.sidecoach.data.navi.NaviRoute
import com.jij6303.sidecoach.data.navi.RoutePoint
import com.jij6303.sidecoach.data.navi.SituationType
import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.feature.coach.CoachMessage
import com.jij6303.sidecoach.feature.coach.CoachType
import com.jij6303.sidecoach.feature.coach.MessageCatalog
import com.jij6303.sidecoach.state.DrivingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** 경로를 받고 36 km/h(10 m/s)로 달리며 남은 거리별로 예고를 확인한다. 지점 1000 m → t초 뒤 1000 − 10t 남음. */
class SituationPreviewEngineTest {

    private val engine = SituationPreviewEngine()

    private fun route(vararg points: RoutePoint) {
        engine.onNavi(NaviEvent.RouteStarted(NaviRoute("r", points.toList()), 0))
        engine.onSnapshot(DrivingSnapshot(timestampMs = 0, speedKmh = 36f, gear = GearPosition.DRIVE))
    }

    private fun at(sec: Double, lane: Int? = null): List<CoachMessage> = engine.onTick((sec * 1000).toLong(), lane)

    private fun stop(sec: Double) {
        engine.onSnapshot(DrivingSnapshot(timestampMs = (sec * 1000).toLong(), speedKmh = 0f, gear = GearPosition.DRIVE))
    }

    @Test
    fun `T7 300m 밖은 조용하고 300~150m에서 한 번만 예고`() {
        route(RoutePoint("r1", SituationType.ROUNDABOUT, 1000f))
        assertTrue(at(60.0).isEmpty())   // 400 m
        assertTrue(at(69.0).isEmpty())   // 310 m
        val m = at(71.0).single()        // 290 m
        assertEquals(CoachType.PREVIEW, m.type)
        assertEquals(MessageCatalog.preview(SituationType.ROUNDABOUT), m.text)
        assertEquals(71_000L, m.createdAtMs)
        assertTrue(at(80.0).isEmpty())   // 200 m
        assertTrue(at(84.0).isEmpty())   // 160 m
    }

    @Test
    fun `150m 안쪽에서 처음 알게 된 지점은 예고하지 않는다`() {
        route(RoutePoint("r1", SituationType.ROUNDABOUT, 120f))
        assertTrue(at(0.0).isEmpty())
        assertTrue(at(1.0).isEmpty())
    }

    @Test
    fun `차로 안내는 500~200m에서 더 일찍 예고한다`() {
        route(RoutePoint("t1", SituationType.LANE_LEFT_TURN, 1000f))
        assertTrue(at(48.0).isEmpty())   // 520 m
        assertEquals(MessageCatalog.preview(SituationType.LANE_LEFT_TURN), at(55.0).single().text) // 450 m
    }

    @Test
    fun `지금 차로를 알면 차로 기준 문구로 예고한다`() {
        route(RoutePoint("t1", SituationType.LANE_LEFT_TURN, 1000f))
        assertEquals(MessageCatalog.preview(SituationType.LANE_LEFT_TURN, 2), at(55.0, lane = 2).single().text)
    }

    @Test
    fun `연속 회전은 앞 회전 150~30m에서 예고한다`() {
        route(
            RoutePoint("t1", SituationType.LANE_LEFT_TURN, 1000f),
            RoutePoint("t2", SituationType.LANE_RIGHT_TURN, 1150f),
        )
        assertTrue(at(84.0).isEmpty())   // 앞 회전까지 160 m
        assertEquals(MessageCatalog.preview(SituationType.CHAIN_RIGHT_TURN), at(86.0).single().text) // 140 m
    }

    @Test
    fun `다음 지점부터 차례로 각각 예고한다`() {
        route(
            RoutePoint("a", SituationType.ROUNDABOUT, 1000f),
            RoutePoint("b", SituationType.MERGE, 1500f),
        )
        assertEquals(MessageCatalog.preview(SituationType.ROUNDABOUT), at(75.0).single().text)
        assertEquals(MessageCatalog.preview(SituationType.MERGE), at(125.0).single().text)
    }

    @Test
    fun `같은 지점은 10분이 지나야 다시 예고`() {
        route(RoutePoint("r1", SituationType.ROUNDABOUT, 1000f))
        assertEquals(1, at(75.0).size) // 250 m
        stop(75.0)
        assertTrue(at(75.0 + 9 * 60).isEmpty())
        assertEquals(1, at(75.0 + 11 * 60).size)
    }

    @Test
    fun `초기화하면 다시 예고한다 (새 주행)`() {
        route(RoutePoint("r1", SituationType.ROUNDABOUT, 1000f))
        assertEquals(1, at(75.0).size)
        stop(75.0)
        engine.reset()
        assertEquals(1, at(76.0).size)
    }

    @Test
    fun `경로가 끝나면 예고하지 않는다`() {
        route(RoutePoint("r1", SituationType.ROUNDABOUT, 1000f))
        engine.onNavi(NaviEvent.RouteEnded(10_000))
        assertTrue(at(75.0).isEmpty())
    }
}

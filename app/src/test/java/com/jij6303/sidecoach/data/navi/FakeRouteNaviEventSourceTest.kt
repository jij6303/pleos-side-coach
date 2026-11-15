package com.jij6303.sidecoach.data.navi

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.state.DrivingSnapshot
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FakeRouteNaviEventSourceTest {

    private val source = FakeRouteNaviEventSource(
        snapshots = emptyFlow(),
        clock = { 0L },
        route = listOf(FakeRouteNaviEventSource.RoutePoint("r", SituationType.ROUNDABOUT, 400f)),
    )
    private val tracker = source.Tracker()

    private fun snap(ts: Long, gear: GearPosition, kmh: Float) =
        DrivingSnapshot(timestampMs = ts, gear = gear, speedKmh = kmh)

    private fun List<NaviEvent>.distances() = map { (it as NaviEvent.Situation).distanceM }

    @Test
    fun `출발하면 경로 처음부터, 차속을 적분해 남은 거리를 보낸다`() {
        assertEquals(listOf(400f), tracker.onSnapshot(snap(0, GearPosition.DRIVE, 36f)).distances())
        // 36 km/h = 10 m/s → 10초에 100 m
        assertEquals(listOf(300f), tracker.advance(10_000).distances())
        assertEquals(listOf(250f), tracker.advance(15_000).distances())
    }

    @Test
    fun `P에서는 경로가 움직이지 않는다`() {
        assertTrue(tracker.onSnapshot(snap(0, GearPosition.PARK, 0f)).isEmpty())
        assertTrue(tracker.advance(10_000).isEmpty())
    }

    @Test
    fun `같은 10m 구간 안에서는 다시 보내지 않는다`() {
        tracker.onSnapshot(snap(0, GearPosition.DRIVE, 3.6f))   // 1 m/s
        assertEquals(1, tracker.advance(5_000).size)            // 395 m: 390~400 구간 진입
        assertTrue(tracker.advance(9_000).isEmpty())            // 391 m: 같은 구간
        assertEquals(1, tracker.advance(11_000).size)           // 389 m: 다음 구간
    }

    @Test
    fun `지점을 지나면 더 보내지 않고, 다시 출발하면 처음부터`() {
        tracker.onSnapshot(snap(0, GearPosition.DRIVE, 72f))    // 20 m/s
        assertTrue(tracker.advance(25_000).isEmpty())           // 500 m 지남
        tracker.onSnapshot(snap(26_000, GearPosition.PARK, 0f))
        assertEquals(listOf(400f), tracker.onSnapshot(snap(30_000, GearPosition.DRIVE, 0f)).distances())
    }
}

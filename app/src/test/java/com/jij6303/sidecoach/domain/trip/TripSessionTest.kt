package com.jij6303.sidecoach.domain.trip

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.domain.DrivingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripSessionTest {

    private val session = TripSession()

    private fun snap(ts: Long, gear: GearPosition, kmh: Float = 0f) =
        DrivingSnapshot(timestampMs = ts, gear = gear, speedKmh = kmh)

    @Test
    fun `P에서 D로 바꾸면 시작, P로 돌아오면 종료`() {
        assertNull(session.onSnapshot(snap(0, GearPosition.PARK)))
        assertNull(session.onSnapshot(snap(1_000, GearPosition.DRIVE)))  // 기어만 바꿈: 아직 시작 아님
        assertEquals(TripSession.Event.Started, session.onSnapshot(snap(2_000, GearPosition.DRIVE, 36f)))   // 36 km/h = 10 m/s
        session.onSnapshot(snap(12_000, GearPosition.DRIVE, 0f))   // 10초 주행 → 100 m
        val end = session.onSnapshot(snap(13_000, GearPosition.PARK))

        assertTrue(end is TripSession.Event.Ended)
        val m = (end as TripSession.Event.Ended).metrics
        assertEquals(2_000L, m.startedAtMs)
        assertEquals(13_000L, m.endedAtMs)
        assertEquals(10_000L, m.movingMs)
        assertEquals(0.1f, m.distanceKm, 0.001f)
        assertEquals(36f, m.maxSpeedKmh, 0f)
    }

    @Test
    fun `앱을 D 상태에서 켜도 움직이면 시작`() {
        assertEquals(TripSession.Event.Started, session.onSnapshot(snap(0, GearPosition.DRIVE, 20f)))
    }

    @Test
    fun `기어를 모르면 판정하지 않는다`() {
        assertNull(session.onSnapshot(snap(0, GearPosition.UNKNOWN, 30f)))
        assertTrue(!session.isActive)
    }

    @Test
    fun `주행 중 R·N으로 바꿔도 같은 주행이다`() {
        session.onSnapshot(snap(0, GearPosition.DRIVE, 20f))
        assertNull(session.onSnapshot(snap(1_000, GearPosition.NEUTRAL)))
        assertNull(session.onSnapshot(snap(2_000, GearPosition.REVERSE)))
        assertTrue(session.isActive)
    }

    @Test
    fun `5초도 안 움직였으면 성적표 없이 끝난다`() {
        session.onSnapshot(snap(0, GearPosition.DRIVE, 20f))
        session.onSnapshot(snap(3_000, GearPosition.DRIVE, 0f))
        assertEquals(TripSession.Event.Discarded, session.onSnapshot(snap(4_000, GearPosition.PARK)))
    }

    @Test
    fun `정속 주행으로 이벤트 간격이 길어도 그대로 적분한다`() {
        session.onSnapshot(snap(0, GearPosition.DRIVE, 36f))
        session.onSnapshot(snap(60_000, GearPosition.DRIVE, 0f))  // 60초 동안 차속 변화 없음
        val m = (session.onSnapshot(snap(61_000, GearPosition.PARK)) as TripSession.Event.Ended).metrics
        assertEquals(60_000L, m.movingMs)
        assertEquals(0.6f, m.distanceKm, 0.001f)
    }

    // ── #51 시작·이어가기 ──

    private fun drive(from: Long, kmh: Float = 36f) {
        session.onSnapshot(snap(from, GearPosition.DRIVE, kmh))
        session.onSnapshot(snap(from + 10_000, GearPosition.DRIVE, 0f))
    }

    @Test
    fun `P에서 기어만 바꿨다 돌아오면 주행이 시작되지 않는다`() {
        assertNull(session.onSnapshot(snap(0, GearPosition.DRIVE)))
        assertNull(session.onSnapshot(snap(1_000, GearPosition.REVERSE, 2f))) // 3km/h 이하
        assertNull(session.onSnapshot(snap(2_000, GearPosition.PARK)))
        assertTrue(!session.isActive)
    }

    @Test
    fun `P에 넣고 60초 안에 다시 움직이면 이어간다`() {
        drive(0)
        assertTrue(session.onSnapshot(snap(11_000, GearPosition.PARK)) is TripSession.Event.Ended)
        assertNull(session.onSnapshot(snap(30_000, GearPosition.DRIVE)))
        assertEquals(TripSession.Event.Resumed, session.onSnapshot(snap(71_000, GearPosition.DRIVE, 36f)))
        session.onSnapshot(snap(81_000, GearPosition.DRIVE, 0f))
        val m = (session.onSnapshot(snap(82_000, GearPosition.PARK)) as TripSession.Event.Ended).metrics
        assertEquals(0L, m.startedAtMs)
        assertEquals(20_000L, m.movingMs)          // 멈춰 있던 60초는 빼고 10초 + 10초
        assertEquals(0.2f, m.distanceKm, 0.001f)
    }

    @Test
    fun `P에 넣고 60초가 지나 움직이면 새 주행이다`() {
        drive(0)
        session.onSnapshot(snap(11_000, GearPosition.PARK))
        assertEquals(TripSession.Event.Started, session.onSnapshot(snap(71_001, GearPosition.DRIVE, 36f)))
    }

    @Test
    fun `짧아서 버린 주행도 60초 안이면 이어간다`() {
        session.onSnapshot(snap(0, GearPosition.DRIVE, 20f))
        session.onSnapshot(snap(3_000, GearPosition.DRIVE, 0f))
        assertEquals(TripSession.Event.Discarded, session.onSnapshot(snap(4_000, GearPosition.PARK)))
        assertEquals(TripSession.Event.Resumed, session.onSnapshot(snap(10_000, GearPosition.DRIVE, 20f)))
    }
}

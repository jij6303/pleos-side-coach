package com.jij6303.sidecoach.domain.session

import com.jij6303.sidecoach.data.navi.FakeRouteNaviEventSource
import com.jij6303.sidecoach.data.navi.NaviEvent
import com.jij6303.sidecoach.data.store.SessionStateCodec
import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.domain.DrivingSnapshot
import com.jij6303.sidecoach.domain.coach.CoachType
import com.jij6303.sidecoach.domain.coach.CoachingArbiter
import com.jij6303.sidecoach.domain.coach.CoachingPipeline
import com.jij6303.sidecoach.domain.detect.BlinkerOmissionDetector
import com.jij6303.sidecoach.domain.detect.HardBrakeDetector
import com.jij6303.sidecoach.domain.report.Grade
import com.jij6303.sidecoach.domain.situation.SituationType
import com.jij6303.sidecoach.domain.trip.TripRecorder
import com.jij6303.sidecoach.domain.trip.TripSession
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionRestoreTest {

    private fun pipeline() = CoachingPipeline(
        updates = emptyFlow(),
        detectors = listOf(BlinkerOmissionDetector(), HardBrakeDetector()),
        arbiter = CoachingArbiter(),
        recorder = TripRecorder(),
    )

    private fun snap(ts: Long, gear: GearPosition, kmh: Float, brake: Boolean = false) =
        DrivingSnapshot(timestampMs = ts, gear = gear, speedKmh = kmh, brakePressed = brake)

    /** 출발 → 60 km/h 10초 → 급제동 1회까지 진행한 파이프라인. */
    private fun drivenPipeline() = pipeline().apply {
        process(snap(0, GearPosition.PARK, 0f))
        process(snap(1_000, GearPosition.DRIVE, 60f))
        process(snap(11_000, GearPosition.DRIVE, 60f, brake = true))
        process(snap(12_000, GearPosition.DRIVE, 30f, brake = true)) // 급제동
    }

    @Test
    fun `T13 프로세스가 다시 만들어져도 통계와 진행 중 주행을 이어간다`() {
        val before = drivenPipeline()
        val saved = SessionStateCodec.decode(SessionStateCodec.encode(before.exportState()))!!
        assertNotNull(saved.trip)

        // 새 프로세스: 20초 뒤(다운타임 8초) 복원
        val after = pipeline()
        after.restore(saved, nowMs = 20_000)
        assertEquals(1, after.recorder.stats.value.hardBrakes)
        assertEquals(1, after.arbiter.spokenCount(CoachType.HARD_BRAKE))

        // 같은 주행이 이어져서 P단에 들어가면 성적표가 나온다 (통계 유지)
        after.process(snap(20_000, GearPosition.DRIVE, 0f))
        after.process(snap(21_000, GearPosition.PARK, 0f))
        val report = after.lastReport.value!!
        assertEquals(1, report.stats.hardBrakes)
        assertEquals(1_000L, report.metrics.startedAtMs)
    }

    @Test
    fun `내려가 있던 동안은 주행 시간과 거리에 넣지 않는다`() {
        val session = TripSession()
        session.onSnapshot(snap(0, GearPosition.DRIVE, 36f))
        session.onSnapshot(snap(10_000, GearPosition.DRIVE, 36f)) // 10초, 100 m
        val state = session.exportState()!!

        val restored = TripSession()
        restored.restore(state, nowMs = 600_000) // 10분 뒤 복원
        restored.onSnapshot(snap(601_000, GearPosition.DRIVE, 36f))
        val m = (restored.onSnapshot(snap(602_000, GearPosition.PARK, 0f)) as TripSession.Event.Ended).metrics
        assertEquals(12_000L, m.movingMs)          // 10초 + 복원 후 2초
        assertEquals(0.12f, m.distanceKm, 0.001f)
    }

    @Test
    fun `안 본 성적표도 복원한다`() {
        val p = drivenPipeline()
        p.process(snap(13_000, GearPosition.DRIVE, 0f))
        p.process(snap(14_000, GearPosition.PARK, 0f))
        val saved = SessionStateCodec.decode(SessionStateCodec.encode(p.exportState()))!!
        assertNull(saved.trip)

        val after = pipeline().apply { restore(saved, nowMs = 15_000) }
        assertEquals(Grade.GREAT, after.lastReport.value?.grade)
        assertEquals(90, after.lastReport.value?.score)
    }

    @Test
    fun `재부팅했거나 30분 넘게 지났으면 쓰지 않는다`() {
        val s = drivenPipeline().exportState() // savedAt = 12000
        assertTrue(s.isUsable(nowMs = 12_000 + 60_000))
        assertFalse("재부팅으로 시계가 되돌아감", s.isUsable(nowMs = 5_000))
        assertFalse("30분 초과", s.isUsable(nowMs = 12_000 + 31 * 60_000L))
    }

    @Test
    fun `예고한 지점은 복원 후에도 다시 말하지 않는다`() {
        val p = pipeline()
        p.process(snap(0, GearPosition.DRIVE, 50f))
        assertEquals(CoachType.PREVIEW, p.processNavi(NaviEvent.Situation("r", SituationType.ROUNDABOUT, 290f, 1_000))?.type)

        val after = pipeline().apply { restore(SessionStateCodec.decode(SessionStateCodec.encode(p.exportState()))!!, 2_000) }
        assertNull(after.processNavi(NaviEvent.Situation("r", SituationType.ROUNDABOUT, 250f, 9_000)))
    }

    @Test
    fun `시연 경로 위치를 이어가고 다운타임은 이동으로 치지 않는다`() {
        var now = 0L
        val route = FakeRouteNaviEventSource(
            snapshots = emptyFlow(),
            clock = { now },
            route = listOf(FakeRouteNaviEventSource.RoutePoint("r", SituationType.ROUNDABOUT, 400f)),
        )
        val state = FakeRouteNaviEventSource.RouteState(odometerM = 150.0, speedKmh = 36f, lastSent = mapOf("r" to 25))
        val restored = SessionStateCodec.decode(
            SessionStateCodec.encode(drivenPipeline().exportState().copy(route = state)),
        )!!.route
        assertEquals(state, restored)

        now = 100_000
        route.restore(restored)
        assertEquals(150.0, route.exportState()!!.odometerM, 0.001)
    }

    @Test
    fun `손상되었거나 다른 버전이면 읽지 않는다`() {
        assertNull(SessionStateCodec.decode("{broken"))
        assertNull(SessionStateCodec.decode("""{"v":999}"""))
        assertNull(SessionStateCodec.decode(null))
    }
}

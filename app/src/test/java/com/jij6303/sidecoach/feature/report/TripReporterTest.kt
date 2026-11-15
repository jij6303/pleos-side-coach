package com.jij6303.sidecoach.feature.report

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.feature.coach.detect.DetectionEvent
import com.jij6303.sidecoach.state.DrivingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TripReporterTest {

    private fun snap(ts: Long, gear: GearPosition, kmh: Float) = DrivingSnapshot(timestampMs = ts, gear = gear, speedKmh = kmh)

    @Test
    fun `출발하면 통계를 비우고, P에서 끝나면 그 스냅샷 기록까지 넣어 성적표를 만든다`() {
        val r = TripReporter()
        r.record(DetectionEvent.HardBrake(12f, 60f, 0)) // 지난 주행의 기록
        assertEquals(TripReporter.Change.Started, r.onSnapshot(snap(0, GearPosition.DRIVE, 40f)))
        assertEquals(0, r.recorder.stats.value.hardBrakes)

        r.onSnapshot(snap(10_000, GearPosition.DRIVE, 0f))
        val ended = r.onSnapshot(snap(11_000, GearPosition.PARK, 0f))
        assertTrue(ended is TripReporter.Change.Ended)
        assertNull("성적표는 기록을 마친 뒤 만든다", r.lastReport.value)

        r.record(DetectionEvent.HardBrake(12f, 40f, 11_000))
        val report = r.issueReport(ended as TripReporter.Change.Ended)
        assertEquals(1, report.stats.hardBrakes)
        assertEquals(report, r.lastReport.value)
    }

    @Test
    fun `P에 잠깐 넣었다 다시 출발하면 성적표만 닫고 통계는 이어간다`() {
        val r = TripReporter()
        r.onSnapshot(snap(0, GearPosition.DRIVE, 40f))
        r.record(DetectionEvent.HardBrake(12f, 60f, 1_000))
        r.onSnapshot(snap(10_000, GearPosition.DRIVE, 0f))
        r.issueReport(r.onSnapshot(snap(11_000, GearPosition.PARK, 0f)) as TripReporter.Change.Ended)

        assertEquals(TripReporter.Change.Resumed, r.onSnapshot(snap(20_000, GearPosition.DRIVE, 10f)))
        assertNull(r.lastReport.value)
        assertEquals(1, r.recorder.stats.value.hardBrakes)
    }

    @Test
    fun `통계, 진행 중 주행, 안 본 성적표를 복원한다`() {
        val r = TripReporter()
        r.onSnapshot(snap(0, GearPosition.DRIVE, 40f))
        r.record(DetectionEvent.HardBrake(12f, 60f, 1_000))
        r.onSnapshot(snap(10_000, GearPosition.DRIVE, 0f))
        r.issueReport(r.onSnapshot(snap(11_000, GearPosition.PARK, 0f)) as TripReporter.Change.Ended)

        val restored = TripReporter().apply { restore(r.exportState(), nowMs = 12_000) }
        assertEquals(1, restored.recorder.stats.value.hardBrakes)
        assertEquals(r.lastReport.value, restored.lastReport.value)
    }
}

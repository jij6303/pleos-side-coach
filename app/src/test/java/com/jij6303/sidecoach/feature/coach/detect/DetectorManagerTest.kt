package com.jij6303.sidecoach.feature.coach.detect

import com.jij6303.sidecoach.state.DrivingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Test

class DetectorManagerTest {

    private class FakeDetector(private val event: DetectionEvent?) : Detector {
        var resets = 0
        override fun onSnapshot(s: DrivingSnapshot) = event
        override fun reset() { resets++ }
    }

    @Test
    fun `판정 결과를 디텍터 순서대로 모으고 결과 없는 디텍터는 건너뛴다`() {
        val brake = DetectionEvent.HardBrake(12f, 60f, 1_000)
        val turn = DetectionEvent.TurnTooFast(40f, 120f, 1_000)
        val manager = DetectorManager(listOf(FakeDetector(brake), FakeDetector(null), FakeDetector(turn)))
        assertEquals(listOf(brake, turn), manager.onSnapshot(DrivingSnapshot(timestampMs = 1_000)))
    }

    @Test
    fun `reset은 모든 디텍터에 전달된다`() {
        val a = FakeDetector(null)
        val b = FakeDetector(null)
        DetectorManager(listOf(a, b)).reset()
        assertEquals(1, a.resets)
        assertEquals(1, b.resets)
    }
}

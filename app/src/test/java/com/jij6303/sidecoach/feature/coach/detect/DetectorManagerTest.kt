package com.jij6303.sidecoach.feature.coach.detect

import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.feature.coach.CoachType
import com.jij6303.sidecoach.feature.coach.MessageCatalog
import com.jij6303.sidecoach.state.DrivingSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DetectorManagerTest {

    /** 정해 둔 판정 결과를 차례로 돌려주는 디텍터. */
    private class FakeDetector(vararg events: DetectionEvent?) : Detector {
        private val queue = ArrayDeque(events.toList())
        var resets = 0
        override fun onSnapshot(s: DrivingSnapshot) = queue.removeFirstOrNull()
        override fun reset() { resets++ }
    }

    private val snap = DrivingSnapshot(timestampMs = 1_000)

    private fun lane(signalled: Boolean, ts: Long = 1_000) = DetectionEvent.LaneChange(TurnSignalState.LEFT, signalled, ts)

    @Test
    fun `판정 결과를 디텍터 순서대로 모으고 각 결과의 교정 메시지를 후보로 만든다`() {
        val brake = DetectionEvent.HardBrake(12f, 60f, 1_000)
        val turn = DetectionEvent.TurnTooFast(40f, 120f, 1_000)
        val d = DetectorManager(listOf(FakeDetector(brake), FakeDetector(null), FakeDetector(turn))).onSnapshot(snap)
        assertEquals(listOf(brake, turn), d.events)
        assertEquals(listOf(CoachType.HARD_BRAKE, CoachType.TURN_SPEED), d.candidates.map { it.type })
        assertEquals(MessageCatalog.hardBrake(), d.candidates[0].text)
        assertEquals(1_000L, d.candidates[0].createdAtMs)
    }

    @Test
    fun `깜빡이 켠 차선 변경 3회 연속이면 칭찬 후보, 그 전에는 후보 없음`() {
        val m = DetectorManager(listOf(FakeDetector(lane(true), lane(true), lane(true))))
        assertTrue(m.onSnapshot(snap).candidates.isEmpty())
        assertTrue(m.onSnapshot(snap).candidates.isEmpty())
        assertEquals(listOf(CoachType.PRAISE), m.onSnapshot(snap).candidates.map { it.type })
        assertEquals(0, m.exportState())
    }

    @Test
    fun `깜빡이 없이 바꾸면 누락 교정 후보가 되고 칭찬 연속은 처음부터`() {
        val m = DetectorManager(listOf(FakeDetector(lane(true), lane(true), lane(false), lane(true))))
        m.onSnapshot(snap)
        m.onSnapshot(snap)
        assertEquals(listOf(CoachType.BLINKER_OMISSION), m.onSnapshot(snap).candidates.map { it.type })
        m.onSnapshot(snap)
        assertEquals(1, m.exportState())
    }

    @Test
    fun `칭찬 연속 횟수를 복원하면 이어서 센다`() {
        val m = DetectorManager(listOf(FakeDetector(lane(true)))).apply { restore(2) }
        assertEquals(listOf(CoachType.PRAISE), m.onSnapshot(snap).candidates.map { it.type })
    }

    @Test
    fun `reset은 모든 디텍터와 칭찬 연속 횟수를 비운다`() {
        val a = FakeDetector()
        val b = FakeDetector()
        val m = DetectorManager(listOf(a, b)).apply { restore(2) }
        m.reset()
        assertEquals(1, a.resets)
        assertEquals(1, b.resets)
        assertEquals(0, m.exportState())
    }
}

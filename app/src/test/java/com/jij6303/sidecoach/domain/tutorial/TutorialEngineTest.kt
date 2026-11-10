package com.jij6303.sidecoach.domain.tutorial

import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.domain.DrivingSnapshot
import com.jij6303.sidecoach.domain.coach.CoachType
import com.jij6303.sidecoach.domain.coach.MessageCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TutorialEngineTest {

    private val engine = TutorialEngine()
    private val missions = listOf(TutorialMission.LEFT_SIGNAL, TutorialMission.BRAKE)

    private fun signal(ts: Long, s: TurnSignalState) = DrivingSnapshot(timestampMs = ts, turnSignal = s, brakePressed = false)
    private fun brake(ts: Long, pressed: Boolean) = DrivingSnapshot(timestampMs = ts, turnSignal = TurnSignalState.OFF, brakePressed = pressed)

    @Test
    fun `시작하면 첫 미션 지시를 말한다`() {
        val m = engine.start(missions, 0)
        assertEquals(CoachType.TUTORIAL, m?.type)
        assertEquals(TutorialMission.LEFT_SIGNAL.instruction, m?.text)
        assertEquals(TutorialMission.LEFT_SIGNAL, engine.state?.current)
    }

    @Test
    fun `T10 조작을 확인하면 칭찬과 다음 지시를 한 문장으로`() {
        engine.start(missions, 0)
        assertNull(engine.onSnapshot(signal(100, TurnSignalState.OFF)))
        val m = engine.onSnapshot(signal(200, TurnSignalState.LEFT))
        assertEquals(MessageCatalog.tutorialInstruction(TutorialMission.BRAKE, afterSuccess = true), m?.text)
        assertEquals(setOf(TutorialMission.LEFT_SIGNAL), engine.state?.completed)
        assertEquals(TutorialMission.BRAKE, engine.state?.current)
    }

    @Test
    fun `반대 방향 깜빡이는 인정하지 않는다`() {
        engine.start(missions, 0)
        engine.onSnapshot(signal(100, TurnSignalState.OFF))
        assertNull(engine.onSnapshot(signal(200, TurnSignalState.RIGHT)))
        assertEquals(TutorialMission.LEFT_SIGNAL, engine.state?.current)
    }

    @Test
    fun `이미 켜져 있던 상태로는 통과하지 않고, 껐다 켜야 인정`() {
        engine.start(missions, 0)
        assertNull(engine.onSnapshot(signal(100, TurnSignalState.LEFT)))
        assertNull(engine.onSnapshot(signal(200, TurnSignalState.OFF)))
        assertEquals(CoachType.TUTORIAL, engine.onSnapshot(signal(300, TurnSignalState.LEFT))?.type)
    }

    @Test
    fun `값을 모르면 판정하지 않는다`() {
        engine.start(missions, 0)
        assertNull(engine.onSnapshot(DrivingSnapshot(timestampMs = 100)))
        assertNull(engine.onSnapshot(signal(200, TurnSignalState.LEFT)))  // OFF를 본 적 없음
    }

    @Test
    fun `마지막 미션을 마치면 완료 문구`() {
        engine.start(missions, 0)
        engine.onSnapshot(signal(100, TurnSignalState.OFF))
        engine.onSnapshot(signal(200, TurnSignalState.LEFT))
        engine.onSnapshot(brake(300, false))
        val m = engine.onSnapshot(brake(400, true))
        assertEquals(MessageCatalog.tutorialDone(), m?.text)
        assertTrue(engine.state!!.finished)
        assertEquals(2, engine.state!!.completed.size)
        assertNull(engine.onSnapshot(brake(500, false)))
    }

    @Test
    fun `건너뛰면 칭찬 없이 다음 지시, 완료 목록에는 안 들어간다`() {
        engine.start(missions, 0)
        val m = engine.skip(100)
        assertEquals(TutorialMission.BRAKE.instruction, m?.text)
        assertTrue(engine.state!!.completed.isEmpty())
        assertEquals(MessageCatalog.tutorialDone(), engine.skip(200)?.text)
        assertNull(engine.skip(300))
    }

    @Test
    fun `끝내면 상태가 사라진다`() {
        engine.start(missions, 0)
        engine.stop()
        assertNull(engine.state)
        assertNull(engine.onSnapshot(signal(100, TurnSignalState.OFF)))
    }
}

package com.jij6303.sidecoach.domain.situation

import com.jij6303.sidecoach.data.navi.NaviEvent
import com.jij6303.sidecoach.domain.coach.CoachType
import com.jij6303.sidecoach.domain.coach.MessageCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class SituationPreviewEngineTest {

    private val engine = SituationPreviewEngine()

    private fun ev(d: Float, ts: Long = 0, key: String = "r1") =
        NaviEvent.Situation(key, SituationType.ROUNDABOUT, d, ts)

    @Test
    fun `T7 300m 밖은 조용하고 300~150m에서 한 번만 예고`() {
        assertNull(engine.onEvent(ev(400f)))
        assertNull(engine.onEvent(ev(310f)))
        val m = engine.onEvent(ev(290f, ts = 1_000))
        assertNotNull(m)
        assertEquals(CoachType.PREVIEW, m!!.type)
        assertEquals(MessageCatalog.preview(SituationType.ROUNDABOUT), m.text)
        assertEquals(1_000L, m.createdAtMs)
        assertNull(engine.onEvent(ev(200f)))
        assertNull(engine.onEvent(ev(160f)))
    }

    @Test
    fun `150m 안쪽에서 처음 알게 된 지점은 예고하지 않는다`() {
        assertNull(engine.onEvent(ev(120f)))
    }

    @Test
    fun `차로 안내는 500~200m에서 더 일찍 예고한다`() {
        fun lane(d: Float) = NaviEvent.Situation("t1", SituationType.LANE_LEFT_TURN, d, 0)
        assertNull(engine.onEvent(lane(520f)))
        val m = engine.onEvent(lane(450f))
        assertEquals(MessageCatalog.preview(SituationType.LANE_LEFT_TURN), m?.text)
        assertNull(SituationPreviewEngine().onEvent(lane(180f))) // 200m 안쪽에서 처음 알면 늦음
    }

    @Test
    fun `다른 지점은 각각 예고한다`() {
        assertNotNull(engine.onEvent(ev(250f, key = "a")))
        assertNotNull(engine.onEvent(ev(250f, key = "b")))
    }

    @Test
    fun `같은 지점은 10분이 지나야 다시 예고`() {
        assertNotNull(engine.onEvent(ev(250f, ts = 0)))
        assertNull(engine.onEvent(ev(250f, ts = 9 * 60_000L)))
        assertNotNull(engine.onEvent(ev(250f, ts = 11 * 60_000L)))
    }

    @Test
    fun `초기화하면 다시 예고한다 (새 주행)`() {
        assertNotNull(engine.onEvent(ev(250f)))
        engine.reset()
        assertNotNull(engine.onEvent(ev(250f)))
    }
}

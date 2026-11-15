package com.jij6303.sidecoach.feature.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PraiseCounterTest {

    @Test
    fun `깜빡이 켠 차선 변경 3회 연속이면 칭찬하고 다시 센다`() {
        val c = PraiseCounter()
        assertFalse(c.onLaneChange(true))
        assertFalse(c.onLaneChange(true))
        assertTrue(c.onLaneChange(true))
        assertEquals(0, c.exportState())
    }

    @Test
    fun `깜빡이 없이 바꾸면 처음부터 센다`() {
        val c = PraiseCounter()
        c.onLaneChange(true)
        c.onLaneChange(true)
        assertFalse(c.onLaneChange(false))
        assertFalse(c.onLaneChange(true))
        assertFalse(c.onLaneChange(true))
        assertTrue(c.onLaneChange(true))
    }

    @Test
    fun `연속 횟수를 복원하면 이어서 센다`() {
        val c = PraiseCounter().apply { restore(2, nowMs = 0) }
        assertTrue(c.onLaneChange(true))
    }
}

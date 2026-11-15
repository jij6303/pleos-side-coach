package com.jij6303.sidecoach.feature.coach.situation

import com.jij6303.sidecoach.data.navi.SituationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PreviewWindowTest {

    @Test
    fun `상황 종류마다 예고 구간이 정해져 있다`() {
        assertEquals(PreviewWindow(300f, 150f), PreviewWindow.of(SituationType.ROUNDABOUT))
        assertEquals(PreviewWindow(500f, 200f), PreviewWindow.of(SituationType.LANE_LEFT_TURN))
        assertEquals(PreviewWindow(150f, 30f), PreviewWindow.of(SituationType.CHAIN_RIGHT_TURN))
    }

    @Test
    fun `경계를 포함하고 바깥은 제외한다`() {
        val w = PreviewWindow.LANE
        assertTrue(500f in w)
        assertTrue(200f in w)
        assertFalse(501f in w)
        assertFalse(199f in w)
    }
}

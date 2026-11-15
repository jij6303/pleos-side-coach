package com.jij6303.sidecoach.data.navi

import com.jij6303.sidecoach.domain.coach.MessageCatalog
import com.jij6303.sidecoach.domain.situation.SituationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TbtMappingTest {

    @Test
    fun `회전 종류별 차로 안내 유형`() {
        assertEquals(SituationType.LANE_LEFT_TURN, TbtMapping.turn("TURN_LEFT"))
        assertEquals(SituationType.LANE_LEFT_TURN, TbtMapping.turn("TURN_SHARP_L"))
        assertEquals(SituationType.LANE_RIGHT_TURN, TbtMapping.turn("TURN_RIGHT"))
        assertEquals(SituationType.LANE_UTURN, TbtMapping.turn("TURN_UTURN"))
        assertEquals(SituationType.LANE_KEEP_LEFT, TbtMapping.turn("TURN_EXIT_IC_L"))
        assertEquals(SituationType.LANE_KEEP_RIGHT, TbtMapping.turn("TURN_KEEP_R"))
        assertEquals(SituationType.LANE_STRAIGHT, TbtMapping.turn("TURN_STRAIGHT"))
        assertEquals(SituationType.ROUNDABOUT, TbtMapping.turn("TURN_ENTER_ROUNDABOUT_09"))
    }

    @Test
    fun `연속 회전은 좌우 회전·유턴·분기만`() {
        assertEquals(SituationType.CHAIN_RIGHT_TURN, TbtMapping.chain(SituationType.LANE_RIGHT_TURN))
        assertEquals(SituationType.CHAIN_UTURN, TbtMapping.chain(SituationType.LANE_UTURN))
        assertEquals(SituationType.CHAIN_KEEP_LEFT, TbtMapping.chain(SituationType.LANE_KEEP_LEFT))
        assertNull(TbtMapping.chain(SituationType.LANE_STRAIGHT))
        assertNull(TbtMapping.chain(SituationType.ROUNDABOUT))
    }

    @Test
    fun `안내하지 않는 유형은 null`() {
        listOf("TURN_ARRIVE", "TURN_TUNNEL", "TURN_TOLL", "TURN_UNKNOWN", "TURN_EXIT_ROUNDABOUT").forEach {
            assertNull(it, TbtMapping.turn(it))
        }
        assertEquals(SituationType.SCHOOL_ZONE, TbtMapping.caution("SCHOOL_ZONE"))
        assertNull(TbtMapping.caution("SPEED_BUMP"))
    }

    @Test
    fun `차로 안내 문구는 15어절 이내`() {
        SituationType.entries.filter { it.name.startsWith("LANE_") || it.name.startsWith("CHAIN_") }.forEach {
            val words = MessageCatalog.preview(it).split(" ").size
            assertTrue("$it: $words", words <= 15)
        }
    }
}

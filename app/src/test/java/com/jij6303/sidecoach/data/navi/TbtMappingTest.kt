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
    fun `지금 차로를 아는 경우 문구`() {
        assertEquals("곧 직진이에요. 1차로는 좌회전 전용일 수 있으니 2차로로 옮기세요.", MessageCatalog.preview(SituationType.LANE_STRAIGHT, 1))
        assertEquals("곧 유턴이에요. 지금 1차로 그대로 가요.", MessageCatalog.preview(SituationType.LANE_UTURN, 1))
        // 우회전은 전체 차로 수를 몰라 일반 안내, 5차로 이상은 문구가 없어 일반 안내
        assertEquals(MessageCatalog.preview(SituationType.LANE_RIGHT_TURN), MessageCatalog.preview(SituationType.LANE_RIGHT_TURN, 1))
        assertEquals(MessageCatalog.preview(SituationType.LANE_LEFT_TURN), MessageCatalog.preview(SituationType.LANE_LEFT_TURN, 5))
        assertEquals(11, MessageCatalog.laneAwarePhrases().size)
        MessageCatalog.laneAwarePhrases().forEach { assertTrue(it, it.split(" ").size <= 15) }
    }

    @Test
    fun `음성 클립 스크립트에 차로 문구가 모두 있다`() {
        // 단위 테스트 작업 디렉터리는 app 모듈
        val script = java.io.File("../scripts/generate_voice_clips.ps1").readText()
        (MessageCatalog.laneAwarePhrases() + (1..4).map(MessageCatalog::laneSet)).forEach {
            assertTrue(it, script.contains("'$it'"))
        }
    }

    @Test
    fun `차로 안내 문구는 15어절 이내`() {
        SituationType.entries.filter { it.name.startsWith("LANE_") || it.name.startsWith("CHAIN_") }.forEach {
            val words = MessageCatalog.preview(it).split(" ").size
            assertTrue("$it: $words", words <= 15)
        }
    }
}

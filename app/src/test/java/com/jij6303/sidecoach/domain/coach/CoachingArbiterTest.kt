package com.jij6303.sidecoach.domain.coach

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class CoachingArbiterTest {

    private fun msg(type: CoachType, ts: Long, key: String = type.name) = CoachMessage(type, type.name, ts, key)

    @Test
    fun `우선순위가 높은 메시지를 먼저 말한다`() {
        val a = CoachingArbiter()
        a.submit(msg(CoachType.BLINKER_OMISSION, 0))
        a.submit(msg(CoachType.HARD_BRAKE, 0))
        assertEquals(CoachType.HARD_BRAKE, a.next(0)?.type)
    }

    @Test
    fun `일반 메시지는 6초 간격, HIGH는 3초 간격`() {
        val a = CoachingArbiter(config = CoachingArbiter.Config(queueTtlMs = 60_000))
        a.submit(msg(CoachType.BLINKER_OMISSION, 0))
        assertEquals(CoachType.BLINKER_OMISSION, a.next(0)?.type)

        a.submit(msg(CoachType.BLINKER_OMISSION, 1_000, key = "b2"))
        assertNull(a.next(5_000))
        assertEquals(CoachType.BLINKER_OMISSION, a.next(6_000)?.type)

        a.submit(msg(CoachType.HARD_BRAKE, 7_000))
        assertNull(a.next(8_000))
        assertEquals(CoachType.HARD_BRAKE, a.next(9_000)?.type)
    }

    @Test
    fun `대기열 TTL 4초가 지나면 버린다`() {
        val a = CoachingArbiter()
        a.submit(msg(CoachType.HARD_BRAKE, 0))
        a.next(0)
        a.submit(msg(CoachType.BLINKER_OMISSION, 1_000))
        assertNull("간격 미달", a.next(4_000))
        assertNull("간격은 지났지만 TTL 초과로 버림", a.next(6_000))
    }

    @Test
    fun `깜빡이 경고는 주행당 5회까지`() {
        val a = CoachingArbiter()
        repeat(10) { i ->
            val t = i * 10_000L
            a.submit(msg(CoachType.BLINKER_OMISSION, t, key = "b$i"))
            a.next(t)
        }
        assertEquals(5, a.spokenCount(CoachType.BLINKER_OMISSION))
    }

    @Test
    fun `칭찬은 주행당 3회까지, reset하면 다시 센다`() {
        val a = CoachingArbiter()
        repeat(5) { i ->
            val t = i * 10_000L
            a.submit(msg(CoachType.PRAISE, t, key = "p$i"))
            a.next(t)
        }
        assertEquals(3, a.spokenCount(CoachType.PRAISE))
        a.reset()
        assertEquals(0, a.spokenCount(CoachType.PRAISE))
    }
}

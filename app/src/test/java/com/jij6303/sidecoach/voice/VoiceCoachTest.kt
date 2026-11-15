package com.jij6303.sidecoach.voice

import com.jij6303.sidecoach.feature.coach.CoachMessage
import com.jij6303.sidecoach.feature.coach.CoachType
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VoiceCoachTest {

    private class FakeOutput : VoiceOutput {
        override var isReady = true
        val spoken = mutableListOf<String>()
        /** null이면 즉시 끝, 아니면 complete될 때까지 말하는 중. */
        var gate: CompletableDeferred<Boolean>? = null
        var stopCount = 0

        override suspend fun speak(text: String): Boolean {
            spoken += text
            return gate?.await() ?: true
        }
        override fun stop() {
            stopCount++
            gate?.complete(false)
        }
        override fun release() = Unit
    }

    private class FakeFocus : AudioFocus {
        var grant = true
        var requests = 0
        var held = false
        var onLoss: (() -> Unit)? = null

        override fun request(onLoss: () -> Unit): Boolean {
            requests++
            if (grant) {
                held = true
                this.onLoss = onLoss
            }
            return grant
        }
        override fun abandon() {
            held = false
            onLoss = null
        }
    }

    private fun TestScope.coach(output: VoiceOutput, focus: AudioFocus) =
        VoiceCoach(output, focus, clock = { testScheduler.currentTime }, ttlMs = 4_000, retryMs = 500)

    private fun msg(text: String, at: Long, type: CoachType = CoachType.BLINKER_OMISSION) =
        CoachMessage(type, text, at)

    @Test
    fun `포커스를 얻으면 말하고 끝나면 돌려준다`() = runTest {
        val output = FakeOutput()
        val focus = FakeFocus()
        coach(output, focus).deliver(msg("깜빡이", at = 0))

        assertEquals(listOf("깜빡이"), output.spoken)
        assertEquals(1, focus.requests)
        assertFalse(focus.held)
    }

    @Test
    fun `포커스 실패 시 재시도하다가 얻으면 말한다`() = runTest {
        val output = FakeOutput()
        val focus = FakeFocus().apply { grant = false }
        val job = launch { coach(output, focus).deliver(msg("급제동", at = 0)) }

        advanceTimeBy(1_100) // 0, 500, 1000ms 시도
        assertTrue(output.spoken.isEmpty())
        focus.grant = true
        advanceUntilIdle()
        job.join()

        assertEquals(listOf("급제동"), output.spoken)
        assertFalse(focus.held)
    }

    @Test
    fun `TTL이 지나도록 포커스를 못 얻으면 버린다`() = runTest {
        val output = FakeOutput()
        val focus = FakeFocus().apply { grant = false }
        coach(output, focus).deliver(msg("깜빡이", at = 0))

        assertTrue(output.spoken.isEmpty())
        assertTrue(testScheduler.currentTime > 4_000)
    }

    @Test
    fun `성적표 요약은 TTL이 지나도 말한다 (#61)`() = runTest {
        val output = FakeOutput()
        val focus = FakeFocus()
        advanceTimeBy(5_000)
        coach(output, focus).deliver(msg("성적표", at = 0, type = CoachType.REPORT))
        assertEquals(listOf("성적표"), output.spoken)
    }

    @Test
    fun `이미 오래된 메시지는 포커스를 요청하지 않고 버린다`() = runTest {
        val output = FakeOutput()
        val focus = FakeFocus()
        advanceTimeBy(5_000)
        coach(output, focus).deliver(msg("깜빡이", at = 0))

        assertEquals(0, focus.requests)
        assertTrue(output.spoken.isEmpty())
    }

    @Test
    fun `엔진이 준비되지 않았으면 포커스를 잡지 않고 기다린다`() = runTest {
        val output = FakeOutput().apply { isReady = false }
        val focus = FakeFocus()
        val job = launch { coach(output, focus).deliver(msg("깜빡이", at = 0)) }

        advanceTimeBy(600)
        assertEquals(0, focus.requests)
        output.isReady = true
        advanceUntilIdle()
        job.join()

        assertEquals(listOf("깜빡이"), output.spoken)
    }

    @Test
    fun `포커스를 잃으면 즉시 멈추고 다시 말하지 않는다`() = runTest {
        val output = FakeOutput().apply { gate = CompletableDeferred() }
        val focus = FakeFocus()
        val job = launch { coach(output, focus).deliver(msg("깜빡이", at = 0)) }
        runCurrent()
        assertTrue(focus.held)

        focus.onLoss!!.invoke() // 예: Gleo 호출로 LOSS_TRANSIENT
        job.join()

        assertEquals(1, output.stopCount)
        assertEquals(listOf("깜빡이"), output.spoken)
        assertFalse(focus.held)
    }

    @Test
    fun `말하는 동안 들어온 메시지는 최신 하나만 이어서 말한다`() = runTest {
        val output = FakeOutput().apply { gate = CompletableDeferred() }
        val focus = FakeFocus()
        val messages = MutableSharedFlow<CoachMessage>()
        val coach = coach(output, focus)
        coach.start(backgroundScope, messages)
        runCurrent()

        messages.emit(msg("첫째", at = 0))
        runCurrent()
        messages.emit(msg("둘째", at = 100))
        messages.emit(msg("셋째", at = 200, type = CoachType.HARD_BRAKE))
        runCurrent()
        val first = output.gate!!
        output.gate = null
        first.complete(true)
        runCurrent() // backgroundScope 작업은 advanceUntilIdle이 기다리지 않는다

        assertEquals(listOf("첫째", "셋째"), output.spoken)
        coach.stop()
    }
}

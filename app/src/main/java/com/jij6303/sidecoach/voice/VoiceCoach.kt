package com.jij6303.sidecoach.voice

import com.jij6303.sidecoach.feature.coach.CoachMessage
import com.jij6303.sidecoach.feature.coach.CoachType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

/**
 * 코칭 메시지를 오디오 포커스 정책에 맞춰 음성으로 내보낸다 (dev-architecture 6.1).
 *
 * - 발화 직전에 포커스를 요청하고, 끝나면 돌려준다.
 * - 포커스를 못 얻거나 엔진이 준비되지 않았으면 [retryMs]마다 다시 시도하고, [ttlMs]가 지나면 버린다.
 * - 포커스를 잃으면 즉시 멈추고 그 메시지는 다시 말하지 않는다.
 * - 말하는 동안 들어온 메시지는 최신 1개만 남긴다. 중재자가 이미 간격·우선순위를 정하므로 쌓아두지 않는다.
 *
 * [clock]은 메시지 createdAtMs와 같은 시계여야 한다 (실차: elapsedRealtime).
 */
class VoiceCoach(
    private val output: VoiceOutput,
    private val focus: AudioFocus,
    private val clock: () -> Long,
    private val ttlMs: Long = 4_000,
    private val retryMs: Long = 500,
    private val log: (String) -> Unit = {},
) {
    private var job: Job? = null

    fun start(scope: CoroutineScope, messages: Flow<CoachMessage>) {
        if (job?.isActive == true) return
        val inbox = Channel<CoachMessage>(capacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
        job = scope.launch {
            launch { messages.collect { inbox.send(it) } }
            for (message in inbox) deliver(message)
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        output.stop()
        focus.abandon()
    }

    internal suspend fun deliver(message: CoachMessage) {
        while (true) {
            val age = clock() - message.createdAtMs
            // 성적표 요약은 시간이 지나도 의미가 같아 버리지 않는다 (기어 경고 뒤에 기다리는 경우, #61)
            if (age > ttlMs && message.type != CoachType.REPORT) {
                log("drop ${message.type} age=${age}ms (ttl)")
                return
            }
            if (output.isReady && focus.request(onLoss = ::onFocusLoss)) break
            delay(retryMs)
        }
        try {
            val done = output.speak(message.text)
            log("${if (done) "spoke" else "interrupted"} ${message.type}: ${message.text}")
        } finally {
            focus.abandon()
        }
    }

    private fun onFocusLoss() {
        log("focus lost -> stop")
        output.stop()
    }
}

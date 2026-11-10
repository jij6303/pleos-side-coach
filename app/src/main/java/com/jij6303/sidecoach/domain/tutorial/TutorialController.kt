package com.jij6303.sidecoach.domain.tutorial

import com.jij6303.sidecoach.domain.DrivingSnapshot
import com.jij6303.sidecoach.domain.coach.CoachMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 튜토리얼 수명 관리: 시작하면 스냅샷을 구독하고, 끝내면 구독을 끊는다.
 * 음성은 [messages]를 VoiceCoach가 구독한다. [clock]은 스냅샷 timestamp와 같은 시계.
 */
class TutorialController(
    private val snapshots: Flow<DrivingSnapshot>,
    private val clock: () -> Long,
    private val engine: TutorialEngine = TutorialEngine(),
) {
    private val _state = MutableStateFlow<TutorialState?>(null)
    /** 진행 중인 튜토리얼. 없으면 null. */
    val state: StateFlow<TutorialState?> = _state.asStateFlow()

    private val _messages = MutableSharedFlow<CoachMessage>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<CoachMessage> = _messages.asSharedFlow()

    private val lock = Mutex()
    private var job: Job? = null

    fun start(scope: CoroutineScope, missions: List<TutorialMission>) {
        job?.cancel()
        job = scope.launch {
            lock.withLock { publish(engine.start(missions, clock())) }
            snapshots.collect { s -> lock.withLock { publish(engine.onSnapshot(s)) } }
        }
    }

    fun skip(scope: CoroutineScope) {
        scope.launch { lock.withLock { publish(engine.skip(clock())) } }
    }

    fun stop() {
        job?.cancel()
        job = null
        engine.stop()
        _state.value = null
    }

    private fun publish(message: CoachMessage?) {
        _state.value = engine.state
        message?.let { _messages.tryEmit(it) }
    }
}

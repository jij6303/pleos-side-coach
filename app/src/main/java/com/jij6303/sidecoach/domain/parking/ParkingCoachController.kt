package com.jij6303.sidecoach.domain.parking

import com.jij6303.sidecoach.domain.DrivingSnapshot
import com.jij6303.sidecoach.domain.coach.CoachMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
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

/** 화면에 보여줄 주차 코치 상태. */
data class ParkingUi(
    val state: ParkingState,
    val canTapNext: Boolean,
)

/**
 * 주차 코치 수명 관리 (#37). 코칭이 켜져 있는 동안 스냅샷을 구독하고, [tickMs]마다 멈춤 판정을 한다.
 * 음성은 [messages]를 VoiceCoach가 구독한다. [clock]은 스냅샷 timestamp와 같은 시계.
 */
class ParkingCoachController(
    private val snapshots: Flow<DrivingSnapshot>,
    private val clock: () -> Long,
    initialType: ParkingType?,
    private val engine: ParkingCoachEngine = ParkingCoachEngine(),
    private val tickMs: Long = 500,
    private val log: (String) -> Unit = {},
) {
    private val _ui = MutableStateFlow<ParkingUi?>(null)
    /** 진행 중인 주차 코치. 없으면 null. */
    val ui: StateFlow<ParkingUi?> = _ui.asStateFlow()

    private val _type = MutableStateFlow(initialType)
    /** 다음 R에서 시작할 유형. null이면 끔. */
    val type: StateFlow<ParkingType?> = _type.asStateFlow()

    private val _messages = MutableSharedFlow<CoachMessage>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<CoachMessage> = _messages.asSharedFlow()

    private val _lastMessage = MutableStateFlow<CoachMessage?>(null)
    val lastMessage: StateFlow<CoachMessage?> = _lastMessage.asStateFlow()

    private val lock = Mutex()
    private var job: Job? = null

    init {
        engine.type = initialType
    }

    fun start(scope: CoroutineScope) {
        if (job?.isActive == true) return
        job = scope.launch {
            launch {
                while (true) {
                    delay(tickMs)
                    lock.withLock { publish(engine.tick(clock())) }
                }
            }
            snapshots.collect { s ->
                lock.withLock {
                    if (engine.state != null && s.speedKmh != loggedSpeed) {
                        log("speed=${s.speedKmh} steering=${s.steeringDeg} brake=${s.brakePressed}")
                        loggedSpeed = s.speedKmh
                    }
                    publish(engine.onSnapshot(s))
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        engine.stop()
        _ui.value = null
    }

    fun tapNext(scope: CoroutineScope) {
        scope.launch { lock.withLock { publish(engine.tapNext(clock())) } }
    }

    /** 유형을 바꾼다. 진행 중인 코치에는 영향이 없고 다음 R부터 적용된다. */
    fun setType(scope: CoroutineScope, type: ParkingType?) {
        _type.value = type
        scope.launch { lock.withLock { engine.type = type } }
    }

    private var loggedStep: Pair<ParkingType, Int>? = null
    private var loggedSpeed: Float? = null

    private fun publish(message: CoachMessage?) {
        val st = engine.state
        val step = st?.let { it.type to it.stepIndex }
        if (step != loggedStep) {
            log("step ${st?.let { "${it.type} ${it.stepIndex + 1}/${it.steps.size} ${it.step.kind}" } ?: "none"}")
            loggedStep = step
        }
        _ui.value = st?.let { ParkingUi(it, engine.canTapNext) }
        message?.let {
            log("say ${it.text}")
            _lastMessage.value = it
            _messages.tryEmit(it)
        }
    }
}

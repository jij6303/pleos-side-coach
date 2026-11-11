package com.jij6303.sidecoach.domain.coach

import com.jij6303.sidecoach.data.navi.NaviEvent
import com.jij6303.sidecoach.domain.DrivingSnapshot
import com.jij6303.sidecoach.domain.detect.DetectionEvent
import com.jij6303.sidecoach.domain.detect.Detector
import com.jij6303.sidecoach.domain.report.ReportGenerator
import com.jij6303.sidecoach.domain.report.TripReport
import com.jij6303.sidecoach.domain.session.SessionState
import com.jij6303.sidecoach.domain.situation.SituationPreviewEngine
import com.jij6303.sidecoach.domain.trip.TripRecorder
import com.jij6303.sidecoach.domain.trip.TripSession
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
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * 스냅샷 → 디텍터 → 기록·중재 → 코칭 메시지.
 * 내비 이벤트는 [previewEngine]이 상황 예고로 바꿔 중재자에 넣는다.
 * 스냅샷과 내비 이벤트는 한 흐름으로 합쳐 순서대로 처리한다 (디텍터·중재자 동시 접근 방지).
 * [tripSession]이 주행 시작(P 아님)을 알리면 통계를 초기화하고, 종료(P)를 알리면 성적표를 만든다.
 * 음성 출력은 [messages]를 구독하는 VoiceCoach가 맡는다.
 *
 * 상태 저장(#31): 코칭 중 [saveIntervalMs]마다, 성적표가 만들어질 때, 멈출 때 [stateSink]로 내보낸다.
 * 처리·내보내기·복원은 같은 잠금 안에서 해 동시 접근을 막는다.
 */
class CoachingPipeline(
    private val updates: Flow<DrivingSnapshot>,
    private val detectors: List<Detector>,
    val arbiter: CoachingArbiter,
    val recorder: TripRecorder,
    private val tripSession: TripSession = TripSession(),
    private val naviEvents: Flow<NaviEvent> = emptyFlow(),
    private val previewEngine: SituationPreviewEngine = SituationPreviewEngine(),
    private val stateSink: (SessionState) -> Unit = {},
    private val saveIntervalMs: Long = 5_000,
) {
    private val lock = Any()

    /** 마지막으로 처리한 입력 시각 (저장 시각으로 쓴다). */
    private var lastInputMs = 0L

    private sealed interface Input {
        data class Snapshot(val s: DrivingSnapshot) : Input
        data class Navi(val e: NaviEvent) : Input
    }

    private val _messages = MutableSharedFlow<CoachMessage>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<CoachMessage> = _messages.asSharedFlow()

    private val _lastMessage = MutableStateFlow<CoachMessage?>(null)
    val lastMessage: StateFlow<CoachMessage?> = _lastMessage.asStateFlow()

    private val _lastReport = MutableStateFlow<TripReport?>(null)

    /** 마지막으로 끝난 주행의 성적표. 새 주행이 시작되거나 [dismissReport]하면 null. */
    val lastReport: StateFlow<TripReport?> = _lastReport.asStateFlow()

    private var job: Job? = null
    val isRunning: Boolean get() = job?.isActive == true

    fun start(scope: CoroutineScope) {
        if (isRunning) return
        job = scope.launch {
            launch {
                while (true) {
                    delay(saveIntervalMs)
                    save()
                }
            }
            merge(updates.map { Input.Snapshot(it) }, naviEvents.map { Input.Navi(it) }).collect {
                when (it) {
                    is Input.Snapshot -> process(it.s)
                    is Input.Navi -> processNavi(it.e)
                }
            }
        }
    }

    /** 멈춘다. 멈추기 전 상태를 저장해 다시 시작하면 이어갈 수 있게 한다. */
    fun stop() {
        job?.cancel()
        job = null
        save()
        detectors.forEach { it.reset() }
        tripSession.reset()
        previewEngine.reset()
    }

    fun dismissReport() {
        _lastReport.value = null
        save()
    }

    /** 지금 상태. 진행 중 주행이 없으면 trip은 null. */
    fun exportState(): SessionState = synchronized(lock) {
        SessionState(
            savedAtMs = lastInputMs,
            recorder = recorder.exportState(),
            arbiter = arbiter.exportState(),
            trip = tripSession.exportState(),
            announced = previewEngine.exportState(),
            pendingReport = _lastReport.value,
        )
    }

    /** 저장된 상태에서 이어간다. [nowMs]는 지금 시각(스냅샷과 같은 시계). */
    fun restore(state: SessionState, nowMs: Long) = synchronized(lock) {
        recorder.restore(state.recorder)
        arbiter.restore(state.arbiter)
        tripSession.restore(state.trip, nowMs)
        previewEngine.restore(state.announced)
        _lastReport.value = state.pendingReport
        lastInputMs = nowMs
    }

    private fun save() {
        if (lastInputMs == 0L) return // 아직 아무 입력도 없으면 저장할 것도 없다
        stateSink(exportState())
    }

    /** 새 주행 시작: 통계·발화 횟수 초기화. */
    fun resetTrip() {
        detectors.forEach { it.reset() }
        arbiter.reset()
        recorder.reset()
        previewEngine.reset()
        _lastMessage.value = null
        _lastReport.value = null
    }

    /** 스냅샷 하나를 처리하고 이번에 말하기로 정한 메시지를 돌려준다. */
    internal fun process(s: DrivingSnapshot): CoachMessage? = synchronized(lock) {
        lastInputMs = s.timestampMs
        val tripEvent = tripSession.onSnapshot(s)
        if (tripEvent == TripSession.Event.Started) resetTrip()

        val produced = mutableListOf<CoachMessage>()
        for (detector in detectors) {
            val event = detector.onSnapshot(s) ?: continue
            val praise = recorder.record(event)
            toMessages(event, praise).forEach {
                produced += it
                arbiter.submit(it)
            }
        }

        if (tripEvent is TripSession.Event.Ended) {
            // 움직이는 중 P에 넣으면 같은 순간 주행이 끝난다. 이 경고는 성적표보다 먼저 바로 말한다.
            produced.firstOrNull { it.type == CoachType.GEAR_SHIFT_MOVING }?.let {
                _lastMessage.value = it
                _messages.tryEmit(it)
            }
            val report = ReportGenerator.generate(tripEvent.metrics, recorder.stats.value)
            _lastReport.value = report
            // 성적표 요약은 중재자를 거치지 않고 바로 음성으로 보낸다 (주행이 끝나 다른 코칭과 겹치지 않음)
            _messages.tryEmit(CoachMessage(CoachType.REPORT, report.announcement, s.timestampMs))
            stateSink(exportState()) // 성적표는 바로 저장 (보기 전에 프로세스가 내려가도 남도록)
            return null
        }
        return speakNext(s.timestampMs)
    }

    /** 내비 이벤트 하나를 처리하고 이번에 말하기로 정한 메시지를 돌려준다. */
    internal fun processNavi(e: NaviEvent): CoachMessage? = synchronized(lock) {
        lastInputMs = e.timestampMs
        previewEngine.onEvent(e)?.let(arbiter::submit)
        return speakNext(e.timestampMs)
    }

    private fun speakNext(nowMs: Long): CoachMessage? {
        val message = arbiter.next(nowMs) ?: return null
        _lastMessage.value = message
        _messages.tryEmit(message)
        return message
    }

    private fun toMessages(event: DetectionEvent, praise: Boolean): List<CoachMessage> {
        val ts = event.timestampMs
        return when (event) {
            is DetectionEvent.LaneChange -> when {
                !event.signalled -> listOf(CoachMessage(CoachType.BLINKER_OMISSION, MessageCatalog.blinkerOmission(), ts))
                praise -> listOf(CoachMessage(CoachType.PRAISE, MessageCatalog.praise(), ts))
                else -> emptyList()
            }
            is DetectionEvent.HardBrake ->
                listOf(CoachMessage(CoachType.HARD_BRAKE, MessageCatalog.hardBrake(), ts))
            is DetectionEvent.BlinkerLeftOn ->
                listOf(CoachMessage(CoachType.BLINKER_LEFT_ON, MessageCatalog.blinkerLeftOn(), ts))
            is DetectionEvent.TurnTooFast ->
                listOf(CoachMessage(CoachType.TURN_SPEED, MessageCatalog.turnSpeed(), ts))
            is DetectionEvent.GearShiftWhileMoving ->
                listOf(CoachMessage(CoachType.GEAR_SHIFT_MOVING, MessageCatalog.gearShiftMoving(), ts))
        }
    }
}

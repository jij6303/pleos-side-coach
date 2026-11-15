package com.jij6303.sidecoach.feature.coach

import com.jij6303.sidecoach.data.navi.NaviEvent
import com.jij6303.sidecoach.feature.coach.detect.DetectionEvent
import com.jij6303.sidecoach.feature.coach.detect.DetectorManager
import com.jij6303.sidecoach.feature.coach.situation.SituationPreviewEngine
import com.jij6303.sidecoach.feature.report.TripReport
import com.jij6303.sidecoach.feature.report.trip.TripRecorder
import com.jij6303.sidecoach.feature.report.trip.TripSession
import com.jij6303.sidecoach.state.DrivingSnapshot
import com.jij6303.sidecoach.state.lane.CurrentLaneTracker
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
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * 코칭 흐름의 조율자. 스냅샷과 내비 경로를 한 흐름으로 합쳐 순서대로 세 담당에게 나눠 준다.
 * - [detectors]: 조작 판정 → 교정·칭찬 후보
 * - [previewEngine]: 경로 위 남은 거리 → 상황 예고 후보
 * - [recorder]: 주행 기록 → 주행이 끝나면 성적표 (요약 음성은 ReportGenerator가 VoiceCoach로 바로 보낸다)
 *
 * 후보는 [arbiter]가 우선순위·간격으로 하나 골라 [messages]로 내보낸다.
 * 한 흐름에서 순서대로 처리하므로 각 담당에 동시 접근이 없다. 처리·저장·복원은 같은 잠금 안에서 한다.
 * 코칭 중 [saveIntervalMs]마다, 성적표가 나올 때, 멈출 때 [stateSink]로 상태를 내보낸다 (저장은 app의 SessionStore).
 */
class CoachingPipeline(
    private val updates: Flow<DrivingSnapshot>,
    private val detectors: DetectorManager,
    val arbiter: CoachingArbiter = CoachingArbiter(),
    val recorder: TripRecorder = TripRecorder(),
    private val naviEvents: Flow<NaviEvent> = emptyFlow(),
    private val previewEngine: SituationPreviewEngine = SituationPreviewEngine(),
    /** 운전자가 알려준 차로(#69). 차선 변경으로 추적하고 상황 예고 문구에 쓴다. */
    val lane: CurrentLaneTracker = CurrentLaneTracker(),
    /** 스냅샷과 같은 시계. 같은 차속이 이어져 스냅샷이 없을 때 예고 거리를 갱신하는 타이머에 쓴다. */
    private val clock: () -> Long = System::currentTimeMillis,
    private val tickMs: Long = 1_000,
    private val stateSink: (State) -> Unit = {},
    private val saveIntervalMs: Long = 5_000,
) {
    /** 이어가기에 필요한 코칭 상태. [savedAtMs]는 스냅샷과 같은 시계(elapsedRealtime)다. */
    data class State(
        val savedAtMs: Long,
        val trip: TripRecorder.State,
        val praiseStreak: Int,
        val arbiter: CoachingArbiter.State,
        val preview: SituationPreviewEngine.State,
    )

    /** 마지막으로 끝난 주행의 성적표. */
    val lastReport: StateFlow<TripReport?> get() = recorder.lastReport

    private val lock = Any()

    /** 마지막으로 처리한 입력 시각 (저장 시각으로 쓴다). */
    private var lastInputMs = 0L

    private sealed interface Input {
        data class Snapshot(val s: DrivingSnapshot) : Input
        data class Navi(val e: NaviEvent) : Input
        data class Tick(val nowMs: Long) : Input
    }

    private val _messages = MutableSharedFlow<CoachMessage>(
        extraBufferCapacity = 8,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<CoachMessage> = _messages.asSharedFlow()

    private val _lastMessage = MutableStateFlow<CoachMessage?>(null)
    val lastMessage: StateFlow<CoachMessage?> = _lastMessage.asStateFlow()

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
            val ticks = flow {
                while (true) {
                    delay(tickMs)
                    emit(Input.Tick(clock()))
                }
            }
            merge(updates.map { Input.Snapshot(it) }, naviEvents.map { Input.Navi(it) }, ticks).collect {
                when (it) {
                    is Input.Snapshot -> process(it.s)
                    is Input.Navi -> processNavi(it.e)
                    is Input.Tick -> processTick(it.nowMs)
                }
            }
        }
    }

    /** 멈춘다. 멈추기 전 상태를 저장해 다시 시작하면 이어갈 수 있게 한다. */
    fun stop() {
        job?.cancel()
        job = null
        save()
        detectors.reset()
        recorder.stop()
        previewEngine.reset()
        lane.clear()
    }

    fun dismissReport() {
        recorder.reports.dismiss()
        save()
    }

    fun exportState(): State = synchronized(lock) {
        State(
            savedAtMs = lastInputMs,
            trip = recorder.exportState(),
            praiseStreak = detectors.exportState(),
            arbiter = arbiter.exportState(),
            preview = previewEngine.exportState(),
        )
    }

    /** 저장된 상태에서 이어간다. [nowMs]는 지금 시각(스냅샷과 같은 시계). */
    fun restore(state: State, nowMs: Long) = synchronized(lock) {
        recorder.restore(state.trip, nowMs)
        detectors.restore(state.praiseStreak)
        arbiter.restore(state.arbiter)
        previewEngine.restore(state.preview, nowMs)
        lastInputMs = nowMs
    }

    private fun save() {
        if (lastInputMs == 0L) return // 아직 아무 입력도 없으면 저장할 것도 없다
        stateSink(exportState())
    }

    /** 새 주행: 판정 진행, 발화 횟수, 예고한 지점을 비운다 (통계는 [recorder]가 비운다). */
    private fun resetForNewTrip() {
        detectors.reset()
        arbiter.reset()
        previewEngine.reset()
        _lastMessage.value = null
    }

    /** 스냅샷 하나를 처리하고 이번에 말하기로 정한 메시지를 돌려준다. */
    internal fun process(s: DrivingSnapshot): CoachMessage? = synchronized(lock) {
        lastInputMs = s.timestampMs
        val tripEvent = recorder.onSnapshot(s)
        if (tripEvent == TripSession.Event.Started) resetForNewTrip()

        lane.onSnapshot(s)
        val detection = detectors.onSnapshot(s)
        detection.events.filterIsInstance<DetectionEvent.LaneChange>().forEach { lane.onLaneChange(it.direction) }
        val candidates = detection.candidates + previewEngine.onSnapshot(s, lane.current(s.timestampMs))

        if (tripEvent is TripSession.Event.Ended) {
            // 움직이는 중 P에 넣으면 같은 순간 주행이 끝난다. 이 경고는 중재자를 거치지 않고 성적표보다 먼저 바로 말한다
            // (중재자에도 넣으면 다음 스냅샷에서 한 번 더 말해 성적표 음성을 밀어낸다, #61 실측).
            val gearWarning = candidates.firstOrNull { it.type == CoachType.GEAR_SHIFT_MOVING }
            candidates.filter { it !== gearWarning }.forEach(arbiter::submit)
            gearWarning?.let(::emit)
            recorder.record(detection.events, s.timestampMs) // 성적표를 만들고 요약 음성을 보낸다
            stateSink(exportState()) // 성적표는 바로 저장 (보기 전에 프로세스가 내려가도 남도록)
            return null
        }
        recorder.record(detection.events, s.timestampMs)
        candidates.forEach(arbiter::submit)
        return speakNext(s.timestampMs)
    }

    /** 내비 이벤트(경로 시작·끝) 하나를 처리한다. */
    internal fun processNavi(e: NaviEvent): CoachMessage? = synchronized(lock) {
        lastInputMs = e.timestampMs
        previewEngine.onNavi(e)
        return speakNext(e.timestampMs)
    }

    /** 타이머: 스냅샷이 없어도 예고 거리를 갱신하고, 간격 때문에 기다리던 메시지를 내보낸다. */
    internal fun processTick(nowMs: Long): CoachMessage? = synchronized(lock) {
        previewEngine.onTick(nowMs, lane.current(nowMs)).forEach(arbiter::submit)
        return speakNext(nowMs)
    }

    private fun speakNext(nowMs: Long): CoachMessage? {
        val message = arbiter.next(nowMs) ?: return null
        emit(message)
        return message
    }

    private fun emit(message: CoachMessage) {
        _lastMessage.value = message
        _messages.tryEmit(message)
    }
}

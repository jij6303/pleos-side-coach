package com.jij6303.sidecoach.feature.coach

import com.jij6303.sidecoach.data.navi.NaviEvent
import com.jij6303.sidecoach.feature.Restorable
import com.jij6303.sidecoach.feature.coach.detect.DetectionEvent
import com.jij6303.sidecoach.feature.coach.detect.DetectorManager
import com.jij6303.sidecoach.feature.coach.situation.SituationPreviewEngine
import com.jij6303.sidecoach.feature.report.TripReport
import com.jij6303.sidecoach.feature.report.TripReporter
import com.jij6303.sidecoach.feature.report.trip.TripRecorder
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
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/**
 * 코칭 흐름의 조율자. 스냅샷과 내비 이벤트를 한 흐름으로 합쳐 순서대로 각 담당에게 넘긴다.
 * 스스로 판단하지 않고, 누구를 어떤 순서로 부를지만 정한다.
 *
 * 스냅샷 하나:
 * 1. [reporter] 주행 시작·이어가기·종료 판단
 * 2. [lane] 지금 차로 추적
 * 3. [detectors] 디텍터 매니저가 판정 결과를 모음
 * 4. 판정 결과마다 [praise] 칭찬 판단, [reporter] 기록, [CoachMessageFactory] 메시지로 바꾸기
 * 5. [arbiter] 중재자가 지금 말할 메시지 하나를 고름 (주행이 끝났으면 [reporter]가 성적표를 만들고 바로 말함)
 *
 * 내비 이벤트 하나: [previewEngine] 상황 예고 → [arbiter].
 *
 * 처리·내보내기·복원은 같은 잠금 안에서 해 동시 접근을 막는다.
 * 코칭 중 [saveIntervalMs]마다, 성적표가 나올 때, 멈출 때 [stateSink]로 상태를 내보낸다 (저장은 app이 맡는다).
 */
class CoachingPipeline(
    private val updates: Flow<DrivingSnapshot>,
    private val detectors: DetectorManager,
    val arbiter: CoachingArbiter = CoachingArbiter(),
    val reporter: TripReporter = TripReporter(),
    private val praise: PraiseCounter = PraiseCounter(),
    private val naviEvents: Flow<NaviEvent> = emptyFlow(),
    private val previewEngine: SituationPreviewEngine = SituationPreviewEngine(),
    /** 운전자가 알려준 차로(#69). 차선 변경으로 추적하고 상황 예고 문구에 쓴다. */
    val lane: CurrentLaneTracker = CurrentLaneTracker(),
    private val stateSink: (State) -> Unit = {},
    private val saveIntervalMs: Long = 5_000,
) : Restorable<CoachingPipeline.State> {

    /** 이어가기에 필요한 코칭 상태. [savedAtMs]는 스냅샷과 같은 시계(elapsedRealtime)다. */
    data class State(
        val savedAtMs: Long,
        val trip: TripReporter.State,
        val praiseStreak: Int,
        val arbiter: CoachingArbiter.State,
        val announced: Map<String, Long>,
    )

    val recorder: TripRecorder get() = reporter.recorder

    /** 마지막으로 끝난 주행의 성적표. */
    val lastReport: StateFlow<TripReport?> get() = reporter.lastReport

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
        detectors.reset()
        reporter.stop()
        previewEngine.reset()
        lane.clear()
    }

    fun dismissReport() {
        reporter.dismissReport()
        save()
    }

    override fun exportState(): State = synchronized(lock) {
        State(
            savedAtMs = lastInputMs,
            trip = reporter.exportState(),
            praiseStreak = praise.exportState(),
            arbiter = arbiter.exportState(),
            announced = previewEngine.exportState(),
        )
    }

    /** 저장된 상태에서 이어간다. [nowMs]는 지금 시각(스냅샷과 같은 시계). */
    override fun restore(state: State, nowMs: Long) = synchronized(lock) {
        reporter.restore(state.trip, nowMs)
        praise.restore(state.praiseStreak, nowMs)
        arbiter.restore(state.arbiter, nowMs)
        previewEngine.restore(state.announced, nowMs)
        lastInputMs = nowMs
    }

    private fun save() {
        if (lastInputMs == 0L) return // 아직 아무 입력도 없으면 저장할 것도 없다
        stateSink(exportState())
    }

    /** 새 주행 시작: 코칭 쪽 상태(판정 진행, 발화 횟수, 칭찬 연속, 예고한 지점)를 비운다. */
    private fun resetForNewTrip() {
        detectors.reset()
        arbiter.reset()
        praise.reset()
        previewEngine.reset()
        _lastMessage.value = null
    }

    /** 스냅샷 하나를 처리하고 이번에 말하기로 정한 메시지를 돌려준다. */
    internal fun process(s: DrivingSnapshot): CoachMessage? = synchronized(lock) {
        lastInputMs = s.timestampMs
        val change = reporter.onSnapshot(s)
        if (change is TripReporter.Change.Started) resetForNewTrip()

        lane.onSnapshot(s)
        val produced = detectors.onSnapshot(s).mapNotNull(::handle)

        if (change is TripReporter.Change.Ended) {
            finishTrip(reporter.issueReport(change), produced, s.timestampMs)
            return null
        }
        produced.forEach(arbiter::submit)
        return speakNext(s.timestampMs)
    }

    /** 판정 결과 하나를 차로·칭찬·기록에 반영하고, 말할 메시지가 있으면 돌려준다. */
    private fun handle(event: DetectionEvent): CoachMessage? {
        var praised = false
        if (event is DetectionEvent.LaneChange) {
            lane.onLaneChange(event.direction)
            praised = praise.onLaneChange(event.signalled)
        }
        reporter.record(event)
        return CoachMessageFactory.from(event, praised)
    }

    /**
     * 주행이 끝난 순간. 움직이는 중 P에 넣은 경고는 중재자를 거치지 않고 성적표보다 먼저 바로 말한다
     * (중재자에도 넣으면 다음 스냅샷에서 한 번 더 말해 성적표 음성을 밀어낸다, #61 실측).
     * 성적표 요약도 다른 코칭과 겹치지 않으므로 바로 음성으로 보낸다.
     */
    private fun finishTrip(report: TripReport, produced: List<CoachMessage>, nowMs: Long) {
        val immediate = produced.firstOrNull { it.type == CoachType.GEAR_SHIFT_MOVING }
        produced.filter { it !== immediate }.forEach(arbiter::submit)
        immediate?.let {
            _lastMessage.value = it
            _messages.tryEmit(it)
        }
        _messages.tryEmit(CoachMessage(CoachType.REPORT, report.announcement, nowMs))
        stateSink(exportState()) // 성적표는 바로 저장 (보기 전에 프로세스가 내려가도 남도록)
    }

    /** 내비 이벤트 하나를 처리하고 이번에 말하기로 정한 메시지를 돌려준다. */
    internal fun processNavi(e: NaviEvent): CoachMessage? = synchronized(lock) {
        lastInputMs = e.timestampMs
        previewEngine.onEvent(e, lane.current(e.timestampMs))?.let(arbiter::submit)
        return speakNext(e.timestampMs)
    }

    private fun speakNext(nowMs: Long): CoachMessage? {
        val message = arbiter.next(nowMs) ?: return null
        _lastMessage.value = message
        _messages.tryEmit(message)
        return message
    }
}

package com.jij6303.sidecoach.domain.coach

import com.jij6303.sidecoach.domain.DrivingSnapshot
import com.jij6303.sidecoach.domain.detect.DetectionEvent
import com.jij6303.sidecoach.domain.detect.Detector
import com.jij6303.sidecoach.domain.report.ReportGenerator
import com.jij6303.sidecoach.domain.report.TripReport
import com.jij6303.sidecoach.domain.trip.TripRecorder
import com.jij6303.sidecoach.domain.trip.TripSession
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

/**
 * 스냅샷 → 디텍터 → 기록·중재 → 코칭 메시지.
 * [tripSession]이 주행 시작(P 아님)을 알리면 통계를 초기화하고, 종료(P)를 알리면 성적표를 만든다.
 * 음성 출력은 [messages]를 구독하는 VoiceCoach가 맡는다.
 */
class CoachingPipeline(
    private val updates: Flow<DrivingSnapshot>,
    private val detectors: List<Detector>,
    val arbiter: CoachingArbiter,
    val recorder: TripRecorder,
    private val tripSession: TripSession = TripSession(),
) {
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
        job = scope.launch { updates.collect { process(it) } }
    }

    fun stop() {
        job?.cancel()
        job = null
        detectors.forEach { it.reset() }
        tripSession.reset()
    }

    fun dismissReport() {
        _lastReport.value = null
    }

    /** 새 주행 시작: 통계·발화 횟수 초기화. */
    fun resetTrip() {
        detectors.forEach { it.reset() }
        arbiter.reset()
        recorder.reset()
        _lastMessage.value = null
        _lastReport.value = null
    }

    /** 스냅샷 하나를 처리하고 이번에 말하기로 정한 메시지를 돌려준다. */
    internal fun process(s: DrivingSnapshot): CoachMessage? {
        val tripEvent = tripSession.onSnapshot(s)
        if (tripEvent == TripSession.Event.Started) resetTrip()

        for (detector in detectors) {
            val event = detector.onSnapshot(s) ?: continue
            val praise = recorder.record(event)
            toMessages(event, praise).forEach(arbiter::submit)
        }

        if (tripEvent is TripSession.Event.Ended) {
            val report = ReportGenerator.generate(tripEvent.metrics, recorder.stats.value)
            _lastReport.value = report
            // 성적표 요약은 중재자를 거치지 않고 바로 음성으로 보낸다 (주행이 끝나 다른 코칭과 겹치지 않음)
            _messages.tryEmit(CoachMessage(CoachType.REPORT, report.announcement, s.timestampMs))
            return null
        }
        val message = arbiter.next(s.timestampMs) ?: return null
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
        }
    }
}

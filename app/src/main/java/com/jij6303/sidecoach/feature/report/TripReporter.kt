package com.jij6303.sidecoach.feature.report

import com.jij6303.sidecoach.feature.Restorable
import com.jij6303.sidecoach.feature.coach.detect.DetectionEvent
import com.jij6303.sidecoach.feature.report.trip.TripMetrics
import com.jij6303.sidecoach.feature.report.trip.TripRecorder
import com.jij6303.sidecoach.feature.report.trip.TripSession
import com.jij6303.sidecoach.feature.report.trip.TripStats
import com.jij6303.sidecoach.state.DrivingSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 주행 기록과 성적표를 맡는다.
 * - [TripSession]으로 주행 시작·이어가기·종료를 나누고,
 * - [TripRecorder]에 판정 결과를 쌓고,
 * - 주행이 끝나면 [ReportGenerator]로 성적표를 만든다.
 *
 * 끝나는 스냅샷의 판정 결과도 성적표에 들어가야 하므로, 종료([Change.Ended])를 알린 뒤
 * 그 스냅샷의 기록을 마치고 [issueReport]를 부른다.
 */
class TripReporter(
    val recorder: TripRecorder = TripRecorder(),
    private val session: TripSession = TripSession(),
) : Restorable<TripReporter.State> {

    /** 저장용: 주행 통계, 진행 중 주행(없으면 null), 아직 안 본 성적표. */
    data class State(
        val stats: TripStats,
        val session: TripSession.State?,
        val pendingReport: TripReport?,
    )

    /** 스냅샷 하나에서 일어난 주행 단위 변화. */
    sealed interface Change {
        /** 새 주행이 시작됨. 통계는 비웠다. */
        data object Started : Change
        /** P에 잠깐 넣었다 다시 출발. 통계는 이어간다. */
        data object Resumed : Change
        /** 주행이 끝남. 이 스냅샷의 기록을 마친 뒤 [issueReport]로 성적표를 만든다. */
        data class Ended(val metrics: TripMetrics) : Change
    }

    private val _lastReport = MutableStateFlow<TripReport?>(null)

    /** 마지막으로 끝난 주행의 성적표. 새 주행이 시작되거나 [dismissReport]하면 null. */
    val lastReport: StateFlow<TripReport?> = _lastReport.asStateFlow()

    fun onSnapshot(s: DrivingSnapshot): Change? = when (val e = session.onSnapshot(s)) {
        TripSession.Event.Started -> {
            recorder.reset()
            _lastReport.value = null
            Change.Started
        }
        // 떠 있던 성적표만 닫고 통계는 이어간다 (#51)
        TripSession.Event.Resumed -> {
            _lastReport.value = null
            Change.Resumed
        }
        is TripSession.Event.Ended -> Change.Ended(e.metrics)
        TripSession.Event.Discarded, null -> null
    }

    fun record(event: DetectionEvent) = recorder.record(event)

    /** 끝난 주행의 성적표를 만들어 [lastReport]에 올린다. */
    fun issueReport(ended: Change.Ended): TripReport =
        ReportGenerator.generate(ended.metrics, recorder.stats.value).also { _lastReport.value = it }

    fun dismissReport() {
        _lastReport.value = null
    }

    /** 코칭을 멈출 때: 진행 중 주행을 끊는다 (통계와 성적표는 저장해 둔 상태로 남는다). */
    fun stop() = session.reset()

    override fun exportState(): State =
        State(recorder.exportState(), session.exportState(), _lastReport.value)

    override fun restore(state: State, nowMs: Long) {
        recorder.restore(state.stats, nowMs)
        session.restore(state.session, nowMs)
        _lastReport.value = state.pendingReport
    }
}

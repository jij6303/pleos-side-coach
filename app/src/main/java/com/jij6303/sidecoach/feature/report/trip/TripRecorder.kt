package com.jij6303.sidecoach.feature.report.trip

import com.jij6303.sidecoach.feature.coach.detect.DetectionEvent
import com.jij6303.sidecoach.feature.report.ReportGenerator
import com.jij6303.sidecoach.feature.report.TripReport
import com.jij6303.sidecoach.state.DrivingSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TripStats(
    val laneChanges: Int = 0,
    val signalledLaneChanges: Int = 0,
    val hardBrakes: Int = 0,
    /** 조향 후 깜빡이를 켠 채 계속 달린 횟수 (#49, #53). */
    val blinkerLeftOn: Int = 0,
    /** 회전 중 과속 횟수 (#49, #53). */
    val turnTooFast: Int = 0,
    /** 움직이는 중 기어 변경 횟수 (#49, #53). */
    val gearShiftsMoving: Int = 0,
) {
    /** 깜빡이 사용률 (0~1). 차선 변경이 없으면 null. */
    val blinkerUsageRate: Float?
        get() = if (laneChanges == 0) null else signalledLaneChanges.toFloat() / laneChanges
}

/**
 * 주행 기록. 주행 단위를 나누고([TripSession]: 시작·이어가기·끝), 그 동안의 판정 결과를 횟수로 쌓는다.
 * 주행이 끝나면 [reports]에 넘겨 성적표를 만든다.
 *
 * 끝나는 스냅샷의 판정 결과(예: 움직이는 중 P)도 성적표에 넣어야 하므로 한 스냅샷은 두 번에 나눠 받는다:
 * [onSnapshot]으로 주행 단위를 판단하고, 판정을 마친 뒤 [record]로 결과를 쌓으면서 끝났으면 성적표를 만든다.
 */
class TripRecorder(
    val reports: ReportGenerator = ReportGenerator(),
    private val session: TripSession = TripSession(),
) {
    /** 저장용: 주행 통계, 진행 중 주행(없으면 null), 아직 안 본 성적표. */
    data class State(val stats: TripStats, val session: TripSession.State?, val pendingReport: TripReport?)

    private val _stats = MutableStateFlow(TripStats())
    val stats: StateFlow<TripStats> = _stats.asStateFlow()

    /** 끝난 주행의 성적표. */
    val lastReport: StateFlow<TripReport?> get() = reports.lastReport

    /** 이번 스냅샷에서 끝난 주행. [record]에서 성적표로 넘긴다. */
    private var ended: TripMetrics? = null

    /**
     * 주행 단위 판단. 새 주행이면 통계를 비우고, 잠깐 P에 넣었다 다시 출발하면 통계는 이어가고 떠 있던 성적표만 닫는다 (#51).
     */
    fun onSnapshot(s: DrivingSnapshot): TripSession.Event? {
        val event = session.onSnapshot(s)
        when (event) {
            TripSession.Event.Started -> {
                _stats.value = TripStats()
                reports.dismiss()
            }
            TripSession.Event.Resumed -> reports.dismiss()
            is TripSession.Event.Ended -> ended = event.metrics
            else -> Unit
        }
        return event
    }

    /** 판정 결과를 쌓는다. 이번 스냅샷에서 주행이 끝났으면 성적표를 만든다. */
    fun record(events: List<DetectionEvent>, nowMs: Long) {
        events.forEach(::count)
        ended?.let { reports.publish(it, _stats.value, nowMs) }
        ended = null
    }

    private fun count(event: DetectionEvent) {
        val s = _stats.value
        _stats.value = when (event) {
            is DetectionEvent.LaneChange -> s.copy(
                laneChanges = s.laneChanges + 1,
                signalledLaneChanges = s.signalledLaneChanges + if (event.signalled) 1 else 0,
            )
            is DetectionEvent.HardBrake -> s.copy(hardBrakes = s.hardBrakes + 1)
            is DetectionEvent.BlinkerLeftOn -> s.copy(blinkerLeftOn = s.blinkerLeftOn + 1)
            is DetectionEvent.TurnTooFast -> s.copy(turnTooFast = s.turnTooFast + 1)
            is DetectionEvent.GearShiftWhileMoving -> s.copy(gearShiftsMoving = s.gearShiftsMoving + 1)
        }
    }

    /** 코칭을 멈출 때: 진행 중 주행을 끊는다. 통계와 성적표는 남긴다. */
    fun stop() {
        session.reset()
        ended = null
    }

    fun exportState(): State = State(_stats.value, session.exportState(), reports.exportState())

    /** 저장된 주행을 이어간다. 내려가 있던 동안은 주행 시간·거리에 넣지 않는다. */
    fun restore(state: State, nowMs: Long) {
        _stats.value = state.stats
        session.restore(state.session, nowMs)
        reports.restore(state.pendingReport)
    }
}

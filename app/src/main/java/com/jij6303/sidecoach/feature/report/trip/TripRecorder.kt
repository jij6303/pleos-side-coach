package com.jij6303.sidecoach.feature.report.trip

import com.jij6303.sidecoach.feature.Restorable
import com.jij6303.sidecoach.feature.coach.detect.DetectionEvent
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

/** 주행 중 판정 결과를 횟수로 쌓는다. 성적표가 이 통계로 점수를 매긴다. */
class TripRecorder : Restorable<TripStats> {

    private val _stats = MutableStateFlow(TripStats())
    val stats: StateFlow<TripStats> = _stats.asStateFlow()

    fun record(event: DetectionEvent) {
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

    fun reset() {
        _stats.value = TripStats()
    }

    override fun exportState(): TripStats = _stats.value

    override fun restore(state: TripStats, nowMs: Long) {
        _stats.value = state
    }
}

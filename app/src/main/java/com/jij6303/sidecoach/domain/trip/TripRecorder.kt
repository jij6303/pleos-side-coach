package com.jij6303.sidecoach.domain.trip

import com.jij6303.sidecoach.domain.detect.DetectionEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class TripStats(
    val laneChanges: Int = 0,
    val signalledLaneChanges: Int = 0,
    val hardBrakes: Int = 0,
) {
    /** 깜빡이 사용률 (0~1). 차선 변경이 없으면 null. */
    val blinkerUsageRate: Float?
        get() = if (laneChanges == 0) null else signalledLaneChanges.toFloat() / laneChanges
}

/** 주행 중 사건을 집계한다. 성적표와 칭찬 트리거에 쓴다. */
class TripRecorder(private val praiseStreak: Int = DEFAULT_PRAISE_STREAK) {

    private val _stats = MutableStateFlow(TripStats())
    val stats: StateFlow<TripStats> = _stats.asStateFlow()

    private var streak = 0

    /** 사건을 기록한다. 깜빡이를 켠 차선 변경이 [praiseStreak]회 연속되면 true(칭찬할 때). */
    fun record(event: DetectionEvent): Boolean {
        val s = _stats.value
        return when (event) {
            is DetectionEvent.LaneChange -> {
                _stats.value = s.copy(
                    laneChanges = s.laneChanges + 1,
                    signalledLaneChanges = s.signalledLaneChanges + if (event.signalled) 1 else 0,
                )
                if (event.signalled) {
                    streak++
                    if (streak >= praiseStreak) {
                        streak = 0
                        true
                    } else {
                        false
                    }
                } else {
                    streak = 0
                    false
                }
            }
            is DetectionEvent.HardBrake -> {
                _stats.value = s.copy(hardBrakes = s.hardBrakes + 1)
                false
            }
        }
    }

    fun reset() {
        _stats.value = TripStats()
        streak = 0
    }

    companion object {
        /** 💭 깜빡이 켠 차선 변경 3회 연속이면 칭찬. */
        const val DEFAULT_PRAISE_STREAK = 3
    }

    /** 저장용 상태: 통계 + 칭찬 연속 횟수. */
    data class State(val stats: TripStats, val praiseStreak: Int)

    fun exportState(): State = State(_stats.value, streak)

    fun restore(state: State) {
        _stats.value = state.stats
        streak = state.praiseStreak
    }
}

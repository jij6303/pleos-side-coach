package com.jij6303.sidecoach.feature.coach

import com.jij6303.sidecoach.feature.Restorable

/**
 * 칭찬할 때를 정한다. 깜빡이를 켠 차선 변경이 [streakToPraise]회 연속되면 칭찬하고 다시 센다.
 * 깜빡이 없이 바꾸면 처음부터 다시 센다. 상태는 지금까지 이어진 연속 횟수다.
 */
class PraiseCounter(private val streakToPraise: Int = DEFAULT_STREAK) : Restorable<Int> {

    private var streak = 0

    /** 차선 변경 하나를 센다. 칭찬할 때면 true. */
    fun onLaneChange(signalled: Boolean): Boolean {
        if (!signalled) {
            streak = 0
            return false
        }
        streak++
        if (streak < streakToPraise) return false
        streak = 0
        return true
    }

    fun reset() {
        streak = 0
    }

    override fun exportState(): Int = streak

    override fun restore(state: Int, nowMs: Long) {
        streak = state
    }

    companion object {
        /** 💭 깜빡이 켠 차선 변경 3회 연속이면 칭찬. */
        const val DEFAULT_STREAK = 3
    }
}

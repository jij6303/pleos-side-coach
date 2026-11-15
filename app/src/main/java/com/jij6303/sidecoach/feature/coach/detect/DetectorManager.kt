package com.jij6303.sidecoach.feature.coach.detect

import com.jij6303.sidecoach.feature.coach.CoachMessage
import com.jij6303.sidecoach.feature.coach.CoachType
import com.jij6303.sidecoach.feature.coach.MessageCatalog
import com.jij6303.sidecoach.state.DrivingSnapshot

/**
 * 디텍터 목록을 들고 스냅샷을 차례로 전달한다. 이번 스냅샷의 판정 결과와, 중재자에 넣을 코칭 후보를 함께 돌려준다.
 * - 교정 후보: 각 판정 결과의 [DetectionEvent.correction]
 * - 칭찬 후보: 깜빡이를 켠 차선 변경이 [praiseStreak]회 연속되면 (깜빡이 없이 바꾸면 처음부터 센다)
 */
class DetectorManager(
    private val detectors: List<Detector>,
    private val praiseStreak: Int = DEFAULT_PRAISE_STREAK,
) {
    /** 판정 결과(기록용)와 코칭 후보(중재자용). 순서는 [detectors] 순서와 같다. */
    data class Detection(val events: List<DetectionEvent>, val candidates: List<CoachMessage>)

    private var streak = 0

    fun onSnapshot(s: DrivingSnapshot): Detection {
        val events = detectors.mapNotNull { it.onSnapshot(s) }
        val candidates = events.mapNotNull { event ->
            val praise = praiseFor(event) // 누락이어도 연속 횟수를 끊어야 하므로 항상 센다
            event.correction() ?: praise
        }
        return Detection(events, candidates)
    }

    private fun praiseFor(event: DetectionEvent): CoachMessage? {
        if (event !is DetectionEvent.LaneChange) return null
        if (!event.signalled) {
            streak = 0
            return null
        }
        if (++streak < praiseStreak) return null
        streak = 0
        return CoachMessage(CoachType.PRAISE, MessageCatalog.praise(), event.timestampMs)
    }

    /** 새 주행: 디텍터 진행 상태와 칭찬 연속 횟수를 비운다. */
    fun reset() {
        detectors.forEach { it.reset() }
        streak = 0
    }

    /** 저장용: 칭찬 연속 횟수. */
    fun exportState(): Int = streak

    fun restore(praiseStreak: Int) {
        streak = praiseStreak
    }

    companion object {
        /** 💭 깜빡이 켠 차선 변경 3회 연속이면 칭찬. */
        const val DEFAULT_PRAISE_STREAK = 3
    }
}

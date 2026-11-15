package com.jij6303.sidecoach.domain.situation

import com.jij6303.sidecoach.data.navi.NaviEvent
import com.jij6303.sidecoach.domain.coach.CoachMessage
import com.jij6303.sidecoach.domain.coach.CoachType
import com.jij6303.sidecoach.domain.coach.MessageCatalog

/**
 * 내비 이벤트 → 상황 예고 (dev-architecture 4.4).
 * 남은 거리가 유형별 예고 구간([SituationType.previewFarM]~[SituationType.previewNearM])일 때 한 번만 예고하고,
 * 같은 지점은 [repeatTtlMs] 동안 다시 말하지 않는다.
 */
class SituationPreviewEngine(
    private val repeatTtlMs: Long = 10 * 60_000L,
) {
    private val announced = mutableMapOf<String, Long>()

    /** [lane]은 운전자가 알려준 지금 차로(#69). 모르면 null. */
    fun onEvent(e: NaviEvent, lane: Int? = null): CoachMessage? = when (e) {
        is NaviEvent.Situation -> onSituation(e, lane)
    }

    private fun onSituation(e: NaviEvent.Situation, lane: Int?): CoachMessage? {
        announced.entries.removeAll { e.timestampMs - it.value > repeatTtlMs }
        if (e.key in announced) return null
        // 가까운 쪽 경계 안에서 처음 알게 된 지점은 늦었으므로 말하지 않는다 (운전에 집중할 때)
        if (e.distanceM > e.type.previewFarM || e.distanceM < e.type.previewNearM) return null
        announced[e.key] = e.timestampMs
        return CoachMessage(
            type = CoachType.PREVIEW,
            text = MessageCatalog.preview(e.type, lane),
            createdAtMs = e.timestampMs,
            dedupeKey = "preview-${e.key}",
        )
    }

    fun reset() {
        announced.clear()
    }

    /** 저장용: 예고한 지점 key → 예고 시각. */
    fun exportState(): Map<String, Long> = announced.toMap()

    fun restore(state: Map<String, Long>) {
        announced.clear()
        announced.putAll(state)
    }
}

package com.jij6303.sidecoach.domain.situation

import com.jij6303.sidecoach.data.navi.NaviEvent
import com.jij6303.sidecoach.domain.coach.CoachMessage
import com.jij6303.sidecoach.domain.coach.CoachType
import com.jij6303.sidecoach.domain.coach.MessageCatalog

/**
 * 내비 이벤트 → 상황 예고 (dev-architecture 4.4).
 * 남은 거리가 [maxDistanceM]~[minDistanceM] 구간일 때 한 번만 예고하고, 같은 지점은 [repeatTtlMs] 동안 다시 말하지 않는다.
 */
class SituationPreviewEngine(
    private val maxDistanceM: Float = 300f,
    private val minDistanceM: Float = 150f,
    private val repeatTtlMs: Long = 10 * 60_000L,
) {
    private val announced = mutableMapOf<String, Long>()

    fun onEvent(e: NaviEvent): CoachMessage? = when (e) {
        is NaviEvent.Situation -> onSituation(e)
    }

    private fun onSituation(e: NaviEvent.Situation): CoachMessage? {
        announced.entries.removeAll { e.timestampMs - it.value > repeatTtlMs }
        if (e.key in announced) return null
        // 150m 안쪽에서 처음 알게 된 지점은 늦었으므로 말하지 않는다 (운전에 집중할 때)
        if (e.distanceM > maxDistanceM || e.distanceM < minDistanceM) return null
        announced[e.key] = e.timestampMs
        return CoachMessage(
            type = CoachType.PREVIEW,
            text = MessageCatalog.preview(e.type),
            createdAtMs = e.timestampMs,
            dedupeKey = "preview-${e.key}",
        )
    }

    fun reset() {
        announced.clear()
    }
}

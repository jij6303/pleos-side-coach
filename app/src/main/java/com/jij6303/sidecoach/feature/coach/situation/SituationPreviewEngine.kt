package com.jij6303.sidecoach.feature.coach.situation

import com.jij6303.sidecoach.data.navi.NaviEvent
import com.jij6303.sidecoach.data.navi.SituationType
import com.jij6303.sidecoach.feature.coach.CoachMessage
import com.jij6303.sidecoach.feature.coach.CoachType
import com.jij6303.sidecoach.feature.coach.MessageCatalog
import com.jij6303.sidecoach.state.DrivingSnapshot

/**
 * 내비 경로 + 스냅샷 → 상황 예고 후보.
 *
 * 내비 이벤트로 경로를 받고, 스냅샷 차속으로 [progress]가 남은 거리를 잰다.
 * 다가오는 지점의 남은 거리가 유형별 예고 구간([SituationType.previewFarM]~[SituationType.previewNearM])이면 한 번만 예고한다.
 * 가까운 쪽 경계 안에서 처음 알게 된 지점은 늦었으므로 말하지 않는다 (운전에 집중할 때).
 * 같은 지점은 [repeatTtlMs] 동안 다시 말하지 않는다.
 */
class SituationPreviewEngine(
    private val repeatTtlMs: Long = 10 * 60_000L,
    private val progress: RouteProgress = RouteProgress(),
) {
    /** 저장용: 예고한 지점(key → 예고 시각)과 경로 위치. */
    data class State(val announced: Map<String, Long>, val route: RouteProgress.State?)

    private val announced = mutableMapOf<String, Long>()

    /** 경로 시작·끝. 예고는 스냅샷·타이머에서 판단한다. */
    fun onNavi(e: NaviEvent) {
        when (e) {
            is NaviEvent.RouteStarted -> progress.setRoute(e.route, e.timestampMs)
            is NaviEvent.RouteEnded -> progress.clearRoute()
        }
    }

    /** [lane]은 운전자가 알려준 지금 차로(#69). 모르면 null. */
    fun onSnapshot(s: DrivingSnapshot, lane: Int? = null): List<CoachMessage> {
        progress.onMotion(s.timestampMs, s.speedKmh, s.gear)
        return previews(s.timestampMs, lane)
    }

    /** 같은 차속이 이어져 스냅샷이 오지 않는 동안에도 거리를 갱신한다. */
    fun onTick(nowMs: Long, lane: Int? = null): List<CoachMessage> {
        progress.advance(nowMs)
        return previews(nowMs, lane)
    }

    private fun previews(nowMs: Long, lane: Int?): List<CoachMessage> {
        announced.entries.removeAll { nowMs - it.value > repeatTtlMs }
        return progress.upcoming().mapNotNull { p ->
            if (p.key in announced) return@mapNotNull null
            if (p.remainingM > p.type.previewFarM || p.remainingM < p.type.previewNearM) return@mapNotNull null
            announced[p.key] = nowMs
            CoachMessage(
                type = CoachType.PREVIEW,
                text = MessageCatalog.preview(p.type, lane),
                createdAtMs = nowMs,
                dedupeKey = "preview-${p.key}",
            )
        }
    }

    /** 새 주행: 예고한 지점을 잊는다. 경로는 내비가 정하므로 유지한다. */
    fun reset() {
        announced.clear()
    }

    fun exportState(): State = State(announced.toMap(), progress.exportState())

    fun restore(state: State, nowMs: Long) {
        announced.clear()
        announced.putAll(state.announced)
        progress.restore(state.route, nowMs)
    }
}

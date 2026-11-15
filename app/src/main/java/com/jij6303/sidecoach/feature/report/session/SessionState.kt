package com.jij6303.sidecoach.feature.report.session

import com.jij6303.sidecoach.data.navi.FakeRouteNaviEventSource
import com.jij6303.sidecoach.feature.coach.CoachingArbiter
import com.jij6303.sidecoach.feature.report.TripReport
import com.jij6303.sidecoach.feature.report.trip.TripRecorder
import com.jij6303.sidecoach.feature.report.trip.TripSession

/**
 * 프로세스가 다시 만들어져도 이어갈 주행 세션 상태 (dev-architecture 9장, #31).
 * [savedAtMs]는 스냅샷과 같은 시계(elapsedRealtime)라 재부팅하면 작아진다.
 */
data class SessionState(
    val savedAtMs: Long,
    val recorder: TripRecorder.State,
    val arbiter: CoachingArbiter.State,
    val trip: TripSession.State?,
    val announced: Map<String, Long>,
    val pendingReport: TripReport?,
    val route: FakeRouteNaviEventSource.RouteState? = null,
) {
    /**
     * 이어가도 되는지. 재부팅으로 시계가 되돌아갔거나 [maxAgeMs]보다 오래됐으면 새 주행으로 본다.
     */
    fun isUsable(nowMs: Long, maxAgeMs: Long = MAX_AGE_MS): Boolean =
        nowMs >= savedAtMs && nowMs - savedAtMs <= maxAgeMs

    companion object {
        /** 💭 30분 넘게 쉬었으면 다른 주행으로 본다. */
        const val MAX_AGE_MS = 30 * 60_000L
    }
}

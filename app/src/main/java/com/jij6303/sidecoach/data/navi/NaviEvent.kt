package com.jij6303.sidecoach.data.navi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 경로 위의 안내 지점. [atM]은 경로 출발점부터의 거리(m). [key]는 같은 지점을 가리키는 고유 값. */
data class RoutePoint(val key: String, val type: SituationType, val atM: Float)

/** 경로 하나. [id]가 같으면 같은 경로다. [isDemo]면 시연 경로(출발할 때마다 처음부터). */
data class NaviRoute(val id: String, val points: List<RoutePoint>, val isDemo: Boolean = false)

/** 내비 이벤트를 앱 모델로 옮긴 것. 남은 거리는 보내지 않는다(받는 쪽이 차속으로 계산). */
sealed interface NaviEvent {
    val timestampMs: Long

    /** 경로 안내 시작 또는 재탐색. */
    data class RouteStarted(val route: NaviRoute, override val timestampMs: Long) : NaviEvent

    /** 경로 안내 끝 (도착·취소). */
    data class RouteEnded(override val timestampMs: Long) : NaviEvent
}

/** 내비 경로 공급원. 실제 내비는 [PleosNaviEventSource], 시연은 [FakeRouteNaviEventSource], 둘을 고르는 건 [RoutingNaviEventSource]. */
interface NaviEventSource {
    /** 지금 시연 경로를 쓰는지. 화면에 표시해 시연임을 밝힌다. */
    val isDemo: StateFlow<Boolean>
    val events: Flow<NaviEvent>
}

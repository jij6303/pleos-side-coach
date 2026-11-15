package com.jij6303.sidecoach.data.navi

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow

/**
 * 시연용 가짜 경로. 구독하면 [route]를 한 번 보낸다.
 * 출발할 때마다 처음부터 따라가는 것과 남은 거리 계산은 받는 쪽(상황 예고)이 맡는다.
 */
class FakeRouteNaviEventSource(
    private val clock: () -> Long,
    private val route: NaviRoute = DEMO_ROUTE,
) : NaviEventSource {

    override val isDemo: StateFlow<Boolean> = MutableStateFlow(true)

    override val events: Flow<NaviEvent> = flow { emit(NaviEvent.RouteStarted(route, clock())) }

    companion object {
        /** 💭 시연 경로: 50 km/h로 달리면 약 10초 뒤 첫 예고. */
        val DEMO_ROUTE = NaviRoute(
            id = "demo",
            isDemo = true,
            points = listOf(
                RoutePoint("demo-roundabout", SituationType.ROUNDABOUT, 400f),
                RoutePoint("demo-school", SituationType.SCHOOL_ZONE, 1_100f),
                RoutePoint("demo-left", SituationType.UNPROTECTED_LEFT, 1_800f),
                RoutePoint("demo-merge", SituationType.MERGE, 2_600f),
            ),
        )
    }
}

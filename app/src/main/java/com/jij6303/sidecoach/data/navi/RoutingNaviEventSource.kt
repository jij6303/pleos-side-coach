package com.jij6303.sidecoach.data.navi

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.stateIn

/**
 * 실제 내비로 경로 안내 중이면 [real] 이벤트만, 아니면 [demo](시연 경로) 이벤트도 보낸다 (#65).
 * [real]은 경로가 없어도 항상 구독해 경로 시작을 놓치지 않는다.
 */
class RoutingNaviEventSource(
    private val real: NaviEventSource,
    private val realRouteActive: StateFlow<Boolean>,
    private val demo: NaviEventSource,
    scope: CoroutineScope,
) : NaviEventSource {

    override val isDemo: StateFlow<Boolean> =
        realRouteActive.map { !it }.stateIn(scope, SharingStarted.Eagerly, !realRouteActive.value)

    override val events: Flow<NaviEvent> = merge(
        real.events,
        demo.events.filter { !realRouteActive.value },
    )
}

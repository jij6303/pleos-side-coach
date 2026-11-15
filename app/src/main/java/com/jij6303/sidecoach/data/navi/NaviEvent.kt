package com.jij6303.sidecoach.data.navi

import com.jij6303.sidecoach.domain.situation.SituationType
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/** 내비 이벤트를 앱 모델로 옮긴 것. NaviHelper 필드는 dev-architecture 0.1 (#63 실측). */
sealed interface NaviEvent {
    val timestampMs: Long

    /** 앞에 [type] 상황이 [distanceM] 남음. [key]는 같은 지점을 가리키는 고유 값. */
    data class Situation(
        val key: String,
        val type: SituationType,
        val distanceM: Float,
        override val timestampMs: Long,
    ) : NaviEvent
}

/** 내비 이벤트 공급원. 실제 내비는 [PleosNaviEventSource], 시연은 [FakeRouteNaviEventSource], 둘을 고르는 건 [RoutingNaviEventSource]. */
interface NaviEventSource {
    /** 지금 시연용 가짜 이벤트를 쓰는지. 화면에 표시해 시연임을 밝힌다. */
    val isDemo: StateFlow<Boolean>
    val events: Flow<NaviEvent>
}

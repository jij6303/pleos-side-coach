package com.jij6303.sidecoach.data.navi

import com.jij6303.sidecoach.domain.situation.SituationType
import kotlinx.coroutines.flow.Flow

/** 내비 이벤트를 앱 모델로 옮긴 것. 실제 NaviHelper 필드 구성은 확인 필요(dev-architecture 11.3-7). */
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

/** 내비 이벤트 공급원. 실제 NaviHelper 구현은 2차 범위, MVP는 [FakeRouteNaviEventSource]. */
interface NaviEventSource {
    /** 시연용 가짜 이벤트인지. 화면에 표시해 시연임을 밝힌다. */
    val isDemo: Boolean
    val events: Flow<NaviEvent>
}

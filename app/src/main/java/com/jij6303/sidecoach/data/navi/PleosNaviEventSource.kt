package com.jij6303.sidecoach.data.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow

/**
 * 차량 내비(NaviHelper) 경로 이벤트 (#65). API·필드는 #63 실측.
 *
 * - 리스너는 권한 없이 받는다. 경로 안내가 시작되면 `onTBTInfo`로 전체 회전 목록이 온다.
 * - 경로 안내 중에 늦게 등록하면 목록이 다시 오지 않아 `getTBTInfo()`로 조회한다. 이 호출은
 *   `pleos.car.permission.NAVI_ROUTE`가 필요하고, 없으면 `SecurityException` → 다음 경로 시작부터 리스너로만 동작한다.
 * - 목록을 경로([TbtMapping.route])로 바꿔 보낸다. 같은 목록이 다시 오면 무시하고, 다른 목록(재탐색)은 새 경로로 보낸다.
 * - 남은 거리는 받는 쪽(상황 예고)이 차속으로 계산한다. 이 소스는 차량 신호를 받지 않는다.
 * - 주의 지점(`onCautionPointInfo`: 어린이보호구역·과속방지턱 등)은 쓰지 않는다. 내비가 이미 알리므로 겹친다 (#71).
 */
class PleosNaviEventSource(
    context: Context,
    private val clock: () -> Long,
    private val log: (String) -> Unit = {},
) : NaviEventSource {

    private val appContext = context.applicationContext

    override val isDemo: StateFlow<Boolean> = MutableStateFlow(false)

    private val _routeActive = MutableStateFlow(false)

    /** 실제 경로 안내 중인지. [events]를 구독하는 동안만 갱신된다. */
    val routeActive: StateFlow<Boolean> = _routeActive.asStateFlow()

    override val events: Flow<NaviEvent> = channelFlow {
        var lastSegments: List<TbtMapping.Segment> = emptyList()
        fun endRoute(reason: String) {
            log("route end: $reason")
            if (lastSegments.isNotEmpty()) trySend(NaviEvent.RouteEnded(clock()))
            lastSegments = emptyList()
            _routeActive.value = false
        }

        val listener = object : NaviHelperEventListener {
            override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
                val segments = tbtInfoList.map { TbtMapping.Segment(it.type.name, it.distance, it.description) }
                log("onTBTInfo ${segments.joinToString { "${it.turnTypeName} ${it.distanceM.toInt()}m" }}")
                if (segments.isEmpty()) return endRoute("empty TBT")
                if (segments != lastSegments) {
                    lastSegments = segments
                    trySend(NaviEvent.RouteStarted(TbtMapping.route(segments, clock()), clock()))
                }
                _routeActive.value = true
            }

            override fun onNavigationStatus(status: NavigationStatus) {
                if (status != NavigationStatus.ROUTE_DRIVE) endRoute("status $status")
            }

            override fun onRouteCancelled() = endRoute("cancelled")
            override fun onDestinationArrived(destinationArrivedInfo: DestinationArrivedInfo) = endRoute("arrived")
            override fun onError(errorCode: NaviErrorCode) = log("onError $errorCode")
        }

        val helper = NaviHelper(appContext)
        helper.initialize(
            {
                helper.addListener(listener)
                try {
                    helper.getTBTInfo() // 이미 경로 안내 중이면 목록을 받는다
                } catch (e: SecurityException) {
                    log("getTBTInfo denied: ${e.message}")
                }
                log("NaviHelper ready")
            },
            { e -> log("NaviHelper init failed: $e") },
        )

        awaitClose {
            runCatching { helper.removeListener(listener) }
            runCatching { helper.release() }
            _routeActive.value = false
        }
    }
}

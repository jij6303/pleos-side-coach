package com.jij6303.sidecoach.data.navi

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.DestinationArrivedInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.content.Context
import com.jij6303.sidecoach.domain.DrivingSnapshot
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * 차량 내비(NaviHelper) 경로 이벤트 (#65). API·필드는 #63 실측(dev-architecture 0.1).
 *
 * - 리스너는 권한 없이 받는다. 경로 안내가 시작되면 `onTBTInfo`로 전체 회전 목록이 온다.
 * - 경로 안내 중에 늦게 등록하면 목록이 다시 오지 않아 `getTBTInfo()`로 조회한다. 이 호출은
 *   `pleos.car.permission.NAVI_ROUTE`가 필요하고, 없으면 `SecurityException` → 다음 경로 시작부터 리스너로만 동작한다.
 * - 남은 거리는 [TbtRouteTracker]가 차속 적분으로 계산한다.
 */
class PleosNaviEventSource(
    context: Context,
    private val snapshots: Flow<DrivingSnapshot>,
    private val clock: () -> Long,
    private val tickMs: Long = 1_000,
    private val log: (String) -> Unit = {},
) : NaviEventSource {

    private val appContext = context.applicationContext
    private val tracker = TbtRouteTracker()

    override val isDemo: StateFlow<Boolean> = MutableStateFlow(false)

    private val _routeActive = MutableStateFlow(false)

    /** 실제 경로 안내 중인지. [events]를 구독하는 동안만 갱신된다. */
    val routeActive: StateFlow<Boolean> = _routeActive.asStateFlow()

    override val events: Flow<NaviEvent> = channelFlow {
        fun emit(list: List<NaviEvent>) = list.forEach { trySend(it) }
        fun endRoute(reason: String) {
            log("route end: $reason")
            tracker.clear()
            _routeActive.value = false
        }

        val listener = object : NaviHelperEventListener {
            override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
                val segments = tbtInfoList.map { TbtRouteTracker.Segment(it.type.name, it.distance, it.description) }
                log("onTBTInfo ${segments.joinToString { "${it.turnTypeName} ${it.distanceM.toInt()}m" }}")
                if (segments.isEmpty()) return endRoute("empty TBT")
                emit(tracker.setRoute(segments, clock()))
                _routeActive.value = true
            }

            override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) {
                for (c in cautionPointList) {
                    val type = TbtMapping.caution(c.type.name) ?: continue
                    emit(tracker.putCaution("caution-${c.id}", type, c.remainDistance.toDouble(), clock()))
                }
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

        launch { snapshots.collect { emit(tracker.onSnapshot(it)) } }
        launch {
            while (true) {
                delay(tickMs)
                emit(tracker.advance(clock()))
            }
        }
        awaitClose {
            runCatching { helper.removeListener(listener) }
            runCatching { helper.release() }
            _routeActive.value = false
        }
    }
}

package com.jij6303.sidecoach.data.navi

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.domain.DrivingSnapshot
import com.jij6303.sidecoach.domain.situation.SituationType
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.launch

/**
 * 시연용 가짜 경로. 주행이 시작되면(기어 P → P 아님) 출발점에서 [route]를 따라간다고 보고,
 * 실제 차속을 적분한 이동 거리로 각 지점까지 남은 거리를 이벤트로 보낸다.
 * 콘솔로 달리면 진짜 내비처럼 상황이 다가온다.
 *
 * 같은 차속이 이어지면 차속 이벤트가 오지 않을 수 있어(Android 14 variable update rate),
 * [tickMs]마다 마지막 차속으로 거리를 갱신한다. [clock]은 스냅샷 timestamp와 같은 시계여야 한다.
 */
class FakeRouteNaviEventSource(
    private val snapshots: Flow<DrivingSnapshot>,
    private val clock: () -> Long,
    private val route: List<RoutePoint> = DEFAULT_ROUTE,
    private val tickMs: Long = 1_000,
    /** 이 거리 안에 들어온 지점만 알린다. */
    private val horizonM: Float = 500f,
    /** 남은 거리를 이 단위로 끊어 보낸다 (이벤트 수 줄이기). */
    private val stepM: Float = 10f,
) : NaviEventSource {

    data class RoutePoint(val key: String, val type: SituationType, val atM: Float)

    override val isDemo: StateFlow<Boolean> = MutableStateFlow(true)

    /** 경로 위치. 저장·복원할 수 있도록 하나만 둔다 (events는 한 곳에서만 구독). */
    private val tracker = Tracker()

    /** 저장용 경로 상태. 경로를 달리는 중이 아니면 null. */
    data class RouteState(val odometerM: Double, val speedKmh: Float, val lastSent: Map<String, Int>)

    fun exportState(): RouteState? = tracker.exportState()

    /** 이전 위치에서 이어간다. 내려가 있던 동안은 이동한 것으로 치지 않는다. */
    fun restore(state: RouteState?) = tracker.restore(state, clock())

    override val events: Flow<NaviEvent> = channelFlow {
        launch {
            snapshots.collect { s -> tracker.onSnapshot(s).forEach { send(it) } }
        }
        while (true) {
            delay(tickMs)
            tracker.advance(clock()).forEach { send(it) }
        }
    }

    /** 경로 위치 계산. 스냅샷 수집과 타이머가 동시에 부르므로 동기화한다. */
    internal inner class Tracker {
        private var active = false
        private var odometerM = 0.0
        private var lastTs = 0L
        private var speedKmh = 0f
        private val lastSent = mutableMapOf<String, Int>()

        @Synchronized
        fun onSnapshot(s: DrivingSnapshot): List<NaviEvent> {
            if (s.gear == GearPosition.UNKNOWN) return emptyList()
            if (!active) {
                if (s.gear == GearPosition.PARK) return emptyList()
                active = true          // 출발: 경로 처음부터
                odometerM = 0.0
                lastTs = s.timestampMs
                speedKmh = s.speedKmh ?: 0f
                lastSent.clear()
                return emitNear(s.timestampMs)
            }
            val out = advance(s.timestampMs)
            speedKmh = s.speedKmh ?: speedKmh
            if (s.gear == GearPosition.PARK) active = false
            return out
        }

        @Synchronized
        fun exportState(): RouteState? =
            if (!active) null else RouteState(odometerM, speedKmh, lastSent.toMap())

        @Synchronized
        fun restore(state: RouteState?, nowMs: Long) {
            if (state == null) {
                active = false
                return
            }
            active = true
            odometerM = state.odometerM
            speedKmh = state.speedKmh
            lastTs = nowMs
            lastSent.clear()
            lastSent.putAll(state.lastSent)
        }

        @Synchronized
        fun advance(nowMs: Long): List<NaviEvent> {
            if (!active) return emptyList()
            val dt = (nowMs - lastTs).coerceAtLeast(0)
            odometerM += speedKmh / 3.6 * dt / 1000.0
            lastTs = nowMs
            return emitNear(nowMs)
        }

        private fun emitNear(nowMs: Long): List<NaviEvent> = route.mapNotNull { p ->
            val remaining = (p.atM - odometerM).toFloat()
            if (remaining < 0f || remaining > horizonM) return@mapNotNull null
            val bucket = (remaining / stepM).toInt()
            if (lastSent[p.key] == bucket) return@mapNotNull null
            lastSent[p.key] = bucket
            NaviEvent.Situation(p.key, p.type, remaining, nowMs)
        }
    }

    companion object {
        /** 💭 시연 경로: 50 km/h로 달리면 약 10초 뒤 첫 예고. */
        val DEFAULT_ROUTE = listOf(
            RoutePoint("demo-roundabout", SituationType.ROUNDABOUT, 400f),
            RoutePoint("demo-school", SituationType.SCHOOL_ZONE, 1_100f),
            RoutePoint("demo-left", SituationType.UNPROTECTED_LEFT, 1_800f),
            RoutePoint("demo-merge", SituationType.MERGE, 2_600f),
        )
    }
}

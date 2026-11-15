package com.jij6303.sidecoach.feature.coach.situation

import com.jij6303.sidecoach.data.navi.NaviRoute
import com.jij6303.sidecoach.data.navi.RoutePoint
import com.jij6303.sidecoach.data.navi.SituationType
import com.jij6303.sidecoach.data.navi.TbtMapping
import com.jij6303.sidecoach.data.vehicle.GearPosition

/**
 * 경로 위 지금 위치와 다가오는 지점까지 남은 거리.
 *
 * 내비는 남은 거리를 주지 않으므로(#63 실측: TBT `distance`는 구간 거리) 차속을 시간으로 적분해 경로를 따라간 거리를 잰다.
 * - 새 경로(다른 [NaviRoute.id])가 오면 지금 자리를 출발점으로 본다. 같은 경로가 다시 오면 위치를 유지한다.
 * - 시연 경로([NaviRoute.isDemo])는 P에서 출발할 때마다 처음부터 따라간다.
 * - 다음 지점 하나만 알린다. 뒤 지점을 먼저 말해 헷갈리지 않게.
 *   단, 다음 회전 바로 뒤([chainGapM] 안)에 또 회전이 있으면 그 회전을 연속 회전([TbtMapping.chain])으로,
 *   **다음 회전까지 남은 거리**를 붙여 함께 알린다(#67). 지나고 나면 차로 안내 구간보다 가까워 놓치기 때문.
 *
 * 시각은 스냅샷과 같은 시계여야 한다.
 */
class RouteProgress(
    /** 이 거리 안에 들어온 지점만 알린다. 가장 이른 예고 구간(500m)보다 넓게. */
    private val horizonM: Float = 600f,
    /** 다음 회전과 그 뒤 회전 사이가 이 거리 이하이면 연속 회전으로 본다. 💭 */
    private val chainGapM: Float = 300f,
) {
    /** 다가오는 지점. [remainingM]은 지금 위치부터 남은 거리. */
    data class Upcoming(val key: String, val type: SituationType, val remainingM: Float)

    /** 저장용: 어떤 경로의 어디까지 왔는지. */
    data class State(val routeId: String, val travelledM: Double, val speedKmh: Float)

    private var route: NaviRoute? = null
    private var travelledM = 0.0
    private var speedKmh = 0f
    private var lastTs: Long? = null
    private var lastGear = GearPosition.UNKNOWN

    /** 복원했지만 아직 그 경로가 다시 오지 않음. 같은 id의 경로가 오면 이 위치에서 이어간다. */
    private var pending: State? = null

    val hasRoute: Boolean get() = route != null

    fun setRoute(newRoute: NaviRoute, nowMs: Long) {
        if (newRoute.id == route?.id) return
        advance(nowMs)
        route = newRoute
        val resumed = pending?.takeIf { it.routeId == newRoute.id }
        travelledM = resumed?.travelledM ?: 0.0
        if (resumed != null) speedKmh = resumed.speedKmh
        pending = null
    }

    fun clearRoute() {
        route = null
        travelledM = 0.0
    }

    /** 스냅샷 하나: 직전 차속으로 지금까지 이동했다고 보고, 새 차속·기어를 기억한다. */
    fun onMotion(timestampMs: Long, speedKmh: Float?, gear: GearPosition) {
        advance(timestampMs)
        if (route?.isDemo == true && lastGear == GearPosition.PARK && gear != GearPosition.PARK && gear != GearPosition.UNKNOWN) {
            travelledM = 0.0 // 시연 경로: 출발할 때마다 처음부터
        }
        if (gear != GearPosition.UNKNOWN) lastGear = gear
        this.speedKmh = speedKmh ?: this.speedKmh
    }

    /** 같은 차속이면 스냅샷이 오지 않을 수 있어(값이 바뀔 때만 옴) 타이머로도 부른다. */
    fun advance(nowMs: Long) {
        val last = lastTs
        if (last != null) travelledM += speedKmh / 3.6 * (nowMs - last).coerceAtLeast(0) / 1000.0
        lastTs = nowMs
    }

    fun upcoming(): List<Upcoming> {
        val points = route?.points ?: return emptyList()
        val nextIndex = points.indexOfFirst { it.atM >= travelledM }
        val next = points.getOrNull(nextIndex) ?: return emptyList()
        // 연속 회전: 위치는 앞 회전(next) 자리로 둬서 남은 거리가 앞 회전 기준이 되게 한다
        val chained = points.getOrNull(nextIndex + 1)
            ?.takeIf { it.atM - next.atM <= chainGapM }
            ?.let { after -> TbtMapping.chain(after.type)?.let { RoutePoint("${after.key}-chain", it, next.atM) } }
        return listOfNotNull(next, chained).mapNotNull { p ->
            val remaining = (p.atM - travelledM).toFloat()
            if (remaining > horizonM) null else Upcoming(p.key, p.type, remaining)
        }
    }

    fun exportState(): State? = route?.let { State(it.id, travelledM, speedKmh) }

    /** 저장된 위치. 경로는 내비가 다시 보내 주므로, 같은 경로가 오면 그 자리에서 이어간다. 내려가 있던 동안은 이동으로 치지 않는다. */
    fun restore(state: State?, nowMs: Long) {
        pending = state
        lastTs = nowMs
        val current = route
        if (state != null && current != null && current.id == state.routeId) {
            travelledM = state.travelledM
            speedKmh = state.speedKmh
            pending = null
        }
    }
}

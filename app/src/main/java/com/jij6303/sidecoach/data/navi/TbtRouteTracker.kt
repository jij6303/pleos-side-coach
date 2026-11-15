package com.jij6303.sidecoach.data.navi


/**
 * 내비 TBT 목록 → 회전 지점까지 남은 거리 (#65).
 *
 * 실측(#63): `TBTInfo.distance`는 남은 거리가 아니라 **이전 지점부터의 구간 거리**이고, 경로 시작 때 전체 목록이 한 번 온다.
 * 그래서 구간 거리를 누적해 지점 위치를 만들고, 차속을 적분한 이동 거리로 남은 거리를 계산한다(시연 경로와 같은 방식).
 * - 같은 목록이 다시 오면 무시한다(이동 거리 유지). 다른 목록이 오면(재탐색 등) 지금 위치를 새 출발점으로 본다.
 * - 회전은 안내할 수 있는 지점([TbtMapping]) 중 **다음 지점 하나만** 알린다. 뒤 지점을 먼저 말해 헷갈리지 않게.
 * - 단, 다음 회전 바로 뒤([chainGapM] 안)에 또 회전이 있으면 그 회전을 연속 회전([TbtMapping.chain])으로,
 *   **다음 회전까지 남은 거리**를 붙여 함께 알린다(#67). 지나고 나면 차로 안내 구간(200m)보다 가까워 놓치기 때문.
 * - 주행 중 내비가 목록·거리를 갱신하는지는 에뮬레이터에서 확인하지 못했다(❓, dev-architecture 0.1).
 *
 * 차량 움직임 수집·타이머·SDK 콜백이 동시에 부르므로 동기화한다. 시각은 차량 움직임 timestamp와 같은 시계여야 한다.
 */
class TbtRouteTracker(
    /** 이 거리 안에 들어온 지점만 알린다. 차로 안내 예고 구간(500m)보다 넓게. */
    private val horizonM: Float = 600f,
    /** 남은 거리를 이 단위로 끊어 보낸다 (이벤트 수 줄이기). */
    private val stepM: Float = 10f,
    /** 다음 회전과 그 뒤 회전 사이가 이 거리 이하이면 연속 회전으로 본다. 💭 */
    private val chainGapM: Double = 300.0,
) {
    /** TBT 한 항목. [turnTypeName]은 SDK `TurnType`의 이름. */
    data class Segment(val turnTypeName: String, val distanceM: Double, val description: String)

    private data class Point(val key: String, val type: SituationType, val atM: Double)

    private var segments: List<Segment> = emptyList()
    private var turns: List<Point> = emptyList()
    private var odometerM = 0.0
    private var lastTs = 0L
    private var started = false
    private var speedKmh = 0f
    private val lastSent = mutableMapOf<String, Int>()

    /** 경로 안내 중인지 (TBT 목록이 있는지). */
    val hasRoute: Boolean @Synchronized get() = segments.isNotEmpty()

    /** 새 TBT 목록. 같은 목록이면 무시한다. */
    @Synchronized
    fun setRoute(newSegments: List<Segment>, nowMs: Long): List<NaviEvent> {
        if (newSegments == segments) return emptyList()
        val out = advance(nowMs)
        segments = newSegments
        var at = odometerM
        turns = newSegments.mapIndexedNotNull { i, seg ->
            at += seg.distanceM
            // 키에 받은 시각을 넣는다. 세션 복원(#31)으로 "예고한 지점"이 남아 있어도 새 프로세스의 같은 순번과 겹치지 않게 (#67 실측)
            TbtMapping.turn(seg.turnTypeName)?.let { Point("tbt-$nowMs-$i", it, at) }
        }
        return out + emitNear(nowMs)
    }

    /** 경로 안내가 끝났다(취소·도착). */
    @Synchronized
    fun clear() {
        segments = emptyList()
        turns = emptyList()
        lastSent.clear()
    }

    @Synchronized
    fun onMotion(s: VehicleMotion): List<NaviEvent> {
        val out = advance(s.timestampMs)
        speedKmh = s.speedKmh ?: speedKmh
        return out
    }

    /** 마지막 차속으로 [nowMs]까지 이동했다고 보고 갱신한다. 같은 차속이면 차속 이벤트가 안 올 수 있어 타이머로도 부른다. */
    @Synchronized
    fun advance(nowMs: Long): List<NaviEvent> {
        if (started) odometerM += speedKmh / 3.6 * (nowMs - lastTs).coerceAtLeast(0) / 1000.0
        started = true
        lastTs = nowMs
        return emitNear(nowMs)
    }

    private fun emitNear(nowMs: Long): List<NaviEvent> {
        val nextIndex = turns.indexOfFirst { it.atM >= odometerM }
        val nextTurn = turns.getOrNull(nextIndex)
        // 연속 회전: 위치는 앞 회전(nextTurn) 자리로 둬서 남은 거리가 앞 회전 기준이 되게 한다
        val chained = turns.getOrNull(nextIndex + 1)?.takeIf { nextTurn != null && it.atM - nextTurn.atM <= chainGapM }
            ?.let { after -> TbtMapping.chain(after.type)?.let { Point("${after.key}-chain", it, nextTurn!!.atM) } }
        return listOfNotNull(nextTurn, chained).mapNotNull { p ->
            val remaining = (p.atM - odometerM).toFloat()
            if (remaining > horizonM) return@mapNotNull null
            val bucket = (remaining / stepM).toInt()
            if (lastSent[p.key] == bucket) return@mapNotNull null
            lastSent[p.key] = bucket
            NaviEvent.Situation(p.key, p.type, remaining, nowMs)
        }
    }
}

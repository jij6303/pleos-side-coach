package com.jij6303.sidecoach.app.session

import com.jij6303.sidecoach.data.navi.FakeRouteNaviEventSource
import com.jij6303.sidecoach.feature.coach.CoachType
import com.jij6303.sidecoach.feature.coach.CoachingArbiter
import com.jij6303.sidecoach.feature.coach.CoachingPipeline
import com.jij6303.sidecoach.feature.coach.situation.RouteProgress
import com.jij6303.sidecoach.feature.coach.situation.SituationPreviewEngine
import com.jij6303.sidecoach.feature.report.Grade
import com.jij6303.sidecoach.feature.report.NextTask
import com.jij6303.sidecoach.feature.report.TripReport
import com.jij6303.sidecoach.feature.report.trip.TripMetrics
import com.jij6303.sidecoach.feature.report.trip.TripRecorder
import com.jij6303.sidecoach.feature.report.trip.TripSession
import com.jij6303.sidecoach.feature.report.trip.TripStats
import org.json.JSONObject

/**
 * [SessionState] ↔ JSON 문자열. 저장 형식이 바뀌면 [VERSION]을 올리고, 다른 버전은 읽지 않는다(새 주행으로 시작).
 */
object SessionStateCodec {
    const val VERSION = 1

    fun encode(state: SessionState): String = JSONObject().apply {
        val s = state.coaching
        put("v", VERSION)
        put("savedAt", s.savedAtMs)
        put("stats", stats(s.trip.stats))
        put("praiseStreak", s.praiseStreak)
        put("spoken", JSONObject().apply { s.arbiter.spokenCounts.forEach { (k, v) -> put(k.name, v) } })
        s.arbiter.lastSpokenMs?.let { put("lastSpoken", it) }
        s.trip.session?.let { put("trip", session(it)) }
        put("announced", JSONObject().apply { s.preview.announced.forEach { (k, v) -> put(k, v) } })
        s.trip.pendingReport?.let { put("report", report(it)) }
        s.preview.route?.let { put("route", route(it)) }
    }.toString()

    /** 읽지 못하면 null (손상·다른 버전). */
    fun decode(json: String?): SessionState? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(json)
            if (o.getInt("v") != VERSION) return null
            SessionState(
                CoachingPipeline.State(
                    savedAtMs = o.getLong("savedAt"),
                    trip = TripRecorder.State(
                        stats = stats(o.getJSONObject("stats")),
                        session = o.optJSONObject("trip")?.let(::session),
                        pendingReport = o.optJSONObject("report")?.let(::report),
                    ),
                    praiseStreak = o.getInt("praiseStreak"),
                    arbiter = CoachingArbiter.State(
                        spokenCounts = o.getJSONObject("spoken").let { sp ->
                            sp.keys().asSequence().mapNotNull { k ->
                                CoachType.entries.firstOrNull { it.name == k }?.let { it to sp.getInt(k) }
                            }.toMap()
                        },
                        lastSpokenMs = if (o.has("lastSpoken")) o.getLong("lastSpoken") else null,
                    ),
                    preview = SituationPreviewEngine.State(
                        announced = o.getJSONObject("announced").let { a -> a.keys().asSequence().associateWith { a.getLong(it) } },
                        route = o.optJSONObject("route")?.let(::route),
                    ),
                ),
            )
        }.getOrNull()
    }

    private fun session(t: TripSession.State) = JSONObject().apply {
        put("startedAt", t.startedAtMs)
        put("lastSpeed", t.lastSpeedKmh.toDouble())
        put("movingMs", t.movingMs)
        put("distanceKm", t.distanceKm)
        put("maxSpeed", t.maxSpeedKmh.toDouble())
        t.pausedAtMs?.let { put("pausedAt", it) }
    }

    private fun session(t: JSONObject) = TripSession.State(
        startedAtMs = t.getLong("startedAt"),
        lastSpeedKmh = t.getDouble("lastSpeed").toFloat(),
        movingMs = t.getLong("movingMs"),
        distanceKm = t.getDouble("distanceKm"),
        maxSpeedKmh = t.getDouble("maxSpeed").toFloat(),
        pausedAtMs = if (t.has("pausedAt")) t.getLong("pausedAt") else null,
    )

    private fun report(r: TripReport) = JSONObject().apply {
        put("startedAt", r.metrics.startedAtMs)
        put("endedAt", r.metrics.endedAtMs)
        put("movingMs", r.metrics.movingMs)
        put("distanceKm", r.metrics.distanceKm.toDouble())
        put("maxSpeed", r.metrics.maxSpeedKmh.toDouble())
        put("stats", stats(r.stats))
        put("score", r.score)
        put("grade", r.grade.name)
        put("nextTask", r.nextTask.name)
    }

    private fun report(r: JSONObject) = TripReport(
        metrics = TripMetrics(
            startedAtMs = r.getLong("startedAt"),
            endedAtMs = r.getLong("endedAt"),
            movingMs = r.getLong("movingMs"),
            distanceKm = r.getDouble("distanceKm").toFloat(),
            maxSpeedKmh = r.getDouble("maxSpeed").toFloat(),
        ),
        stats = stats(r.getJSONObject("stats")),
        score = r.getInt("score"),
        grade = Grade.valueOf(r.getString("grade")),
        nextTask = NextTask.valueOf(r.getString("nextTask")),
    )

    /** 경로 위치. "odometerM"은 이 경로를 따라온 거리. id가 없는 예전 저장분은 시연 경로였다. */
    private fun route(rt: RouteProgress.State) = JSONObject().apply {
        put("id", rt.routeId)
        put("odometerM", rt.travelledM)
        put("speed", rt.speedKmh.toDouble())
    }

    private fun route(rt: JSONObject) = RouteProgress.State(
        routeId = rt.optString("id", FakeRouteNaviEventSource.DEMO_ROUTE.id),
        travelledM = rt.getDouble("odometerM"),
        speedKmh = rt.getDouble("speed").toFloat(),
    )

    private fun stats(s: TripStats) = JSONObject().apply {
        put("laneChanges", s.laneChanges)
        put("signalled", s.signalledLaneChanges)
        put("hardBrakes", s.hardBrakes)
        put("blinkerLeftOn", s.blinkerLeftOn)
        put("turnTooFast", s.turnTooFast)
        put("gearShiftsMoving", s.gearShiftsMoving)
    }

    private fun stats(o: JSONObject) = TripStats(
        laneChanges = o.getInt("laneChanges"),
        signalledLaneChanges = o.getInt("signalled"),
        hardBrakes = o.getInt("hardBrakes"),
        // #53 이전에 저장한 데이터에는 없으므로 0으로 읽는다
        blinkerLeftOn = o.optInt("blinkerLeftOn", 0),
        turnTooFast = o.optInt("turnTooFast", 0),
        gearShiftsMoving = o.optInt("gearShiftsMoving", 0),
    )
}

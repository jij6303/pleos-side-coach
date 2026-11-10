package com.jij6303.sidecoach.data.store

import com.jij6303.sidecoach.data.navi.FakeRouteNaviEventSource
import com.jij6303.sidecoach.domain.coach.CoachType
import com.jij6303.sidecoach.domain.coach.CoachingArbiter
import com.jij6303.sidecoach.domain.report.Grade
import com.jij6303.sidecoach.domain.report.NextTask
import com.jij6303.sidecoach.domain.report.TripReport
import com.jij6303.sidecoach.domain.session.SessionState
import com.jij6303.sidecoach.domain.trip.TripMetrics
import com.jij6303.sidecoach.domain.trip.TripRecorder
import com.jij6303.sidecoach.domain.trip.TripSession
import com.jij6303.sidecoach.domain.trip.TripStats
import org.json.JSONObject

/**
 * [SessionState] ↔ JSON 문자열. 저장 형식이 바뀌면 [VERSION]을 올리고, 다른 버전은 읽지 않는다(새 주행으로 시작).
 */
object SessionStateCodec {
    const val VERSION = 1

    fun encode(s: SessionState): String = JSONObject().apply {
        put("v", VERSION)
        put("savedAt", s.savedAtMs)
        put("stats", stats(s.recorder.stats))
        put("praiseStreak", s.recorder.praiseStreak)
        put("spoken", JSONObject().apply { s.arbiter.spokenCounts.forEach { (k, v) -> put(k.name, v) } })
        s.arbiter.lastSpokenMs?.let { put("lastSpoken", it) }
        s.trip?.let { t ->
            put("trip", JSONObject().apply {
                put("startedAt", t.startedAtMs)
                put("lastSpeed", t.lastSpeedKmh.toDouble())
                put("movingMs", t.movingMs)
                put("distanceKm", t.distanceKm)
                put("maxSpeed", t.maxSpeedKmh.toDouble())
            })
        }
        put("announced", JSONObject().apply { s.announced.forEach { (k, v) -> put(k, v) } })
        s.pendingReport?.let { r ->
            put("report", JSONObject().apply {
                put("startedAt", r.metrics.startedAtMs)
                put("endedAt", r.metrics.endedAtMs)
                put("movingMs", r.metrics.movingMs)
                put("distanceKm", r.metrics.distanceKm.toDouble())
                put("maxSpeed", r.metrics.maxSpeedKmh.toDouble())
                put("stats", stats(r.stats))
                put("score", r.score)
                put("grade", r.grade.name)
                put("nextTask", r.nextTask.name)
            })
        }
        s.route?.let { rt ->
            put("route", JSONObject().apply {
                put("odometerM", rt.odometerM)
                put("speed", rt.speedKmh.toDouble())
                put("lastSent", JSONObject().apply { rt.lastSent.forEach { (k, v) -> put(k, v) } })
            })
        }
    }.toString()

    /** 읽지 못하면 null (손상·다른 버전). */
    fun decode(json: String?): SessionState? {
        if (json.isNullOrBlank()) return null
        return runCatching {
            val o = JSONObject(json)
            if (o.getInt("v") != VERSION) return null
            SessionState(
                savedAtMs = o.getLong("savedAt"),
                recorder = TripRecorder.State(stats(o.getJSONObject("stats")), o.getInt("praiseStreak")),
                arbiter = CoachingArbiter.State(
                    spokenCounts = o.getJSONObject("spoken").let { sp ->
                        sp.keys().asSequence().mapNotNull { k ->
                            CoachType.entries.firstOrNull { it.name == k }?.let { it to sp.getInt(k) }
                        }.toMap()
                    },
                    lastSpokenMs = if (o.has("lastSpoken")) o.getLong("lastSpoken") else null,
                ),
                trip = o.optJSONObject("trip")?.let { t ->
                    TripSession.State(
                        startedAtMs = t.getLong("startedAt"),
                        lastSpeedKmh = t.getDouble("lastSpeed").toFloat(),
                        movingMs = t.getLong("movingMs"),
                        distanceKm = t.getDouble("distanceKm"),
                        maxSpeedKmh = t.getDouble("maxSpeed").toFloat(),
                    )
                },
                announced = o.getJSONObject("announced").let { a ->
                    a.keys().asSequence().associateWith { a.getLong(it) }
                },
                pendingReport = o.optJSONObject("report")?.let { r ->
                    TripReport(
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
                },
                route = o.optJSONObject("route")?.let { rt ->
                    FakeRouteNaviEventSource.RouteState(
                        odometerM = rt.getDouble("odometerM"),
                        speedKmh = rt.getDouble("speed").toFloat(),
                        lastSent = rt.getJSONObject("lastSent").let { ls ->
                            ls.keys().asSequence().associateWith { ls.getInt(it) }
                        },
                    )
                },
            )
        }.getOrNull()
    }

    private fun stats(s: TripStats) = JSONObject().apply {
        put("laneChanges", s.laneChanges)
        put("signalled", s.signalledLaneChanges)
        put("hardBrakes", s.hardBrakes)
    }

    private fun stats(o: JSONObject) = TripStats(
        laneChanges = o.getInt("laneChanges"),
        signalledLaneChanges = o.getInt("signalled"),
        hardBrakes = o.getInt("hardBrakes"),
    )
}

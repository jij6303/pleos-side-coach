package com.jij6303.sidecoach.domain.trip

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.domain.DrivingSnapshot

/** 한 번의 주행에서 잰 값. */
data class TripMetrics(
    val startedAtMs: Long,
    val endedAtMs: Long,
    /** 차속이 [TripSession.Config.movingSpeedKmh]를 넘은 시간. */
    val movingMs: Long,
    val distanceKm: Float,
    val maxSpeedKmh: Float,
)

/**
 * 기어로 주행 단위를 나눈다. P가 아닌 기어가 되면 시작, P로 돌아오면 끝.
 * 시간은 스냅샷 timestamp를 쓴다 (JVM 테스트에서 조작 가능).
 */
class TripSession(private val config: Config = Config()) {

    /** 💭 설계 가정. */
    data class Config(
        val movingSpeedKmh: Float = 3f,
        /** 이보다 짧게 움직였으면 성적표를 만들지 않는다 (P↔D만 바꾼 경우 등). */
        val minMovingMs: Long = 5_000,
    )

    sealed interface Event {
        data object Started : Event
        data class Ended(val metrics: TripMetrics) : Event
        /** 너무 짧아서 성적표 없이 끝남. */
        data object Discarded : Event
    }

    private var active = false
    private var startedAt = 0L
    private var lastTs = 0L
    private var lastSpeed = 0f
    private var movingMs = 0L
    private var distanceKm = 0.0
    private var maxSpeed = 0f

    val isActive: Boolean get() = active

    fun onSnapshot(s: DrivingSnapshot): Event? {
        if (s.gear == GearPosition.UNKNOWN) return null
        return if (!active) {
            if (s.gear == GearPosition.PARK) return null
            start(s)
            Event.Started
        } else {
            accumulate(s)
            if (s.gear != GearPosition.PARK) return null
            active = false
            if (movingMs < config.minMovingMs) {
                Event.Discarded
            } else {
                Event.Ended(
                    TripMetrics(
                        startedAtMs = startedAt,
                        endedAtMs = s.timestampMs,
                        movingMs = movingMs,
                        distanceKm = distanceKm.toFloat(),
                        maxSpeedKmh = maxSpeed,
                    ),
                )
            }
        }
    }

    fun reset() {
        active = false
    }

    private fun start(s: DrivingSnapshot) {
        active = true
        startedAt = s.timestampMs
        lastTs = s.timestampMs
        lastSpeed = s.speedKmh ?: 0f
        movingMs = 0
        distanceKm = 0.0
        maxSpeed = lastSpeed
    }

    /**
     * 직전 차속이 다음 이벤트까지 유지됐다고 보고(계단형) 적분한다.
     * 차속 이벤트는 값이 바뀔 때만 오므로 정속 주행 중에는 간격이 길 수 있다. 간격을 자르지 않는다.
     */
    private fun accumulate(s: DrivingSnapshot) {
        val dt = (s.timestampMs - lastTs).coerceAtLeast(0)
        if (lastSpeed > config.movingSpeedKmh) movingMs += dt
        distanceKm += lastSpeed * dt / 3_600_000.0
        lastTs = s.timestampMs
        lastSpeed = s.speedKmh ?: lastSpeed
        if (lastSpeed > maxSpeed) maxSpeed = lastSpeed
    }

    /** 저장용 진행 중 주행 상태. */
    data class State(
        val startedAtMs: Long,
        val lastSpeedKmh: Float,
        val movingMs: Long,
        val distanceKm: Double,
        val maxSpeedKmh: Float,
    )

    /** 진행 중인 주행이 없으면 null. */
    fun exportState(): State? =
        if (!active) null else State(startedAt, lastSpeed, movingMs, distanceKm, maxSpeed)

    /**
     * 진행 중이던 주행을 이어간다. 마지막 시각을 [nowMs]로 맞춰,
     * 프로세스가 내려가 있던 동안은 주행 시간·거리에 넣지 않는다.
     */
    fun restore(state: State?, nowMs: Long) {
        if (state == null) {
            active = false
            return
        }
        active = true
        startedAt = state.startedAtMs
        lastTs = nowMs
        lastSpeed = state.lastSpeedKmh
        movingMs = state.movingMs
        distanceKm = state.distanceKm
        maxSpeed = state.maxSpeedKmh
    }
}

package com.jij6303.sidecoach.feature.report.trip

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.feature.Restorable
import com.jij6303.sidecoach.state.DrivingSnapshot

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
 * 기어와 차속으로 주행 단위를 나눈다.
 *
 * - 시작: P가 아닌 기어에서 실제로 움직이기 시작할 때([Config.movingSpeedKmh] 초과).
 *   P에서 기어만 바꿨다 돌아오면 주행이 시작되지 않아 이전 기록이 남는다 (#51).
 * - 끝: P에 넣을 때. 성적표를 바로 만든다.
 * - 이어가기: P에 넣고 [Config.resumeWindowMs] 안에 다시 움직이면 같은 주행으로 이어간다
 *   (신호 대기 중 P에 넣었다 출발하는 경우, #51). 그보다 늦으면 새 주행이다.
 *
 * 시간은 스냅샷 timestamp를 쓴다 (JVM 테스트에서 조작 가능).
 */
class TripSession(private val config: Config = Config()) : Restorable<TripSession.State?> {

    /** 💭 설계 가정. */
    data class Config(
        val movingSpeedKmh: Float = 3f,
        /** 이보다 짧게 움직였으면 성적표를 만들지 않는다. */
        val minMovingMs: Long = 5_000,
        /** P에 넣은 뒤 이 시간 안에 다시 움직이면 같은 주행. */
        val resumeWindowMs: Long = 60_000,
    )

    sealed interface Event {
        data object Started : Event
        /** P에 잠깐 넣었다가 다시 출발해 같은 주행을 이어감. 통계는 유지한다. */
        data object Resumed : Event
        data class Ended(val metrics: TripMetrics) : Event
        /** 너무 짧아서 성적표 없이 끝남. */
        data object Discarded : Event
    }

    private var active = false
    /** P에 넣어 멈춘 시각. 이어갈 수 있는 주행이 없으면 null. */
    private var pausedAt: Long? = null
    private var startedAt = 0L
    private var lastTs = 0L
    private var lastSpeed = 0f
    private var movingMs = 0L
    private var distanceKm = 0.0
    private var maxSpeed = 0f

    val isActive: Boolean get() = active

    fun onSnapshot(s: DrivingSnapshot): Event? {
        if (s.gear == GearPosition.UNKNOWN) return null
        if (!active) {
            val speed = s.speedKmh ?: return null
            if (s.gear == GearPosition.PARK || speed <= config.movingSpeedKmh) return null
            val paused = pausedAt
            return if (paused != null && s.timestampMs - paused <= config.resumeWindowMs) {
                resume(s)
                Event.Resumed
            } else {
                start(s)
                Event.Started
            }
        }
        accumulate(s)
        if (s.gear != GearPosition.PARK) return null
        active = false
        pausedAt = s.timestampMs
        return if (movingMs < config.minMovingMs) {
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

    fun reset() {
        active = false
        pausedAt = null
    }

    private fun start(s: DrivingSnapshot) {
        active = true
        pausedAt = null
        startedAt = s.timestampMs
        lastTs = s.timestampMs
        lastSpeed = s.speedKmh ?: 0f
        movingMs = 0
        distanceKm = 0.0
        maxSpeed = lastSpeed
    }

    /** 멈춰 있던 동안은 주행 시간·거리에 넣지 않고 지표를 이어간다. */
    private fun resume(s: DrivingSnapshot) {
        active = true
        pausedAt = null
        lastTs = s.timestampMs
        lastSpeed = s.speedKmh ?: 0f
        if (lastSpeed > maxSpeed) maxSpeed = lastSpeed
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

    /** 저장용 주행 상태. [pausedAtMs]가 있으면 P에 넣어 멈춘 상태(이어가기 대기). */
    data class State(
        val startedAtMs: Long,
        val lastSpeedKmh: Float,
        val movingMs: Long,
        val distanceKm: Double,
        val maxSpeedKmh: Float,
        val pausedAtMs: Long? = null,
    )

    /** 진행 중이거나 이어갈 수 있는 주행이 없으면 null. */
    override fun exportState(): State? =
        if (!active && pausedAt == null) null
        else State(startedAt, lastSpeed, movingMs, distanceKm, maxSpeed, pausedAt.takeIf { !active })

    /**
     * 저장된 주행을 이어간다. 마지막 시각을 [nowMs]로 맞춰,
     * 프로세스가 내려가 있던 동안은 주행 시간·거리에 넣지 않는다.
     */
    override fun restore(state: State?, nowMs: Long) {
        if (state == null) {
            reset()
            return
        }
        active = state.pausedAtMs == null
        pausedAt = state.pausedAtMs
        startedAt = state.startedAtMs
        lastTs = nowMs
        lastSpeed = state.lastSpeedKmh
        movingMs = state.movingMs
        distanceKm = state.distanceKm
        maxSpeed = state.maxSpeedKmh
    }
}

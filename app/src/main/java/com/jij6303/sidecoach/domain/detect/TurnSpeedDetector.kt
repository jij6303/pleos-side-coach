package com.jij6303.sidecoach.domain.detect

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.domain.DrivingSnapshot
import kotlin.math.abs

/**
 * 회전 중 과속 (#49).
 *
 * 핸들을 크게 꺾은 채([Config.minSteeringDeg] 이상) 차속이 [Config.minSpeedKmh] 이상인 상태가
 * [Config.holdMs] 넘게 이어지면 알린다. 차선 변경 정도의 작은 조향은 해당하지 않는다.
 * 조향각은 핸들 각도(°)이며 왼쪽 +, 오른쪽 − (실측). 방향과 상관없이 절댓값으로 본다.
 */
class TurnSpeedDetector(private val config: Config = Config()) : Detector {

    /** 💭 설계 가정. */
    data class Config(
        val minSteeringDeg: Float = 90f,
        val minSpeedKmh: Float = 30f,
        val holdMs: Long = 1_000,
        val cooldownMs: Long = 15_000,
    )

    private var since: Long? = null
    private var lastFiredMs: Long? = null

    override fun onSnapshot(s: DrivingSnapshot): DetectionEvent? {
        val now = s.timestampMs
        val steering = s.steeringDeg
        val speed = s.speedKmh
        if (s.gear != GearPosition.DRIVE || steering == null || speed == null ||
            abs(steering) < config.minSteeringDeg || speed < config.minSpeedKmh
        ) {
            since = null
            return null
        }
        val start = since ?: now.also { since = it }
        if (now - start < config.holdMs) return null
        val last = lastFiredMs
        if (last != null && now - last < config.cooldownMs) return null
        lastFiredMs = now
        return DetectionEvent.TurnTooFast(speed, steering, now)
    }

    override fun reset() {
        since = null
        lastFiredMs = null
    }
}

package com.jij6303.sidecoach.feature.coach.detect

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.state.DrivingSnapshot

/**
 * 움직이는 중 기어 변경 (#49).
 *
 * 차가 [Config.movingSpeedKmh] 이상으로 움직이는 동안 D↔R로 바꾸거나 P에 넣으면 알린다.
 * N으로 바꾸는 것과 N에서 바꾸는 것은 판정하지 않는다.
 *
 * 변속 직전 차속은 "변속 전 [Config.lookbackMs] 동안의 최고 차속"으로 본다.
 * 실측(#49): Virtual Console은 기어를 바꾸는 순간 차속 0을 같은 시각에 먼저 보내므로,
 * 마지막 차속만 보면 달리던 중 변속을 놓친다. 실차는 차속이 순간적으로 0이 되지 않는다.
 */
class GearShiftWhileMovingDetector(private val config: Config = Config()) : Detector {

    /** 💭 설계 가정. */
    data class Config(
        val movingSpeedKmh: Float = 3f,
        val lookbackMs: Long = 500,
    )

    private data class Sample(val ts: Long, val kmh: Float)

    private var lastGear: GearPosition = GearPosition.UNKNOWN
    private val samples = ArrayDeque<Sample>()

    override fun onSnapshot(s: DrivingSnapshot): DetectionEvent? {
        val now = s.timestampMs
        val from = lastGear
        val to = s.gear
        // 기어 신호가 오기 전까지의 차속으로 판단한다 (이번 스냅샷의 차속은 변속 후 값일 수 있음)
        val speedBefore = maxSpeedSince(now - config.lookbackMs)
        if (to != GearPosition.UNKNOWN) lastGear = to
        s.speedKmh?.let { record(now, it) }

        if (from == to || from !in DRIVING_GEARS || to !in TARGET_GEARS) return null
        if (speedBefore < config.movingSpeedKmh) return null
        return DetectionEvent.GearShiftWhileMoving(from, to, speedBefore, now)
    }

    override fun reset() {
        lastGear = GearPosition.UNKNOWN
        samples.clear()
    }

    private fun record(ts: Long, kmh: Float) {
        if (samples.lastOrNull()?.kmh == kmh) return
        samples.addLast(Sample(ts, kmh))
        // 창 바로 바깥의 샘플 하나는 남긴다 (창 시작 시점에 유효했던 차속)
        val cutoff = ts - config.lookbackMs
        while (samples.size >= 2 && samples[1].ts <= cutoff) samples.removeFirst()
    }

    /** [since] 이후 한 번이라도 유효했던 차속 중 최고값. 기록이 없으면 0. */
    private fun maxSpeedSince(since: Long): Float {
        var max = 0f
        for (i in samples.indices) {
            val validUntil = samples.getOrNull(i + 1)?.ts ?: Long.MAX_VALUE
            if (validUntil > since) max = maxOf(max, samples[i].kmh)
        }
        return max
    }

    private companion object {
        val DRIVING_GEARS = setOf(GearPosition.DRIVE, GearPosition.REVERSE)
        val TARGET_GEARS = setOf(GearPosition.DRIVE, GearPosition.REVERSE, GearPosition.PARK)
    }
}

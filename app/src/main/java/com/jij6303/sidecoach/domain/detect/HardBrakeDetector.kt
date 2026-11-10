package com.jij6303.sidecoach.domain.detect

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.domain.DrivingSnapshot

/**
 * 급제동 판정 (dev-architecture 4.2).
 *
 * 브레이크는 Boolean만 오므로(실측) 차속 감속도가 주 판정이고, 브레이크 눌림은 보조 조건이다.
 * 저속 제외 조건은 현재 속도가 아니라 **감속 시작 속도**에 적용한다. 현재 속도로 걸면 정지까지 가는
 * 급제동(예: 45 → 0 km/h)이 잡히지 않는다.
 * 후진(R) 중에는 판정하지 않는다. 주차 중 정지는 주차 코치가 맡는다(#37 실측: 콘솔 후진 후 정지에서 오탐).
 */
class HardBrakeDetector(private val config: Config = Config()) : Detector {

    /** 💭 설계 가정. */
    data class Config(
        val decelThresholdKmhPerSec: Float = 10f,
        val minStartSpeedKmh: Float = 15f,
        val windowMs: Long = 1_000,
        /** 샘플 간격이 이보다 짧으면 감속도를 계산하지 않는다(노이즈 방지). */
        val minSpanMs: Long = 300,
        val brakeWithinMs: Long = 1_000,
        val cooldownMs: Long = 10_000,
    )

    private data class Sample(val ts: Long, val kmh: Float)

    private val samples = ArrayDeque<Sample>()
    private var lastBrakePressedMs: Long? = null
    private var lastFiredMs: Long? = null

    override fun onSnapshot(s: DrivingSnapshot): DetectionEvent? {
        val now = s.timestampMs
        if (s.brakePressed == true) lastBrakePressedMs = now

        if (s.gear == GearPosition.REVERSE) {
            samples.clear()
            return null
        }
        val speed = s.speedKmh ?: return null
        if (samples.isEmpty() || samples.last().ts != now || samples.last().kmh != speed) {
            samples.addLast(Sample(now, speed))
        }
        val cutoff = now - config.windowMs
        // 창 바로 바깥의 샘플 하나는 남겨 1초 전 속도를 추정할 기준으로 쓴다
        while (samples.size >= 2 && samples[1].ts <= cutoff) samples.removeFirst()

        // 차속 이벤트는 값이 바뀔 때만 올 수 있어 계단형으로 본다.
        // 창 바깥 샘플이면 1초 전 속도도 그 값이므로 간격을 창 길이로 둔다.
        val ref = samples.first()
        val span = if (ref.ts <= cutoff) config.windowMs else now - ref.ts
        if (span < config.minSpanMs || ref.kmh < config.minStartSpeedKmh) return null

        val decel = (ref.kmh - speed) * 1_000f / span
        if (decel < config.decelThresholdKmhPerSec) return null

        val braked = lastBrakePressedMs?.let { now - it <= config.brakeWithinMs } ?: false
        if (!braked) return null

        val last = lastFiredMs
        if (last != null && now - last < config.cooldownMs) return null

        lastFiredMs = now
        return DetectionEvent.HardBrake(decel, ref.kmh, now)
    }

    override fun reset() {
        samples.clear()
        lastBrakePressedMs = null
        lastFiredMs = null
    }
}

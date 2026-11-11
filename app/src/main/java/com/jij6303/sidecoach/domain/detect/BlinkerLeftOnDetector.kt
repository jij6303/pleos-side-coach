package com.jij6303.sidecoach.domain.detect

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.domain.DrivingSnapshot
import kotlin.math.abs

/**
 * 깜빡이 끄기 잊음 (#49).
 *
 * 깜빡이가 켜진 동안 핸들을 꺾었다가(회전·차선 변경) 가운데로 되돌렸는데,
 * 그 뒤로도 깜빡이를 켠 채 [Config.forgotAfterMovingMs] 넘게 달리면 알린다.
 * 정차 중(신호 대기)에는 시간을 세지 않는다. 깜빡이를 한 번 켤 때마다 최대 1회.
 * 핸들을 꺾지 않고 켜만 둔 경우(합류 대기 등)는 판정하지 않는다.
 */
class BlinkerLeftOnDetector(private val config: Config = Config()) : Detector {

    /** 💭 설계 가정. */
    data class Config(
        val thetaTurnDeg: Float = 12f,
        val thetaCenterDeg: Float = 5f,
        val minSpeedKmh: Float = 10f,
        val forgotAfterMovingMs: Long = 8_000,
    )

    private var signal: TurnSignalState = TurnSignalState.OFF
    private var steered = false
    private var centered = false
    private var movingSinceCenterMs = 0L
    private var lastTs: Long? = null
    private var fired = false

    override fun onSnapshot(s: DrivingSnapshot): DetectionEvent? {
        val now = s.timestampMs
        val dt = lastTs?.let { (now - it).coerceAtLeast(0) } ?: 0L
        lastTs = now

        val current = s.turnSignal ?: return null
        if (current != signal) {
            signal = current
            resetActivation()
        }
        if (signal == TurnSignalState.OFF || s.gear != GearPosition.DRIVE) return null

        val steering = s.steeringDeg ?: return null
        val speed = s.speedKmh ?: 0f
        if (abs(steering) >= config.thetaTurnDeg) {
            steered = true
            centered = false
            movingSinceCenterMs = 0
            return null
        }
        if (!steered) return null
        if (!centered) {
            if (abs(steering) <= config.thetaCenterDeg) centered = true
            return null
        }
        if (speed >= config.minSpeedKmh) movingSinceCenterMs += dt
        if (fired || movingSinceCenterMs < config.forgotAfterMovingMs) return null
        fired = true
        return DetectionEvent.BlinkerLeftOn(signal, now)
    }

    override fun reset() {
        signal = TurnSignalState.OFF
        lastTs = null
        resetActivation()
    }

    private fun resetActivation() {
        steered = false
        centered = false
        movingSinceCenterMs = 0
        fired = false
    }
}

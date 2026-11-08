package com.jij6303.sidecoach.domain.detect

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.domain.DrivingSnapshot
import kotlin.math.abs
import kotlin.math.sign

/**
 * 깜빡이 누락 판정 (dev-architecture 4.1).
 *
 * 차선 정보가 없으므로 "한쪽으로 꺾었다가 반대로 되돌린 뒤 중립 복귀"하는 S자 조향을 차선 변경으로 본다.
 * 조향각 부호는 실측 기준 왼쪽 = +, 오른쪽 = − 이다.
 *
 * 한계: 비상등 상태를 읽을 수 없어(#7) 비상등 중에도 판정한다. 비상등 중엔 TurnSignal이 OFF로 보고되므로
 * 비상등을 켠 채 차선을 바꾸면 누락으로 판정될 수 있다.
 */
class BlinkerOmissionDetector(
    private val config: Config = Config(),
    /** TBT 회전·분기 지점 근처면 true. 내비 연동 전에는 항상 false. */
    private val isNearTurn: () -> Boolean = { false },
) : Detector {

    /** 임계값은 전부 💭 설계 가정. 에뮬레이터·실차 로그로 튜닝한다. */
    data class Config(
        val minSpeedKmh: Float = 30f,
        val thetaOnDeg: Float = 12f,
        val thetaOffDeg: Float = 4f,
        val minManeuverMs: Long = 2_000,
        val maxManeuverMs: Long = 8_000,
        /** 조향 시작 전 이 시간 안에 깜빡이가 켜져 있었으면 인정. */
        val signalPreMs: Long = 3_000,
        /** 조향 시작 후 이 시간 안에 켜도 인정. */
        val signalPostMs: Long = 1_000,
    )

    private enum class State { IDLE, SWING_1, SWING_2 }

    private var state = State.IDLE
    private var dir1 = 0f
    private var tStart = 0L
    private var signalled = false

    /** 깜빡이 방향별 상태 추적: 현재 켜진 방향과, 방향별로 마지막으로 꺼진 시각. */
    private var currentSignal: TurnSignalState = TurnSignalState.OFF
    private val lastOffMs = mutableMapOf<TurnSignalState, Long>()

    override fun onSnapshot(s: DrivingSnapshot): DetectionEvent? {
        trackTurnSignal(s)

        val speed = s.speedKmh
        val steering = s.steeringDeg
        if (speed == null || steering == null || speed < config.minSpeedKmh ||
            s.gear != GearPosition.DRIVE || isNearTurn()
        ) {
            resetManeuver()
            return null
        }

        when (state) {
            State.IDLE -> if (abs(steering) >= config.thetaOnDeg) {
                dir1 = sign(steering)
                tStart = s.timestampMs
                state = State.SWING_1
                signalled = wasSignalOn(expectedSignal(), since = tStart - config.signalPreMs)
            }

            State.SWING_1 -> {
                checkLateSignal(s)
                if (s.timestampMs - tStart > config.maxManeuverMs) {
                    resetManeuver() // 너무 길면 커브로 본다
                } else if (sign(steering) == -dir1 && abs(steering) >= config.thetaOnDeg) {
                    state = State.SWING_2
                }
            }

            State.SWING_2 -> {
                checkLateSignal(s)
                val duration = s.timestampMs - tStart
                if (duration > config.maxManeuverMs) {
                    resetManeuver()
                } else if (abs(steering) <= config.thetaOffDeg) {
                    val event = if (duration >= config.minManeuverMs) {
                        DetectionEvent.LaneChange(expectedSignal(), signalled, s.timestampMs)
                    } else {
                        null // 너무 짧은 흔들림은 무시
                    }
                    resetManeuver()
                    return event
                }
            }
        }
        return null
    }

    override fun reset() {
        resetManeuver()
        currentSignal = TurnSignalState.OFF
        lastOffMs.clear()
    }

    private fun resetManeuver() {
        state = State.IDLE
        dir1 = 0f
        signalled = false
    }

    private fun expectedSignal(): TurnSignalState =
        if (dir1 > 0f) TurnSignalState.LEFT else TurnSignalState.RIGHT

    private fun trackTurnSignal(s: DrivingSnapshot) {
        val now = s.turnSignal ?: return
        if (now != currentSignal) {
            if (currentSignal != TurnSignalState.OFF) lastOffMs[currentSignal] = s.timestampMs
            currentSignal = now
        }
    }

    private fun wasSignalOn(dir: TurnSignalState, since: Long): Boolean =
        currentSignal == dir || (lastOffMs[dir] ?: Long.MIN_VALUE) >= since

    private fun checkLateSignal(s: DrivingSnapshot) {
        if (!signalled && s.timestampMs <= tStart + config.signalPostMs && currentSignal == expectedSignal()) {
            signalled = true
        }
    }
}

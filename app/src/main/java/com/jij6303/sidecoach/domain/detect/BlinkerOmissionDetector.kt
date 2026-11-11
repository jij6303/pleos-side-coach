package com.jij6303.sidecoach.domain.detect

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.domain.DrivingSnapshot
import kotlin.math.abs
import kotlin.math.sign

/**
 * 깜빡이 누락 판정 (dev-architecture 4.1).
 *
 * 차선 정보가 없으므로 조향 패턴으로 차선 변경을 본다. 조향각 부호는 실측 기준 왼쪽 = +, 오른쪽 = − 이다.
 * - 한쪽으로 꺾었다가 중립으로 돌아옴 (2~8초) → 차선 변경 (#57: 반대쪽 조향이 작거나 없는 경우)
 * - 한쪽으로 꺾고 반대쪽으로 되돌린 뒤 중립 (S자) → 차선 변경
 * - 차선 변경 직후 [Config.counterSteerMs] 안에 시작한 반대쪽 조향은 같은 차선 변경의 되돌림으로 보고 세지 않는다.
 * - 한쪽 조향이 2초도 안 돼 중립에 오면 [Config.counterSteerMs] 동안 반대쪽 조향을 기다려 S자로 이어 본다.
 * - [Config.maxLaneChangeDeg] 이상 꺾거나 8초 넘게 꺾고 있으면 회전·커브로 보고 세지 않는다.
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
        /** 차선 변경 직후 반대쪽 되돌림 조향을 같은 차선 변경으로 볼 시간. */
        val counterSteerMs: Long = 1_500,
        /** 이보다 크게 꺾으면 교차로 회전으로 보고 차선 변경에서 뺀다 (#57). */
        val maxLaneChangeDeg: Float = 90f,
    )

    private enum class State {
        IDLE,
        SWING_1,
        /** 첫 조향이 짧게 끝나 중립에 옴. 반대쪽 조향이 이어지면 S자로 본다. */
        PAUSE,
        SWING_2,
        /** 방금 판정한 차선 변경의 되돌림 조향. 중립에 오면 IDLE. */
        COUNTER,

        /** 회전(크게 꺾음)이나 커브(오래 꺾음). 핸들이 중립에 올 때까지 아무것도 세지 않는다. */
        WAIT_NEUTRAL,
    }

    private var state = State.IDLE
    private var dir1 = 0f
    private var tStart = 0L
    private var signalled = false
    private var neutralAtMs = 0L

    /** 마지막으로 판정한 차선 변경의 방향(조향 부호)과 시각. 되돌림 조향을 거르는 데 쓴다. */
    private var lastDir = 0f
    private var lastEventMs: Long? = null

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

        val now = s.timestampMs
        if (abs(steering) >= config.maxLaneChangeDeg) {
            // 크게 꺾음: 회전이다. 핸들을 푸는 동안도 차선 변경으로 세지 않도록 중립까지 기다린다
            waitNeutral()
            return null
        }
        val swingDir = if (abs(steering) >= config.thetaOnDeg) sign(steering) else 0f
        when (state) {
            State.IDLE -> if (swingDir != 0f) {
                val last = lastEventMs
                if (last != null && swingDir == -lastDir && now - last <= config.counterSteerMs) {
                    state = State.COUNTER // 직전 차선 변경의 되돌림
                } else {
                    dir1 = swingDir
                    tStart = now
                    state = State.SWING_1
                    signalled = wasSignalOn(expectedSignal(), since = tStart - config.signalPreMs)
                }
            }

            State.SWING_1 -> {
                checkLateSignal(s)
                val duration = now - tStart
                when {
                    duration > config.maxManeuverMs -> waitNeutral() // 너무 길면 커브로 본다
                    swingDir == -dir1 -> state = State.SWING_2
                    abs(steering) <= config.thetaOffDeg ->
                        if (duration >= config.minManeuverMs) {
                            return finish(now) // 한쪽으로 꺾었다 돌아옴
                        } else {
                            neutralAtMs = now
                            state = State.PAUSE
                        }
                }
            }

            State.PAUSE -> {
                checkLateSignal(s)
                when {
                    now - neutralAtMs > config.counterSteerMs -> resetManeuver() // 짧은 흔들림
                    swingDir == -dir1 -> state = State.SWING_2
                    swingDir == dir1 -> state = State.SWING_1
                }
            }

            State.SWING_2 -> {
                checkLateSignal(s)
                val duration = now - tStart
                if (duration > config.maxManeuverMs) {
                    waitNeutral()
                } else if (abs(steering) <= config.thetaOffDeg) {
                    if (duration >= config.minManeuverMs) return finish(now)
                    resetManeuver() // 너무 짧은 흔들림은 무시
                }
            }

            State.WAIT_NEUTRAL -> if (abs(steering) <= config.thetaOffDeg) state = State.IDLE

            State.COUNTER -> if (abs(steering) <= config.thetaOffDeg || now - (lastEventMs ?: now) > config.maxManeuverMs) {
                state = State.IDLE
            }
        }
        return null
    }

    /** 회전·커브: 핸들을 푸는 동안 차선 변경으로 세지 않도록 중립까지 기다린다. */
    private fun waitNeutral() {
        resetManeuver()
        state = State.WAIT_NEUTRAL
    }

    private fun finish(now: Long): DetectionEvent {
        val event = DetectionEvent.LaneChange(expectedSignal(), signalled, now)
        lastDir = dir1
        lastEventMs = now
        resetManeuver()
        return event
    }

    override fun reset() {
        resetManeuver()
        lastDir = 0f
        lastEventMs = null
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

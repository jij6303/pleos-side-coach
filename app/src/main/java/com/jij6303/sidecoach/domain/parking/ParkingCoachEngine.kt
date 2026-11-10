package com.jij6303.sidecoach.domain.parking

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.domain.DrivingSnapshot
import com.jij6303.sidecoach.domain.coach.CoachMessage
import com.jij6303.sidecoach.domain.coach.CoachType
import kotlin.math.abs
import kotlin.math.sign

/** 진행 중인 주차 코치. */
data class ParkingState(
    val type: ParkingType,
    val stepIndex: Int = 0,
) {
    val steps: List<ParkingStep> get() = type.steps
    val step: ParkingStep get() = steps[stepIndex]
}

/**
 * 주차 절차 판정 (#37). 시간은 스냅샷 timestamp와 [tick]의 인자로 받는다 (JVM 테스트).
 *
 * - 시작: 직전 기어가 D/N이고 R이 되면. P → R은 주차칸에서 빼는 경우라 시작하지 않는다.
 * - 핸들 단계는 조향각으로 판정한다. 기준점 단계는 뒤로 움직인 뒤 [Config.stopHoldMs] 동안 멈춰 있으면 넘긴다.
 *   브레이크 신호는 조건에 넣지 않는다(실측: 콘솔 BRAKE는 누르는 동안만 true, 멈춘 뒤 떼면 false).
 *   실차는 R에서 크립 때문에 멈춰 있으려면 브레이크를 밟고 있어야 하므로 결과는 같다.
 *   앱이 보이면 [tapNext]("다음" 버튼)로도 넘긴다.
 * - 핸들이 [Config.stallMs] 동안 멈춰 있으면 덜 돌렸다/덜 풀었다고 알려 준다.
 *   스냅샷은 신호가 바뀔 때만 오므로 멈춤 판정은 [tick]으로 한다.
 * - P → 마지막 단계면 완료, 아니면 중단. D → 중단하고 다음 R에서 처음부터.
 */
class ParkingCoachEngine(private val config: Config = Config()) {

    /** 💭 설계 가정. 조향각 실측 범위 -470 ~ 500°. */
    data class Config(
        val fullLockDeg: Float = 420f,
        val centerToleranceDeg: Float = 20f,
        /** 이 각도 이상 돌렸을 때부터 "덜 돌렸어요"·"반대쪽이에요"를 판단한다. */
        val turningDeg: Float = 60f,
        /** 이 값 이하로 바뀌면 핸들이 멈춰 있다고 본다. */
        val stallDeltaDeg: Float = 5f,
        val stallMs: Long = 2_500,
        val nudgeCooldownMs: Long = 6_000,
        val tooFastKmh: Float = 7f,
        val tooFastCooldownMs: Long = 8_000,
        /**
         * 단계 안내 뒤 이 시간 동안은 속도 경고를 하지 않는다.
         * 음성 대기열은 최신 1개만 남겨서, 경고가 대기 중인 단계 안내를 밀어낼 수 있다.
         */
        val quietAfterInstructionMs: Long = 6_000,
        /** 이 속도를 넘으면 기준점 단계에서 "움직였다"고 본다. */
        val movingKmh: Float = 1.5f,
        /** 이 속도 미만이면 멈춘 것. */
        val stoppedKmh: Float = 1f,
        val stopHoldMs: Long = 800,
    )

    /** 다음 R에서 시작할 주차 유형. null이면 주차 코치를 쓰지 않는다. */
    var type: ParkingType? = ParkingType.REVERSE

    var state: ParkingState? = null
        private set

    private var lastGear: GearPosition? = null
    private var last: DrivingSnapshot? = null

    /** 처음 끝까지 꺾은 방향(+1 왼쪽, −1 오른쪽). 반대쪽 단계에서 쓴다. */
    private var lockSign = 0f
    private var stepStartedMs = 0L
    /** 단계에 들어와 처음 본 조향각. 풀기 시작했는지 판단한다. */
    private var stepStartDeg: Float? = null
    private var steerRefDeg: Float? = null
    private var steerRefMs = 0L
    private var lastNudgeMs = Long.MIN_VALUE / 2
    private var lastTooFastMs = Long.MIN_VALUE / 2
    /** 기준점 단계에 들어온 뒤 뒤로 움직였는가. */
    private var movedInStep = false
    private var stoppedSinceMs: Long? = null

    /** 지금 단계를 버튼으로 넘길 수 있는가. 조향 신호가 없으면 핸들 단계도 버튼으로 넘긴다. */
    val canTapNext: Boolean
        get() {
            val st = state ?: return false
            return when (st.step.kind) {
                StepKind.REFERENCE -> true
                StepKind.FINISH -> false
                else -> last?.steeringDeg == null
            }
        }

    fun onSnapshot(s: DrivingSnapshot): CoachMessage? {
        last = s
        if (s.gear == GearPosition.UNKNOWN) return null
        val prevGear = lastGear
        lastGear = s.gear
        val st = state
        if (st == null) {
            val chosen = type ?: return null
            if (s.gear == GearPosition.REVERSE && (prevGear == GearPosition.DRIVE || prevGear == GearPosition.NEUTRAL)) {
                return start(chosen, s.timestampMs)
            }
            return null
        }
        return when (s.gear) {
            GearPosition.PARK -> finish(if (st.step.kind == StepKind.FINISH) ParkingPhrases.DONE else ParkingPhrases.CANCELLED, s.timestampMs)
            GearPosition.DRIVE -> finish(ParkingPhrases.RETRY, s.timestampMs)
            GearPosition.REVERSE -> evaluate(st, s, s.timestampMs)
            else -> null
        }
    }

    /** 신호가 안 바뀌어도 멈춤 판정을 하도록 주기적으로 부른다. */
    fun tick(nowMs: Long): CoachMessage? {
        val st = state ?: return null
        val s = last ?: return null
        if (s.gear != GearPosition.REVERSE) return null
        return evaluate(st, s, nowMs)
    }

    /** "다음" 버튼. 넘길 수 없는 단계면 null. */
    fun tapNext(nowMs: Long): CoachMessage? {
        val st = state ?: return null
        if (!canTapNext) return null
        return advance(st, nowMs)
    }

    /** 코칭이 멈출 때. 말없이 끝낸다. */
    fun stop() {
        state = null
        lastGear = null
        last = null
    }

    private fun start(type: ParkingType, nowMs: Long): CoachMessage {
        val st = ParkingState(type)
        state = st
        lockSign = 0f
        enterStep(nowMs)
        return message(st.step.instruction, nowMs)
    }

    private fun finish(text: String, nowMs: Long): CoachMessage {
        state = null
        return message(text, nowMs)
    }

    private fun advance(st: ParkingState, nowMs: Long): CoachMessage {
        val next = st.copy(stepIndex = (st.stepIndex + 1).coerceAtMost(st.steps.lastIndex))
        state = next
        enterStep(nowMs)
        return message(next.step.instruction, nowMs)
    }

    private fun enterStep(nowMs: Long) {
        stepStartedMs = nowMs
        stepStartDeg = null
        steerRefDeg = null
        movedInStep = false
        stoppedSinceMs = null
        steerRefMs = nowMs
    }

    private fun evaluate(st: ParkingState, s: DrivingSnapshot, nowMs: Long): CoachMessage? {
        stepMessage(st, s.steeringDeg, nowMs)?.let { return it }
        val speed = s.speedKmh?.let(::abs) ?: return null
        if (st.step.kind == StepKind.REFERENCE) referenceStop(st, speed, nowMs)?.let { return it }
        if (speed > config.tooFastKmh &&
            nowMs - stepStartedMs >= config.quietAfterInstructionMs &&
            nowMs - lastTooFastMs >= config.tooFastCooldownMs
        ) {
            lastTooFastMs = nowMs
            return message(ParkingPhrases.TOO_FAST, nowMs)
        }
        return null
    }

    private fun stepMessage(st: ParkingState, deg: Float?, nowMs: Long): CoachMessage? {
        if (deg == null) return null
        val stalled = updateStall(deg, nowMs)
        val startDeg = stepStartDeg ?: deg.also { stepStartDeg = it }
        val magnitude = abs(deg)
        return when (st.step.kind) {
            StepKind.FULL_LOCK -> when {
                magnitude >= config.fullLockDeg -> {
                    lockSign = sign(deg)
                    advance(st, nowMs)
                }
                magnitude >= config.turningDeg && stalled -> nudge(ParkingPhrases.NOT_ENOUGH_LOCK, nowMs)
                else -> null
            }
            StepKind.FULL_LOCK_OPPOSITE -> {
                // 앞에서 꺾은 방향을 모르면(조향 단계를 버튼으로 넘긴 경우) 어느 쪽이든 받는다
                val sameSide = lockSign != 0f && sign(deg) == lockSign
                when {
                    sameSide && magnitude >= config.turningDeg -> nudge(ParkingPhrases.WRONG_DIRECTION, nowMs)
                    magnitude >= config.fullLockDeg -> {
                        lockSign = sign(deg)
                        advance(st, nowMs)
                    }
                    magnitude >= config.turningDeg && stalled -> nudge(ParkingPhrases.NOT_ENOUGH_LOCK, nowMs)
                    else -> null
                }
            }
            StepKind.CENTER -> when {
                magnitude <= config.centerToleranceDeg -> advance(st, nowMs)
                // 풀기 시작한 뒤에 멈췄을 때만 (끝까지 꺾은 채 가만히 있으면 지시 문구로 충분)
                stalled && magnitude <= abs(startDeg) - config.turningDeg -> nudge(ParkingPhrases.NOT_CENTERED, nowMs)
                else -> null
            }
            StepKind.REFERENCE, StepKind.FINISH -> null
        }
    }

    /** 기준점 단계: 뒤로 움직인 뒤 [Config.stopHoldMs] 동안 멈춰 있으면 다음 단계. */
    private fun referenceStop(st: ParkingState, speed: Float, nowMs: Long): CoachMessage? {
        if (speed > config.movingKmh) movedInStep = true
        if (!movedInStep || speed >= config.stoppedKmh) {
            stoppedSinceMs = null
            return null
        }
        val since = stoppedSinceMs ?: nowMs.also { stoppedSinceMs = it }
        return if (nowMs - since >= config.stopHoldMs) advance(st, nowMs) else null
    }

    /** 핸들이 [Config.stallMs] 넘게 거의 안 움직였는가. 단계에 들어온 직후는 제외. */
    private fun updateStall(deg: Float, nowMs: Long): Boolean {
        val ref = steerRefDeg
        if (ref == null || abs(deg - ref) > config.stallDeltaDeg) {
            steerRefDeg = deg
            steerRefMs = nowMs
            return false
        }
        return nowMs - steerRefMs >= config.stallMs && nowMs - stepStartedMs >= config.stallMs
    }

    private fun nudge(text: String, nowMs: Long): CoachMessage? {
        if (nowMs - lastNudgeMs < config.nudgeCooldownMs) return null
        lastNudgeMs = nowMs
        return message(text, nowMs)
    }

    private fun message(text: String, nowMs: Long) =
        CoachMessage(CoachType.PARKING, text, nowMs, dedupeKey = "parking")
}

package com.jij6303.sidecoach.domain.lane

import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.domain.DrivingSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.abs

/**
 * 운전자가 알려준 현재 차로(1 = 가장 왼쪽)를 차선 변경 감지로 이어서 추적한다 (#69).
 *
 * 추적은 휴리스틱이라 오차가 쌓일 수 있다. 그래서 확실하지 않으면 "모름"(null)으로 되돌리고 다시 묻는다.
 * - 크게 꺾음(조향 [turnResetDeg] 이상): 교차로 회전으로 보고 모름. 돌고 나면 몇 차로로 들어갔는지 알 수 없다.
 * - 1차로에서 왼쪽 변경, [maxLane] 넘는 오른쪽 변경: 추적이 어긋났다고 보고 모름
 * - 입력 후 [ttlMs] 경과: 놓친 차선 변경이 있을 수 있어 모름
 * 시각은 스냅샷과 같은 시계. 입력(Gleo·화면)과 코칭 파이프라인이 동시에 부르므로 동기화한다.
 */
class CurrentLaneTracker(
    private val ttlMs: Long = 5 * 60_000L,
    private val turnResetDeg: Float = 90f,
    private val maxLane: Int = 8,
) {
    private val _lane = MutableStateFlow<Int?>(null)

    /** 지금 차로. 모르면 null. */
    val lane: StateFlow<Int?> = _lane.asStateFlow()

    private var setAtMs = 0L

    @Synchronized
    fun set(lane: Int, nowMs: Long) {
        require(lane in 1..maxLane)
        _lane.value = lane
        setAtMs = nowMs
    }

    @Synchronized
    fun clear() {
        _lane.value = null
    }

    /** 지금 차로. 입력한 지 오래됐으면 모름. */
    @Synchronized
    fun current(nowMs: Long): Int? {
        expire(nowMs)
        return _lane.value
    }

    /** 차선 변경 감지. [direction]은 조향 방향(왼쪽 +)으로 정한 기대 깜빡이 방향. */
    @Synchronized
    fun onLaneChange(direction: TurnSignalState) {
        val now = _lane.value ?: return
        val next = when (direction) {
            TurnSignalState.LEFT -> now - 1
            TurnSignalState.RIGHT -> now + 1
            TurnSignalState.OFF -> return
        }
        _lane.value = next.takeIf { it in 1..maxLane }
    }

    @Synchronized
    fun onSnapshot(s: DrivingSnapshot) {
        expire(s.timestampMs)
        val steering = s.steeringDeg ?: return
        if (abs(steering) >= turnResetDeg) _lane.value = null
    }

    private fun expire(nowMs: Long) {
        if (_lane.value != null && nowMs - setAtMs > ttlMs) _lane.value = null
    }
}

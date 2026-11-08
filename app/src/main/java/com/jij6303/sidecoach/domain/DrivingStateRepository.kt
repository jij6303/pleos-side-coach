package com.jij6303.sidecoach.domain

import com.jij6303.sidecoach.data.vehicle.VehicleDataSource
import com.jij6303.sidecoach.data.vehicle.VehicleSignal
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * 차량 신호를 받아 최신 [DrivingSnapshot]과 최근 [historyWindowMs] 동안의 시계열을 유지한다.
 * 이벤트 기반으로만 갱신하고 폴링하지 않는다. Android 의존성이 없어 JVM 테스트로 검증한다.
 */
class DrivingStateRepository(
    private val source: VehicleDataSource,
    private val historyWindowMs: Long = DEFAULT_HISTORY_WINDOW_MS,
) {
    private val _snapshot = MutableStateFlow(DrivingSnapshot())
    val snapshot: StateFlow<DrivingSnapshot> = _snapshot.asStateFlow()

    private val history = ArrayDeque<DrivingSnapshot>()
    private var job: Job? = null

    val isRunning: Boolean get() = job?.isActive == true

    /** 구독 시작. 이미 실행 중이면 아무것도 하지 않는다. */
    fun start(scope: CoroutineScope) {
        if (isRunning) return
        job = scope.launch { source.signals.collect(::onSignal) }
    }

    /** 구독 해제. 마지막 스냅샷은 유지한다. */
    fun stop() {
        job?.cancel()
        job = null
    }

    /** 최근 [historyWindowMs] 동안의 스냅샷(오래된 순). 디텍터의 시간 창 계산용. */
    fun history(): List<DrivingSnapshot> = synchronized(history) { history.toList() }

    internal fun onSignal(signal: VehicleSignal) {
        val next = _snapshot.value.apply(signal)
        _snapshot.value = next
        if (signal is VehicleSignal.Unavailable) return
        synchronized(history) {
            history.addLast(next)
            val cutoff = next.timestampMs - historyWindowMs
            while (history.isNotEmpty() && history.first().timestampMs < cutoff) history.removeFirst()
        }
    }

    companion object {
        /** 💭 10초. 깜빡이 누락 판정의 최대 시간 창(8초) + 여유. */
        const val DEFAULT_HISTORY_WINDOW_MS = 10_000L
    }
}

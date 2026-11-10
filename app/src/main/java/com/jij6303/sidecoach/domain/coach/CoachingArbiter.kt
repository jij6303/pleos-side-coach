package com.jij6303.sidecoach.domain.coach

/**
 * 여러 디텍터가 동시에 말하려 할 때 무엇을 언제 말할지 정한다 (dev-architecture 4.5).
 *
 * 시간은 인자로 받는다. [submit]으로 후보를 넣고, [next]로 지금 말할 메시지를 꺼낸다.
 * [next]는 새 스냅샷이 들어올 때마다 호출되므로, 간격 때문에 대기 중인 메시지도 다음 호출 때 나간다.
 */
class CoachingArbiter(private val config: Config = Config()) {
    /** 💭 설계 가정. */
    data class Config(
        val minGapMs: Long = 6_000,
        val minGapHighMs: Long = 3_000,
        val queueTtlMs: Long = 4_000,
        /** 교습 앱이라 숙련도 구분 없이 한 기준만 둔다 (#27). */
        val blinkerCap: Int = 5,
        val praiseCap: Int = 3,
    )

    private val queue = mutableListOf<CoachMessage>()
    private val spokenCount = mutableMapOf<CoachType, Int>()
    private var lastSpokenMs: Long? = null

    /** 말할 후보 추가. 상한에 도달한 유형이면 버린다. */
    fun submit(message: CoachMessage) {
        if (reachedCap(message.type)) return
        queue.removeAll { it.dedupeKey == message.dedupeKey } // 같은 키는 최신 것만
        queue.add(message)
    }

    /** 지금 말할 메시지. 없으면 null. 반환한 메시지는 발화한 것으로 기록한다. */
    fun next(nowMs: Long): CoachMessage? {
        queue.removeAll { nowMs - it.createdAtMs > config.queueTtlMs || reachedCap(it.type) }
        val candidate = queue
            .maxWithOrNull(compareBy<CoachMessage> { it.type.priority }.thenBy { it.createdAtMs })
            ?: return null

        val gap = if (candidate.type.high) config.minGapHighMs else config.minGapMs
        if (!gapPassed(nowMs, gap)) return null

        queue.remove(candidate)
        lastSpokenMs = nowMs
        spokenCount[candidate.type] = (spokenCount[candidate.type] ?: 0) + 1
        return candidate
    }

    fun spokenCount(type: CoachType): Int = spokenCount[type] ?: 0

    /** 새 주행 시작. */
    fun reset() {
        queue.clear()
        spokenCount.clear()
        lastSpokenMs = null
    }

    private fun gapPassed(nowMs: Long, gapMs: Long): Boolean =
        lastSpokenMs?.let { nowMs - it >= gapMs } ?: true

    private fun reachedCap(type: CoachType): Boolean {
        val cap = when (type) {
            CoachType.BLINKER_OMISSION -> config.blinkerCap
            CoachType.PRAISE -> config.praiseCap
            else -> return false
        }
        return spokenCount(type) >= cap
    }

    /** 저장용 상태. 대기열은 TTL(4초)이 짧아 저장하지 않는다. */
    data class State(val spokenCounts: Map<CoachType, Int>, val lastSpokenMs: Long?)

    fun exportState(): State = State(spokenCount.toMap(), lastSpokenMs)

    fun restore(state: State) {
        queue.clear()
        spokenCount.clear()
        spokenCount.putAll(state.spokenCounts)
        lastSpokenMs = state.lastSpokenMs
    }
}

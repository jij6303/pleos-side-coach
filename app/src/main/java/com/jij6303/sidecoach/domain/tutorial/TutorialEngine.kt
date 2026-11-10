package com.jij6303.sidecoach.domain.tutorial

import com.jij6303.sidecoach.domain.DrivingSnapshot
import com.jij6303.sidecoach.domain.coach.CoachMessage
import com.jij6303.sidecoach.domain.coach.CoachType
import com.jij6303.sidecoach.domain.coach.MessageCatalog

/** 튜토리얼 진행 상태. */
data class TutorialState(
    val missions: List<TutorialMission>,
    val completed: Set<TutorialMission> = emptySet(),
    val currentIndex: Int = 0,
) {
    val current: TutorialMission? get() = missions.getOrNull(currentIndex)
    val finished: Boolean get() = currentIndex >= missions.size
}

/**
 * 미션을 순서대로 진행한다. 시간은 인자로 받는다 (JVM 테스트).
 *
 * 조건이 거짓인 것을 한 번 본 뒤에 참이 되어야 완료로 친다. 이미 켜져 있던 상태로는 통과하지 않는다.
 * 완료하면 칭찬과 다음 지시를 한 문장으로 말한다 (음성 대기열은 최신 1개만 남기므로 따로 보내면 칭찬이 버려짐).
 */
class TutorialEngine {

    var state: TutorialState? = null
        private set

    private var armed = false

    fun start(missions: List<TutorialMission>, nowMs: Long): CoachMessage? {
        state = TutorialState(missions)
        armed = false
        val first = missions.firstOrNull() ?: return message(MessageCatalog.tutorialDone(), nowMs)
        return message(MessageCatalog.tutorialInstruction(first, afterSuccess = false), nowMs)
    }

    fun onSnapshot(s: DrivingSnapshot): CoachMessage? {
        val st = state ?: return null
        val mission = st.current ?: return null
        val met = mission.isMet(s) ?: return null
        if (!met) {
            armed = true
            return null
        }
        if (!armed) return null
        return advance(st.copy(completed = st.completed + mission), success = true, nowMs = s.timestampMs)
    }

    fun skip(nowMs: Long): CoachMessage? {
        val st = state ?: return null
        if (st.finished) return null
        return advance(st, success = false, nowMs = nowMs)
    }

    fun stop() {
        state = null
        armed = false
    }

    private fun advance(st: TutorialState, success: Boolean, nowMs: Long): CoachMessage {
        val next = st.copy(currentIndex = st.currentIndex + 1)
        state = next
        armed = false
        val nextMission = next.current
        val text = if (nextMission == null) {
            MessageCatalog.tutorialDone()
        } else {
            MessageCatalog.tutorialInstruction(nextMission, afterSuccess = success)
        }
        return message(text, nowMs)
    }

    private fun message(text: String, nowMs: Long) =
        CoachMessage(CoachType.TUTORIAL, text, nowMs, dedupeKey = "tutorial")
}

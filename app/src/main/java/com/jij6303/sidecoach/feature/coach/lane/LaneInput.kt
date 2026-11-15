package com.jij6303.sidecoach.feature.coach.lane

import com.jij6303.sidecoach.state.lane.CurrentLaneTracker
import com.jij6303.sidecoach.feature.coach.CoachMessage
import com.jij6303.sidecoach.feature.coach.CoachType
import com.jij6303.sidecoach.feature.coach.MessageCatalog
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 운전자가 알려준 차로를 기억하고 음성으로 확인한다 (#69). Gleo 명령과 대시보드 버튼이 쓴다.
 * VoiceCoach가 [messages]를 구독한다. [clock]은 스냅샷·VoiceCoach와 같은 시계.
 */
class LaneInput(private val tracker: CurrentLaneTracker, private val clock: () -> Long) {
    // replay 1: Gleo 명령으로 앱이 막 켜지면 음성 출력이 구독하기 전에 확인 음성이 나간다(#69 실측).
    // 늦게 구독해도 받게 하고, 오래된 것은 VoiceCoach TTL(4초)이 버린다.
    private val _messages = MutableSharedFlow<CoachMessage>(
        replay = 1,
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<CoachMessage> = _messages.asSharedFlow()

    /** [lane]차로로 기억한다. 입력 범위([LANES]) 밖이면 false. */
    fun set(lane: Int): Boolean {
        if (lane !in LANES) return false
        tracker.set(lane, clock())
        _messages.tryEmit(CoachMessage(CoachType.LANE, MessageCatalog.laneSet(lane), clock(), dedupeKey = "lane"))
        return true
    }

    /** 모름으로 되돌린다 (화면의 "모름" 버튼). */
    fun clear() = tracker.clear()

    companion object {
        /** 입력받는 차로. 1이 가장 왼쪽. */
        val LANES = 1..4
    }
}

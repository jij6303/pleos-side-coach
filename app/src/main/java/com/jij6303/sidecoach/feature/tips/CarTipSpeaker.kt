package com.jij6303.sidecoach.feature.tips

import com.jij6303.sidecoach.feature.coach.CoachMessage
import com.jij6303.sidecoach.feature.coach.CoachType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/** 도움말을 음성으로 보낸다. VoiceCoach가 [messages]를 구독한다. [clock]은 VoiceCoach와 같은 시계. */
class CarTipSpeaker(private val clock: () -> Long) {
    private val _messages = MutableSharedFlow<CoachMessage>(
        extraBufferCapacity = 2,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val messages: SharedFlow<CoachMessage> = _messages.asSharedFlow()

    fun speak(tip: CarTip) {
        _messages.tryEmit(CoachMessage(CoachType.TIP, tip.spoken, clock(), dedupeKey = "tip"))
    }
}

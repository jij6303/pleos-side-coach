package com.jij6303.sidecoach.app.di

import android.content.Context
import android.util.Log
import com.jij6303.sidecoach.feature.coach.CoachClips
import com.jij6303.sidecoach.feature.coach.VoiceCoach
import com.jij6303.sidecoach.feature.parking.ParkingClips
import com.jij6303.sidecoach.feature.report.ReportClips
import com.jij6303.sidecoach.feature.tips.TipClips
import com.jij6303.sidecoach.voice.AndroidTtsVoiceOutput
import com.jij6303.sidecoach.voice.AudioFocusController
import com.jij6303.sidecoach.voice.ClipVoiceOutput
import com.jij6303.sidecoach.voice.NoAudioFocus
import com.jij6303.sidecoach.voice.VoiceOutput

/**
 * voice: 모든 기능의 메시지를 소리로 낸다.
 * 녹음 클립은 각 기능이 자기 문구만큼 내놓고(`*Clips`), 여기서는 합치기만 한다. 클립이 없는 문구는 Android TTS.
 */
class VoiceModule(context: Context, clock: () -> Long, requestAudioFocus: Boolean) {

    val output: VoiceOutput = ClipVoiceOutput(
        context = context,
        clips = CoachClips.all + ReportClips.all + TipClips.all + ParkingClips.all,
        fallback = AndroidTtsVoiceOutput(context),
    )

    val coach = VoiceCoach(
        output = output,
        focus = if (requestAudioFocus) AudioFocusController(context) else NoAudioFocus,
        clock = clock,
        log = { Log.i("Voice", it) },
    )
}

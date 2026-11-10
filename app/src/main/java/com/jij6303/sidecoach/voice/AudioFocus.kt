package com.jij6303.sidecoach.voice

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.util.Log

/** 발화 동안만 오디오 포커스를 잡는다. JVM 테스트를 위해 인터페이스로 둔다. */
interface AudioFocus {
    /** 포커스 요청. 성공하면 true. 이후 포커스를 잃으면 [onLoss]가 불린다. */
    fun request(onLoss: () -> Unit): Boolean

    /** 발화가 끝났을 때 포커스를 돌려준다. 잡고 있지 않으면 아무것도 하지 않는다. */
    fun abandon()
}

/** 포커스를 잡지 않는 구현. TTS SDK가 직접 포커스를 잡는 것으로 확인되면 이걸 쓴다 (dev-architecture 6.2 ❓). */
object NoAudioFocus : AudioFocus {
    override fun request(onLoss: () -> Unit): Boolean = true
    override fun abandon() = Unit
}

/** 음성 안내용 오디오 속성. 포커스 요청과 TTS 재생에 같은 값을 쓴다 (Pleos NAVIGATION 컨텍스트). */
val VoiceAudioAttributes: AudioAttributes = AudioAttributes.Builder()
    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
    .build()

/**
 * dev-architecture 6.1: NAVIGATION 컨텍스트, GAIN_TRANSIENT_MAY_DUCK.
 * 어떤 손실(LOSS / LOSS_TRANSIENT / LOSS_TRANSIENT_CAN_DUCK)이든 즉시 멈추고 다시 재생하지 않는다.
 * 포커스 상태 변화는 가이드 필수 로그 항목(context, gain, 결과)으로 남긴다.
 */
class AudioFocusController(context: Context) : AudioFocus {

    private val audioManager = context.getSystemService(AudioManager::class.java)

    @Volatile private var current: AudioFocusRequest? = null
    @Volatile private var onLoss: (() -> Unit)? = null

    override fun request(onLoss: () -> Unit): Boolean {
        val req = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(VoiceAudioAttributes)
            .setOnAudioFocusChangeListener(::onFocusChange)
            .build()
        val result = audioManager.requestAudioFocus(req)
        val granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        Log.i(TAG, "request context=NAVIGATION gain=GAIN_TRANSIENT_MAY_DUCK result=${resultName(result)}")
        if (granted) {
            current = req
            this.onLoss = onLoss
        }
        return granted
    }

    override fun abandon() {
        val req = current ?: return
        current = null
        onLoss = null
        val result = audioManager.abandonAudioFocusRequest(req)
        Log.i(TAG, "abandon context=NAVIGATION result=${resultName(result)}")
    }

    private fun onFocusChange(change: Int) {
        Log.i(TAG, "change context=NAVIGATION focus=${changeName(change)}")
        when (change) {
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> onLoss?.invoke()
        }
    }

    private companion object {
        const val TAG = "AudioFocus"

        fun resultName(r: Int) = when (r) {
            AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> "GRANTED"
            AudioManager.AUDIOFOCUS_REQUEST_FAILED -> "FAILED"
            AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> "DELAYED"
            else -> r.toString()
        }

        fun changeName(c: Int) = when (c) {
            AudioManager.AUDIOFOCUS_GAIN -> "GAIN"
            AudioManager.AUDIOFOCUS_LOSS -> "LOSS"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> "LOSS_TRANSIENT"
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> "LOSS_TRANSIENT_CAN_DUCK"
            else -> c.toString()
        }
    }
}

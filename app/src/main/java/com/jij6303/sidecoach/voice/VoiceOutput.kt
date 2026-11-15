package com.jij6303.sidecoach.voice

/**
 * 코칭 문구를 소리로 내는 출력 장치.
 * MVP는 Android 표준 TTS([AndroidTtsVoiceOutput]), Pleos TTS SDK가 나오면 구현만 바꾼다.
 */
interface VoiceOutput {
    /** 엔진이 준비돼 지금 말할 수 있는지. */
    val isReady: Boolean

    /** [text]를 말하고 끝날 때까지 기다린다. 끝까지 말했으면 true, 중단·오류면 false. */
    suspend fun speak(text: String): Boolean

    /** 말하던 것을 즉시 멈춘다. 진행 중인 [speak]는 false로 끝난다. */
    fun stop()

    fun release()
}

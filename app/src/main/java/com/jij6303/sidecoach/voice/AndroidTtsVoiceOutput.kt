package com.jij6303.sidecoach.voice

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.suspendCancellableCoroutine
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import kotlin.coroutines.resume

/**
 * Android 표준 TextToSpeech 구현 (dev-architecture 6.2).
 * 발화 앞에 [leadSilenceMs]만큼 무음을 넣어 출력 경로가 열리는 동안 첫 음절이 잘리지 않게 한다.
 *
 * 기본 엔진 연결이 실패하면(기본 엔진 설정이 비어 있는 Pleos 에뮬레이터 실측) TTS_SERVICE를 제공하는
 * 엔진을 찾아 패키지를 직접 지정해 한 번 더 시도한다.
 */
class AndroidTtsVoiceOutput(
    context: Context,
    private val locale: Locale = Locale.KOREA,
    private val leadSilenceMs: Long = 150,
) : VoiceOutput {

    @Volatile private var ready = false
    override val isReady: Boolean get() = ready

    private val pending = ConcurrentHashMap<String, CancellableContinuation<Boolean>>()
    private val seq = AtomicLong()

    private val appContext = context.applicationContext

    private val mainHandler = Handler(Looper.getMainLooper())

    // 엔진 연결에 실패하면 onInit이 TextToSpeech 생성자 안에서 바로 불린다(필드 대입 전).
    // 그래서 onInit 처리는 항상 메인 루퍼에 한 번 미뤄서 tts 필드가 채워진 뒤에 실행한다.
    @Volatile private var tts: TextToSpeech =
        TextToSpeech(appContext) { status -> mainHandler.post { onInit(status, fallback = true) } }

    private fun onInit(status: Int, fallback: Boolean) {
        if (status != TextToSpeech.SUCCESS) {
            val engine = if (fallback) findEngine() else null
            Log.w(TAG, "init failed status=$status, retry engine=$engine")
            if (engine != null) {
                tts.shutdown()
                tts = TextToSpeech(appContext, { mainHandler.post { onInit(it, fallback = false) } }, engine)
            }
            return
        }
        val lang = tts.setLanguage(locale)
        tts.setAudioAttributes(VoiceAudioAttributes)
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String) = Unit
            override fun onDone(utteranceId: String) = finish(utteranceId, true)
            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String) = finish(utteranceId, false)
            override fun onError(utteranceId: String, errorCode: Int) {
                Log.w(TAG, "error id=$utteranceId code=$errorCode")
                finish(utteranceId, false)
            }
            override fun onStop(utteranceId: String, interrupted: Boolean) = finish(utteranceId, false)
        })
        ready = lang != TextToSpeech.LANG_MISSING_DATA && lang != TextToSpeech.LANG_NOT_SUPPORTED
        Log.i(TAG, "init engine=${tts.defaultEngine} locale=$locale lang=$lang ready=$ready")
    }

    private fun findEngine(): String? = appContext.packageManager
        .queryIntentServices(Intent(TextToSpeech.Engine.INTENT_ACTION_TTS_SERVICE), 0) // Pleos 엔진은 CATEGORY_DEFAULT가 없어 MATCH_DEFAULT_ONLY로는 안 잡힘
        .firstOrNull()?.serviceInfo?.packageName

    override suspend fun speak(text: String): Boolean {
        if (!ready) return false
        val id = "coach-${seq.incrementAndGet()}"
        return suspendCancellableCoroutine { cont ->
            pending[id] = cont
            cont.invokeOnCancellation {
                pending.remove(id)
                tts.stop()
            }
            val mode = if (leadSilenceMs > 0) {
                tts.playSilentUtterance(leadSilenceMs, TextToSpeech.QUEUE_FLUSH, "$id-silence")
                TextToSpeech.QUEUE_ADD
            } else {
                TextToSpeech.QUEUE_FLUSH
            }
            if (tts.speak(text, mode, null, id) != TextToSpeech.SUCCESS) finish(id, false)
        }
    }

    private fun finish(id: String, ok: Boolean) {
        // 무음 구간(id-silence)은 pending에 없으므로 무시된다
        pending.remove(id)?.let { if (it.isActive) it.resume(ok) }
    }

    override fun stop() {
        tts.stop()
        // stop()이 onStop 콜백을 보장하지 않으므로 남은 대기를 직접 끝낸다
        pending.keys.toList().forEach { finish(it, false) }
    }

    override fun release() {
        stop()
        tts.shutdown()
        ready = false
    }

    private companion object { const val TAG = "Voice" }
}

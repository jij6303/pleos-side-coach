package com.jij6303.sidecoach.voice

import android.content.Context
import android.media.MediaPlayer
import android.util.Log
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * 미리 만든 음성 클립(res/raw)을 재생하는 출력.
 * Pleos 에뮬레이터에는 표준 TTS 엔진이 없어서(#15 실측), 정해진 코칭 문구는 클립으로 낸다.
 * [clips]에 없는 문구는 [fallback](Android TTS)으로 넘긴다.
 *
 * 포커스를 얻은 뒤 [leadSilenceMs]만큼 쉬었다가 재생해 출력 경로가 열리는 동안 첫 음절이 잘리지 않게 한다.
 */
class ClipVoiceOutput(
    context: Context,
    /** 문구 → raw 리소스 id. */
    private val clips: Map<String, Int>,
    private val fallback: VoiceOutput,
    private val leadSilenceMs: Long = 150,
) : VoiceOutput {

    private val appContext = context.applicationContext

    @Volatile private var player: MediaPlayer? = null
    @Volatile private var finish: ((Boolean) -> Unit)? = null

    /** 클립은 항상 재생 가능. 클립 없는 문구가 fallback 미준비로 실패하면 speak가 false를 돌려준다. */
    override val isReady: Boolean = true

    override suspend fun speak(text: String): Boolean {
        val resId = clips[text] ?: return fallback.speak(text)
        if (leadSilenceMs > 0) delay(leadSilenceMs)
        return suspendCancellableCoroutine { cont ->
            val mp = MediaPlayer()
            val done: (Boolean) -> Unit = { ok ->
                if (player === mp) {
                    player = null
                    finish = null
                }
                mp.release()
                if (cont.isActive) cont.resume(ok)
            }
            try {
                mp.setAudioAttributes(VoiceAudioAttributes)
                appContext.resources.openRawResourceFd(resId).use {
                    mp.setDataSource(it.fileDescriptor, it.startOffset, it.length)
                }
                mp.setOnCompletionListener { done(true) }
                mp.setOnErrorListener { _, what, extra ->
                    Log.w(TAG, "clip error what=$what extra=$extra")
                    done(false)
                    true
                }
                mp.prepare()
                player = mp
                finish = done
                mp.start()
            } catch (e: Exception) {
                Log.w(TAG, "clip play failed", e)
                done(false)
            }
            cont.invokeOnCancellation { stop() }
        }
    }

    override fun stop() {
        player?.runCatching { stop() }
        finish?.invoke(false)
        fallback.stop()
    }

    override fun release() {
        stop()
        fallback.release()
    }

    private companion object { const val TAG = "Voice" }
}

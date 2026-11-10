package com.jij6303.sidecoach.gleo

import android.content.Context
import android.content.Intent
import android.util.Log

/**
 * 응답 브로드캐스트 (dev-architecture 7.3).
 * 수신 패키지(setPackage) 지정 여부는 문서에 없어 지정하지 않는다 (확인 필요).
 */
object GleoCallbackSender {
    private const val TAG = "Gleo"

    fun send(context: Context, response: GleoResponse) {
        runCatching {
            val json = response.toJson()
            context.sendBroadcast(
                Intent(GleoContract.ACTION_RESPONSE)
                    .putExtra(GleoContract.EXTRA_PACKAGE, context.packageName)
                    .putExtra(GleoContract.EXTRA_RESPONSE, json),
            )
            Log.i(TAG, "response $json")
        }.onFailure { Log.w(TAG, "response failed", it) }
    }
}

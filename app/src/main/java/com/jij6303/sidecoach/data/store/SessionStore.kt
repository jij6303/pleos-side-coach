package com.jij6303.sidecoach.data.store

import android.content.Context
import android.util.Log
import com.jij6303.sidecoach.domain.session.SessionState

/**
 * 주행 세션 저장소 (dev-architecture 9장 SessionStore, #31).
 * 설계는 DataStore였지만 값 하나(JSON 문자열)만 저장하므로 SharedPreferences로 충분해 의존성을 늘리지 않았다.
 * apply()는 비동기 쓰기라 5초마다 저장해도 주행 처리를 막지 않는다.
 */
class SessionStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun save(state: SessionState) {
        prefs.edit().putString(KEY, SessionStateCodec.encode(state)).apply()
    }

    fun load(): SessionState? = SessionStateCodec.decode(prefs.getString(KEY, null)).also {
        Log.i(TAG, if (it == null) "load: none" else "load: savedAt=${it.savedAtMs} trip=${it.trip != null} stats=${it.recorder.stats}")
    }

    fun clear() {
        prefs.edit().remove(KEY).apply()
    }

    private companion object {
        const val TAG = "SessionStore"
        const val PREFS = "session"
        const val KEY = "state"
    }
}

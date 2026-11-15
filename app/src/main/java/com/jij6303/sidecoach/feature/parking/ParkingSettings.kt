package com.jij6303.sidecoach.feature.parking

import android.content.Context

/** 주차 코치 유형 설정 (#37). 값 하나라 SharedPreferences에 둔다. 저장된 값이 없으면 후진 주차. */
class ParkingSettings(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): ParkingType? {
        val name = prefs.getString(KEY, null) ?: return ParkingType.REVERSE
        return ParkingType.entries.firstOrNull { it.name == name }
    }

    fun save(type: ParkingType?) {
        prefs.edit().putString(KEY, type?.name ?: OFF).apply()
    }

    private companion object {
        const val PREFS = "parking"
        const val KEY = "type"
        const val OFF = "OFF"
    }
}

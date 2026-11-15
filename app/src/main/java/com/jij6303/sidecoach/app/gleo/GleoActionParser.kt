package com.jij6303.sidecoach.app.gleo

import org.json.JSONObject

/** ACTION_PARAM JSON 문자열 → 문자열 맵. 비었거나 잘못된 JSON이면 빈 맵. */
object GleoActionParser {
    fun parse(json: String?): Map<String, String> {
        if (json.isNullOrBlank()) return emptyMap()
        return runCatching {
            val obj = JSONObject(json)
            obj.keys().asSequence().associateWith { obj.get(it).toString() }
        }.getOrDefault(emptyMap())
    }
}

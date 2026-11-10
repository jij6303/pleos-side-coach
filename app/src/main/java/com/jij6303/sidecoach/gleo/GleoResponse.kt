package com.jij6303.sidecoach.gleo

import org.json.JSONObject

/**
 * Gleo로 보내는 응답 (dev-architecture 7.3).
 * [message]는 영어 완전한 문장, [data]는 JSON 배열 문자열(권장).
 */
data class GleoResponse(
    val functionName: String,
    val status: Status,
    val message: String,
    val mode: Mode = Mode.VOICE,
    val data: String? = null,
) {
    enum class Status(val wire: String) { SUCCESS("success"), ASKING("asking"), FAIL("fail") }
    enum class Mode(val wire: String) { VOICE("voice"), BEEP("beep"), SILENT("silent") }

    fun toJson(): String = JSONObject().apply {
        put("functionName", functionName)
        put("status", status.wire)
        put("message", message)
        put("mode", mode.wire)
        data?.let { put("data", it) }
    }.toString()
}

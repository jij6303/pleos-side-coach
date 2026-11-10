package com.jij6303.sidecoach.gleo

import com.jij6303.sidecoach.domain.coach.ExperienceLevel
import com.jij6303.sidecoach.ui.UiMode
import kotlinx.coroutines.test.runTest
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GleoTest {

    // ── 파싱 ──

    @Test
    fun `ACTION_PARAM JSON을 맵으로 읽는다`() {
        assertEquals(mapOf("experienceLevel" to "INTERMEDIATE"), GleoActionParser.parse("""{"experienceLevel":"INTERMEDIATE"}"""))
    }

    @Test
    fun `비었거나 잘못된 JSON이면 빈 맵`() {
        assertEquals(emptyMap<String, String>(), GleoActionParser.parse(null))
        assertEquals(emptyMap<String, String>(), GleoActionParser.parse(""))
        assertEquals(emptyMap<String, String>(), GleoActionParser.parse("{not json"))
    }

    // ── 응답 JSON ──

    @Test
    fun `응답 JSON 필드는 가이드 명세와 같다`() {
        val json = JSONObject(
            GleoResponse("START_DRIVING_COACH", GleoResponse.Status.SUCCESS, "Started.", data = """[{"a":"b"}]""").toJson(),
        )
        assertEquals("START_DRIVING_COACH", json.getString("functionName"))
        assertEquals("success", json.getString("status"))
        assertEquals("Started.", json.getString("message"))
        assertEquals("voice", json.getString("mode"))
        assertEquals("b", JSONArray(json.getString("data")).getJSONObject(0).getString("a"))
    }

    @Test
    fun `data가 없으면 넣지 않는다`() {
        assertFalse(JSONObject(GleoResponse("X", GleoResponse.Status.FAIL, "No.").toJson()).has("data"))
    }

    // ── 명령 처리 ──

    private var startedLevel: ExperienceLevel? = null
    private var tutorialStarted = false
    private var mode: UiMode? = UiMode.PARKED_FULL

    private val handler = GleoActionHandler(
        startCoaching = { startedLevel = it },
        currentMode = { mode },
        startTutorial = { tutorialStarted = true },
    )

    @Test
    fun `T1 코칭 시작은 숙련도를 적용하고 success`() = runTest {
        val r = handler.handle("START_DRIVING_COACH", mapOf("experienceLevel" to "INTERMEDIATE"))!!
        assertEquals(ExperienceLevel.INTERMEDIATE, startedLevel)
        assertEquals(GleoResponse.Status.SUCCESS, r.status)
        assertEquals("START_DRIVING_COACH", r.functionName)
    }

    @Test
    fun `숙련도가 없거나 모르는 값이면 초급`() = runTest {
        handler.handle("START_DRIVING_COACH", emptyMap())
        assertEquals(ExperienceLevel.BEGINNER, startedLevel)
        handler.handle("START_DRIVING_COACH", mapOf("experienceLevel" to "EXPERT"))
        assertEquals(ExperienceLevel.BEGINNER, startedLevel)
    }

    @Test
    fun `P단이면 튜토리얼을 열고 success`() = runTest {
        val r = handler.handle("RUN_VEHICLE_TUTORIAL", emptyMap())!!
        assertTrue(tutorialStarted)
        assertEquals(GleoResponse.Status.SUCCESS, r.status)
    }

    @Test
    fun `주행 중이거나 상태를 모르면 튜토리얼은 fail`() = runTest {
        mode = UiMode.DRIVING_VOICE
        assertEquals(GleoResponse.Status.FAIL, handler.handle("RUN_VEHICLE_TUTORIAL", emptyMap())!!.status)
        mode = null
        assertEquals(GleoResponse.Status.FAIL, handler.handle("RUN_VEHICLE_TUTORIAL", emptyMap())!!.status)
        assertFalse(tutorialStarted)
    }

    @Test
    fun `모르는 액션은 처리하지 않는다`() = runTest {
        assertNull(handler.handle("EXPLAIN_CURRENT_TRAFFIC_RULE", emptyMap()))
        assertNull(handler.handle(null, emptyMap()))
    }
}

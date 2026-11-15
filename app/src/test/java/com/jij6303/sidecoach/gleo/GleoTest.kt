package com.jij6303.sidecoach.gleo

import com.jij6303.sidecoach.domain.tips.CarTip
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
        assertEquals(mapOf("key" to "VALUE"), GleoActionParser.parse("""{"key":"VALUE"}"""))
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

    private var coachingStarted = false
    private var mode: UiMode? = UiMode.PARKED_FULL

    private var spokenTip: CarTip? = null
    private var openedTip: CarTip? = null

    private val handler = GleoActionHandler(
        startCoaching = { coachingStarted = true },
        currentMode = { mode },
        speakTip = { spokenTip = it },
        openTip = { openedTip = it },
        setLane = { lane -> (lane in 1..4).also { if (it) setLane = lane } },
    )

    private var setLane: Int? = null

    @Test
    fun `T1 코칭 시작은 서비스를 시작하고 success`() = runTest {
        val r = handler.handle("START_DRIVING_COACH", emptyMap())!!
        assertTrue(coachingStarted)
        assertEquals(GleoResponse.Status.SUCCESS, r.status)
        assertEquals("START_DRIVING_COACH", r.functionName)
    }

    @Test
    fun `삭제한 튜토리얼 액션은 처리하지 않는다`() = runTest {
        assertNull(handler.handle("RUN_VEHICLE_TUTORIAL", emptyMap()))
    }

    @Test
    fun `모르는 액션은 처리하지 않는다`() = runTest {
        assertNull(handler.handle("EXPLAIN_CURRENT_TRAFFIC_RULE", emptyMap()))
        assertNull(handler.handle(null, emptyMap()))
    }

    @Test
    fun `도움말은 앱이 직접 말하고 Gleo는 읽지 않게 silent로 응답`() = runTest {
        val r = handler.handle("ASK_CAR_TIP", mapOf("topic" to "WINDSHIELD_FOG"))!!
        assertEquals(CarTip.WINDSHIELD_FOG, spokenTip)
        assertEquals(GleoResponse.Status.SUCCESS, r.status)
        assertEquals(GleoResponse.Mode.SILENT, r.mode)
    }

    @Test
    fun `정차 중이면 도움말 화면도 열고, 주행 중이면 음성만`() = runTest {
        handler.handle("ASK_CAR_TIP", mapOf("topic" to "RECIRCULATION"))
        assertEquals(CarTip.RECIRCULATION, openedTip)
        openedTip = null
        mode = UiMode.DRIVING_VOICE
        handler.handle("ASK_CAR_TIP", mapOf("topic" to "FRESH_AIR"))
        assertEquals(CarTip.FRESH_AIR, spokenTip)
        assertNull(openedTip)
    }

    @Test
    fun `모르는 주제면 fail`() = runTest {
        assertEquals(GleoResponse.Status.FAIL, handler.handle("ASK_CAR_TIP", mapOf("topic" to "WIPER"))!!.status)
        assertEquals(GleoResponse.Status.FAIL, handler.handle("ASK_CAR_TIP", emptyMap())!!.status)
        assertNull(spokenTip)
    }

    @Test
    fun `차로 입력은 앱이 기억·확인하고 silent로 응답`() = runTest {
        val r = handler.handle("SET_CURRENT_LANE", mapOf("laneNumber" to "2"))!!
        assertEquals(2, setLane)
        assertEquals(GleoResponse.Status.SUCCESS, r.status)
        assertEquals(GleoResponse.Mode.SILENT, r.mode)
        assertEquals(2, JSONArray(r.data).getJSONObject(0).getInt("laneNumber"))
        // 숫자가 소수 형태로 와도 받는다
        handler.handle("SET_CURRENT_LANE", mapOf("laneNumber" to "3.0"))
        assertEquals(3, setLane)
    }

    @Test
    fun `차로 번호가 없거나 범위 밖이면 fail`() = runTest {
        listOf(emptyMap(), mapOf("laneNumber" to "5"), mapOf("laneNumber" to "two"), mapOf("laneNumber" to "1.5")).forEach {
            assertEquals(it.toString(), GleoResponse.Status.FAIL, handler.handle("SET_CURRENT_LANE", it)!!.status)
        }
        assertNull(setLane)
    }
}

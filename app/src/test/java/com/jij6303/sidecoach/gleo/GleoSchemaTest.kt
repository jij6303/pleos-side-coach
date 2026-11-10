package com.jij6303.sidecoach.gleo

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** assets/schema.json이 가이드 규칙과 코드에 맞는지 확인 (pleos-dev-guide-summary 3장). */
class GleoSchemaTest {

    // 단위 테스트의 작업 디렉터리는 app 모듈
    private val schema = JSONObject(File("src/main/assets/schema.json").readText())
    private val functions = schema.getJSONArray("actionCalls").let { arr ->
        (0 until arr.length()).map { arr.getJSONObject(it).getJSONObject("function") }
    }

    @Test
    fun `패키지명이 앱과 같다`() {
        assertEquals("com.jij6303.sidecoach", schema.getString("packageName"))
    }

    @Test
    fun `액션은 3개 이하, 이름은 대문자와 밑줄`() {
        assertTrue(functions.size in 1..3)
        functions.forEach { assertTrue(it.getString("name").matches(Regex("[A-Z_]+"))) }
    }

    @Test
    fun `선언한 액션은 모두 코드에서 처리한다`() {
        assertEquals(
            setOf(GleoContract.START_DRIVING_COACH, GleoContract.RUN_VEHICLE_TUTORIAL),
            functions.map { it.getString("name") }.toSet(),
        )
        functions.forEach { assertEquals("activity", it.getString("component")) }
    }

    @Test
    fun `required에는 선언한 파라미터만`() {
        functions.forEach { f ->
            val params = f.getJSONObject("parameters")
            val props = params.getJSONObject("properties").keys().asSequence().toSet()
            val required = params.getJSONArray("required").let { a -> (0 until a.length()).map { a.getString(it) } }
            assertTrue(props.containsAll(required))
        }
    }
}

package com.jij6303.sidecoach.domain.parking

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.GearPosition.DRIVE
import com.jij6303.sidecoach.data.vehicle.GearPosition.NEUTRAL
import com.jij6303.sidecoach.data.vehicle.GearPosition.PARK
import com.jij6303.sidecoach.data.vehicle.GearPosition.REVERSE
import com.jij6303.sidecoach.domain.DrivingSnapshot
import com.jij6303.sidecoach.domain.coach.CoachType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ParkingCoachEngineTest {

    private val engine = ParkingCoachEngine()
    private var now = 0L

    private fun snap(gear: GearPosition, steering: Float? = 0f, speed: Float = 0f, brake: Boolean = false, dt: Long = 100) =
        engine.onSnapshot(
            DrivingSnapshot(timestampMs = now.also { now += dt }, gear = gear, steeringDeg = steering, speedKmh = speed, brakePressed = brake),
        )

    private fun tick(dt: Long) = engine.tick((now + dt).also { now = it })

    /** D에서 R로 바꿔 시작한다. */
    private fun startFromDrive(type: ParkingType = ParkingType.REVERSE): String? {
        engine.type = type
        snap(DRIVE)
        return snap(REVERSE)?.text
    }

    private fun stepKind() = engine.state?.step?.kind

    // ── 시작 조건 ──

    @Test
    fun `D에서 R로 바꾸면 고른 유형으로 시작한다`() {
        snap(DRIVE)
        val m = snap(REVERSE)!!
        assertEquals(ParkingPhrases.REVERSE_START, m.text)
        assertEquals(CoachType.PARKING, m.type)
        assertEquals(StepKind.REFERENCE, stepKind())
    }

    @Test
    fun `N에서 R도 시작한다`() {
        snap(NEUTRAL)
        assertEquals(ParkingPhrases.REVERSE_START, snap(REVERSE)?.text)
    }

    @Test
    fun `P에서 바로 R은 주차칸에서 빼는 경우라 시작하지 않는다`() {
        snap(PARK)
        assertNull(snap(REVERSE))
        assertNull(engine.state)
    }

    @Test
    fun `앱이 R 상태에서 처음 값을 받으면 시작하지 않는다`() {
        assertNull(snap(REVERSE))
    }

    @Test
    fun `끄면 시작하지 않는다`() {
        engine.type = null
        snap(DRIVE)
        assertNull(snap(REVERSE))
    }

    @Test
    fun `평행 주차는 평행 문구로 시작한다`() {
        assertEquals(ParkingPhrases.PARALLEL_START, startFromDrive(ParkingType.PARALLEL))
    }

    // ── 후진 주차 전체 흐름 ──

    @Test
    fun `후진 주차 - 버튼, 끝까지, 버튼, 가운데, P로 완료`() {
        startFromDrive()
        assertTrue(engine.canTapNext)
        assertEquals(ParkingPhrases.FULL_LOCK, engine.tapNext(now)?.text)

        // 핸들 단계는 버튼으로 넘기지 않는다
        assertFalse(engine.canTapNext)
        assertNull(engine.tapNext(now))

        assertNull(snap(REVERSE, steering = -200f))
        assertEquals(ParkingPhrases.REVERSE_UNTIL_ALIGNED, snap(REVERSE, steering = -450f)?.text)
        assertEquals(StepKind.REFERENCE, stepKind())

        assertEquals(ParkingPhrases.CENTER, engine.tapNext(now)?.text)
        assertNull(snap(REVERSE, steering = -100f))
        assertEquals(ParkingPhrases.REVERSE_FINISH, snap(REVERSE, steering = -10f)?.text)
        assertEquals(StepKind.FINISH, stepKind())
        assertFalse(engine.canTapNext)

        assertEquals(ParkingPhrases.DONE, snap(PARK)?.text)
        assertNull(engine.state)
    }

    // ── 기준점: 움직인 뒤 멈추면 다음 ──

    @Test
    fun `뒤로 가다가 브레이크로 멈춰 있으면 다음 단계로 넘어간다`() {
        startFromDrive()
        snap(REVERSE, speed = 4f)
        assertNull(snap(REVERSE, speed = 0f, brake = true))
        assertNull(tick(500))
        assertEquals(ParkingPhrases.FULL_LOCK, tick(400)?.text)
        assertEquals(StepKind.FULL_LOCK, stepKind())
    }

    @Test
    fun `멈춘 뒤 브레이크를 떼도(콘솔 BRAKE 버튼) 정지가 이어지면 넘어간다`() {
        startFromDrive()
        snap(REVERSE, speed = 4f)
        snap(REVERSE, speed = 0f, brake = true)
        assertNull(snap(REVERSE, speed = 0f, brake = false))
        assertEquals(ParkingPhrases.FULL_LOCK, tick(1_000)?.text)
    }

    @Test
    fun `속도 조절로 살짝 밟는 것(아직 움직임)은 넘어가지 않는다`() {
        startFromDrive()
        snap(REVERSE, speed = 4f)
        assertNull(snap(REVERSE, speed = 3f, brake = true))
        assertNull(tick(2_000))
        assertEquals(StepKind.REFERENCE, stepKind())
    }

    @Test
    fun `움직이지 않고 서 있기만 하면 넘어가지 않는다`() {
        startFromDrive()
        assertNull(snap(REVERSE, speed = 0f, brake = true))
        assertNull(tick(3_000))
        assertEquals(StepKind.REFERENCE, stepKind())
    }

    @Test
    fun `멈췄다가 0_8초 전에 다시 움직이면 처음부터 기다린다`() {
        startFromDrive()
        snap(REVERSE, speed = 4f)
        snap(REVERSE, speed = 0f, dt = 500)
        assertNull(snap(REVERSE, speed = 2f, dt = 500))
        assertNull(snap(REVERSE, speed = 0f))
        assertNull(tick(500))
        assertEquals(StepKind.REFERENCE, stepKind())
    }

    @Test
    fun `후진 주차를 버튼 없이 끝까지 - 멈춤과 핸들만으로`() {
        startFromDrive()
        snap(REVERSE, speed = 3f)
        snap(REVERSE, speed = 0f, brake = true, dt = 1_000)
        assertEquals(ParkingPhrases.FULL_LOCK, snap(REVERSE, speed = 0f, brake = true)?.text)
        assertEquals(ParkingPhrases.REVERSE_UNTIL_ALIGNED, snap(REVERSE, steering = -450f, brake = true)?.text)
        // 앞 단계에서 밟고 있던 브레이크로 바로 넘어가지 않는다
        assertNull(tick(2_000))
        snap(REVERSE, steering = -450f, speed = 3f)
        snap(REVERSE, steering = -450f, speed = 0f, brake = true, dt = 1_000)
        assertEquals(ParkingPhrases.CENTER, snap(REVERSE, steering = -450f, brake = true)?.text)
        assertEquals(ParkingPhrases.REVERSE_FINISH, snap(REVERSE, steering = 0f, brake = true)?.text)
        assertEquals(ParkingPhrases.DONE, snap(PARK)?.text)
    }

    // ── 덜 돌림·덜 풀림 ──

    @Test
    fun `끝까지 안 돌리고 멈춰 있으면 덜 돌렸다고 한 번 알려준다`() {
        startFromDrive()
        engine.tapNext(now)
        snap(REVERSE, steering = 250f)
        assertNull(tick(1_000))
        assertEquals(ParkingPhrases.NOT_ENOUGH_LOCK, tick(2_000)?.text)
        // 재안내 간격 안에는 다시 말하지 않는다
        assertNull(tick(3_000))
        assertEquals(ParkingPhrases.NOT_ENOUGH_LOCK, tick(3_100)?.text)
    }

    @Test
    fun `아직 돌리기 전(가운데)이면 덜 돌렸다고 하지 않는다`() {
        startFromDrive()
        engine.tapNext(now)
        snap(REVERSE, steering = 0f)
        assertNull(tick(5_000))
    }

    @Test
    fun `계속 돌리는 중이면 멈춤으로 보지 않는다`() {
        startFromDrive()
        engine.tapNext(now)
        var deg = 100f
        repeat(10) {
            deg += 30f
            assertNull(snap(REVERSE, steering = deg, dt = 800))
        }
    }

    @Test
    fun `풀다가 멈추면 덜 풀렸다고 알려준다`() {
        toCenterStep()
        snap(REVERSE, steering = 450f)
        // 끝까지 꺾은 채 가만히 있으면 지시 문구로 충분
        assertNull(tick(3_000))
        snap(REVERSE, steering = 150f)
        assertNull(tick(1_000))
        assertEquals(ParkingPhrases.NOT_CENTERED, tick(2_000)?.text)
    }

    private fun toCenterStep() {
        startFromDrive()
        engine.tapNext(now)
        snap(REVERSE, steering = 450f)
        engine.tapNext(now)
        assertEquals(StepKind.CENTER, stepKind())
    }

    // ── 평행 주차: 반대 방향 ──

    @Test
    fun `평행 주차 반대쪽 단계에서 같은 방향으로 돌리면 반대쪽이라고 알려준다`() {
        startFromDrive(ParkingType.PARALLEL)
        engine.tapNext(now) // → 끝까지
        snap(REVERSE, steering = -450f) // 오른쪽 끝 → 45도까지 후진
        engine.tapNext(now) // → 가운데
        snap(REVERSE, steering = 0f) // → 앞차 지날 때까지
        assertEquals(ParkingPhrases.FULL_LOCK_OPPOSITE, engine.tapNext(now)?.text)

        assertEquals(ParkingPhrases.WRONG_DIRECTION, snap(REVERSE, steering = -120f)?.text)
        assertEquals(StepKind.FULL_LOCK_OPPOSITE, stepKind())
        // 이번엔 맞는 방향(왼쪽 = +)
        assertEquals(ParkingPhrases.PARALLEL_UNTIL_ALIGNED, snap(REVERSE, steering = 480f, dt = 7_000)?.text)
        assertEquals(ParkingPhrases.CENTER, engine.tapNext(now)?.text)
        assertEquals(ParkingPhrases.PARALLEL_FINISH, snap(REVERSE, steering = 5f)?.text)
        assertEquals(ParkingPhrases.DONE, snap(PARK)?.text)
    }

    // ── 속도·종료 ──

    @Test
    fun `후진이 빠르면 천천히 하라고 하고 간격을 둔다`() {
        startFromDrive()
        // 단계 안내 직후에는 안내가 밀리지 않도록 말하지 않는다
        assertNull(snap(REVERSE, speed = 10f, dt = 6_000))
        assertEquals(ParkingPhrases.TOO_FAST, snap(REVERSE, speed = 10f)?.text)
        assertNull(snap(REVERSE, speed = 10f, dt = 8_000))
        // 차속이 음수로 와도 크기로 판단
        assertEquals(ParkingPhrases.TOO_FAST, snap(REVERSE, speed = -10f)?.text)
    }

    @Test
    fun `중간에 P로 바꾸면 중단 문구`() {
        startFromDrive()
        assertEquals(ParkingPhrases.CANCELLED, snap(PARK)?.text)
        assertNull(engine.state)
    }

    @Test
    fun `D로 바꾸면 중단하고 다음 R에서 처음부터 다시`() {
        startFromDrive()
        engine.tapNext(now)
        assertEquals(ParkingPhrases.RETRY, snap(DRIVE)?.text)
        assertNull(engine.state)
        assertEquals(ParkingPhrases.REVERSE_START, snap(REVERSE)?.text)
        assertEquals(0, engine.state?.stepIndex)
    }

    @Test
    fun `조향 신호가 없으면 핸들 단계도 버튼으로 넘긴다`() {
        startFromDrive()
        engine.tapNext(now)
        snap(REVERSE, steering = null)
        assertTrue(engine.canTapNext)
        assertEquals(ParkingPhrases.REVERSE_UNTIL_ALIGNED, engine.tapNext(now)?.text)
    }

    @Test
    fun `유형을 바꿔도 진행 중인 코치는 그대로`() {
        startFromDrive(ParkingType.REVERSE)
        engine.type = ParkingType.PARALLEL
        assertEquals(ParkingType.REVERSE, engine.state?.type)
    }

    // ── 녹음 클립 ──

    @Test
    fun `모든 단계 문구에 녹음 클립이 있다`() {
        val phrases = ParkingPhrases.clips.values.toSet()
        ParkingType.entries.flatMap { it.steps }.forEach { assertTrue(it.instruction, it.instruction in phrases) }
        ParkingPhrases.clips.keys.forEach { name ->
            // 개발용 TTS wav 또는 직접 녹음한 mp3·ogg (#39)
            val found = listOf("wav", "mp3", "ogg").any { File("src/main/res/raw/$name.$it").exists() }
            assertTrue("res/raw/$name 클립 없음", found)
        }
    }
}

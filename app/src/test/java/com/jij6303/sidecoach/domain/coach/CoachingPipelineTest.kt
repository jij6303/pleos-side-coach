package com.jij6303.sidecoach.domain.coach

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.domain.DrivingSnapshot
import com.jij6303.sidecoach.domain.detect.BlinkerOmissionDetector
import com.jij6303.sidecoach.domain.detect.HardBrakeDetector
import com.jij6303.sidecoach.domain.trip.TripRecorder
import kotlinx.coroutines.flow.emptyFlow
import com.jij6303.sidecoach.data.navi.NaviEvent
import com.jij6303.sidecoach.domain.report.Grade
import com.jij6303.sidecoach.domain.situation.SituationType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class CoachingPipelineTest {

    private val pipeline = CoachingPipeline(
        updates = emptyFlow(),
        detectors = listOf(BlinkerOmissionDetector(), HardBrakeDetector()),
        arbiter = CoachingArbiter(),
        recorder = TripRecorder(),
    )

    private fun laneChange(t0: Long, signal: TurnSignalState): List<CoachMessage> {
        val sig = signal
        val steps = listOf(
            t0 to 0f, t0 + 1_000 to -20f, t0 + 2_500 to 20f, t0 + 4_000 to 0f,
        )
        return steps.mapNotNull { (ts, deg) ->
            pipeline.process(
                DrivingSnapshot(
                    timestampMs = ts, speedKmh = 50f, gear = GearPosition.DRIVE,
                    steeringDeg = deg, turnSignal = if (ts == t0 + 4_000) TurnSignalState.OFF else sig,
                ),
            )
        }
    }

    @Test
    fun `T2 깜빡이 누락이면 경고 메시지를 낸다`() {
        val out = laneChange(0, TurnSignalState.OFF)
        assertEquals(listOf(CoachType.BLINKER_OMISSION), out.map { it.type })
        assertEquals(MessageCatalog.blinkerOmission(), pipeline.lastMessage.value?.text)
        assertEquals(1, pipeline.recorder.stats.value.laneChanges)
        assertEquals(0f, pipeline.recorder.stats.value.blinkerUsageRate!!, 0f)
    }

    @Test
    fun `T3 깜빡이 켠 차선 변경 3회 연속이면 칭찬`() {
        val out = (0 until 3).flatMap { laneChange(it * 20_000L, TurnSignalState.RIGHT) }
        assertEquals(listOf(CoachType.PRAISE), out.map { it.type })
        assertEquals(3, pipeline.recorder.stats.value.signalledLaneChanges)
        assertEquals(1f, pipeline.recorder.stats.value.blinkerUsageRate!!, 0f)
    }

    @Test
    fun `T6 급제동이면 경고하고 횟수를 센다`() {
        pipeline.process(DrivingSnapshot(timestampMs = 0, speedKmh = 60f, brakePressed = true))
        val m = pipeline.process(DrivingSnapshot(timestampMs = 1_000, speedKmh = 30f, brakePressed = true))
        assertEquals(CoachType.HARD_BRAKE, m?.type)
        assertEquals(1, pipeline.recorder.stats.value.hardBrakes)
    }

    @Test
    fun `T9 P단에 들어가면 이번 주행 성적표를 만들고, 새 주행이 시작되면 초기화`() {
        pipeline.process(DrivingSnapshot(timestampMs = 0, speedKmh = 0f, gear = GearPosition.PARK))
        pipeline.process(DrivingSnapshot(timestampMs = 1_000, speedKmh = 60f, gear = GearPosition.DRIVE, brakePressed = true))
        pipeline.process(DrivingSnapshot(timestampMs = 11_000, speedKmh = 60f, gear = GearPosition.DRIVE, brakePressed = true))
        pipeline.process(DrivingSnapshot(timestampMs = 12_000, speedKmh = 30f, gear = GearPosition.DRIVE, brakePressed = true))
        pipeline.process(DrivingSnapshot(timestampMs = 14_000, speedKmh = 0f, gear = GearPosition.DRIVE))
        assertNull(pipeline.lastReport.value)

        pipeline.process(DrivingSnapshot(timestampMs = 15_000, speedKmh = 0f, gear = GearPosition.PARK))
        val report = pipeline.lastReport.value
        assertNotNull(report)
        assertEquals(1, report!!.stats.hardBrakes)
        assertEquals(90, report.score)
        assertEquals(Grade.GREAT, report.grade)
        assertEquals(60f, report.metrics.maxSpeedKmh, 0f)

        // D로 바꾸기만 하면 아직 새 주행이 아니다 → 성적표·통계 유지 (#51)
        pipeline.process(DrivingSnapshot(timestampMs = 100_000, speedKmh = 0f, gear = GearPosition.DRIVE))
        assertNotNull(pipeline.lastReport.value)
        assertEquals(1, pipeline.recorder.stats.value.hardBrakes)

        // 60초가 지나 실제로 출발: 새 주행 → 성적표·통계 초기화
        pipeline.process(DrivingSnapshot(timestampMs = 101_000, speedKmh = 10f, gear = GearPosition.DRIVE))
        assertNull(pipeline.lastReport.value)
        assertEquals(0, pipeline.recorder.stats.value.hardBrakes)
    }

    @Test
    fun `P에 넣고 60초 안에 다시 출발하면 통계를 이어간다`() {
        pipeline.process(DrivingSnapshot(timestampMs = 0, speedKmh = 60f, gear = GearPosition.DRIVE, brakePressed = true))
        pipeline.process(DrivingSnapshot(timestampMs = 10_000, speedKmh = 60f, gear = GearPosition.DRIVE, brakePressed = true))
        pipeline.process(DrivingSnapshot(timestampMs = 11_000, speedKmh = 30f, gear = GearPosition.DRIVE, brakePressed = true))
        pipeline.process(DrivingSnapshot(timestampMs = 13_000, speedKmh = 0f, gear = GearPosition.DRIVE))
        pipeline.process(DrivingSnapshot(timestampMs = 14_000, speedKmh = 0f, gear = GearPosition.PARK))
        assertNotNull(pipeline.lastReport.value)

        pipeline.process(DrivingSnapshot(timestampMs = 40_000, speedKmh = 0f, gear = GearPosition.DRIVE))
        pipeline.process(DrivingSnapshot(timestampMs = 41_000, speedKmh = 10f, gear = GearPosition.DRIVE))
        assertNull(pipeline.lastReport.value) // 이어가면 성적표는 닫힌다
        assertEquals(1, pipeline.recorder.stats.value.hardBrakes)

        pipeline.process(DrivingSnapshot(timestampMs = 50_000, speedKmh = 0f, gear = GearPosition.DRIVE))
        pipeline.process(DrivingSnapshot(timestampMs = 51_000, speedKmh = 0f, gear = GearPosition.PARK))
        val report = pipeline.lastReport.value!!
        assertEquals(1, report.stats.hardBrakes)
        assertEquals(0L, report.metrics.startedAtMs) // 처음 출발 시각 그대로
    }

    @Test
    fun `지금 차로를 알면 차선 변경으로 따라가 단정해서 안내한다`() {
        pipeline.lane.set(1, 0)
        laneChange(0, TurnSignalState.RIGHT) // 조향이 먼저 오른쪽(−) → 1차로에서 2차로
        assertEquals(2, pipeline.lane.current(5_000))
        val m = pipeline.processNavi(NaviEvent.Situation("t", SituationType.LANE_LEFT_TURN, 450f, 10_000))
        assertEquals("지금 2차로예요. 좌회전하려면 깜빡이 켜고 1차로로 옮겨요.", m?.text)
    }

    @Test
    fun `차로를 모르면 일반 안내`() {
        val m = pipeline.processNavi(NaviEvent.Situation("t", SituationType.LANE_LEFT_TURN, 450f, 0))
        assertEquals(MessageCatalog.preview(SituationType.LANE_LEFT_TURN), m?.text)
    }

    @Test
    fun `T7 내비 이벤트가 예고 구간에 들어오면 상황 예고를 말한다`() {
        assertNull(pipeline.processNavi(NaviEvent.Situation("r", SituationType.ROUNDABOUT, 350f, 0)))
        val m = pipeline.processNavi(NaviEvent.Situation("r", SituationType.ROUNDABOUT, 290f, 1_000))
        assertEquals(CoachType.PREVIEW, m?.type)
        assertEquals(m, pipeline.lastMessage.value)
        assertNull(pipeline.processNavi(NaviEvent.Situation("r", SituationType.ROUNDABOUT, 200f, 9_000)))
    }

    @Test
    fun `달리는 중 P에 넣으면 성적표와 함께 기어 경고를 바로 말한다`() {
        val p = CoachingPipeline(
            updates = emptyFlow(),
            detectors = listOf(com.jij6303.sidecoach.domain.detect.GearShiftWhileMovingDetector()),
            arbiter = CoachingArbiter(),
            recorder = TripRecorder(),
        )
        (0L..10_000L step 1_000).forEach {
            p.process(DrivingSnapshot(timestampMs = it, speedKmh = 30f, gear = GearPosition.DRIVE))
        }
        p.process(DrivingSnapshot(timestampMs = 11_000, speedKmh = 30f, gear = GearPosition.PARK))
        assertNotNull(p.lastReport.value)
        assertEquals(CoachType.GEAR_SHIFT_MOVING, p.lastMessage.value?.type)
        // 바로 말한 기어 경고를 중재자가 다음 스냅샷에서 또 말하면 안 된다 (#61 실측: 성적표 음성이 밀려남)
        assertNull(p.process(DrivingSnapshot(timestampMs = 11_500, speedKmh = 0f, gear = GearPosition.PARK)))
    }
}

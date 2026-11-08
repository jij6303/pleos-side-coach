package com.jij6303.sidecoach.domain.coach

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.domain.DrivingSnapshot
import com.jij6303.sidecoach.domain.detect.BlinkerOmissionDetector
import com.jij6303.sidecoach.domain.detect.HardBrakeDetector
import com.jij6303.sidecoach.domain.trip.TripRecorder
import kotlinx.coroutines.flow.emptyFlow
import org.junit.Assert.assertEquals
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
}

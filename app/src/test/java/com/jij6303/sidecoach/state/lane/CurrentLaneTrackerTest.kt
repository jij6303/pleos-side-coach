package com.jij6303.sidecoach.state.lane

import com.jij6303.sidecoach.feature.coach.lane.LaneInput
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.state.DrivingSnapshot
import com.jij6303.sidecoach.feature.coach.CoachType
import com.jij6303.sidecoach.feature.coach.MessageCatalog
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CurrentLaneTrackerTest {

    private val tracker = CurrentLaneTracker()

    @Test
    fun `차선 변경으로 차로를 따라간다`() {
        tracker.set(2, 0)
        tracker.onLaneChange(TurnSignalState.LEFT)
        assertEquals(1, tracker.current(1_000))
        tracker.onLaneChange(TurnSignalState.RIGHT)
        tracker.onLaneChange(TurnSignalState.RIGHT)
        assertEquals(3, tracker.current(2_000))
    }

    @Test
    fun `1차로에서 왼쪽 변경이면 추적이 어긋난 것이라 모름`() {
        tracker.set(1, 0)
        tracker.onLaneChange(TurnSignalState.LEFT)
        assertNull(tracker.current(1_000))
    }

    @Test
    fun `크게 꺾으면(교차로 회전) 모름`() {
        tracker.set(3, 0)
        tracker.onSnapshot(DrivingSnapshot(timestampMs = 1_000, steeringDeg = 30f))
        assertEquals(3, tracker.current(1_000))
        tracker.onSnapshot(DrivingSnapshot(timestampMs = 2_000, steeringDeg = -120f))
        assertNull(tracker.lane.value)
    }

    @Test
    fun `입력 후 5분이 지나면 모름`() {
        tracker.set(2, 0)
        assertEquals(2, tracker.current(5 * 60_000L))
        tracker.onSnapshot(DrivingSnapshot(timestampMs = 5 * 60_000L + 1))
        assertNull(tracker.lane.value)
    }

    @Test
    fun `모를 때 차선 변경은 무시한다`() {
        tracker.onLaneChange(TurnSignalState.RIGHT)
        assertNull(tracker.current(0))
    }

    @Test
    fun `입력하면 기억하고 음성으로 확인한다`() = runTest {
        val input = LaneInput(tracker) { 7L }
        val spoken = backgroundScope.async { input.messages.first() }
        runCurrent()
        assertTrue(input.set(2))
        val m = spoken.await()
        assertEquals(CoachType.LANE, m.type)
        assertEquals(MessageCatalog.laneSet(2), m.text)
        assertEquals(2, tracker.current(7))
    }

    @Test
    fun `1~4 밖은 받지 않는다`() {
        val input = LaneInput(tracker) { 0L }
        assertFalse(input.set(0))
        assertFalse(input.set(5))
        assertNull(tracker.current(0))
    }
}

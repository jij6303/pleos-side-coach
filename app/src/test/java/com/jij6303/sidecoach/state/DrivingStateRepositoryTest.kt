package com.jij6303.sidecoach.state

import com.jij6303.sidecoach.data.vehicle.FakeVehicleDataSource
import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.SignalKind
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.data.vehicle.VehicleSignal
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DrivingStateRepositoryTest {

    @Test
    fun `스냅샷은 신호별 값을 반영한다`() {
        val s = DrivingSnapshot()
            .apply(VehicleSignal.Gear(GearPosition.DRIVE, 1))
            .apply(VehicleSignal.Speed(10f, 2))
            .apply(VehicleSignal.SteeringAngle(-20f, 3))
            .apply(VehicleSignal.TurnSignal(TurnSignalState.RIGHT, 4))
            .apply(VehicleSignal.BrakePedal(true, 5))
            .apply(VehicleSignal.Hazard(false, 6))
            .apply(VehicleSignal.ParkingBrake(false, 7))

        assertEquals(GearPosition.DRIVE, s.gear)
        assertEquals(36f, s.speedKmh!!, 0.001f) // 10 m/s = 36 km/h
        assertEquals(-20f, s.steeringDeg!!, 0f)
        assertEquals(TurnSignalState.RIGHT, s.turnSignal)
        assertEquals(true, s.brakePressed)
        assertEquals(false, s.hazardOn)
        assertEquals(false, s.parkingBrakeOn)
        assertEquals(7L, s.timestampMs)
    }

    @Test
    fun `수신 불가 사유는 값이 다시 들어오면 지워진다`() {
        val down = DrivingSnapshot().apply(VehicleSignal.Unavailable(SignalKind.SPEED, "권한 없음", 1))
        assertEquals("권한 없음", down.unavailable[SignalKind.SPEED])
        assertNull(down.speedKmh)

        val up = down.apply(VehicleSignal.Speed(5f, 2))
        assertFalse(SignalKind.SPEED in up.unavailable)
    }

    @Test
    fun `이력은 시간 창 밖의 스냅샷을 버린다`() {
        val repo = DrivingStateRepository(FakeVehicleDataSource(), historyWindowMs = 1_000)
        repo.onSignal(VehicleSignal.Speed(1f, 0))
        repo.onSignal(VehicleSignal.Speed(2f, 500))
        repo.onSignal(VehicleSignal.Speed(3f, 1_500))

        val history = repo.history()
        assertEquals(listOf(500L, 1_500L), history.map { it.timestampMs })
    }

    @Test
    fun `수신 불가 신호는 이력에 남기지 않는다`() {
        val repo = DrivingStateRepository(FakeVehicleDataSource())
        repo.onSignal(VehicleSignal.Unavailable(SignalKind.BRAKE, "x", 0))
        assertTrue(repo.history().isEmpty())
        assertEquals("x", repo.snapshot.value.unavailable[SignalKind.BRAKE])
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `start와 stop으로 소스 구독을 제어한다`() = runTest(UnconfinedTestDispatcher()) {
        var now = 0L
        val fake = FakeVehicleDataSource(clock = { now })
        val repo = DrivingStateRepository(fake)

        repo.start(backgroundScope)
        assertTrue(repo.isRunning)
        now = 100
        fake.speedKmh(54f)
        fake.turnSignal(TurnSignalState.LEFT)
        assertEquals(54f, repo.snapshot.value.speedKmh!!, 0.01f)
        assertEquals(TurnSignalState.LEFT, repo.snapshot.value.turnSignal)

        repo.stop()
        assertFalse(repo.isRunning)
        fake.speedKmh(10f)
        assertEquals("정지 후에는 반영하지 않는다", 54f, repo.snapshot.value.speedKmh!!, 0.01f)
    }
}

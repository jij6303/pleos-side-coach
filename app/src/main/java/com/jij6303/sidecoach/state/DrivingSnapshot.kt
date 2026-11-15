package com.jij6303.sidecoach.state

import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.SignalKind
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.data.vehicle.VehicleSignal

/**
 * 특정 시점의 차량 상태. null은 "아직 값을 받지 못함"을 뜻한다.
 */
data class DrivingSnapshot(
    val timestampMs: Long = 0L,
    val speedKmh: Float? = null,
    val gear: GearPosition = GearPosition.UNKNOWN,
    /** 왼쪽 = +, 오른쪽 = − (실측). */
    val steeringDeg: Float? = null,
    val turnSignal: TurnSignalState? = null,
    val brakePressed: Boolean? = null,
    val hazardOn: Boolean? = null,
    val parkingBrakeOn: Boolean? = null,
    /** 받을 수 없는 신호와 그 사유. 값이 다시 들어오면 제거된다. */
    val unavailable: Map<SignalKind, String> = emptyMap(),
) {
    /** 신호 하나를 반영한 새 스냅샷. */
    fun apply(signal: VehicleSignal): DrivingSnapshot {
        val ts = signal.timestampMs
        return when (signal) {
            is VehicleSignal.SteeringAngle ->
                copy(timestampMs = ts, steeringDeg = signal.degrees, unavailable = unavailable - SignalKind.STEERING)
            is VehicleSignal.TurnSignal ->
                copy(timestampMs = ts, turnSignal = signal.state, unavailable = unavailable - SignalKind.TURN_SIGNAL)
            is VehicleSignal.BrakePedal ->
                copy(timestampMs = ts, brakePressed = signal.pressed, unavailable = unavailable - SignalKind.BRAKE)
            is VehicleSignal.Gear ->
                copy(timestampMs = ts, gear = signal.position, unavailable = unavailable - SignalKind.GEAR)
            is VehicleSignal.Speed ->
                copy(timestampMs = ts, speedKmh = signal.metersPerSecond * MPS_TO_KMH, unavailable = unavailable - SignalKind.SPEED)
            is VehicleSignal.Hazard ->
                copy(timestampMs = ts, hazardOn = signal.on, unavailable = unavailable - SignalKind.HAZARD)
            is VehicleSignal.ParkingBrake ->
                copy(timestampMs = ts, parkingBrakeOn = signal.on, unavailable = unavailable - SignalKind.PARKING_BRAKE)
            is VehicleSignal.Unavailable ->
                copy(timestampMs = ts, unavailable = unavailable + (signal.kind to signal.reason))
        }
    }

    companion object {
        const val MPS_TO_KMH = 3.6f
    }
}

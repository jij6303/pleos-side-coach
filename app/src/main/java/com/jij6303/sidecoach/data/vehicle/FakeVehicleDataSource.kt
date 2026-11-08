package com.jij6303.sidecoach.data.vehicle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * 테스트·데모용 소스. [emit]으로 넣은 신호를 그대로 흘려보낸다.
 */
class FakeVehicleDataSource(
    private val clock: () -> Long = System::currentTimeMillis,
) : VehicleDataSource {

    private val flow = MutableSharedFlow<VehicleSignal>(extraBufferCapacity = 64)

    override val signals: Flow<VehicleSignal> = flow.asSharedFlow()

    fun emit(signal: VehicleSignal): Boolean = flow.tryEmit(signal)

    fun steering(degrees: Float) = emit(VehicleSignal.SteeringAngle(degrees, clock()))
    fun turnSignal(state: TurnSignalState) = emit(VehicleSignal.TurnSignal(state, clock()))
    fun brake(pressed: Boolean) = emit(VehicleSignal.BrakePedal(pressed, clock()))
    fun gear(position: GearPosition) = emit(VehicleSignal.Gear(position, clock()))
    fun speedKmh(kmh: Float) = emit(VehicleSignal.Speed(kmh / 3.6f, clock()))
    fun hazard(on: Boolean) = emit(VehicleSignal.Hazard(on, clock()))
    fun parkingBrake(on: Boolean) = emit(VehicleSignal.ParkingBrake(on, clock()))
}

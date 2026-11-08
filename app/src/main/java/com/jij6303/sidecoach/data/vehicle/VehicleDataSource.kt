package com.jij6303.sidecoach.data.vehicle

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.merge

/**
 * 차량 신호 소스. [signals]는 cold Flow라서 collect를 시작하면 구독하고, 취소하면 구독을 해제한다.
 */
interface VehicleDataSource {
    val signals: Flow<VehicleSignal>
}

/** 여러 소스(Pleos SDK + AAOS)를 하나로 합친다. */
class CompositeVehicleDataSource(private vararg val sources: VehicleDataSource) : VehicleDataSource {
    override val signals: Flow<VehicleSignal> = merge(*sources.map { it.signals }.toTypedArray())
}

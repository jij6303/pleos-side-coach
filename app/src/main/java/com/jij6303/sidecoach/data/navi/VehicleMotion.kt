package com.jij6303.sidecoach.data.navi

import com.jij6303.sidecoach.data.vehicle.GearPosition

/**
 * 내비 소스가 남은 거리를 계산하는 데 필요한 차량 움직임.
 * 내비 SDK는 남은 거리를 주지 않으므로 차속을 적분해 직접 계산한다.
 * 시연 경로는 기어가 P를 벗어날 때 시작하므로 기어도 받는다.
 */
data class VehicleMotion(
    val timestampMs: Long = 0L,
    val speedKmh: Float? = null,
    val gear: GearPosition = GearPosition.UNKNOWN,
)

package com.jij6303.sidecoach.data.vehicle

/**
 * 차량 신호. SDK·AAOS 타입에 의존하지 않는 순수 Kotlin 모델이라 도메인 레이어와 JVM 테스트에서 그대로 쓴다.
 *
 * [timestampMs]는 단조 증가 시계(Android에서는 elapsedRealtime) 기준이다.
 */
sealed interface VehicleSignal {
    val timestampMs: Long

    /** 조향각(°). 실측 기준 왼쪽 = +, 오른쪽 = −, 범위 -470 ~ 500. */
    data class SteeringAngle(val degrees: Float, override val timestampMs: Long) : VehicleSignal

    /** 깜빡이 방향. 비상등이 켜져 있으면 OFF로 보고된다(실측). */
    data class TurnSignal(val state: TurnSignalState, override val timestampMs: Long) : VehicleSignal

    /** 브레이크 페달 눌림 여부. SDK는 페달 %가 아니라 Boolean만 준다(실측). */
    data class BrakePedal(val pressed: Boolean, override val timestampMs: Long) : VehicleSignal

    data class Gear(val position: GearPosition, override val timestampMs: Long) : VehicleSignal

    /** 차속(m/s). AAOS PERF_VEHICLE_SPEED 원값. */
    data class Speed(val metersPerSecond: Float, override val timestampMs: Long) : VehicleSignal

    data class Hazard(val on: Boolean, override val timestampMs: Long) : VehicleSignal

    data class ParkingBrake(val on: Boolean, override val timestampMs: Long) : VehicleSignal

    /** 해당 신호를 받을 수 없음(미지원, 권한 없음, 연결 실패 등). 디텍터 비활성화와 디버그 표시에 쓴다. */
    data class Unavailable(
        val kind: SignalKind,
        val reason: String,
        override val timestampMs: Long,
    ) : VehicleSignal
}

enum class TurnSignalState { OFF, LEFT, RIGHT }

enum class GearPosition { PARK, REVERSE, NEUTRAL, DRIVE, UNKNOWN }

enum class SignalKind { STEERING, TURN_SIGNAL, BRAKE, GEAR, SPEED, HAZARD, PARKING_BRAKE }

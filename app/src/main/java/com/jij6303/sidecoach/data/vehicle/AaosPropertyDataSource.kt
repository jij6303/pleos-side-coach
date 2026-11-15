package com.jij6303.sidecoach.data.vehicle

import android.car.Car
import android.car.VehicleGear
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.car.hardware.property.VehicleLightState
import android.content.Context
import android.content.pm.PackageManager
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * AAOS CarPropertyManager 구독: 기어, 차속, 비상등, 주차 브레이크.
 *
 * Pleos 정책상 표준 데이터는 AAOS API 사용을 권장한다.
 *
 * 에뮬레이터 실측(2026-11-09):
 * - GEAR_SELECTION, PARKING_BRAKE_ON: CAR_POWERTRAIN(normal)으로 수신됨
 * - PERF_VEHICLE_SPEED: CAR_SPEED(dangerous, 위치 그룹) 런타임 승인 후 수신됨
 * - HAZARD_LIGHTS_STATE: CAR_EXTERIOR_LIGHTS가 signature|privileged라 서드파티 앱은 수신 불가.
 *   등록은 시도해서 "수신 불가"로 표시만 한다(대체 경로 확인 필요).
 */
class AaosPropertyDataSource(
    context: Context,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : VehicleDataSource {

    private val appContext = context.applicationContext

    override val signals: Flow<VehicleSignal> = callbackFlow {
        fun unavailable(kind: SignalKind, reason: String) {
            Log.w(TAG, "$kind unavailable: $reason")
            trySend(VehicleSignal.Unavailable(kind, reason, clock()))
        }

        val car: Car? = runCatching { Car.createCar(appContext) }
            .onFailure { Log.w(TAG, "createCar failed", it) }
            .getOrNull()
        val manager = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        if (manager == null) {
            PROPERTIES.values.forEach { unavailable(it.kind, "Car 서비스 연결 실패") }
            awaitClose { runCatching { car?.disconnect() } }
            return@callbackFlow
        }

        val callback = object : CarPropertyManager.CarPropertyEventCallback {
            override fun onChangeEvent(value: CarPropertyValue<*>) {
                if (value.status != CarPropertyValue.STATUS_AVAILABLE) return
                toSignal(value)?.let { trySend(it) }
            }

            override fun onErrorEvent(propertyId: Int, areaId: Int) {
                PROPERTIES[propertyId]?.let { unavailable(it.kind, "onErrorEvent area=$areaId") }
            }
        }

        PROPERTIES.forEach { (propertyId, spec) ->
            val registered = try {
                manager.registerCallback(callback, propertyId, spec.rate)
            } catch (e: SecurityException) {
                unavailable(spec.kind, "권한 없음: ${e.message}")
                return@forEach
            } catch (e: IllegalArgumentException) {
                unavailable(spec.kind, "미지원 프로퍼티: ${e.message}")
                return@forEach
            }
            if (!registered) {
                val reason = spec.permission
                    ?.takeIf { appContext.checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
                    ?.let { "권한 없음: ${it.substringAfterLast('.')}" }
                    ?: "registerCallback=false (미지원)"
                unavailable(spec.kind, reason)
            }
        }

        awaitClose {
            runCatching { manager.unregisterCallback(callback) }
                .onFailure { Log.w(TAG, "unregister failed", it) }
            runCatching { car.disconnect() }
        }
    }

    private fun toSignal(value: CarPropertyValue<*>): VehicleSignal? {
        val ts = clock()
        val raw = value.value ?: return null
        return when (value.propertyId) {
            VehiclePropertyIds.GEAR_SELECTION -> VehicleSignal.Gear(gearOf(raw as Int), ts)
            VehiclePropertyIds.PERF_VEHICLE_SPEED -> VehicleSignal.Speed(raw as Float, ts)
            VehiclePropertyIds.HAZARD_LIGHTS_STATE ->
                VehicleSignal.Hazard((raw as Int) == VehicleLightState.STATE_ON, ts)
            VehiclePropertyIds.PARKING_BRAKE_ON -> VehicleSignal.ParkingBrake(raw as Boolean, ts)
            else -> null
        }
    }

    private fun gearOf(raw: Int): GearPosition = when (raw) {
        VehicleGear.GEAR_PARK -> GearPosition.PARK
        VehicleGear.GEAR_REVERSE -> GearPosition.REVERSE
        VehicleGear.GEAR_NEUTRAL -> GearPosition.NEUTRAL
        VehicleGear.GEAR_DRIVE -> GearPosition.DRIVE
        else -> GearPosition.UNKNOWN
    }

    private data class PropertySpec(val kind: SignalKind, val rate: Float, val permission: String? = null)

    private companion object {
        const val TAG = "AaosPropertySource"
        const val PERM_POWERTRAIN = "android.car.permission.CAR_POWERTRAIN"
        const val PERM_SPEED = "android.car.permission.CAR_SPEED"
        const val PERM_EXTERIOR_LIGHTS = "android.car.permission.CAR_EXTERIOR_LIGHTS"

        /** 차속은 감속도 계산에 쓰므로 10Hz(FAST)로 받는다 💭 CPU 사용량 보고 조정. 나머지는 변화 시에만. */
        val PROPERTIES = mapOf(
            VehiclePropertyIds.GEAR_SELECTION to
                PropertySpec(SignalKind.GEAR, CarPropertyManager.SENSOR_RATE_ONCHANGE, PERM_POWERTRAIN),
            VehiclePropertyIds.PERF_VEHICLE_SPEED to
                PropertySpec(SignalKind.SPEED, CarPropertyManager.SENSOR_RATE_FAST, PERM_SPEED),
            VehiclePropertyIds.HAZARD_LIGHTS_STATE to
                PropertySpec(SignalKind.HAZARD, CarPropertyManager.SENSOR_RATE_ONCHANGE, PERM_EXTERIOR_LIGHTS),
            VehiclePropertyIds.PARKING_BRAKE_ON to
                PropertySpec(SignalKind.PARKING_BRAKE, CarPropertyManager.SENSOR_RATE_ONCHANGE, PERM_POWERTRAIN),
        )
    }
}

package com.jij6303.sidecoach.data.vehicle

import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.capability.AccessType
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.TurnSignalValue
import ai.pleos.playground.vehicle.listener.BrakePedalStatusListener
import ai.pleos.playground.vehicle.listener.SteeringWheelAngleListener
import ai.pleos.playground.vehicle.listener.TurnSignalStateListener
import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Pleos Vehicle SDK 2.3.2 구독: 조향각, 깜빡이, 브레이크.
 *
 * 시그니처는 Vehicle-2.3.2-sources.jar에서 확인했다.
 * - `initialize()`는 반환값이 없고, 서비스 연결 전에 들어온 register 요청은 SDK가 연결 후 처리한다.
 * - 각 신호는 `check*Capabilities`로 READ 가능 여부를 확인한 뒤 register한다.
 * - 해제는 등록할 때와 같은 listener 객체로 unregister한 다음 `release()`한다.
 */
class PleosVehicleDataSource(
    context: Context,
    private val clock: () -> Long = SystemClock::elapsedRealtime,
) : VehicleDataSource {

    private val appContext = context.applicationContext

    override val signals: Flow<VehicleSignal> = callbackFlow {
        val closed = AtomicBoolean(false)

        fun unavailable(kind: SignalKind, reason: String) {
            Log.w(TAG, "$kind unavailable: $reason")
            trySend(VehicleSignal.Unavailable(kind, reason, clock()))
        }

        val vehicle = Vehicle(appContext)
        try {
            vehicle.initialize()
        } catch (e: Exception) {
            PLEOS_KINDS.forEach { unavailable(it, "SDK 초기화 실패: ${e.message}") }
            awaitClose { }
            return@callbackFlow
        }

        val steering = vehicle.getSteeringWheel()
        val turnSignal = vehicle.getTurnSignal()
        val brake = vehicle.getBrake()

        val steeringListener = object : SteeringWheelAngleListener {
            override fun onSteeringWheelAngleUpdated(angle: Float?) {
                angle?.let { trySend(VehicleSignal.SteeringAngle(it, clock())) }
            }
            override fun onFailed(e: Exception) = unavailable(SignalKind.STEERING, e.message ?: "onFailed")
        }
        val turnSignalListener = object : TurnSignalStateListener {
            override fun onTurnSignalStateUpdated(state: TurnSignalValue?) {
                state?.let { trySend(VehicleSignal.TurnSignal(it.toDomain(), clock())) }
            }
            override fun onFailed(e: Exception) = unavailable(SignalKind.TURN_SIGNAL, e.message ?: "onFailed")
        }
        val brakeListener = object : BrakePedalStatusListener {
            override fun onBrakePedalStatusUpdated(status: Boolean?) {
                status?.let { trySend(VehicleSignal.BrakePedal(it, clock())) }
            }
            override fun onFailed(e: Exception) = unavailable(SignalKind.BRAKE, e.message ?: "onFailed")
        }

        val steeringRegistered = AtomicBoolean(false)
        val turnSignalRegistered = AtomicBoolean(false)
        val brakeRegistered = AtomicBoolean(false)

        // 조향각
        steering.checkSteeringWheelAngleCapabilities(
            onComplete = { config, bounds ->
                Log.d(TAG, "steering capability=$config bounds=$bounds")
                if (closed.get()) return@checkSteeringWheelAngleCapabilities
                if (config.canRead()) {
                    steering.registerSteeringWheelAngle(steeringListener)
                    steeringRegistered.set(true)
                    steering.getCurrentSteeringWheelAngle(
                        onComplete = { it?.let { v -> trySend(VehicleSignal.SteeringAngle(v, clock())) } },
                        onFailed = { e -> Log.w(TAG, "steering initial read failed", e) },
                    )
                } else {
                    unavailable(SignalKind.STEERING, "accessType=${config.accessType}")
                }
            },
            onFailed = { e -> unavailable(SignalKind.STEERING, e.message ?: "capability 확인 실패") },
        )

        // 깜빡이
        turnSignal.checkTurnSignalStateCapabilities(
            onComplete = { config ->
                Log.d(TAG, "turnSignal capability=$config")
                if (closed.get()) return@checkTurnSignalStateCapabilities
                if (config.canRead()) {
                    turnSignal.registerTurnSignalState(turnSignalListener)
                    turnSignalRegistered.set(true)
                    turnSignal.getCurrentTurnSignalState(
                        onComplete = { it?.let { v -> trySend(VehicleSignal.TurnSignal(v.toDomain(), clock())) } },
                        onFailed = { e -> Log.w(TAG, "turnSignal initial read failed", e) },
                    )
                } else {
                    unavailable(SignalKind.TURN_SIGNAL, "accessType=${config.accessType}")
                }
            },
            onFailed = { e -> unavailable(SignalKind.TURN_SIGNAL, e.message ?: "capability 확인 실패") },
        )

        // 브레이크
        brake.checkBrakePedalStatusCapabilities(
            onComplete = { config ->
                Log.d(TAG, "brake capability=$config")
                if (closed.get()) return@checkBrakePedalStatusCapabilities
                if (config.canRead()) {
                    brake.registerBrakePedalStatus(brakeListener)
                    brakeRegistered.set(true)
                    brake.getCurrentBrakePedalStatus(
                        onComplete = { it?.let { v -> trySend(VehicleSignal.BrakePedal(v, clock())) } },
                        onFailed = { e -> Log.w(TAG, "brake initial read failed", e) },
                    )
                } else {
                    unavailable(SignalKind.BRAKE, "accessType=${config.accessType}")
                }
            },
            onFailed = { e -> unavailable(SignalKind.BRAKE, e.message ?: "capability 확인 실패") },
        )

        awaitClose {
            closed.set(true)
            runCatching {
                if (steeringRegistered.get()) steering.unregisterSteeringWheelAngle(steeringListener)
                if (turnSignalRegistered.get()) turnSignal.unregisterTurnSignalState(turnSignalListener)
                if (brakeRegistered.get()) brake.unregisterBrakePedalStatus(brakeListener)
            }.onFailure { Log.w(TAG, "unregister failed", it) }
            runCatching { vehicle.release() }.onFailure { Log.w(TAG, "release failed", it) }
        }
    }

    private fun CapabilityConfig.canRead(): Boolean =
        accessType == AccessType.READ || accessType == AccessType.READ_WRITE

    private fun TurnSignalValue.toDomain(): TurnSignalState = when (this) {
        TurnSignalValue.OFF -> TurnSignalState.OFF
        TurnSignalValue.LEFT -> TurnSignalState.LEFT
        TurnSignalValue.RIGHT -> TurnSignalState.RIGHT
    }

    private companion object {
        const val TAG = "PleosVehicleSource"
        val PLEOS_KINDS = setOf(SignalKind.STEERING, SignalKind.TURN_SIGNAL, SignalKind.BRAKE)
    }
}

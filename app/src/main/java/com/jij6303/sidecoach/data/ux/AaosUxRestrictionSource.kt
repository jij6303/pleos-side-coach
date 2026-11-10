package com.jij6303.sidecoach.data.ux

import android.car.Car
import android.car.drivingstate.CarUxRestrictions
import android.car.drivingstate.CarUxRestrictionsManager
import android.content.Context
import android.util.Log
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow

/**
 * AAOS CarUxRestrictionsManager 구독 (Pleos 가이드: registerListener + onUxRestrictionsChanged).
 *
 * 에뮬레이터 실측(#17): 규제는 콘솔 기어로 바뀐다.
 * P → parked (DO 불필요, 0x0), 그 외 기어 → idling/moving (DO 필요, 0x50 = NO_VIDEO | NO_SETUP).
 */
class AaosUxRestrictionSource(context: Context) : UxRestrictionSource {

    private val appContext = context.applicationContext

    override val state: Flow<UxState> = callbackFlow {
        val car: Car? = runCatching { Car.createCar(appContext) }
            .onFailure { Log.w(TAG, "createCar failed", it) }
            .getOrNull()
        val manager = car?.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE) as? CarUxRestrictionsManager
        if (manager == null) {
            Log.w(TAG, "CarUxRestrictionsManager unavailable -> treat as driving")
            trySend(UxState.Unavailable)
            awaitClose { runCatching { car?.disconnect() } }
            return@callbackFlow
        }

        fun emit(r: CarUxRestrictions?) {
            val s = r?.toUxState() ?: UxState.Unavailable
            Log.i(TAG, "ux DO=${s.requiresDistractionOptimization} restrictions=0x${s.activeRestrictions.toString(16)}")
            trySend(s)
        }

        manager.registerListener { emit(it) }
        emit(runCatching { manager.currentCarUxRestrictions }.getOrNull())

        awaitClose {
            runCatching { manager.unregisterListener() }
            runCatching { car.disconnect() }
        }
    }

    private fun CarUxRestrictions.toUxState() = UxState(
        requiresDistractionOptimization = isRequiresDistractionOptimization,
        activeRestrictions = activeRestrictions,
    )

    private companion object { const val TAG = "UxRestriction" }
}

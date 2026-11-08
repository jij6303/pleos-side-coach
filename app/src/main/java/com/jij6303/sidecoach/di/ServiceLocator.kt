package com.jij6303.sidecoach.di

import android.content.Context
import com.jij6303.sidecoach.data.vehicle.AaosPropertyDataSource
import com.jij6303.sidecoach.data.vehicle.CompositeVehicleDataSource
import com.jij6303.sidecoach.data.vehicle.FakeVehicleDataSource
import com.jij6303.sidecoach.data.vehicle.PleosVehicleDataSource
import com.jij6303.sidecoach.data.vehicle.VehicleDataSource
import com.jij6303.sidecoach.domain.DrivingStateRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * 수동 DI. [useFakeVehicle]로 실제 차량 소스와 Fake 소스를 전환한다.
 */
object ServiceLocator {

    /** true면 차량 신호 대신 [fakeVehicle]을 쓴다 (데모·UI 확인용). */
    var useFakeVehicle: Boolean = false
        private set

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    lateinit var vehicleDataSource: VehicleDataSource
        private set
    lateinit var drivingStateRepository: DrivingStateRepository
        private set

    val fakeVehicle: FakeVehicleDataSource by lazy { FakeVehicleDataSource() }

    fun init(context: Context, useFake: Boolean = false) {
        useFakeVehicle = useFake
        vehicleDataSource = if (useFake) {
            fakeVehicle
        } else {
            CompositeVehicleDataSource(
                PleosVehicleDataSource(context),
                AaosPropertyDataSource(context),
            )
        }
        drivingStateRepository = DrivingStateRepository(vehicleDataSource)
    }
}

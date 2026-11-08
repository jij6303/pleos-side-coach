package com.jij6303.sidecoach.di

import android.content.Context
import android.util.Log
import com.jij6303.sidecoach.data.vehicle.AaosPropertyDataSource
import com.jij6303.sidecoach.data.vehicle.CompositeVehicleDataSource
import com.jij6303.sidecoach.data.vehicle.FakeVehicleDataSource
import com.jij6303.sidecoach.data.vehicle.PleosVehicleDataSource
import com.jij6303.sidecoach.data.vehicle.VehicleDataSource
import com.jij6303.sidecoach.domain.DrivingStateRepository
import com.jij6303.sidecoach.domain.coach.CoachingArbiter
import com.jij6303.sidecoach.domain.coach.CoachingPipeline
import com.jij6303.sidecoach.domain.detect.BlinkerOmissionDetector
import com.jij6303.sidecoach.domain.detect.HardBrakeDetector
import com.jij6303.sidecoach.domain.trip.TripRecorder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

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
    lateinit var coachingPipeline: CoachingPipeline
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
        coachingPipeline = CoachingPipeline(
            updates = drivingStateRepository.updates,
            detectors = listOf(BlinkerOmissionDetector(), HardBrakeDetector()),
            arbiter = CoachingArbiter(),
            recorder = TripRecorder(),
        )
        // TTS 연결 전까지 발화 내용을 로그로 확인한다 (logcat 태그: Coach)
        appScope.launch {
            coachingPipeline.messages.collect { Log.i("Coach", "${it.type}: ${it.text}") }
        }
    }

    /** 신호 구독과 코칭 판정을 함께 시작한다. */
    fun startCoaching() {
        drivingStateRepository.start(appScope)
        coachingPipeline.start(appScope)
    }

    fun stopCoaching() {
        coachingPipeline.stop()
        drivingStateRepository.stop()
    }
}

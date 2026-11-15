package com.jij6303.sidecoach.app.di

import android.content.Context
import android.util.Log
import com.jij6303.sidecoach.feature.parking.ParkingCoachController
import com.jij6303.sidecoach.feature.parking.ParkingSettings

/** feature/parking: 주차 코치와 주차 유형 설정. 같은 스냅샷 흐름을 따로 구독해 R 기어일 때만 동작한다. */
class ParkingModule(context: Context, data: DataModule) {

    val settings = ParkingSettings(context)

    val controller = ParkingCoachController(
        snapshots = data.drivingStateRepository.updates,
        clock = data.clock,
        initialType = settings.load(),
        log = { Log.i("Parking", it) },
    )
}

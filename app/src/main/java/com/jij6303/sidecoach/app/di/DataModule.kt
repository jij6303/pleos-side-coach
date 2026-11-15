package com.jij6303.sidecoach.app.di

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.jij6303.sidecoach.data.navi.FakeRouteNaviEventSource
import com.jij6303.sidecoach.data.navi.NaviEventSource
import com.jij6303.sidecoach.data.navi.PleosNaviEventSource
import com.jij6303.sidecoach.data.navi.RoutingNaviEventSource
import com.jij6303.sidecoach.data.navi.VehicleMotion
import com.jij6303.sidecoach.data.ux.AaosUxRestrictionSource
import com.jij6303.sidecoach.data.ux.FakeUxRestrictionSource
import com.jij6303.sidecoach.data.ux.UxRestrictionSource
import com.jij6303.sidecoach.data.vehicle.AaosPropertyDataSource
import com.jij6303.sidecoach.data.vehicle.CompositeVehicleDataSource
import com.jij6303.sidecoach.data.vehicle.FakeVehicleDataSource
import com.jij6303.sidecoach.data.vehicle.PleosVehicleDataSource
import com.jij6303.sidecoach.data.vehicle.VehicleDataSource
import com.jij6303.sidecoach.state.DrivingStateRepository
import com.jij6303.sidecoach.state.UiMode
import com.jij6303.sidecoach.state.UxModeController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/**
 * data + state 계층: SDK 소스를 만들고 판단하기 좋은 흐름(스냅샷, 화면 모드)으로 묶는다.
 * [useFake]면 차량·규제·내비 모두 Fake 소스를 쓴다 (차량 없이 화면과 로직 확인).
 */
class DataModule(context: Context, val useFake: Boolean, scope: CoroutineScope) {

    /** 메시지·스냅샷과 같은 시계: 실제 소스는 elapsedRealtime, Fake는 currentTimeMillis. */
    val clock: () -> Long = if (useFake) System::currentTimeMillis else SystemClock::elapsedRealtime

    val vehicleDataSource: VehicleDataSource = if (useFake) {
        FakeVehicleDataSource()
    } else {
        CompositeVehicleDataSource(PleosVehicleDataSource(context), AaosPropertyDataSource(context))
    }

    val drivingStateRepository = DrivingStateRepository(vehicleDataSource)

    val uxRestrictionSource: UxRestrictionSource =
        if (useFake) FakeUxRestrictionSource() else AaosUxRestrictionSource(context)

    /** 화면 모드 원본(cold). 구독자가 없어도 지금 값을 바로 계산해야 할 때(Gleo 명령) 쓴다. */
    val uiModeSource: Flow<UiMode> = UxModeController(
        ux = uxRestrictionSource.state,
        gear = drivingStateRepository.snapshot.map { it.gear },
    ).mode

    /** 화면 모드. 화면이 구독하는 동안만 규제를 구독한다. 첫 값이 오기 전에는 안전하게 주행 중으로 둔다. */
    val uiMode: StateFlow<UiMode> = uiModeSource.stateIn(scope, SharingStarted.WhileSubscribed(5_000), UiMode.DRIVING_VOICE)

    /** 내비 소스는 남은 거리 계산에 시각·차속·기어만 쓴다. */
    private val motions = drivingStateRepository.updates.map { VehicleMotion(it.timestampMs, it.speedKmh, it.gear) }

    /** 시연용 경로. 위치를 저장·복원하려고 구체 타입으로도 들고 있는다. */
    val fakeRoute = FakeRouteNaviEventSource(motions, clock)

    /** 실제 내비로 경로 안내 중이면 NaviHelper, 아니면 시연 경로. Fake 모드는 시연 경로만. */
    val naviEventSource: NaviEventSource = if (useFake) {
        fakeRoute
    } else {
        val real = PleosNaviEventSource(context = context, motions = motions, clock = clock, log = { Log.i("Navi", it) })
        RoutingNaviEventSource(real, real.routeActive, fakeRoute, scope)
    }
}

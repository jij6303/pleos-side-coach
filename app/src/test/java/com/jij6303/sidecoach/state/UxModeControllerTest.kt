package com.jij6303.sidecoach.state

import com.jij6303.sidecoach.data.ux.FakeUxRestrictionSource
import com.jij6303.sidecoach.data.ux.UxState
import com.jij6303.sidecoach.data.vehicle.GearPosition
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class UxModeControllerTest {

    private val driving = UxState(requiresDistractionOptimization = true, activeRestrictions = 0x50)

    @Test
    fun `규제 없음 + P단이면 전체 화면`() {
        assertEquals(UiMode.PARKED_FULL, UxModeController.decide(UxState.Parked, GearPosition.PARK))
    }

    @Test
    fun `규제 없음 + P단이 아니면 간단 화면`() {
        assertEquals(UiMode.IDLE_LIMITED, UxModeController.decide(UxState.Parked, GearPosition.DRIVE))
        assertEquals(UiMode.IDLE_LIMITED, UxModeController.decide(UxState.Parked, GearPosition.UNKNOWN))
    }

    @Test
    fun `규제 중이면 기어와 상관없이 주행 화면`() {
        GearPosition.entries.forEach {
            assertEquals(UiMode.DRIVING_VOICE, UxModeController.decide(driving, it))
        }
    }

    @Test
    fun `규제 정보를 못 받으면 주행 화면`() {
        assertEquals(UiMode.DRIVING_VOICE, UxModeController.decide(UxState.Unavailable, GearPosition.PARK))
    }

    @Test
    fun `규제와 기어 변화를 따라가고 같은 모드는 반복하지 않는다`() = runTest {
        val ux = FakeUxRestrictionSource(UxState.Parked)
        val gear = MutableStateFlow(GearPosition.PARK)
        val modes = mutableListOf<UiMode>()
        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            UxModeController(ux.state, gear).mode.toList(modes)
        }

        gear.value = GearPosition.DRIVE       // 콘솔: P → D (규제보다 기어가 먼저 올 수 있음)
        ux.set(driving)                       // 규제 시작
        gear.value = GearPosition.NEUTRAL     // 주행 중 기어 변경: 모드 그대로
        ux.set(UxState.Parked)
        gear.value = GearPosition.PARK
        job.cancel()

        assertEquals(
            listOf(UiMode.PARKED_FULL, UiMode.IDLE_LIMITED, UiMode.DRIVING_VOICE, UiMode.IDLE_LIMITED, UiMode.PARKED_FULL),
            modes,
        )
    }
}

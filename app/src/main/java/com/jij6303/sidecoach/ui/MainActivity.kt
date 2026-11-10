package com.jij6303.sidecoach.ui

import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jij6303.sidecoach.di.ServiceLocator
import com.jij6303.sidecoach.service.CoachingService
import com.jij6303.sidecoach.ui.dashboard.DashboardScreen
import com.jij6303.sidecoach.ui.driving.DrivingScreen
import com.jij6303.sidecoach.ui.report.ReportScreen
import com.jij6303.sidecoach.ui.theme.SideCoachTheme

class MainActivity : ComponentActivity() {

    private val repository get() = ServiceLocator.drivingStateRepository
    private val pipeline get() = ServiceLocator.coachingPipeline

    /** 런타임 승인이 필요한 권한을 요청하고, 결과가 오면 차량 신호 구독을 다시 건다. */
    private val carPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) && ServiceLocator.isCoaching) {
                ServiceLocator.stopCoaching()
                ServiceLocator.startCoaching()
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // 최상단 화면의 Back은 앱을 숨긴다. finish()는 쓰지 않는다 (Pleos 심사 기준).
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                moveTaskToBack(true)
            }
        })
        // 하위 화면(성적표)이 떠 있으면 Back은 그 화면만 닫는다. 나중에 등록한 콜백이 먼저 불린다.
        val closeReport = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                pipeline.dismissReport()
            }
        }
        onBackPressedDispatcher.addCallback(this, closeReport)

        setContent {
            SideCoachTheme {
                val snapshot by repository.snapshot.collectAsStateWithLifecycle()
                val lastMessage by pipeline.lastMessage.collectAsStateWithLifecycle()
                val stats by pipeline.recorder.stats.collectAsStateWithLifecycle()
                val uiMode by ServiceLocator.uiMode.collectAsStateWithLifecycle()
                val report by pipeline.lastReport.collectAsStateWithLifecycle()
                // 성적표는 정차(P단, 규제 없음)일 때만 보여준다 (dev-architecture 5.2)
                val shownReport = report?.takeIf { uiMode == UiMode.PARKED_FULL }
                closeReport.isEnabled = shownReport != null
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // 화면 전환에 애니메이션을 넣지 않는다 (주행 중 애니메이션 금지)
                    when (uiMode) {
                        UiMode.DRIVING_VOICE -> DrivingScreen(
                            snapshot = snapshot,
                            lastMessage = lastMessage,
                            modifier = Modifier.padding(innerPadding),
                        )
                        UiMode.PARKED_FULL if shownReport != null -> ReportScreen(
                            report = shownReport,
                            onClose = pipeline::dismissReport,
                            modifier = Modifier.padding(innerPadding),
                        )
                        // 튜토리얼 화면이 생기기 전까지는 정차 화면은 대시보드
                        UiMode.IDLE_LIMITED, UiMode.PARKED_FULL -> DashboardScreen(
                            snapshot = snapshot,
                            lastMessage = lastMessage,
                            stats = stats,
                            usingFake = ServiceLocator.useFakeVehicle,
                            demoRoute = ServiceLocator.naviEventSource.isDemo,
                            modifier = Modifier.padding(innerPadding),
                        )
                    }
                }
            }
        }

        requestMissingCarPermissions()
    }

    // 코칭은 CoachingService가 맡는다. 화면이 보일 때 시작만 하고, 숨겨져도 멈추지 않는다.
    // 끄는 것은 알림의 "코칭 끄기" 버튼.
    override fun onStart() {
        super.onStart()
        CoachingService.start(this)
    }

    private fun requestMissingCarPermissions() {
        val missing = RUNTIME_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) carPermissionLauncher.launch(missing.toTypedArray())
    }

    private companion object {
        /**
         * CAR_SPEED는 dangerous 권한(위치 그룹)이라 런타임 승인이 필요하다. CAR_POWERTRAIN은 normal이라 자동 승인.
         * POST_NOTIFICATIONS(13+)는 코칭 서비스 알림 표시용. 거부해도 서비스는 동작한다.
         */
        val RUNTIME_PERMISSIONS = buildList {
            add("android.car.permission.CAR_SPEED")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) add(android.Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

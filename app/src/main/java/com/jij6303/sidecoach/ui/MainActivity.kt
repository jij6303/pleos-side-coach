package com.jij6303.sidecoach.ui

import android.content.Intent
import android.content.pm.PackageManager
import android.util.Log
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
import androidx.lifecycle.lifecycleScope
import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.gleo.GleoActionHandler
import com.jij6303.sidecoach.gleo.GleoActionParser
import com.jij6303.sidecoach.gleo.GleoCallbackSender
import com.jij6303.sidecoach.gleo.GleoContract
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.jij6303.sidecoach.di.ServiceLocator
import com.jij6303.sidecoach.service.CoachingService
import com.jij6303.sidecoach.ui.dashboard.DashboardScreen
import com.jij6303.sidecoach.ui.driving.DrivingScreen
import com.jij6303.sidecoach.ui.report.ReportScreen
import com.jij6303.sidecoach.ui.tips.CarTipsScreen
import com.jij6303.sidecoach.domain.tips.CarTip
import kotlinx.coroutines.flow.MutableStateFlow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import com.jij6303.sidecoach.ui.theme.SideCoachTheme

class MainActivity : ComponentActivity() {

    private val repository get() = ServiceLocator.drivingStateRepository
    private val pipeline get() = ServiceLocator.coachingPipeline

    /** 열려 있는 도움말 항목. null이면 도움말 화면이 닫혀 있다. */
    private val openTip = MutableStateFlow<CarTip?>(null)

    private val gleoHandler by lazy {
        GleoActionHandler(
            startCoaching = { CoachingService.start(this) },
            // 서비스가 막 시작됐으면 기어 값이 아직 없을 수 있어 잠시 기다린다 (응답 7초 제한 안)
            currentMode = {
                withTimeoutOrNull(MODE_WAIT_MS) {
                    repository.snapshot.first { it.gear != GearPosition.UNKNOWN }
                    ServiceLocator.uiModeSource.first()
                }
            },
            speakTip = ServiceLocator.tipSpeaker::speak,
            openTip = { openTip.value = it },
        )
    }

    /** Gleo가 startActivity로 보낸 명령을 처리하고 응답을 보낸다 (dev-architecture 7.2). */
    private fun handleGleo(intent: Intent?) {
        val action = intent?.action ?: return
        if (action == Intent.ACTION_MAIN) return
        val params = GleoActionParser.parse(intent.getStringExtra(GleoContract.EXTRA_PARAM))
        Log.i("Gleo", "action=$action params=$params")
        lifecycleScope.launch {
            gleoHandler.handle(action, params)?.let { GleoCallbackSender.send(this@MainActivity, it) }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleGleo(intent)
    }

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
        val closeTips = object : OnBackPressedCallback(false) {
            override fun handleOnBackPressed() {
                openTip.value = null
            }
        }
        onBackPressedDispatcher.addCallback(this, closeTips)

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
                val parking by ServiceLocator.parking.ui.collectAsStateWithLifecycle()
                val parkingMessage by ServiceLocator.parking.lastMessage.collectAsStateWithLifecycle()
                val parkingType by ServiceLocator.parking.type.collectAsStateWithLifecycle()
                val tip by openTip.collectAsStateWithLifecycle()
                val shownTip = tip?.takeIf { uiMode == UiMode.PARKED_FULL && shownReport == null }
                closeTips.isEnabled = shownTip != null
                // P단을 벗어나면(주행 규제 등) 도움말을 닫는다 (dev-architecture 5.2)
                LaunchedEffect(uiMode) {
                    if (uiMode != UiMode.PARKED_FULL) openTip.value = null
                }
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // 화면 전환에 애니메이션을 넣지 않는다 (주행 중 애니메이션 금지)
                    val drivingScreen = @Composable {
                        DrivingScreen(
                            snapshot = snapshot,
                            lastMessage = lastMessage,
                            modifier = Modifier.padding(innerPadding),
                            parking = parking,
                            parkingMessage = parkingMessage,
                            onParkingNext = { ServiceLocator.parking.tapNext(ServiceLocator.appScope) },
                        )
                    }
                    when (uiMode) {
                        UiMode.DRIVING_VOICE -> drivingScreen()
                        // 주차 코치 중이면 규제가 없는 상태(IDLE_LIMITED)여도 주차 카드를 보여준다
                        UiMode.IDLE_LIMITED if parking != null -> drivingScreen()
                        UiMode.PARKED_FULL if shownReport != null -> ReportScreen(
                            report = shownReport,
                            onClose = pipeline::dismissReport,
                            modifier = Modifier.padding(innerPadding),
                        )
                        UiMode.PARKED_FULL if shownTip != null -> CarTipsScreen(
                            selected = shownTip,
                            onSelect = { openTip.value = it },
                            onListen = ServiceLocator.tipSpeaker::speak,
                            onClose = { openTip.value = null },
                            modifier = Modifier.padding(innerPadding),
                        )
                        UiMode.IDLE_LIMITED, UiMode.PARKED_FULL -> DashboardScreen(
                            snapshot = snapshot,
                            lastMessage = lastMessage,
                            stats = stats,
                            usingFake = ServiceLocator.useFakeVehicle,
                            demoRoute = ServiceLocator.naviEventSource.isDemo,
                            modifier = Modifier.padding(innerPadding),
                            onOpenTips = if (uiMode == UiMode.PARKED_FULL) {
                                { openTip.value = CarTip.entries.first() }
                            } else {
                                null
                            },
                            parkingType = parkingType,
                            onParkingTypeChange = if (uiMode == UiMode.PARKED_FULL) {
                                { type ->
                                    ServiceLocator.parkingSettings.save(type)
                                    ServiceLocator.parking.setType(ServiceLocator.appScope, type)
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }
        }

        requestMissingCarPermissions()
        // 화면 회전 등으로 다시 만들어질 때 같은 명령을 또 처리하지 않는다
        if (savedInstanceState == null) handleGleo(intent)
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
        const val MODE_WAIT_MS = 3_000L

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

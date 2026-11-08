package com.jij6303.sidecoach.ui

import android.content.pm.PackageManager
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
import com.jij6303.sidecoach.ui.dashboard.DashboardScreen
import com.jij6303.sidecoach.ui.theme.SideCoachTheme

class MainActivity : ComponentActivity() {

    private val repository get() = ServiceLocator.drivingStateRepository
    private val pipeline get() = ServiceLocator.coachingPipeline

    /** AAOS 권한 중 런타임 승인이 필요한 것이 있으면 요청하고, 결과가 오면 구독을 다시 건다. */
    private val carPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
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

        setContent {
            SideCoachTheme {
                val snapshot by repository.snapshot.collectAsStateWithLifecycle()
                val lastMessage by pipeline.lastMessage.collectAsStateWithLifecycle()
                val stats by pipeline.recorder.stats.collectAsStateWithLifecycle()
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    DashboardScreen(
                        snapshot = snapshot,
                        lastMessage = lastMessage,
                        stats = stats,
                        usingFake = ServiceLocator.useFakeVehicle,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }

        requestMissingCarPermissions()
    }

    // 지금은 화면이 보일 때만 코칭한다. 코칭 Service(TTS 이슈)가 생기면 구독 주체를 Service로 옮긴다.
    override fun onStart() {
        super.onStart()
        ServiceLocator.startCoaching()
    }

    override fun onStop() {
        ServiceLocator.stopCoaching()
        super.onStop()
    }

    private fun requestMissingCarPermissions() {
        val missing = CAR_RUNTIME_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) carPermissionLauncher.launch(missing.toTypedArray())
    }

    private companion object {
        /** CAR_SPEED는 dangerous 권한(위치 그룹)이라 런타임 승인이 필요하다. CAR_POWERTRAIN은 normal이라 자동 승인. */
        val CAR_RUNTIME_PERMISSIONS = listOf("android.car.permission.CAR_SPEED")
    }
}

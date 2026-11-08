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
import com.jij6303.sidecoach.ui.debug.SignalDebugScreen
import com.jij6303.sidecoach.ui.theme.SideCoachTheme

class MainActivity : ComponentActivity() {

    private val repository get() = ServiceLocator.drivingStateRepository

    /** AAOS 권한 중 런타임 승인이 필요한 것이 있으면 요청하고, 결과가 오면 구독을 다시 건다. */
    private val carPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {
            if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) {
                repository.stop()
                repository.start(ServiceLocator.appScope)
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    SignalDebugScreen(
                        snapshot = snapshot,
                        usingFake = ServiceLocator.useFakeVehicle,
                        modifier = Modifier.padding(innerPadding),
                    )
                }
            }
        }

        requestMissingCarPermissions()
    }

    // 백그라운드에서는 신호 수집을 멈춘다. 이후 코칭 Service가 생기면 구독 주체를 Service로 옮긴다.
    override fun onStart() {
        super.onStart()
        repository.start(ServiceLocator.appScope)
    }

    override fun onStop() {
        repository.stop()
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

package com.jij6303.sidecoach.debug

import ai.pleos.playground.navi.constants.NaviErrorCode
import ai.pleos.playground.navi.constants.NavigationStatus
import ai.pleos.playground.navi.data.CautionPoint
import ai.pleos.playground.navi.data.CurrentLocationInfo
import ai.pleos.playground.navi.data.DrivingInfo
import ai.pleos.playground.navi.data.RouteStartInfo
import ai.pleos.playground.navi.data.RouteStateInfo
import ai.pleos.playground.navi.data.TBTInfo
import ai.pleos.playground.navi.helper.NaviHelper
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener
import android.app.Activity
import android.os.Bundle
import android.util.Log
import android.widget.TextView

/**
 * #63 실측용 프로브 (debug 빌드 전용). NaviHelper 이벤트를 logcat `NaviProbe` 태그로 덤프한다.
 *
 * 실행: `adb shell am start -n com.jij6303.sidecoach/.debug.NaviProbeActivity [--ez query true]`
 * - query=true면 초기화 뒤 get*Info()를 호출한다. 이 호출들은 `pleos.car.permission.NAVI_ROUTE`를 검사한다(바이트코드 확인).
 * - 내비로 목적지를 설정하는 동안 이 화면이 뒤로 가도 onDestroy 전까지 리스너는 유지된다.
 */
class NaviProbeActivity : Activity(), NaviHelperEventListener {

    private lateinit var helper: NaviHelper
    private lateinit var status: TextView
    private var count = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        status = TextView(this).apply { textSize = 28f; text = "NaviProbe 시작" }
        setContentView(status)

        val query = intent.getBooleanExtra("query", false)
        helper = NaviHelper(applicationContext)
        helper.initialize(
            {
                log("initialize OK (query=$query)")
                helper.addListener(this)
                if (query) queryAll()
            },
            { e -> log("initialize FAIL: $e") },
        )
    }

    private fun queryAll() {
        val calls = listOf<Pair<String, () -> Unit>>(
            "getTBTInfo" to helper::getTBTInfo,
            "getCurrentLocationInfo" to helper::getCurrentLocationInfo,
            "getRouteStateInfo" to helper::getRouteStateInfo,
            "getDestinationInfo" to helper::getDestinationInfo,
        )
        for ((name, call) in calls) {
            runCatching(call)
                .onSuccess { log("$name called") }
                .onFailure { log("$name FAIL: $it") }
        }
    }

    override fun onDestroy() {
        runCatching { helper.removeListener(this) }
        runCatching { helper.release() }
        super.onDestroy()
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
        count++
        runOnUiThread { status.text = "NaviProbe 이벤트 $count\n$msg" }
    }

    override fun onTBTInfo(tbtInfoList: List<TBTInfo>) {
        log("onTBTInfo size=${tbtInfoList.size}")
        tbtInfoList.forEachIndexed { i, t ->
            log("  TBT[$i] type=${t.type} distance=${t.distance} description=${t.description}")
        }
    }

    override fun onCurrentLocationInfo(currentLocationInfo: CurrentLocationInfo) = log("onCurrentLocationInfo $currentLocationInfo")
    override fun onCautionPointInfo(cautionPointList: List<CautionPoint>) = log("onCautionPointInfo $cautionPointList")
    override fun onDrivingInfo(drivingInfo: DrivingInfo) = log("onDrivingInfo $drivingInfo")
    override fun onRouteStateInfo(routeStateInfo: RouteStateInfo) = log("onRouteStateInfo $routeStateInfo")
    override fun onRouteStarted(routeStartInfo: RouteStartInfo) = log("onRouteStarted $routeStartInfo")
    override fun onNavigationStatus(status: NavigationStatus) = log("onNavigationStatus $status")
    override fun onCurrentRoadSpeedLimit(speedLimit: Int) = log("onCurrentRoadSpeedLimit $speedLimit")
    override fun onRoutingCompleted() = log("onRoutingCompleted")
    override fun onReRouteEnded() = log("onReRouteEnded")
    override fun onRouteCancelled() = log("onRouteCancelled")
    override fun onError(errorCode: NaviErrorCode) = log("onError $errorCode")

    private companion object {
        const val TAG = "NaviProbe"
    }
}

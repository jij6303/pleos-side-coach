package com.jij6303.sidecoach.app.di

import android.content.Context
import android.util.Log
import com.jij6303.sidecoach.app.session.SessionState
import com.jij6303.sidecoach.app.session.SessionStore
import com.jij6303.sidecoach.feature.coach.CoachingPipeline
import com.jij6303.sidecoach.feature.coach.detect.BlinkerLeftOnDetector
import com.jij6303.sidecoach.feature.coach.detect.BlinkerOmissionDetector
import com.jij6303.sidecoach.feature.coach.detect.DetectorManager
import com.jij6303.sidecoach.feature.coach.detect.GearShiftWhileMovingDetector
import com.jij6303.sidecoach.feature.coach.detect.HardBrakeDetector
import com.jij6303.sidecoach.feature.coach.detect.TurnSpeedDetector
import com.jij6303.sidecoach.feature.coach.lane.LaneInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * feature/coach + feature/report: 코칭 파이프라인을 조립하고, 세션 저장·복원을 연결한다.
 */
class CoachModule(context: Context, private val data: DataModule, scope: CoroutineScope) {

    private val sessionStore = SessionStore(context)

    val pipeline = CoachingPipeline(
        updates = data.drivingStateRepository.updates,
        detectors = DetectorManager(
            listOf(
                BlinkerOmissionDetector(),
                HardBrakeDetector(),
                BlinkerLeftOnDetector(),
                TurnSpeedDetector(),
                GearShiftWhileMovingDetector(),
            ),
        ),
        naviEvents = data.naviEventSource.events,
        // 프로세스가 다시 만들어져도 이어가도록 주기적으로 저장 (코칭 상태 + 시연 경로 위치)
        stateSink = { sessionStore.save(SessionState(it, data.fakeRoute.exportState())) },
    )

    /** 운전자가 알려준 차로 입력. Gleo·대시보드가 쓴다. */
    val laneInput = LaneInput(pipeline.lane, data.clock)

    init {
        // 중재자가 고른 메시지를 로그로도 남긴다 (logcat 태그: Coach)
        scope.launch { pipeline.messages.collect { Log.i("Coach", "${it.type}: ${it.text}") } }
    }

    /** 저장된 주행이 있으면 이어간다. 재부팅했거나 30분 넘게 지났으면 버리고 새 주행으로 시작한다. */
    fun restoreSession() {
        if (pipeline.isRunning) return
        val saved = sessionStore.load() ?: return
        val now = data.clock()
        if (!saved.isUsable(now)) {
            Log.i("SessionStore", "discard: savedAt=${saved.savedAtMs} now=$now")
            sessionStore.clear()
            return
        }
        pipeline.restore(saved.coaching, now)
        data.fakeRoute.restore(saved.route)
        val trip = saved.coaching.trip
        Log.i("SessionStore", "restored: trip=${trip.session != null} stats=${trip.stats} report=${trip.pendingReport != null}")
    }
}

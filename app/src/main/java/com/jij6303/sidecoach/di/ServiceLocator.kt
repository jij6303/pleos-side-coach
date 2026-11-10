package com.jij6303.sidecoach.di

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.jij6303.sidecoach.R
import com.jij6303.sidecoach.data.navi.FakeRouteNaviEventSource
import com.jij6303.sidecoach.data.navi.NaviEventSource
import com.jij6303.sidecoach.data.ux.AaosUxRestrictionSource
import com.jij6303.sidecoach.data.ux.FakeUxRestrictionSource
import com.jij6303.sidecoach.data.ux.UxRestrictionSource
import com.jij6303.sidecoach.data.vehicle.AaosPropertyDataSource
import com.jij6303.sidecoach.data.vehicle.CompositeVehicleDataSource
import com.jij6303.sidecoach.data.vehicle.FakeVehicleDataSource
import com.jij6303.sidecoach.data.vehicle.PleosVehicleDataSource
import com.jij6303.sidecoach.data.vehicle.VehicleDataSource
import com.jij6303.sidecoach.domain.DrivingStateRepository
import com.jij6303.sidecoach.domain.coach.CoachingArbiter
import com.jij6303.sidecoach.domain.coach.CoachingPipeline
import com.jij6303.sidecoach.domain.coach.MessageCatalog
import com.jij6303.sidecoach.domain.report.Grade
import com.jij6303.sidecoach.domain.situation.SituationType
import com.jij6303.sidecoach.domain.detect.BlinkerOmissionDetector
import com.jij6303.sidecoach.domain.detect.HardBrakeDetector
import com.jij6303.sidecoach.domain.trip.TripRecorder
import com.jij6303.sidecoach.ui.UiMode
import com.jij6303.sidecoach.ui.UxModeController
import com.jij6303.sidecoach.voice.AndroidTtsVoiceOutput
import com.jij6303.sidecoach.voice.AudioFocusController
import com.jij6303.sidecoach.voice.ClipVoiceOutput
import com.jij6303.sidecoach.voice.NoAudioFocus
import com.jij6303.sidecoach.voice.VoiceCoach
import com.jij6303.sidecoach.voice.VoiceOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
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

    /** MVP는 시연용 가짜 경로 (NaviHelper 실연동은 2차). */
    lateinit var naviEventSource: NaviEventSource
        private set

    lateinit var uxRestrictionSource: UxRestrictionSource
        private set

    /** 화면 모드. 화면이 구독하는 동안만 규제를 구독한다. 첫 값이 오기 전에는 안전하게 주행 중으로 둔다. */
    lateinit var uiMode: StateFlow<UiMode>
        private set

    val fakeUx: FakeUxRestrictionSource by lazy { FakeUxRestrictionSource() }

    lateinit var voiceOutput: VoiceOutput
        private set
    lateinit var voiceCoach: VoiceCoach
        private set

    /**
     * 앱이 직접 오디오 포커스를 잡을지. TTS SDK가 내부에서 포커스를 잡는 것으로 확인되면 false로 바꾼다
     * (dev-architecture 6.2 ❓, 중복 요청 방지).
     */
    var requestAudioFocus: Boolean = true

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
        uxRestrictionSource = if (useFake) fakeUx else AaosUxRestrictionSource(context)
        uiMode = UxModeController(
            ux = uxRestrictionSource.state,
            gear = drivingStateRepository.snapshot.map { it.gear },
        ).mode.stateIn(appScope, SharingStarted.WhileSubscribed(5_000), UiMode.DRIVING_VOICE)
        // 메시지 시각과 같은 시계: 실제 소스는 elapsedRealtime, Fake는 currentTimeMillis
        val clock: () -> Long = if (useFake) System::currentTimeMillis else SystemClock::elapsedRealtime
        naviEventSource = FakeRouteNaviEventSource(drivingStateRepository.updates, clock)
        coachingPipeline = CoachingPipeline(
            updates = drivingStateRepository.updates,
            detectors = listOf(BlinkerOmissionDetector(), HardBrakeDetector()),
            arbiter = CoachingArbiter(),
            recorder = TripRecorder(),
            naviEvents = naviEventSource.events,
        )
        // 중재자가 고른 메시지를 로그로도 남긴다 (logcat 태그: Coach)
        appScope.launch {
            coachingPipeline.messages.collect { Log.i("Coach", "${it.type}: ${it.text}") }
        }
        // 정해진 문구는 녹음 클립, 그 외는 Android TTS (Pleos 에뮬레이터에 표준 TTS 엔진 없음)
        voiceOutput = ClipVoiceOutput(
            context = context,
            clips = mapOf(
                MessageCatalog.blinkerOmission() to R.raw.coach_blinker_omission,
                MessageCatalog.hardBrake() to R.raw.coach_hard_brake,
                MessageCatalog.praise() to R.raw.coach_praise,
                MessageCatalog.preview(SituationType.ROUNDABOUT) to R.raw.preview_roundabout,
                MessageCatalog.preview(SituationType.SCHOOL_ZONE) to R.raw.preview_school_zone,
                MessageCatalog.preview(SituationType.UNPROTECTED_LEFT) to R.raw.preview_unprotected_left,
                MessageCatalog.preview(SituationType.MERGE) to R.raw.preview_merge,
                MessageCatalog.report(Grade.GREAT) to R.raw.report_great,
                MessageCatalog.report(Grade.GOOD) to R.raw.report_good,
                MessageCatalog.report(Grade.PRACTICE) to R.raw.report_practice,
            ),
            fallback = AndroidTtsVoiceOutput(context),
        )
        voiceCoach = VoiceCoach(
            output = voiceOutput,
            focus = if (requestAudioFocus) AudioFocusController(context) else NoAudioFocus,
            clock = clock,
            log = { Log.i("Voice", it) },
        )
    }

    val isCoaching: Boolean get() = coachingPipeline.isRunning

    /** 신호 구독과 코칭 판정을 함께 시작한다. CoachingService가 호출한다. */
    fun startCoaching() {
        drivingStateRepository.start(appScope)
        coachingPipeline.start(appScope)
        voiceCoach.start(appScope, coachingPipeline.messages)
    }

    fun stopCoaching() {
        voiceCoach.stop()
        coachingPipeline.stop()
        drivingStateRepository.stop()
    }
}

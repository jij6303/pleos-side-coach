package com.jij6303.sidecoach.di

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.jij6303.sidecoach.R
import com.jij6303.sidecoach.data.navi.FakeRouteNaviEventSource
import com.jij6303.sidecoach.data.navi.NaviEventSource
import com.jij6303.sidecoach.data.store.ParkingSettings
import com.jij6303.sidecoach.data.store.SessionStore
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
import com.jij6303.sidecoach.domain.parking.ParkingCoachController
import com.jij6303.sidecoach.domain.parking.ParkingPhrases
import com.jij6303.sidecoach.domain.report.Grade
import com.jij6303.sidecoach.domain.situation.SituationType
import com.jij6303.sidecoach.domain.tips.CarTip
import com.jij6303.sidecoach.domain.tips.CarTipSpeaker
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
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

    /** 시연용 경로. 위치를 저장·복원하려고 구체 타입으로도 들고 있는다. */
    private lateinit var fakeRoute: FakeRouteNaviEventSource
    private lateinit var sessionStore: SessionStore

    /** 메시지·스냅샷과 같은 시계: 실제 소스는 elapsedRealtime, Fake는 currentTimeMillis. */
    private lateinit var clock: () -> Long

    lateinit var uxRestrictionSource: UxRestrictionSource
        private set

    /** 화면 모드. 화면이 구독하는 동안만 규제를 구독한다. 첫 값이 오기 전에는 안전하게 주행 중으로 둔다. */
    lateinit var uiMode: StateFlow<UiMode>
        private set

    /** [uiMode]의 원본(cold). 구독자가 없어도 지금 값을 바로 계산해야 할 때(Gleo 명령) 쓴다. */
    lateinit var uiModeSource: Flow<UiMode>
        private set

    val fakeUx: FakeUxRestrictionSource by lazy { FakeUxRestrictionSource() }

    lateinit var tipSpeaker: CarTipSpeaker
        private set
    lateinit var parking: ParkingCoachController
        private set
    lateinit var parkingSettings: ParkingSettings
        private set

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
        uiModeSource = UxModeController(
            ux = uxRestrictionSource.state,
            gear = drivingStateRepository.snapshot.map { it.gear },
        ).mode
        uiMode = uiModeSource.stateIn(appScope, SharingStarted.WhileSubscribed(5_000), UiMode.DRIVING_VOICE)
        clock = if (useFake) System::currentTimeMillis else SystemClock::elapsedRealtime
        fakeRoute = FakeRouteNaviEventSource(drivingStateRepository.updates, clock)
        naviEventSource = fakeRoute
        sessionStore = SessionStore(context)
        tipSpeaker = CarTipSpeaker(clock)
        parkingSettings = ParkingSettings(context)
        parking = ParkingCoachController(
            snapshots = drivingStateRepository.updates,
            clock = clock,
            initialType = parkingSettings.load(),
            log = { Log.i("Parking", it) },
        )
        coachingPipeline = CoachingPipeline(
            updates = drivingStateRepository.updates,
            detectors = listOf(BlinkerOmissionDetector(), HardBrakeDetector()),
            arbiter = CoachingArbiter(),
            recorder = TripRecorder(),
            naviEvents = naviEventSource.events,
            // 프로세스가 다시 만들어져도 이어가도록 주기적으로 저장 (dev-architecture 9장, #31)
            stateSink = { sessionStore.save(it.copy(route = fakeRoute.exportState())) },
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
                CarTip.WINDSHIELD_FOG.spoken to R.raw.tip_windshield_fog,
                CarTip.REAR_WINDOW_FOG.spoken to R.raw.tip_rear_window_fog,
                CarTip.RECIRCULATION.spoken to R.raw.tip_recirculation,
                CarTip.FRESH_AIR.spoken to R.raw.tip_fresh_air,
                CarTip.AIR_CONDITIONER.spoken to R.raw.tip_air_conditioner,
                CarTip.DRIVE_MODE.spoken to R.raw.tip_drive_mode,
                CarTip.REGEN_BRAKING.spoken to R.raw.tip_regen_braking,
                CarTip.PEDAL_MODE.spoken to R.raw.tip_pedal_mode,
                CarTip.CHILD_LOCK.spoken to R.raw.tip_child_lock,
                MessageCatalog.report(Grade.GREAT) to R.raw.report_great,
                MessageCatalog.report(Grade.GOOD) to R.raw.report_good,
                MessageCatalog.report(Grade.PRACTICE) to R.raw.report_practice,
                ParkingPhrases.REVERSE_START to R.raw.parking_reverse_start,
                ParkingPhrases.PARALLEL_START to R.raw.parking_parallel_start,
                ParkingPhrases.FULL_LOCK to R.raw.parking_full_lock,
                ParkingPhrases.FULL_LOCK_OPPOSITE to R.raw.parking_full_lock_opposite,
                ParkingPhrases.CENTER to R.raw.parking_center,
                ParkingPhrases.REVERSE_UNTIL_ALIGNED to R.raw.parking_reverse_until_aligned,
                ParkingPhrases.REVERSE_FINISH to R.raw.parking_reverse_finish,
                ParkingPhrases.PARALLEL_UNTIL_45 to R.raw.parking_parallel_until_45,
                ParkingPhrases.PARALLEL_UNTIL_PASSED to R.raw.parking_parallel_until_passed,
                ParkingPhrases.PARALLEL_UNTIL_ALIGNED to R.raw.parking_parallel_until_aligned,
                ParkingPhrases.PARALLEL_FINISH to R.raw.parking_parallel_finish,
                ParkingPhrases.NOT_ENOUGH_LOCK to R.raw.parking_not_enough_lock,
                ParkingPhrases.NOT_CENTERED to R.raw.parking_not_centered,
                ParkingPhrases.WRONG_DIRECTION to R.raw.parking_wrong_direction,
                ParkingPhrases.TOO_FAST to R.raw.parking_too_fast,
                ParkingPhrases.DONE to R.raw.parking_done,
                ParkingPhrases.CANCELLED to R.raw.parking_cancelled,
                ParkingPhrases.RETRY to R.raw.parking_retry,
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
        restoreSession()
        drivingStateRepository.start(appScope)
        coachingPipeline.start(appScope)
        parking.start(appScope)
        voiceCoach.start(appScope, merge(coachingPipeline.messages, parking.messages, tipSpeaker.messages))
    }

    /**
     * 저장된 주행이 있으면 이어간다. 재부팅했거나 30분 넘게 지났으면 버리고 새 주행으로 시작한다.
     */
    private fun restoreSession() {
        if (coachingPipeline.isRunning) return
        val saved = sessionStore.load() ?: return
        val now = clock()
        if (!saved.isUsable(now)) {
            Log.i("SessionStore", "discard: savedAt=${saved.savedAtMs} now=$now")
            sessionStore.clear()
            return
        }
        coachingPipeline.restore(saved, now)
        fakeRoute.restore(saved.route)
        Log.i("SessionStore", "restored: trip=${saved.trip != null} stats=${saved.recorder.stats} report=${saved.pendingReport != null}")
    }

    fun stopCoaching() {
        parking.stop()
        voiceCoach.stop()
        coachingPipeline.stop()
        drivingStateRepository.stop()
    }
}

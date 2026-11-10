package com.jij6303.sidecoach.di

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.jij6303.sidecoach.R
import com.jij6303.sidecoach.data.vehicle.AaosPropertyDataSource
import com.jij6303.sidecoach.data.vehicle.CompositeVehicleDataSource
import com.jij6303.sidecoach.data.vehicle.FakeVehicleDataSource
import com.jij6303.sidecoach.data.vehicle.PleosVehicleDataSource
import com.jij6303.sidecoach.data.vehicle.VehicleDataSource
import com.jij6303.sidecoach.domain.DrivingStateRepository
import com.jij6303.sidecoach.domain.coach.CoachingArbiter
import com.jij6303.sidecoach.domain.coach.CoachingPipeline
import com.jij6303.sidecoach.domain.coach.MessageCatalog
import com.jij6303.sidecoach.domain.detect.BlinkerOmissionDetector
import com.jij6303.sidecoach.domain.detect.HardBrakeDetector
import com.jij6303.sidecoach.domain.trip.TripRecorder
import com.jij6303.sidecoach.voice.AndroidTtsVoiceOutput
import com.jij6303.sidecoach.voice.AudioFocusController
import com.jij6303.sidecoach.voice.ClipVoiceOutput
import com.jij6303.sidecoach.voice.NoAudioFocus
import com.jij6303.sidecoach.voice.VoiceCoach
import com.jij6303.sidecoach.voice.VoiceOutput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
        coachingPipeline = CoachingPipeline(
            updates = drivingStateRepository.updates,
            detectors = listOf(BlinkerOmissionDetector(), HardBrakeDetector()),
            arbiter = CoachingArbiter(),
            recorder = TripRecorder(),
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
            ),
            fallback = AndroidTtsVoiceOutput(context),
        )
        voiceCoach = VoiceCoach(
            output = voiceOutput,
            focus = if (requestAudioFocus) AudioFocusController(context) else NoAudioFocus,
            // 메시지 시각과 같은 시계: 실제 소스는 elapsedRealtime, Fake는 currentTimeMillis
            clock = if (useFake) System::currentTimeMillis else SystemClock::elapsedRealtime,
            log = { Log.i("Voice", it) },
        )
    }

    /** 신호 구독과 코칭 판정을 함께 시작한다. */
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

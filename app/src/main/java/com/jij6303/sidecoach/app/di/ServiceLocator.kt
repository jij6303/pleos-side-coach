package com.jij6303.sidecoach.app.di

import android.content.Context
import com.jij6303.sidecoach.data.navi.NaviEventSource
import com.jij6303.sidecoach.feature.coach.CoachingPipeline
import com.jij6303.sidecoach.feature.coach.lane.LaneInput
import com.jij6303.sidecoach.feature.parking.ParkingCoachController
import com.jij6303.sidecoach.feature.parking.ParkingSettings
import com.jij6303.sidecoach.feature.tips.CarTipSpeaker
import com.jij6303.sidecoach.state.DrivingStateRepository
import com.jij6303.sidecoach.state.UiMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.merge

/**
 * 수동 DI의 입구. 계층별 모듈을 만들고 화면·서비스·Gleo가 쓰는 객체를 내놓는다.
 * - [DataModule]: SDK 소스, 스냅샷, 화면 모드, 내비
 * - [CoachModule]: 코칭 파이프라인, 차로 입력, 세션 저장·복원
 * - [ParkingModule]: 주차 코치
 * - [VoiceModule]: 음성 출력
 */
object ServiceLocator {

    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    /**
     * 앱이 직접 오디오 포커스를 잡을지. TTS SDK가 내부에서 포커스를 잡는 것으로 확인되면 false로 바꾼다
     * (dev-architecture 6.2 ❓, 중복 요청 방지).
     */
    var requestAudioFocus: Boolean = true

    private lateinit var data: DataModule
    private lateinit var coach: CoachModule
    private lateinit var parkingModule: ParkingModule
    private lateinit var voice: VoiceModule

    lateinit var tipSpeaker: CarTipSpeaker
        private set

    /** [useFake]면 차량 신호 대신 Fake 소스를 쓴다 (데모·UI 확인용). */
    fun init(context: Context, useFake: Boolean = false) {
        data = DataModule(context, useFake, appScope)
        coach = CoachModule(context, data, appScope)
        parkingModule = ParkingModule(context, data)
        voice = VoiceModule(context, data.clock, requestAudioFocus)
        tipSpeaker = CarTipSpeaker(data.clock)
    }

    val useFakeVehicle: Boolean get() = data.useFake
    val drivingStateRepository: DrivingStateRepository get() = data.drivingStateRepository
    val naviEventSource: NaviEventSource get() = data.naviEventSource
    val uiMode: StateFlow<UiMode> get() = data.uiMode
    val uiModeSource: Flow<UiMode> get() = data.uiModeSource

    val coachingPipeline: CoachingPipeline get() = coach.pipeline
    val laneInput: LaneInput get() = coach.laneInput

    val parking: ParkingCoachController get() = parkingModule.controller
    val parkingSettings: ParkingSettings get() = parkingModule.settings

    val isCoaching: Boolean get() = coach.pipeline.isRunning

    /** 신호 구독과 코칭 판정을 함께 시작한다. CoachingService가 호출한다. */
    fun startCoaching() {
        coach.restoreSession()
        data.drivingStateRepository.start(appScope)
        coach.pipeline.start(appScope)
        parking.start(appScope)
        voice.coach.start(appScope, merge(coach.pipeline.messages, parking.messages, tipSpeaker.messages, laneInput.messages))
    }

    fun stopCoaching() {
        parking.stop()
        voice.coach.stop()
        coach.pipeline.stop()
        data.drivingStateRepository.stop()
    }
}

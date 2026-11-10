package com.jij6303.sidecoach.domain.tutorial

import com.jij6303.sidecoach.data.vehicle.SignalKind
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.domain.DrivingSnapshot

/**
 * 내 차 익히기 미션 (dev-architecture 4.6). 새 권한 없이 이미 받는 신호만 쓴다.
 * 와이퍼는 CAR_WINDOWS 권한 승인 여부가 확정되면 추가한다.
 */
enum class TutorialMission(
    val title: String,
    /** 화면·음성 지시. 녹음 클립과 같은 문구. */
    val instruction: String,
    /** 이 신호를 받을 수 없으면 미션을 숨긴다. */
    val signal: SignalKind,
) {
    LEFT_SIGNAL("왼쪽 깜빡이", "왼쪽 깜빡이를 켜보세요. 레버를 아래로 내리면 돼요.", SignalKind.TURN_SIGNAL),
    RIGHT_SIGNAL("오른쪽 깜빡이", "오른쪽 깜빡이를 켜보세요. 레버를 위로 올리면 돼요.", SignalKind.TURN_SIGNAL),
    BRAKE("브레이크", "브레이크 페달을 밟아보세요. 오른발로 왼쪽 페달이에요.", SignalKind.BRAKE),
    PARKING_BRAKE("주차 브레이크", "주차 브레이크를 채워보세요. 이미 채워져 있으면 풀었다가 다시 채워요.", SignalKind.PARKING_BRAKE),
    ;

    /** 완료 조건. 값을 아직 모르면 null. */
    fun isMet(s: DrivingSnapshot): Boolean? = when (this) {
        LEFT_SIGNAL -> s.turnSignal?.let { it == TurnSignalState.LEFT }
        RIGHT_SIGNAL -> s.turnSignal?.let { it == TurnSignalState.RIGHT }
        BRAKE -> s.brakePressed
        PARKING_BRAKE -> s.parkingBrakeOn
    }
}

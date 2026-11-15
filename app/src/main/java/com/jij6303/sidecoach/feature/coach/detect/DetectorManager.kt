package com.jij6303.sidecoach.feature.coach.detect

import com.jij6303.sidecoach.state.DrivingSnapshot

/**
 * 디텍터 목록을 들고 스냅샷을 차례로 전달하고, 이번 스냅샷에서 나온 판정 결과를 모아 돌려준다.
 * 결과 순서는 [detectors] 순서와 같다.
 */
class DetectorManager(private val detectors: List<Detector>) {

    fun onSnapshot(s: DrivingSnapshot): List<DetectionEvent> = detectors.mapNotNull { it.onSnapshot(s) }

    fun reset() = detectors.forEach { it.reset() }
}

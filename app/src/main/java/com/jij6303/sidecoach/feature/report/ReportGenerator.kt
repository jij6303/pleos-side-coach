package com.jij6303.sidecoach.feature.report

import com.jij6303.sidecoach.feature.coach.CoachMessage
import com.jij6303.sidecoach.feature.coach.CoachType
import com.jij6303.sidecoach.feature.coach.MessageCatalog
import com.jij6303.sidecoach.feature.report.trip.TripMetrics
import com.jij6303.sidecoach.feature.report.trip.TripStats
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

enum class Grade(val title: String) {
    GREAT("아주 잘했어요"),
    GOOD("잘했어요"),
    PRACTICE("조금 더 연습해요"),
}

/** 다음 주행에서 집중할 한 가지. */
enum class NextTask(val title: String, val detail: String) {
    STOP_BEFORE_SHIFT(
        "기어는 완전히 멈춘 뒤에",
        "브레이크로 차를 완전히 세운 다음 기어를 바꿔요. D와 R 사이는 꼭이요.",
    ),
    BLINKER(
        "차선 바꾸기 전에 깜빡이 먼저",
        "핸들을 돌리기 3초 전에 깜빡이를 켜는 연습을 해봐요.",
    ),
    SLOW_TURN(
        "돌기 전에 속도 줄이기",
        "회전하기 전에 미리 브레이크로 속도를 줄이고, 도는 동안에는 천천히 일정하게 가요.",
    ),
    SMOOTH_BRAKE(
        "브레이크는 일찍, 나눠서",
        "멈출 곳이 보이면 미리 발을 올리고 두세 번에 나눠 밟아봐요.",
    ),
    BLINKER_OFF(
        "돌고 나면 깜빡이 끄기",
        "차선을 바꾸거나 돌고 나면 계기판에서 깜빡이가 꺼졌는지 확인해요.",
    ),
    TRY_LANE_CHANGE(
        "차선 변경 연습",
        "다음엔 차선을 바꿀 일이 있는 길에서 깜빡이까지 같이 연습해요.",
    ),
    KEEP_GOING(
        "지금처럼!",
        "기본기가 좋아요. 다음엔 조금 더 긴 코스에 도전해봐요.",
    ),
}

data class TripReport(
    val metrics: TripMetrics,
    val stats: TripStats,
    val score: Int,
    val grade: Grade,
    val nextTask: NextTask,
) {
    val missedBlinkers: Int get() = stats.laneChanges - stats.signalledLaneChanges

    /** 성적표가 뜰 때 말할 한 문장 (녹음 클립과 같은 문구). */
    val announcement: String get() = MessageCatalog.report(grade)
}

/**
 * 주행 통계 → 성적표 (concept ④).
 *
 * 끝난 주행의 성적표를 [lastReport]로 보관하고(화면이 본다), 요약 한 문장을 [announcements]로 내보낸다.
 * 요약 음성은 주행이 끝나 다른 코칭과 겹치지 않으므로 중재자를 거치지 않고 VoiceCoach로 바로 간다.
 *
 * 💭 점수: 100점에서 깜빡이 누락·급제동·회전 중 과속·움직이는 중 기어 변경은 1회당 [penalty]점,
 * 깜빡이 끄기 잊음은 위험도가 낮아 1회당 [minorPenalty]점씩 뺀다 (#53).
 */
class ReportGenerator {

    private val _lastReport = MutableStateFlow<TripReport?>(null)

    /** 마지막으로 끝난 주행의 성적표. 새 주행이 시작되거나 닫으면 null. */
    val lastReport: StateFlow<TripReport?> = _lastReport.asStateFlow()

    private val _announcements = MutableSharedFlow<CoachMessage>(
        extraBufferCapacity = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )

    /** 성적표 요약 음성. */
    val announcements: SharedFlow<CoachMessage> = _announcements.asSharedFlow()

    /** 끝난 주행의 성적표를 만들어 보관하고 요약을 음성으로 보낸다. */
    fun publish(metrics: TripMetrics, stats: TripStats, nowMs: Long): TripReport {
        val report = generate(metrics, stats)
        _lastReport.value = report
        _announcements.tryEmit(CoachMessage(CoachType.REPORT, report.announcement, nowMs))
        return report
    }

    /** 성적표를 닫는다 (사용자가 닫거나, 새 주행이 시작되거나, 잠깐 쉬었다 다시 출발). */
    fun dismiss() {
        _lastReport.value = null
    }

    /** 저장용: 아직 안 본 성적표. */
    fun exportState(): TripReport? = _lastReport.value

    fun restore(pending: TripReport?) {
        _lastReport.value = pending
    }

    companion object {
        const val penalty = 10
        const val minorPenalty = 5

        /** 점수·등급·다음 과제 계산 (순수 함수). */
        fun generate(metrics: TripMetrics, stats: TripStats): TripReport {
            val missed = stats.laneChanges - stats.signalledLaneChanges
            val major = missed + stats.hardBrakes + stats.turnTooFast + stats.gearShiftsMoving
            val score = (100 - penalty * major - minorPenalty * stats.blinkerLeftOn).coerceIn(0, 100)
            val grade = when {
                score >= 90 -> Grade.GREAT
                score >= 70 -> Grade.GOOD
                else -> Grade.PRACTICE
            }
            // 위험한 것부터: 기어 > 깜빡이 누락 > 회전 과속 > 급제동 > 깜빡이 끄기
            val task = when {
                stats.gearShiftsMoving > 0 -> NextTask.STOP_BEFORE_SHIFT
                missed > 0 -> NextTask.BLINKER
                stats.turnTooFast > 0 -> NextTask.SLOW_TURN
                stats.hardBrakes > 0 -> NextTask.SMOOTH_BRAKE
                stats.blinkerLeftOn > 0 -> NextTask.BLINKER_OFF
                stats.laneChanges == 0 -> NextTask.TRY_LANE_CHANGE
                else -> NextTask.KEEP_GOING
            }
            return TripReport(metrics, stats, score, grade, task)
        }
    }
}

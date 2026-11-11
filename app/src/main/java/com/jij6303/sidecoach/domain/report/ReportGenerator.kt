package com.jij6303.sidecoach.domain.report

import com.jij6303.sidecoach.domain.coach.MessageCatalog
import com.jij6303.sidecoach.domain.trip.TripMetrics
import com.jij6303.sidecoach.domain.trip.TripStats

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
 * 주행 통계 → 성적표 (concept ⑤).
 * 💭 점수: 100점에서 깜빡이 누락·급제동·회전 중 과속·움직이는 중 기어 변경은 1회당 [penalty]점,
 * 깜빡이 끄기 잊음은 위험도가 낮아 1회당 [minorPenalty]점씩 뺀다 (#53).
 */
object ReportGenerator {
    const val penalty = 10
    const val minorPenalty = 5

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

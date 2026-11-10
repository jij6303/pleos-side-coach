package com.jij6303.sidecoach.domain.coach

import com.jij6303.sidecoach.domain.report.Grade
import com.jij6303.sidecoach.domain.situation.SituationType

/** 코칭 유형. [priority]가 클수록 먼저 말한다 (dev-architecture 4.5). */
enum class CoachType(val priority: Int, val high: Boolean) {
    HARD_BRAKE(priority = 50, high = true),
    PREVIEW(priority = 40, high = true),
    SPEEDING(priority = 30, high = false),
    BLINKER_OMISSION(priority = 20, high = false),
    PRAISE(priority = 10, high = false),

    /** 주행 종료 후 성적표 요약. 중재자를 거치지 않는다. */
    REPORT(priority = 0, high = false),

    /** 차량 기능 도움말 음성. 중재자를 거치지 않는다. */
    TIP(priority = 0, high = false),
}

data class CoachMessage(
    val type: CoachType,
    val text: String,
    /** 생성 시각. 대기열 TTL 계산에 쓴다. */
    val createdAtMs: Long,
    /** 같은 키의 메시지를 연달아 말하지 않기 위한 키. */
    val dedupeKey: String = type.name,
)

/** 레벨·상황별 한국어 문구. 한 문장, 15어절 이내. */
object MessageCatalog {
    fun blinkerOmission(): String = "차선 바꿀 땐 깜빡이 먼저 켜요!"
    fun hardBrake(): String = "조금 더 일찍, 부드럽게 밟아볼까요?"
    fun praise(): String = "깜빡이 아주 잘 켜고 있어요. 좋아요!"

    /** 상황 예고. 거리는 말하지 않는다(녹음 클립으로 재생하기 위해 문구를 고정). */
    fun preview(type: SituationType): String = when (type) {
        SituationType.ROUNDABOUT -> "곧 회전교차로예요. 돌고 있는 차가 우선이니 왼쪽을 보고 천천히 들어가요."
        SituationType.SCHOOL_ZONE -> "곧 어린이보호구역이에요. 시속 30 이하로, 횡단보도 앞에서는 일단 멈춰요."
        SituationType.UNPROTECTED_LEFT -> "곧 비보호좌회전이에요. 맞은편 직진 차가 지나간 뒤에 돌아요."
        SituationType.MERGE -> "곧 합류해요. 깜빡이 켜고 가속 차로에서 속도를 맞춰요."
    }

    /** 성적표가 뜰 때 한 문장. */
    fun report(grade: Grade): String = when (grade) {
        Grade.GREAT -> "오늘 운전 아주 잘했어요. 성적표를 확인해 보세요."
        Grade.GOOD -> "오늘 운전 잘했어요. 성적표에서 연습할 점을 확인해 보세요."
        Grade.PRACTICE -> "수고했어요. 성적표에서 다음 연습 과제를 확인해 보세요."
    }
}

package com.jij6303.sidecoach.domain.coach

/** 코칭 유형. [priority]가 클수록 먼저 말한다 (dev-architecture 4.5). */
enum class CoachType(val priority: Int, val high: Boolean) {
    HARD_BRAKE(priority = 50, high = true),
    PREVIEW(priority = 40, high = true),
    SPEEDING(priority = 30, high = false),
    BLINKER_OMISSION(priority = 20, high = false),
    PRAISE(priority = 10, high = false),
}

enum class ExperienceLevel { BEGINNER, INTERMEDIATE }

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
}

package com.jij6303.sidecoach.feature.coach

import com.jij6303.sidecoach.feature.report.Grade
import com.jij6303.sidecoach.data.navi.SituationType

/** 코칭 유형. [priority]가 클수록 먼저 말한다. */
enum class CoachType(val priority: Int, val high: Boolean) {
    HARD_BRAKE(priority = 50, high = true),

    /** 움직이는 중 기어 변경 (#49). */
    GEAR_SHIFT_MOVING(priority = 45, high = true),
    PREVIEW(priority = 40, high = true),
    /** 회전 중 과속 (#49). 회전은 금방 끝나므로 짧은 간격으로도 말한다. */
    TURN_SPEED(priority = 35, high = true),
    SPEEDING(priority = 30, high = false),
    BLINKER_OMISSION(priority = 20, high = false),

    /** 깜빡이 끄기 잊음 (#49). */
    BLINKER_LEFT_ON(priority = 15, high = false),
    PRAISE(priority = 10, high = false),

    /** 주행 종료 후 성적표 요약. 중재자를 거치지 않는다. */
    REPORT(priority = 0, high = false),

    /** 주차 절차 코치(#37). 주차 중에는 다른 코칭이 거의 없어 중재자를 거치지 않는다. */
    PARKING(priority = 0, high = false),

    /** 차량 기능 도움말 음성. 중재자를 거치지 않는다. */
    TIP(priority = 0, high = false),

    /** 운전자가 알려준 차로 확인 음성 (#69). 중재자를 거치지 않는다. */
    LANE(priority = 0, high = false),
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
    fun blinkerLeftOn(): String = "깜빡이가 아직 켜져 있어요. 꺼 주세요."
    fun turnSpeed(): String = "돌 때는 속도를 줄여요. 천천히요."
    fun gearShiftMoving(): String = "기어는 완전히 멈춘 뒤에 바꿔요."

    /**
     * 상황 예고. 거리는 말하지 않는다(녹음 클립으로 재생하기 위해 문구를 고정).
     * [lane]은 운전자가 알려준 지금 차로(#69). 알면 단정해서 말하고, 모르거나 차로별 문구가 없으면 일반 안내.
     */
    fun preview(type: SituationType, lane: Int? = null): String = lane?.let { laneAware(type, it) } ?: when (type) {
        SituationType.ROUNDABOUT -> "곧 회전교차로예요. 돌고 있는 차가 우선이니 왼쪽을 보고 천천히 들어가요."
        SituationType.SCHOOL_ZONE -> "곧 어린이보호구역이에요. 시속 30 이하로, 횡단보도 앞에서는 일단 멈춰요."
        SituationType.UNPROTECTED_LEFT -> "곧 비보호좌회전이에요. 맞은편 직진 차가 지나간 뒤에 돌아요."
        SituationType.MERGE -> "곧 합류해요. 깜빡이 켜고 가속 차로에서 속도를 맞춰요."
        // 차로 사전 안내 (#65). 지금 몇 차로인지 모르므로 "어느 쪽 차로로" 옮길지만 말한다.
        // 회전 예고("곧 좌회전이에요")는 내비가 하므로 빼고 행동 코칭으로 시작한다 (#71)
        SituationType.LANE_LEFT_TURN -> "좌회전 준비해요. 깜빡이 먼저 켜고 왼쪽 끝 차로로 미리 옮겨요."
        SituationType.LANE_RIGHT_TURN -> "우회전 준비해요. 깜빡이 먼저 켜고 오른쪽 끝 차로로 미리 옮겨요."
        SituationType.LANE_UTURN -> "유턴 준비해요. 깜빡이 먼저 켜고 1차로로 미리 옮겨요."
        SituationType.LANE_KEEP_LEFT -> "왼쪽 길로 빠질 준비해요. 깜빡이 먼저 켜고 왼쪽 차로로 미리 옮겨요."
        SituationType.LANE_KEEP_RIGHT -> "오른쪽 길로 빠질 준비해요. 깜빡이 먼저 켜고 오른쪽 차로로 미리 옮겨요."
        SituationType.LANE_STRAIGHT -> "1차로는 좌회전 전용일 수 있어요. 직진하려면 2차로 이상에 있어요."
        // 연속 회전 (#67). 앞 회전 직전에, 돌 때 들어갈 차로를 먼저 말한다 (#71: 다음 회전 예고는 내비 몫)
        SituationType.CHAIN_LEFT_TURN -> "이번에 돌 때 왼쪽 차로로 들어가요. 바로 좌회전이 이어져요."
        SituationType.CHAIN_RIGHT_TURN -> "이번에 돌 때 오른쪽 차로로 들어가요. 바로 우회전이 이어져요."
        SituationType.CHAIN_UTURN -> "이번에 돌 때 1차로로 들어가요. 바로 유턴이 이어져요."
        SituationType.CHAIN_KEEP_LEFT -> "이번에 돌 때 왼쪽 차로로 들어가요. 바로 왼쪽 길로 빠져요."
        SituationType.CHAIN_KEEP_RIGHT -> "이번에 돌 때 오른쪽 차로로 들어가요. 바로 오른쪽 길로 빠져요."
    }

    /**
     * 지금 차로를 아는 경우의 차로 안내 (#69). 문구가 없으면 null.
     * 우회전·오른쪽 길은 전체 차로 수를 몰라 "몇 차로로" 옮길지 말할 수 없어 일반 안내를 쓴다.
     */
    private fun laneAware(type: SituationType, lane: Int): String? = when (type) {
        SituationType.LANE_LEFT_TURN -> toFirstLane("좌회전", lane)
        SituationType.LANE_UTURN -> toFirstLane("유턴", lane)
        SituationType.LANE_STRAIGHT -> when (lane) {
            1 -> "지금 1차로예요. 좌회전 전용일 수 있으니 직진하려면 2차로로 옮겨요."
            else -> "지금 차로 그대로 직진해요."
        }
        SituationType.LANE_KEEP_LEFT -> if (lane == 1) "지금 1차로예요. 왼쪽 길로 가려면 그대로 가요." else null
        else -> null
    }

    private fun toFirstLane(turn: String, lane: Int): String? = when (lane) {
        1 -> "지금 1차로예요. ${turn}하려면 그대로 가요."
        in 2..4 -> "지금 ${lane}차로예요. ${turn}하려면 깜빡이 켜고 1차로로 옮겨요."
        else -> null
    }

    /** 운전자가 알려준 차로를 기억했다는 확인 (#69). */
    fun laneSet(lane: Int): String = "${lane}차로로 기억할게요."

    /** 차로 안내 문구 전체 (클립 등록·테스트용). */
    fun laneAwarePhrases(): List<String> = buildList {
        for (type in listOf(SituationType.LANE_LEFT_TURN, SituationType.LANE_UTURN, SituationType.LANE_STRAIGHT, SituationType.LANE_KEEP_LEFT)) {
            for (lane in 1..4) laneAware(type, lane)?.let(::add)
        }
    }.distinct()

    /** 성적표가 뜰 때 한 문장. */
    fun report(grade: Grade): String = when (grade) {
        Grade.GREAT -> "오늘 운전 아주 잘했어요. 성적표를 확인해 보세요."
        Grade.GOOD -> "오늘 운전 잘했어요. 성적표에서 연습할 점을 확인해 보세요."
        Grade.PRACTICE -> "수고했어요. 성적표에서 다음 연습 과제를 확인해 보세요."
    }
}

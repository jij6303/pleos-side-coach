package com.jij6303.sidecoach.domain.parking

/**
 * 주차 절차 코치 (concept ④, #37). 카메라 없이 실제 연수 방식대로 단계와 기준점을 음성으로 안내하고,
 * 핸들 조작은 조향각으로 확인한다. 기준점은 운전자가 보고 판단해 멈추면(브레이크 + 정지) 다음 단계로 넘어간다.
 * R에서는 시스템 후방카메라 화면이 앱을 덮으므로(실측) 화면 버튼에 기대지 않는다.
 */
enum class ParkingType(val label: String) {
    REVERSE("후진 주차"),
    PARALLEL("평행 주차"),
    ;

    val steps: List<ParkingStep>
        get() = when (this) {
            REVERSE -> listOf(
                ParkingStep(StepKind.REFERENCE, "기준점까지 후진", ParkingPhrases.REVERSE_START),
                ParkingStep(StepKind.FULL_LOCK, "핸들 끝까지", ParkingPhrases.FULL_LOCK),
                ParkingStep(StepKind.REFERENCE, "나란해질 때까지 후진", ParkingPhrases.REVERSE_UNTIL_ALIGNED),
                ParkingStep(StepKind.CENTER, "핸들 가운데로", ParkingPhrases.CENTER),
                ParkingStep(StepKind.FINISH, "멈추고 P", ParkingPhrases.REVERSE_FINISH),
            )
            PARALLEL -> listOf(
                ParkingStep(StepKind.REFERENCE, "기준점까지 후진", ParkingPhrases.PARALLEL_START),
                ParkingStep(StepKind.FULL_LOCK, "핸들 끝까지", ParkingPhrases.FULL_LOCK),
                ParkingStep(StepKind.REFERENCE, "45도까지 후진", ParkingPhrases.PARALLEL_UNTIL_45),
                ParkingStep(StepKind.CENTER, "핸들 가운데로", ParkingPhrases.CENTER),
                ParkingStep(StepKind.REFERENCE, "앞차를 지날 때까지 후진", ParkingPhrases.PARALLEL_UNTIL_PASSED),
                ParkingStep(StepKind.FULL_LOCK_OPPOSITE, "반대쪽 끝까지", ParkingPhrases.FULL_LOCK_OPPOSITE),
                ParkingStep(StepKind.REFERENCE, "나란해질 때까지 후진", ParkingPhrases.PARALLEL_UNTIL_ALIGNED),
                ParkingStep(StepKind.CENTER, "핸들 가운데로", ParkingPhrases.CENTER),
                ParkingStep(StepKind.FINISH, "P로 마무리", ParkingPhrases.PARALLEL_FINISH),
            )
        }
}

enum class StepKind {
    /** 뒤로 가다가 기준점에서 멈추면 넘어간다. 앱이 보이면 "다음" 버튼으로도 넘긴다. */
    REFERENCE,

    /** 핸들을 한쪽 끝까지. 처음 꺾은 방향을 주차칸 쪽으로 기억한다. */
    FULL_LOCK,

    /** 핸들을 앞 단계와 반대쪽 끝까지. */
    FULL_LOCK_OPPOSITE,

    /** 핸들을 가운데로. */
    CENTER,

    /** 멈추고 P에 놓으면 끝. */
    FINISH,
}

data class ParkingStep(
    val kind: StepKind,
    /** 화면에 크게 보이는 짧은 제목. */
    val title: String,
    /** 단계에 들어갈 때 말하는 문구. 녹음 클립과 같은 문구. */
    val instruction: String,
)

/**
 * 주차 코치 문구. 녹음 클립(res/raw/parking_*.wav)과 같아야 한다 (scripts/generate_voice_clips.ps1).
 * 핸들 조작에 성공한 뒤의 문구는 "좋아요."로 시작한다.
 * 방향은 말하지 않는다(주차칸이 왼쪽·오른쪽 어느 쪽이든 같은 클립을 쓰기 위해).
 */
object ParkingPhrases {
    const val REVERSE_START = "후진 주차를 시작해요. 천천히 뒤로 가다가 뒷바퀴가 주차칸 첫 선에 오면 멈춰요."
    const val PARALLEL_START = "평행 주차를 시작해요. 천천히 뒤로 가다가 뒷바퀴가 앞차 뒤범퍼에 오면 멈춰요."
    const val FULL_LOCK = "핸들을 주차칸 쪽으로 끝까지 돌려요."
    const val FULL_LOCK_OPPOSITE = "핸들을 반대쪽으로 끝까지 돌려요."
    const val CENTER = "핸들을 가운데로 풀어요."
    const val REVERSE_UNTIL_ALIGNED = "좋아요. 천천히 뒤로 가다가 차가 주차칸과 나란해지면 멈춰요."
    const val REVERSE_FINISH = "좋아요. 똑바로 뒤로 가다가 칸 끝에서 멈추고 P에 놓아요."
    const val PARALLEL_UNTIL_45 = "좋아요. 천천히 뒤로 가다가 차가 45도쯤 꺾이면 멈춰요."
    const val PARALLEL_UNTIL_PASSED = "좋아요. 똑바로 뒤로 가다가 앞 범퍼가 앞차 뒤범퍼를 지나면 멈춰요."
    const val PARALLEL_UNTIL_ALIGNED = "좋아요. 천천히 뒤로 가다가 차가 나란해지면 멈춰요."
    const val PARALLEL_FINISH = "좋아요. 바퀴가 똑바로예요. P에 놓아 주차를 마쳐요."

    const val NOT_ENOUGH_LOCK = "아직 덜 돌렸어요. 끝까지 돌려요."
    const val NOT_CENTERED = "바퀴가 덜 풀렸어요. 조금 더 풀어요."
    const val WRONG_DIRECTION = "반대쪽이에요. 핸들을 반대로 돌려요."
    const val TOO_FAST = "천천히요. 브레이크를 살짝 밟아요."
    const val DONE = "주차 끝! 잘했어요."
    const val CANCELLED = "주차 코치를 마칠게요."
    const val RETRY = "앞으로 빼서 다시 해도 괜찮아요. R에 놓으면 처음부터 안내할게요."

    /** 녹음 클립 파일 이름(res/raw) → 문구. 클립 매핑과 생성 스크립트의 기준. */
    val clips: Map<String, String> = linkedMapOf(
        "parking_reverse_start" to REVERSE_START,
        "parking_parallel_start" to PARALLEL_START,
        "parking_full_lock" to FULL_LOCK,
        "parking_full_lock_opposite" to FULL_LOCK_OPPOSITE,
        "parking_center" to CENTER,
        "parking_reverse_until_aligned" to REVERSE_UNTIL_ALIGNED,
        "parking_reverse_finish" to REVERSE_FINISH,
        "parking_parallel_until_45" to PARALLEL_UNTIL_45,
        "parking_parallel_until_passed" to PARALLEL_UNTIL_PASSED,
        "parking_parallel_until_aligned" to PARALLEL_UNTIL_ALIGNED,
        "parking_parallel_finish" to PARALLEL_FINISH,
        "parking_not_enough_lock" to NOT_ENOUGH_LOCK,
        "parking_not_centered" to NOT_CENTERED,
        "parking_wrong_direction" to WRONG_DIRECTION,
        "parking_too_fast" to TOO_FAST,
        "parking_done" to DONE,
        "parking_cancelled" to CANCELLED,
        "parking_retry" to RETRY,
    )
}

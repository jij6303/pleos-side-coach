package com.jij6303.sidecoach.feature.parking

import com.jij6303.sidecoach.R

/** 주차 코치 문구 → 녹음 클립. 문구가 바뀌면 클립도 다시 만들어야 한다. */
object ParkingClips {
    val all: Map<String, Int> = mapOf(
        ParkingPhrases.REVERSE_START to R.raw.parking_reverse_start,
        ParkingPhrases.PARALLEL_START to R.raw.parking_parallel_start,
        ParkingPhrases.FULL_LOCK to R.raw.parking_full_lock,
        ParkingPhrases.FULL_LOCK_OPPOSITE to R.raw.parking_full_lock_opposite,
        ParkingPhrases.CENTER to R.raw.parking_center,
        ParkingPhrases.REVERSE_UNTIL_ALIGNED to R.raw.parking_reverse_until_aligned,
        ParkingPhrases.REVERSE_FINISH to R.raw.parking_reverse_finish,
        ParkingPhrases.PARALLEL_UNTIL_45 to R.raw.parking_parallel_until_45,
        ParkingPhrases.PARALLEL_UNTIL_PASSED to R.raw.parking_parallel_until_passed,
        ParkingPhrases.PARALLEL_UNTIL_ALIGNED to R.raw.parking_parallel_until_aligned,
        ParkingPhrases.PARALLEL_FINISH to R.raw.parking_parallel_finish,
        ParkingPhrases.NOT_ENOUGH_LOCK to R.raw.parking_not_enough_lock,
        ParkingPhrases.NOT_CENTERED to R.raw.parking_not_centered,
        ParkingPhrases.WRONG_DIRECTION to R.raw.parking_wrong_direction,
        ParkingPhrases.TOO_FAST to R.raw.parking_too_fast,
        ParkingPhrases.DONE to R.raw.parking_done,
        ParkingPhrases.CANCELLED to R.raw.parking_cancelled,
        ParkingPhrases.RETRY to R.raw.parking_retry,
    )
}

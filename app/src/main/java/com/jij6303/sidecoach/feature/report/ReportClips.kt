package com.jij6303.sidecoach.feature.report

import com.jij6303.sidecoach.R
import com.jij6303.sidecoach.feature.coach.MessageCatalog

/** 성적표 요약 문구 → 녹음 클립. 문구가 바뀌면 클립도 다시 만들어야 한다. */
object ReportClips {
    val all: Map<String, Int> = mapOf(
        MessageCatalog.report(Grade.GREAT) to R.raw.report_great,
        MessageCatalog.report(Grade.GOOD) to R.raw.report_good,
        MessageCatalog.report(Grade.PRACTICE) to R.raw.report_practice,
    )
}

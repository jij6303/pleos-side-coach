package com.jij6303.sidecoach.feature.tips

import com.jij6303.sidecoach.R

/** 차량 기능 도움말 문구 → 녹음 클립. 문구가 바뀌면 클립도 다시 만들어야 한다. */
object TipClips {
    val all: Map<String, Int> = mapOf(
        CarTip.WINDSHIELD_FOG.spoken to R.raw.tip_windshield_fog,
        CarTip.REAR_WINDOW_FOG.spoken to R.raw.tip_rear_window_fog,
        CarTip.RECIRCULATION.spoken to R.raw.tip_recirculation,
        CarTip.FRESH_AIR.spoken to R.raw.tip_fresh_air,
        CarTip.AIR_CONDITIONER.spoken to R.raw.tip_air_conditioner,
        CarTip.DRIVE_MODE.spoken to R.raw.tip_drive_mode,
        CarTip.REGEN_BRAKING.spoken to R.raw.tip_regen_braking,
        CarTip.PEDAL_MODE.spoken to R.raw.tip_pedal_mode,
        CarTip.CHILD_LOCK.spoken to R.raw.tip_child_lock,
    )
}

package com.jij6303.sidecoach.ui.debug

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.SignalKind
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.domain.DrivingSnapshot
import com.jij6303.sidecoach.ui.theme.SideCoachTheme
import java.util.Locale

/**
 * 신호 디버그 화면. 차량 신호가 실제로 들어오는지 확인하는 용도(dev-architecture 11.3 확인 목록 5-1).
 * 애니메이션 없이 값만 표시한다.
 */
@Composable
fun SignalDebugScreen(
    snapshot: DrivingSnapshot,
    usingFake: Boolean,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = if (usingFake) "신호 확인 (Fake 소스)" else "신호 확인",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        HorizontalDivider()

        SignalRow("기어", snapshot.gear.label(), snapshot.unavailable[SignalKind.GEAR])
        SignalRow("차속", snapshot.speedKmh?.let { "%.1f km/h".format(Locale.US, it) }, snapshot.unavailable[SignalKind.SPEED])
        SignalRow("조향각", snapshot.steeringDeg?.let(::steeringLabel), snapshot.unavailable[SignalKind.STEERING])
        SignalRow("깜빡이", snapshot.turnSignal?.label(), snapshot.unavailable[SignalKind.TURN_SIGNAL])
        SignalRow("비상등", snapshot.hazardOn?.onOff(), snapshot.unavailable[SignalKind.HAZARD])
        SignalRow("브레이크", snapshot.brakePressed?.let { if (it) "밟음" else "뗌" }, snapshot.unavailable[SignalKind.BRAKE])
        SignalRow("주차 브레이크", snapshot.parkingBrakeOn?.onOff(), snapshot.unavailable[SignalKind.PARKING_BRAKE])
    }
}

@Composable
private fun SignalRow(label: String, value: String?, unavailableReason: String?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(text = label, style = MaterialTheme.typography.titleLarge)
            Text(
                text = value ?: "—",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
            )
        }
        if (unavailableReason != null) {
            Text(
                text = "수신 불가: $unavailableReason",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }
    }
}

private fun steeringLabel(deg: Float): String {
    val rounded = Math.round(deg)
    val dir = when {
        rounded > 0 -> "왼쪽"
        rounded < 0 -> "오른쪽"
        else -> "중앙"
    }
    return if (rounded == 0) "0° ($dir)" else "%+d° (%s)".format(Locale.US, rounded, dir)
}

private fun GearPosition.label(): String = when (this) {
    GearPosition.PARK -> "P"
    GearPosition.REVERSE -> "R"
    GearPosition.NEUTRAL -> "N"
    GearPosition.DRIVE -> "D"
    GearPosition.UNKNOWN -> "—"
}

private fun TurnSignalState.label(): String = when (this) {
    TurnSignalState.OFF -> "꺼짐"
    TurnSignalState.LEFT -> "왼쪽"
    TurnSignalState.RIGHT -> "오른쪽"
}

private fun Boolean.onOff(): String = if (this) "켜짐" else "꺼짐"

@Preview(showBackground = true, widthDp = 600, heightDp = 800)
@Composable
private fun SignalDebugScreenPreview() {
    SideCoachTheme {
        SignalDebugScreen(
            snapshot = DrivingSnapshot(
                speedKmh = 52.3f,
                gear = GearPosition.DRIVE,
                steeringDeg = 15f,
                turnSignal = TurnSignalState.LEFT,
                brakePressed = false,
                hazardOn = false,
                unavailable = mapOf(SignalKind.PARKING_BRAKE to "registerCallback=false"),
            ),
            usingFake = false,
        )
    }
}

package com.jij6303.sidecoach.feature.parking.ui

import com.jij6303.sidecoach.feature.coach.ui.DrivingScreen
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.state.DrivingSnapshot
import com.jij6303.sidecoach.feature.coach.CoachMessage
import com.jij6303.sidecoach.feature.parking.ParkingState
import com.jij6303.sidecoach.feature.parking.ParkingType
import com.jij6303.sidecoach.feature.parking.ParkingUi
import com.jij6303.sidecoach.feature.parking.StepKind
import com.jij6303.sidecoach.ui.components.Chip
import com.jij6303.sidecoach.ui.theme.SideCoachTheme
import kotlin.math.abs
import kotlin.math.roundToInt

/** 게이지 양 끝 각도. 실측 범위(-470 ~ 500)가 다 들어가도록. */
private const val GAUGE_MAX_DEG = 500f

/** 게이지에 표시하는 목표 구간 (ParkingCoachEngine.Config 기본값과 같게). */
private const val FULL_LOCK_DEG = 420f
private const val CENTER_DEG = 20f

/**
 * 주차 코치 카드 (#37). 단계 제목, 지시 문구, 조향 게이지, "다음" 버튼.
 * 주행 규제 중 화면이라 애니메이션·스크롤 없이 큰 글자와 큰 버튼 하나만 둔다.
 */
@Composable
fun ParkingPanel(
    parking: ParkingUi,
    snapshot: DrivingSnapshot,
    /** 최근 주차 코치 음성. 덜 돌림 같은 안내를 화면에도 보여준다. */
    message: CoachMessage?,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tone = SideCoachTheme.signal.neutral
    val st = parking.state
    Surface(modifier = modifier, shape = RoundedCornerShape(32.dp), color = tone.container) {
        Column(
            modifier = Modifier.padding(40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
        ) {
            Chip(text = "${st.type.label} ${st.stepIndex + 1}/${st.steps.size}", tone = tone)
            Text(st.step.title, style = MaterialTheme.typography.displayMedium, color = tone.onContainer)
            Text(
                text = message?.text ?: st.step.instruction,
                style = MaterialTheme.typography.headlineMedium,
                color = tone.onContainer,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            SteeringGauge(snapshot.steeringDeg, st.step.kind)
            if (parking.canTapNext) {
                Button(
                    onClick = onNext,
                    modifier = Modifier.fillMaxWidth().height(112.dp),
                    shape = RoundedCornerShape(24.dp),
                ) {
                    Text("다음", style = MaterialTheme.typography.displaySmall)
                }
            }
        }
    }
}

/** 조향각 막대. 지금 단계의 목표 구간(끝까지·가운데)을 함께 칠한다. 왼쪽 = +(실측). */
@Composable
private fun SteeringGauge(degrees: Float?, kind: StepKind) {
    val rounded = degrees?.roundToInt()
    val label = when {
        rounded == null -> "핸들 —"
        abs(rounded) <= CENTER_DEG -> "핸들 가운데"
        rounded > 0 -> "핸들 왼쪽 ${abs(rounded)}°"
        else -> "핸들 오른쪽 ${abs(rounded)}°"
    }
    val track = MaterialTheme.colorScheme.surface
    val target = SideCoachTheme.signal.praise.accent.copy(alpha = 0.45f)
    val marker = MaterialTheme.colorScheme.primary
    val onContainer = SideCoachTheme.signal.neutral.onContainer
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, style = MaterialTheme.typography.titleLarge, color = onContainer)
        Canvas(Modifier.fillMaxWidth().height(28.dp)) {
            val r = size.height / 2
            drawRoundRect(track, cornerRadius = CornerRadius(r, r))
            fun x(deg: Float) = size.width / 2 - (deg / GAUGE_MAX_DEG).coerceIn(-1f, 1f) * size.width / 2
            when (kind) {
                StepKind.FULL_LOCK, StepKind.FULL_LOCK_OPPOSITE -> {
                    val w = x(FULL_LOCK_DEG) // 왼쪽 끝 구간의 오른쪽 경계
                    drawRoundRect(target, Offset(0f, 0f), Size(w, size.height), CornerRadius(r, r))
                    drawRoundRect(target, Offset(size.width - w, 0f), Size(w, size.height), CornerRadius(r, r))
                }
                StepKind.CENTER -> {
                    val left = x(CENTER_DEG)
                    drawRect(target, Offset(left, 0f), Size(x(-CENTER_DEG) - left, size.height))
                }
                StepKind.REFERENCE, StepKind.FINISH -> Unit
            }
            if (degrees != null) {
                val mx = x(degrees)
                drawRoundRect(marker, Offset(mx - 6f, -4f), Size(12f, size.height + 8f), CornerRadius(6f, 6f))
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("왼쪽 끝", style = MaterialTheme.typography.labelLarge, color = onContainer)
            Text("가운데", style = MaterialTheme.typography.labelLarge, color = onContainer)
            Text("오른쪽 끝", style = MaterialTheme.typography.labelLarge, color = onContainer)
        }
    }
}

@Preview(widthDp = 1680, heightDp = 1100)
@Composable
private fun ParkingPanelPreview() {
    SideCoachTheme {
        DrivingScreen(
            snapshot = DrivingSnapshot(timestampMs = 5_000, speedKmh = 3f, gear = GearPosition.REVERSE, steeringDeg = 310f),
            lastMessage = null,
            parking = ParkingUi(ParkingState(ParkingType.REVERSE, stepIndex = 1), canTapNext = false),
        )
    }
}

@Preview(widthDp = 840, heightDp = 1200)
@Composable
private fun ParkingPanelTapPreview() {
    SideCoachTheme {
        DrivingScreen(
            snapshot = DrivingSnapshot(timestampMs = 5_000, speedKmh = 2f, gear = GearPosition.REVERSE, steeringDeg = 0f),
            lastMessage = null,
            parking = ParkingUi(ParkingState(ParkingType.PARALLEL), canTapNext = true),
        )
    }
}

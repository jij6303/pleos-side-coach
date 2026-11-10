package com.jij6303.sidecoach.ui.driving

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.domain.DrivingSnapshot
import com.jij6303.sidecoach.domain.coach.CoachMessage
import com.jij6303.sidecoach.domain.coach.CoachType
import com.jij6303.sidecoach.domain.coach.MessageCatalog
import com.jij6303.sidecoach.ui.dashboard.Chip
import com.jij6303.sidecoach.ui.theme.SideCoachTheme
import kotlin.math.roundToInt

/** 코칭 문구를 화면에 남겨 두는 시간. 지난 코칭이 계속 떠 있으면 지금 상황으로 오해할 수 있다. */
private const val MESSAGE_VISIBLE_MS = 10_000L

/**
 * 주행 규제 중 화면 (dev-architecture 5.2 DRIVING_VOICE).
 * 큰 글자 코칭 카드 1장 + 차속·기어 한 줄. 애니메이션·스크롤·입력 없음. 코칭은 음성이 주이고 화면은 보조.
 */
@Composable
fun DrivingScreen(
    snapshot: DrivingSnapshot,
    lastMessage: CoachMessage?,
    modifier: Modifier = Modifier,
) {
    // 메시지 시각과 스냅샷 시각은 같은 시계(elapsedRealtime)라 별도 타이머 없이 비교할 수 있다
    val message = lastMessage?.takeIf { snapshot.timestampMs - it.createdAtMs in 0..MESSAGE_VISIBLE_MS }
    val signal = SideCoachTheme.signal
    val (tone, label) = when (message?.type) {
        CoachType.HARD_BRAKE, CoachType.SPEEDING -> signal.brake to "주의"
        CoachType.BLINKER_OMISSION -> signal.turn to "깜빡이"
        CoachType.PRAISE -> signal.praise to "칭찬"
        CoachType.PREVIEW -> signal.neutral to "곧 만나요"
        CoachType.REPORT -> signal.praise to "성적표"
        null -> signal.neutral to "코칭 중"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(32.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp),
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth().weight(1f),
            shape = RoundedCornerShape(32.dp),
            color = tone.container,
        ) {
            Column(
                modifier = Modifier.padding(48.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterVertically),
            ) {
                Chip(text = label, tone = tone)
                Text(
                    text = message?.text ?: "안전 운전 중이에요",
                    style = MaterialTheme.typography.displayMedium,
                    color = tone.onContainer,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = snapshot.speedKmh?.roundToInt()?.toString() ?: "—",
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                "km/h",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp).weight(1f),
            )
            Text(
                text = gearLabel(snapshot.gear),
                style = MaterialTheme.typography.displayMedium,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun gearLabel(gear: GearPosition) = when (gear) {
    GearPosition.PARK -> "P"
    GearPosition.REVERSE -> "R"
    GearPosition.NEUTRAL -> "N"
    GearPosition.DRIVE -> "D"
    GearPosition.UNKNOWN -> "—"
}

@Preview(widthDp = 1680, heightDp = 1100)
@Composable
private fun DrivingScreenPreview() {
    SideCoachTheme {
        DrivingScreen(
            snapshot = DrivingSnapshot(timestampMs = 5_000, speedKmh = 52f, gear = GearPosition.DRIVE),
            lastMessage = CoachMessage(CoachType.BLINKER_OMISSION, MessageCatalog.blinkerOmission(), 1_000),
        )
    }
}

@Preview(widthDp = 800, heightDp = 1200)
@Composable
private fun DrivingScreenIdlePreview() {
    SideCoachTheme {
        DrivingScreen(snapshot = DrivingSnapshot(speedKmh = 0f, gear = GearPosition.DRIVE), lastMessage = null)
    }
}

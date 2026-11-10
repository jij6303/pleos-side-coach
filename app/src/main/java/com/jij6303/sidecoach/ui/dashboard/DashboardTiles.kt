package com.jij6303.sidecoach.ui.dashboard

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.domain.coach.CoachMessage
import com.jij6303.sidecoach.domain.coach.CoachType
import com.jij6303.sidecoach.ui.theme.SideCoachTheme
import com.jij6303.sidecoach.ui.theme.SignalTone
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

private val TileShape = RoundedCornerShape(24.dp)
private val TilePadding = 24.dp

/** 흰 카드 바탕의 타일. [unavailableReason]이 있으면 흐리게 하고 사유를 아래에 적는다. */
@Composable
fun Tile(
    title: String,
    modifier: Modifier = Modifier,
    unavailableReason: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(modifier = modifier, shape = TileShape, color = MaterialTheme.colorScheme.surface) {
        Column(
            modifier = Modifier.padding(TilePadding),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(
                modifier = Modifier.alpha(if (unavailableReason != null) 0.45f else 1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = content,
            )
            if (unavailableReason != null) {
                Text(
                    "수신 불가 · $unavailableReason",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
        }
    }
}

// ───────────────────────── 코칭 카드 ─────────────────────────

@Composable
fun CoachingCard(message: CoachMessage?, modifier: Modifier = Modifier) {
    val signal = SideCoachTheme.signal
    val (tone, label) = when (message?.type) {
        CoachType.HARD_BRAKE -> signal.brake to "급제동"
        CoachType.BLINKER_OMISSION -> signal.turn to "깜빡이"
        CoachType.PRAISE -> signal.praise to "칭찬"
        CoachType.SPEEDING -> signal.brake to "과속"
        CoachType.PREVIEW -> signal.neutral to "상황 예고"
        CoachType.REPORT -> signal.praise to "성적표"
        null -> signal.neutral to "코칭 대기"
    }
    Surface(modifier = modifier, shape = TileShape, color = tone.container) {
        Column(
            modifier = Modifier.padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Chip(text = label, tone = tone)
            Text(
                text = message?.text ?: "안전 운전 중이에요",
                style = MaterialTheme.typography.headlineLarge,
                color = tone.onContainer,
                maxLines = 3,
            )
            if (message == null) {
                Text(
                    "코칭이 필요한 순간에 바로 알려드릴게요.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = tone.onContainer,
                )
            }
        }
    }
}

@Composable
fun Chip(text: String, tone: SignalTone, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(tone.accent.copy(alpha = 0.18f))
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(tone.accent))
        Text(text, style = MaterialTheme.typography.labelLarge, color = tone.onContainer)
    }
}

// ───────────────────────── 차량 상태 ─────────────────────────

@Composable
fun SpeedTile(speedKmh: Float?, unavailableReason: String?, modifier: Modifier = Modifier) {
    Tile("차속", modifier, unavailableReason) {
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                speedKmh?.roundToInt()?.toString() ?: "—",
                style = MaterialTheme.typography.displayLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                "km/h",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 14.dp),
            )
        }
    }
}

@Composable
fun GearTile(gear: GearPosition, unavailableReason: String?, modifier: Modifier = Modifier) {
    Tile("기어", modifier, unavailableReason) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            listOf(GearPosition.PARK to "P", GearPosition.REVERSE to "R", GearPosition.NEUTRAL to "N", GearPosition.DRIVE to "D")
                .forEach { (pos, letter) ->
                    val selected = pos == gear
                    Box(
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            letter,
                            style = MaterialTheme.typography.headlineMedium,
                            color = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
        }
    }
}

@Composable
fun TurnSignalTile(state: TurnSignalState?, unavailableReason: String?, modifier: Modifier = Modifier) {
    val signal = SideCoachTheme.signal
    Tile("깜빡이", modifier, unavailableReason) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Arrow(left = true, color = if (state == TurnSignalState.LEFT) signal.turn.accent else signal.inactive)
            Text(
                when (state) {
                    TurnSignalState.LEFT -> "왼쪽"
                    TurnSignalState.RIGHT -> "오른쪽"
                    TurnSignalState.OFF -> "꺼짐"
                    null -> "—"
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Arrow(left = false, color = if (state == TurnSignalState.RIGHT) signal.turn.accent else signal.inactive)
        }
    }
}

@Composable
private fun Arrow(left: Boolean, color: Color) {
    Canvas(Modifier.size(width = 56.dp, height = 64.dp)) {
        val w = size.width
        val h = size.height
        val path = Path().apply {
            if (left) {
                moveTo(0f, h / 2); lineTo(w, 0f); lineTo(w, h)
            } else {
                moveTo(w, h / 2); lineTo(0f, 0f); lineTo(0f, h)
            }
            close()
        }
        drawPath(path, color)
    }
}

/** 조향각. 실측 부호(왼쪽 +)를 화면 방향으로 그린다: + 면 중앙에서 왼쪽으로 막대가 뻗는다. */
@Composable
fun SteeringTile(degrees: Float?, unavailableReason: String?, modifier: Modifier = Modifier) {
    val rounded = degrees?.roundToInt()
    val label = when {
        rounded == null -> "—"
        rounded == 0 -> "0° 중앙"
        rounded > 0 -> "${abs(rounded)}° 왼쪽"
        else -> "${abs(rounded)}° 오른쪽"
    }
    val track = MaterialTheme.colorScheme.surfaceVariant
    val fill = MaterialTheme.colorScheme.primary
    val center = MaterialTheme.colorScheme.onSurfaceVariant
    Tile("조향각", modifier, unavailableReason) {
        Text(label, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(20.dp),
        ) {
            val mid = size.width / 2
            val r = size.height / 2
            drawRoundRect(track, cornerRadius = CornerRadius(r, r))
            val ratio = ((degrees ?: 0f) / STEERING_DISPLAY_MAX_DEG).coerceIn(-1f, 1f)
            val len = mid * abs(ratio)
            val x = if (ratio > 0) mid - len else mid
            drawRoundRect(
                fill,
                topLeft = Offset(x, 0f),
                size = Size(len, size.height),
                cornerRadius = CornerRadius(r, r),
            )
            drawLine(center, Offset(mid, -4f), Offset(mid, size.height + 4f), strokeWidth = 3f)
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("왼쪽", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("오른쪽", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** 막대가 꽉 차는 조향각. 차선 변경 수준(±10~40°)이 잘 보이도록 좁게 잡았다. */
private const val STEERING_DISPLAY_MAX_DEG = 90f

/** 켜짐/꺼짐 표시. */
@Composable
fun IndicatorTile(
    title: String,
    on: Boolean?,
    tone: SignalTone,
    onText: String,
    offText: String,
    unavailableReason: String?,
    modifier: Modifier = Modifier,
) {
    val signal = SideCoachTheme.signal
    Tile(title, modifier, unavailableReason) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            Box(
                Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (on == true) tone.accent else signal.inactive),
            )
            Text(
                when (on) {
                    true -> onText
                    false -> offText
                    null -> "—"
                },
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

// ───────────────────────── 주행 통계 ─────────────────────────

@Composable
fun StatTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    caption: String? = null,
    progress: Float? = null,
    progressTone: SignalTone? = null,
) {
    Tile(title, modifier) {
        Text(value, style = MaterialTheme.typography.displayMedium, color = MaterialTheme.colorScheme.onSurface)
        if (progress != null) {
            val track = MaterialTheme.colorScheme.surfaceVariant
            val fill = progressTone?.accent ?: MaterialTheme.colorScheme.primary
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(12.dp)
                    .clip(CircleShape)
                    .background(track),
            ) {
                Box(
                    Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .clip(CircleShape)
                        .background(fill),
                )
            }
        }
        if (caption != null) {
            Text(caption, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

// ───────────────────────── 헤더 ─────────────────────────

@Composable
fun DashboardHeader(usingFake: Boolean, demoRoute: Boolean = false, modifier: Modifier = Modifier) {
    val signal = SideCoachTheme.signal
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text("초보운전탈출", style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            // 상황 예고가 가짜 경로 기반이면 시연임을 밝힌다
            if (demoRoute) Chip(text = "시연 경로", tone = signal.neutral)
            Chip(
                text = if (usingFake) "Fake 신호" else "코칭 중",
                tone = if (usingFake) signal.turn else signal.praise,
            )
        }
    }
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier.padding(start = 4.dp),
        textAlign = TextAlign.Start,
    )
}

internal fun percent(rate: Float?): String = rate?.let { String.format(Locale.US, "%d%%", (it * 100).roundToInt()) } ?: "—"

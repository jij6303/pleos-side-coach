package com.jij6303.sidecoach.feature.coach.ui

import com.jij6303.sidecoach.feature.parking.ui.ParkingPanel
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.state.DrivingSnapshot
import com.jij6303.sidecoach.feature.coach.CoachMessage
import com.jij6303.sidecoach.feature.coach.CoachType
import com.jij6303.sidecoach.feature.coach.MessageCatalog
import com.jij6303.sidecoach.feature.parking.ParkingUi
import com.jij6303.sidecoach.feature.report.trip.TripStats
import com.jij6303.sidecoach.ui.components.percent
import com.jij6303.sidecoach.ui.components.Chip
import com.jij6303.sidecoach.ui.theme.Navy800
import com.jij6303.sidecoach.ui.theme.SideCoachTheme
import com.jij6303.sidecoach.ui.theme.Sun500
import com.jij6303.sidecoach.ui.theme.SignalColors
import com.jij6303.sidecoach.ui.theme.SignalTone
import com.jij6303.sidecoach.ui.theme.WIDE_MIN_DP
import kotlin.math.roundToInt

/** 코칭 문구를 화면에 남겨 두는 시간. 지난 코칭이 계속 떠 있으면 지금 상황으로 오해할 수 있다. */
private const val MESSAGE_VISIBLE_MS = 10_000L

private val PanelShape = RoundedCornerShape(32.dp)

/**
 * 주행 규제 중 화면 (DRIVING_VOICE).
 * 큰 글자 코칭 카드 + 아래 상태 띠(깜빡이·브레이크·이번 주행 기록). 애니메이션·스크롤 없음. 코칭은 음성이 주이고 화면은 보조.
 *
 * 차속·기어는 Pleos 고정 주행 영역에 이미 크게 나오므로 여기서 다시 그리지 않는다.
 * 깜빡이를 켜면 시스템 측후방 카메라 창이 앱 영역 왼쪽 위를 덮으므로(에뮬레이터 실측) 코칭 문구는 카드 아래쪽에 붙인다.
 * 주차 코치 중([parking])에는 코칭 카드 대신 주차 카드를 보여준다. 입력은 주차 카드의 "다음" 버튼 하나뿐.
 */
@Composable
fun DrivingScreen(
    snapshot: DrivingSnapshot,
    lastMessage: CoachMessage?,
    modifier: Modifier = Modifier,
    stats: TripStats = TripStats(),
    parking: ParkingUi? = null,
    parkingMessage: CoachMessage? = null,
    onParkingNext: () -> Unit = {},
) {
    // 메시지 시각과 스냅샷 시각은 같은 시계(elapsedRealtime)라 별도 타이머 없이 비교할 수 있다
    val message = lastMessage?.takeIf { snapshot.timestampMs - it.createdAtMs in 0..MESSAGE_VISIBLE_MS }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
    ) {
        val wide = maxWidth.value >= WIDE_MIN_DP
        Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(24.dp)) {
            val main = Modifier.fillMaxWidth().weight(1f)
            if (parking != null) {
                ParkingPanel(
                    parking = parking,
                    snapshot = snapshot,
                    // 멈춤 안내는 틱에서 나와 스냅샷보다 늦을 수 있으므로 아래쪽 경계는 두지 않는다
                    message = parkingMessage?.takeIf { snapshot.timestampMs - it.createdAtMs <= MESSAGE_VISIBLE_MS },
                    onNext = onParkingNext,
                    modifier = main,
                )
            } else {
                CoachPanel(message, wide, main)
            }
            StatusStrip(snapshot, stats, wide, Modifier.fillMaxWidth())
        }
    }
}

// ───────────────────────── 코칭 카드 ─────────────────────────

private data class CoachLook(val tone: SignalTone, val label: String, val mood: CoachMood)

private fun lookOf(type: CoachType?, signal: SignalColors): CoachLook =
    when (type) {
        CoachType.HARD_BRAKE -> CoachLook(signal.brake, "급제동", CoachMood.STERN)
        CoachType.SPEEDING -> CoachLook(signal.brake, "과속 주의", CoachMood.STERN)
        CoachType.BLINKER_OMISSION -> CoachLook(signal.turn, "깜빡이", CoachMood.ALERT)
        CoachType.BLINKER_LEFT_ON -> CoachLook(signal.turn, "깜빡이 끄기", CoachMood.ALERT)
        CoachType.TURN_SPEED -> CoachLook(signal.turn, "회전 속도", CoachMood.ALERT)
        CoachType.GEAR_SHIFT_MOVING -> CoachLook(signal.brake, "기어 변경", CoachMood.STERN)
        CoachType.PRAISE -> CoachLook(signal.praise, "칭찬", CoachMood.HAPPY)
        CoachType.REPORT -> CoachLook(signal.praise, "성적표", CoachMood.HAPPY)
        CoachType.PREVIEW -> CoachLook(signal.neutral, "곧 만나요", CoachMood.CALM)
        CoachType.PARKING -> CoachLook(signal.neutral, "주차 코치", CoachMood.CALM)
        CoachType.TIP -> CoachLook(signal.neutral, "도움말", CoachMood.CALM)
        CoachType.LANE -> CoachLook(signal.neutral, "차로 기억", CoachMood.CALM)
        null -> CoachLook(signal.neutral, "코칭 중", CoachMood.CALM)
    }

/**
 * 연수 강사 얼굴 + 말풍선. 얼굴 고리와 말풍선 색이 코칭 유형을 알리고, 표정이 같은 뜻을 한 번 더 전한다.
 * 코칭이 없으면 미소 짓는 얼굴과 차분한 말풍선으로 물러난다.
 */
@Composable
private fun CoachPanel(message: CoachMessage?, wide: Boolean, modifier: Modifier) {
    val look = lookOf(message?.type, SideCoachTheme.signal)
    val tone = look.tone
    val idle = message == null
    val bubble = if (idle) MaterialTheme.colorScheme.surfaceVariant else tone.container
    val ink = if (idle) MaterialTheme.colorScheme.onSurface else tone.onContainer
    Surface(modifier = modifier, shape = PanelShape, color = MaterialTheme.colorScheme.surface) {
        Row(
            modifier = Modifier.fillMaxSize().padding(start = 48.dp, end = 48.dp, bottom = 56.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            // 차분한 상태의 얼굴 고리는 남색 대신 밝은 파랑 (남색은 답답해 보임)
            val ring = if (tone == SideCoachTheme.signal.neutral) FaceRingBlue else tone.accent
            CoachFace(look.mood, ring = ring, modifier = Modifier.size(if (wide) 440.dp else 240.dp))
            // 말풍선 꼬리: 얼굴 쪽을 가리킨다
            Canvas(
                Modifier
                    .padding(bottom = if (wide) 170.dp else 84.dp)
                    .size(width = 32.dp, height = 48.dp),
            ) {
                val path = Path().apply {
                    moveTo(size.width, 0f); lineTo(0f, size.height / 2); lineTo(size.width, size.height); close()
                }
                drawPath(path, bubble)
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(40.dp))
                    .background(bubble)
                    .padding(horizontal = 48.dp, vertical = 40.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                if (idle) IdleChip(look.label) else Chip(text = look.label, tone = tone)
                Text(
                    text = message?.text ?: "안전 운전 중이에요",
                    style = if (wide) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineLarge,
                    color = ink,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
                if (idle) {
                    Text(
                        "코칭이 필요한 순간에 음성으로 알려드릴게요",
                        style = (if (wide) MaterialTheme.typography.headlineMedium else MaterialTheme.typography.titleLarge)
                            .copy(fontWeight = FontWeight.Normal),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private val FaceRingBlue = Color(0xFF3B82F6)

/** 대기 중 말풍선의 "코칭 중" 칩: 앱 아이콘처럼 남색 바탕에 노란 글자. */
@Composable
private fun IdleChip(text: String) {
    Row(
        modifier = Modifier
            .clip(CircleShape)
            .background(Navy800)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(Sun500))
        Text(text, style = MaterialTheme.typography.labelLarge, color = Sun500)
    }
}

// ───────────────────────── 상태 띠 ─────────────────────────

/** 깜빡이·브레이크와 이번 주행 기록. 넓으면 한 줄, 좁으면 두 줄. */
@Composable
private fun StatusStrip(snapshot: DrivingSnapshot, stats: TripStats, wide: Boolean, modifier: Modifier) {
    Surface(modifier = modifier, shape = PanelShape, color = MaterialTheme.colorScheme.surface) {
        if (wide) {
            Row(
                modifier = Modifier.padding(horizontal = 48.dp, vertical = 28.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SignalRow(snapshot)
                Spacer(Modifier.weight(1f))
                StatRow(stats)
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = 40.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                SignalRow(snapshot)
                StatRow(stats)
            }
        }
    }
}

/** 이번 주행 기록 세 칸. 값이 0이 아니어도 꾸짖는 색은 쓰지 않는다(코칭은 음성 몫). */
@Composable
private fun StatRow(stats: TripStats) {
    Row(horizontalArrangement = Arrangement.spacedBy(48.dp)) {
        Stat("차선 변경", "${stats.laneChanges}회")
        Stat("깜빡이 사용", percent(stats.blinkerUsageRate))
        Stat("급제동", "${stats.hardBrakes}회")
    }
}

@Composable
private fun Stat(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, style = MaterialTheme.typography.headlineLarge, color = MaterialTheme.colorScheme.onSurface)
        Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * 깜빡이 좌우 화살표 + 가운데 브레이크 표시. 켜진 것만 신호색.
 * 비상등 중엔 TurnSignal이 OFF로 보고되므로(실측) 비상등이면 양쪽을 모두 켠다.
 */
@Composable
private fun SignalRow(snapshot: DrivingSnapshot) {
    val signal = SideCoachTheme.signal
    val hazard = snapshot.hazardOn == true
    val left = hazard || snapshot.turnSignal == TurnSignalState.LEFT
    val right = hazard || snapshot.turnSignal == TurnSignalState.RIGHT
    val braking = snapshot.brakePressed == true
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
        TurnArrow(left = true, color = if (left) signal.turn.accent else signal.inactive)
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(if (braking) signal.brake.container else MaterialTheme.colorScheme.surfaceVariant)
                .padding(horizontal = 20.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier.size(14.dp).clip(CircleShape)
                    .background(if (braking) signal.brake.accent else signal.inactive),
            )
            Text(
                if (hazard) "비상등" else "브레이크",
                style = MaterialTheme.typography.titleMedium,
                color = if (braking) signal.brake.onContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        TurnArrow(left = false, color = if (right) signal.turn.accent else signal.inactive)
    }
}

@Composable
private fun TurnArrow(left: Boolean, color: Color) {
    Canvas(Modifier.size(width = 44.dp, height = 52.dp)) {
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

// ───────────────────────── 미리보기 ─────────────────────────

@Preview(widthDp = 1682, heightDp = 1100)
@Composable
private fun DrivingScreenPreview() {
    SideCoachTheme {
        DrivingScreen(
            snapshot = DrivingSnapshot(
                timestampMs = 5_000, speedKmh = 52f, gear = GearPosition.DRIVE, turnSignal = TurnSignalState.LEFT,
            ),
            lastMessage = CoachMessage(CoachType.BLINKER_OMISSION, MessageCatalog.blinkerOmission(), 1_000),
        )
    }
}

@Preview(widthDp = 841, heightDp = 1100)
@Composable
private fun DrivingScreenIdlePreview() {
    SideCoachTheme {
        DrivingScreen(
            snapshot = DrivingSnapshot(speedKmh = 0f, gear = GearPosition.DRIVE, brakePressed = true),
            lastMessage = null,
        )
    }
}

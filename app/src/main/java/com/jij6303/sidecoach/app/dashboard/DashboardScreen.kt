package com.jij6303.sidecoach.app.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jij6303.sidecoach.data.vehicle.GearPosition
import com.jij6303.sidecoach.data.vehicle.SignalKind
import com.jij6303.sidecoach.data.vehicle.TurnSignalState
import com.jij6303.sidecoach.state.DrivingSnapshot
import com.jij6303.sidecoach.feature.coach.CoachMessage
import com.jij6303.sidecoach.feature.coach.CoachType
import com.jij6303.sidecoach.feature.parking.ParkingType
import com.jij6303.sidecoach.feature.report.trip.TripStats
import com.jij6303.sidecoach.ui.components.StatTile
import com.jij6303.sidecoach.ui.components.percent
import com.jij6303.sidecoach.ui.theme.SideCoachTheme
import com.jij6303.sidecoach.ui.theme.WIDE_MIN_DP

private val Gap = 20.dp

/**
 * 메인 대시보드: 코칭 카드, 차량 상태, 주행 통계.
 * 애니메이션 없이 값만 바뀐다 (주행 중 애니메이션 금지). 너비에 따라 2열/1열로 배치한다.
 */
@Composable
fun DashboardScreen(
    snapshot: DrivingSnapshot,
    lastMessage: CoachMessage?,
    stats: TripStats,
    usingFake: Boolean,
    modifier: Modifier = Modifier,
    demoRoute: Boolean = false,
    onOpenTips: (() -> Unit)? = null,
    parkingType: ParkingType? = ParkingType.REVERSE,
    onParkingTypeChange: ((ParkingType?) -> Unit)? = null,
    lane: Int? = null,
    onLaneChange: ((Int?) -> Unit)? = null,
) {
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        val wide = maxWidth.value >= WIDE_MIN_DP
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(32.dp),
            verticalArrangement = Arrangement.spacedBy(Gap),
        ) {
            DashboardHeader(usingFake, demoRoute, onOpenTips)
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Gap)) {
                        CoachingCard(lastMessage, Modifier.fillMaxWidth().height(260.dp))
                        SectionTitle("이번 주행")
                        StatsGrid(stats)
                        ParkingCoachTile(parkingType, onParkingTypeChange, Modifier.fillMaxWidth())
                    }
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Gap)) {
                        SectionTitle("차량 상태")
                        VehicleGrid(snapshot)
                        LaneTile(lane, onLaneChange, Modifier.fillMaxWidth())
                    }
                }
            } else {
                CoachingCard(lastMessage, Modifier.fillMaxWidth())
                SectionTitle("차량 상태")
                VehicleGrid(snapshot)
                SectionTitle("이번 주행")
                StatsGrid(stats)
                ParkingCoachTile(parkingType, onParkingTypeChange, Modifier.fillMaxWidth())
                LaneTile(lane, onLaneChange, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun VehicleGrid(s: DrivingSnapshot) {
    val signal = SideCoachTheme.signal
    val na = s.unavailable
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        EqualRow {
            SpeedTile(s.speedKmh, na[SignalKind.SPEED], it)
            GearTile(s.gear, na[SignalKind.GEAR], it)
        }
        EqualRow {
            TurnSignalTile(s.turnSignal, na[SignalKind.TURN_SIGNAL], it)
            SteeringTile(s.steeringDeg, na[SignalKind.STEERING], it)
        }
        EqualRow {
            IndicatorTile("브레이크", s.brakePressed, signal.brake, "밟음", "뗌", na[SignalKind.BRAKE], it)
            IndicatorTile("주차 브레이크", s.parkingBrakeOn, signal.brake, "걸림", "풀림", na[SignalKind.PARKING_BRAKE], it)
            IndicatorTile("비상등", s.hazardOn, signal.turn, "켜짐", "꺼짐", na[SignalKind.HAZARD]?.let { "권한 제한" }, it)
        }
    }
}

@Composable
private fun StatsGrid(stats: TripStats) {
    val signal = SideCoachTheme.signal
    val rate = stats.blinkerUsageRate
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        EqualRow {
            StatTile("차선 변경", "${stats.laneChanges}회", it, caption = "깜빡이 켬 ${stats.signalledLaneChanges}회")
            StatTile(
                "깜빡이 사용률", percent(rate), it,
                progress = rate ?: 0f,
                progressTone = if ((rate ?: 1f) >= 0.8f) signal.praise else signal.turn,
            )
            StatTile("급제동", "${stats.hardBrakes}회", it, caption = if (stats.hardBrakes == 0) "부드러워요" else "조금 더 일찍 밟아요")
        }
        // 성적표와 같은 코칭 항목을 보여준다 (#55)
        EqualRow {
            StatTile("깜빡이 끄기", "${stats.blinkerLeftOn}회", it, caption = if (stats.blinkerLeftOn == 0) "잘 껐어요" else "돌고 나서 켜 둔 횟수")
            StatTile("회전 속도", "${stats.turnTooFast}회", it, caption = if (stats.turnTooFast == 0) "천천히 잘 돌았어요" else "빠르게 돈 횟수")
            StatTile("기어 변경", "${stats.gearShiftsMoving}회", it, caption = if (stats.gearShiftsMoving == 0) "멈춘 뒤 잘 바꿨어요" else "움직이며 바꾼 횟수")
        }
    }
}

/** 자식 타일들을 같은 너비·같은 높이로 나란히 배치한다. */
@Composable
private fun EqualRow(content: @Composable (Modifier) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        horizontalArrangement = Arrangement.spacedBy(Gap),
    ) {
        content(Modifier.weight(1f).fillMaxHeight())
    }
}

// ───────────────────────── Preview ─────────────────────────

private val previewSnapshot = DrivingSnapshot(
    speedKmh = 52.3f,
    gear = GearPosition.DRIVE,
    steeringDeg = 24f,
    turnSignal = TurnSignalState.LEFT,
    brakePressed = false,
    parkingBrakeOn = false,
    unavailable = mapOf(SignalKind.HAZARD to "권한 없음: CAR_EXTERIOR_LIGHTS"),
)
private val previewStats = TripStats(laneChanges = 6, signalledLaneChanges = 4, hardBrakes = 1)

@Preview(name = "가로 (차량 창)", widthDp = 1680, heightDp = 1210)
@Composable
private fun DashboardWidePreview() {
    SideCoachTheme {
        DashboardScreen(
            previewSnapshot,
            CoachMessage(CoachType.BLINKER_OMISSION, "차선 바꿀 땐 깜빡이 먼저 켜요!", 0),
            previewStats,
            usingFake = false,
        )
    }
}

@Preview(name = "세로", widthDp = 800, heightDp = 1280)
@Composable
private fun DashboardNarrowPreview() {
    SideCoachTheme {
        DashboardScreen(previewSnapshot, null, TripStats(), usingFake = true)
    }
}

@Preview(name = "다크", widthDp = 1680, heightDp = 1210)
@Composable
private fun DashboardDarkPreview() {
    SideCoachTheme(darkTheme = true) {
        DashboardScreen(
            previewSnapshot,
            CoachMessage(CoachType.HARD_BRAKE, "조금 더 일찍, 부드럽게 밟아볼까요?", 0),
            previewStats,
            usingFake = false,
        )
    }
}

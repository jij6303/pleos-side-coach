package com.jij6303.sidecoach.ui.report

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jij6303.sidecoach.domain.report.Grade
import com.jij6303.sidecoach.domain.report.ReportGenerator
import com.jij6303.sidecoach.domain.report.TripReport
import com.jij6303.sidecoach.domain.trip.TripMetrics
import com.jij6303.sidecoach.domain.trip.TripStats
import com.jij6303.sidecoach.ui.dashboard.Chip
import com.jij6303.sidecoach.ui.dashboard.StatTile
import com.jij6303.sidecoach.ui.dashboard.percent
import com.jij6303.sidecoach.ui.theme.SideCoachTheme
import java.util.Locale
import kotlin.math.roundToInt

private val Gap = 20.dp
private const val WIDE_MIN_DP = 840

/**
 * 주행 후 성적표 (concept ⑤, dev-architecture 5.2 PARKED_FULL 전용).
 * [onClose]는 "확인" 버튼과 Back(하위 화면 규칙)에서 부른다.
 */
@Composable
fun ReportScreen(report: TripReport, onClose: () -> Unit, modifier: Modifier = Modifier) {
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "오늘의 운전 성적표",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                Button(onClick = onClose, modifier = Modifier.height(64.dp)) {
                    Text("확인", style = MaterialTheme.typography.titleMedium)
                }
            }

            if (wide) {
                Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    ScoreCard(report, Modifier.weight(1f).fillMaxHeight())
                    NextTaskCard(report, Modifier.weight(1f).fillMaxHeight())
                }
            } else {
                ScoreCard(report, Modifier.fillMaxWidth())
                NextTaskCard(report, Modifier.fillMaxWidth())
            }

            MetricTiles(report, columns = if (wide) 5 else 2)
        }
    }
}

@Composable
private fun ScoreCard(report: TripReport, modifier: Modifier) {
    val signal = SideCoachTheme.signal
    val tone = when (report.grade) {
        Grade.GREAT -> signal.praise
        Grade.GOOD -> signal.neutral
        Grade.PRACTICE -> signal.turn
    }
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = tone.container) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Chip(text = "점수", tone = tone)
            Row(verticalAlignment = Alignment.Bottom) {
                Text("${report.score}", style = MaterialTheme.typography.displayLarge, color = tone.onContainer)
                Text(
                    "점",
                    style = MaterialTheme.typography.headlineMedium,
                    color = tone.onContainer,
                    modifier = Modifier.padding(start = 8.dp, bottom = 12.dp),
                )
            }
            Text(report.grade.title, style = MaterialTheme.typography.headlineLarge, color = tone.onContainer)
        }
    }
}

@Composable
private fun NextTaskCard(report: TripReport, modifier: Modifier) {
    val tone = SideCoachTheme.signal.neutral
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Chip(text = "다음 연습 과제", tone = tone)
            Text(
                report.nextTask.title,
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                report.nextTask.detail,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MetricTiles(report: TripReport, columns: Int) {
    val signal = SideCoachTheme.signal
    val stats = report.stats
    val m = report.metrics
    val rate = stats.blinkerUsageRate
    val tiles: List<@Composable (Modifier) -> Unit> = listOf(
        {
            StatTile(
                "깜빡이 사용률", percent(rate), it,
                caption = if (stats.laneChanges == 0) "차선 변경 없음" else "차선 변경 ${stats.laneChanges}회 중 ${stats.signalledLaneChanges}회",
                progress = rate ?: 0f,
                progressTone = if ((rate ?: 1f) >= 0.8f) signal.praise else signal.turn,
            )
        },
        {
            StatTile(
                "급제동", "${stats.hardBrakes}회", it,
                caption = if (stats.hardBrakes == 0) "부드러워요" else "조금 더 일찍 밟아요",
            )
        },
        { StatTile("주행 시간", duration(m.movingMs), it, caption = "움직인 시간") },
        { StatTile("주행 거리", String.format(Locale.US, "%.1f km", m.distanceKm), it) },
        { StatTile("최고 속도", "${m.maxSpeedKmh.roundToInt()} km/h", it) },
    )
    Column(verticalArrangement = Arrangement.spacedBy(Gap)) {
        tiles.chunked(columns).forEach { row ->
            Row(Modifier.height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Gap)) {
                row.forEach { tile -> tile(Modifier.weight(1f).fillMaxHeight()) }
                repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

internal fun duration(ms: Long): String {
    val totalSec = ms / 1000
    val min = totalSec / 60
    val sec = totalSec % 60
    return if (min == 0L) "${sec}초" else "${min}분 ${sec}초"
}

private val previewReport = ReportGenerator.generate(
    TripMetrics(startedAtMs = 0, endedAtMs = 600_000, movingMs = 512_000, distanceKm = 4.3f, maxSpeedKmh = 62f),
    TripStats(laneChanges = 4, signalledLaneChanges = 3, hardBrakes = 1),
)

@Preview(widthDp = 1680, heightDp = 1100)
@Composable
private fun ReportWidePreview() {
    SideCoachTheme { ReportScreen(previewReport, onClose = {}) }
}

@Preview(widthDp = 800, heightDp = 1400)
@Composable
private fun ReportNarrowPreview() {
    SideCoachTheme { ReportScreen(previewReport, onClose = {}) }
}

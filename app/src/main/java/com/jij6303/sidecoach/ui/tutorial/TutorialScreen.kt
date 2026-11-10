package com.jij6303.sidecoach.ui.tutorial

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jij6303.sidecoach.domain.tutorial.TutorialMission
import com.jij6303.sidecoach.domain.tutorial.TutorialState
import com.jij6303.sidecoach.ui.dashboard.Chip
import com.jij6303.sidecoach.ui.theme.SideCoachTheme

private val Gap = 20.dp
private const val WIDE_MIN_DP = 840

/**
 * 내 차 익히기 (concept ③, PARKED_FULL 전용).
 * 지시대로 조작하면 차가 확인해 자동으로 다음 미션으로 넘어간다. 애니메이션 없음.
 */
@Composable
fun TutorialScreen(
    state: TutorialState,
    onSkip: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
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
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "내 차 익히기",
                    style = MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.weight(1f),
                )
                if (state.finished) {
                    Button(onClick = onClose, modifier = Modifier.height(64.dp)) {
                        Text("닫기", style = MaterialTheme.typography.titleMedium)
                    }
                } else {
                    OutlinedButton(onClick = onClose, modifier = Modifier.height(64.dp)) {
                        Text("끝내기", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    CurrentCard(state, onSkip, Modifier.weight(1.4f))
                    MissionList(state, Modifier.weight(1f))
                }
            } else {
                CurrentCard(state, onSkip, Modifier.fillMaxWidth())
                MissionList(state, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun CurrentCard(state: TutorialState, onSkip: () -> Unit, modifier: Modifier) {
    val signal = SideCoachTheme.signal
    val mission = state.current
    val tone = if (mission == null) signal.praise else signal.neutral
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = tone.container) {
        Column(Modifier.padding(40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            if (mission == null) {
                Chip(text = "완료", tone = tone)
                Text("모든 미션을 마쳤어요", style = MaterialTheme.typography.displayMedium, color = tone.onContainer)
                Text(
                    "${state.missions.size}개 중 ${state.completed.size}개를 직접 해봤어요.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = tone.onContainer,
                )
            } else {
                Chip(text = "미션 ${state.currentIndex + 1} / ${state.missions.size}", tone = tone)
                Text(mission.title, style = MaterialTheme.typography.displayMedium, color = tone.onContainer)
                Text(mission.instruction, style = MaterialTheme.typography.headlineMedium, color = tone.onContainer)
                Text(
                    "차가 조작을 확인하면 자동으로 넘어가요.",
                    style = MaterialTheme.typography.bodyLarge,
                    color = tone.onContainer,
                )
                OutlinedButton(onClick = onSkip, modifier = Modifier.height(56.dp)) {
                    Text("건너뛰기", style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun MissionList(state: TutorialState, modifier: Modifier) {
    val signal = SideCoachTheme.signal
    Surface(modifier = modifier, shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
        Column(Modifier.padding(32.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Text("미션 목록", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            state.missions.forEachIndexed { i, m ->
                val done = m in state.completed
                val current = i == state.currentIndex
                val skipped = i < state.currentIndex && !done
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(
                                when {
                                    done -> signal.praise.accent
                                    current -> signal.neutral.accent
                                    else -> MaterialTheme.colorScheme.surfaceVariant
                                },
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            if (done) "✓" else "${i + 1}",
                            style = MaterialTheme.typography.titleMedium,
                            color = if (done || current) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(
                        m.title + if (skipped) " (건너뜀)" else "",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (current) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Preview(widthDp = 1680, heightDp = 1100)
@Composable
private fun TutorialPreview() {
    SideCoachTheme {
        TutorialScreen(
            state = TutorialState(
                missions = TutorialMission.entries,
                completed = setOf(TutorialMission.LEFT_SIGNAL),
                currentIndex = 1,
            ),
            onSkip = {}, onClose = {},
        )
    }
}

@Preview(widthDp = 800, heightDp = 1200)
@Composable
private fun TutorialDonePreview() {
    SideCoachTheme {
        TutorialScreen(
            state = TutorialState(TutorialMission.entries, setOf(TutorialMission.LEFT_SIGNAL, TutorialMission.BRAKE), 4),
            onSkip = {}, onClose = {},
        )
    }
}

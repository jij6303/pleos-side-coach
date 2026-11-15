package com.jij6303.sidecoach.feature.tips.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.jij6303.sidecoach.feature.tips.CarTip
import com.jij6303.sidecoach.ui.components.Chip
import com.jij6303.sidecoach.ui.theme.ScreenHeader
import com.jij6303.sidecoach.ui.theme.SideCoachTheme
import com.jij6303.sidecoach.ui.theme.WIDE_MIN_DP

private val Gap = 20.dp

/**
 * 차량 기능 도움말 (#29, PARKED_FULL 전용). 왼쪽 목록, 오른쪽 상세.
 * 주행 중에는 Gleo 명령으로 음성 답변만 한다.
 */
@Composable
fun CarTipsScreen(
    selected: CarTip,
    onSelect: (CarTip) -> Unit,
    onListen: (CarTip) -> Unit,
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
            ScreenHeader(title = "차량 기능 도움말", subtitle = "버튼 위치와 쓰는 법을 알려드려요") {
                OutlinedButton(onClick = onClose, modifier = Modifier.height(64.dp)) {
                    Text("닫기", style = MaterialTheme.typography.titleMedium)
                }
            }
            if (wide) {
                Row(horizontalArrangement = Arrangement.spacedBy(Gap)) {
                    TipList(selected, onSelect, Modifier.weight(1f))
                    TipDetail(selected, onListen, Modifier.weight(1.6f))
                }
            } else {
                // 좁은 창(앱 창 1개): 주제를 가로 탭으로 두어 고르자마자 상세가 바로 아래에 보이게 한다
                TipTabs(selected, onSelect)
                TipDetail(selected, onListen, Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun TipList(selected: CarTip, onSelect: (CarTip) -> Unit, modifier: Modifier) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            CarTip.entries.forEach { tip ->
                val active = tip == selected
                Text(
                    tip.title,
                    style = MaterialTheme.typography.titleLarge,
                    color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                        .clickable { onSelect(tip) }
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                )
            }
        }
    }
}

@Composable
private fun TipTabs(selected: CarTip, onSelect: (CarTip) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        CarTip.entries.forEach { tip ->
            val active = tip == selected
            Text(
                tip.title,
                style = MaterialTheme.typography.titleMedium,
                color = if (active) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .clip(RoundedCornerShape(28.dp))
                    .background(if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surface)
                    .border(1.dp, if (active) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(28.dp))
                    .clickable { onSelect(tip) }
                    .padding(horizontal = 24.dp, vertical = 16.dp),
            )
        }
    }
}

@Composable
private fun TipDetail(tip: CarTip, onListen: (CarTip) -> Unit, modifier: Modifier) {
    val tone = SideCoachTheme.signal.neutral
    Surface(modifier = modifier, shape = RoundedCornerShape(28.dp), color = tone.container) {
        Column(Modifier.padding(40.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
            Chip(text = "이럴 땐 이렇게", tone = tone)
            Text(tip.title, style = MaterialTheme.typography.headlineLarge, color = tone.onContainer)
            Text(tip.situation, style = MaterialTheme.typography.bodyLarge, color = tone.onContainer)
            tip.whereToFind?.let {
                Text("찾는 곳: $it", style = MaterialTheme.typography.titleMedium, color = tone.onContainer)
            }
            if (tip.buttons.isNotEmpty()) {
                Text("이 버튼을 찾아요", style = MaterialTheme.typography.titleMedium, color = tone.onContainer)
                CarButtonRow(tip.buttons)
                Text(
                    "차종에 따라 버튼 모양이 조금 다를 수 있어요.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = tone.onContainer,
                )
            }
            tip.steps.forEachIndexed { i, step ->
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Box(
                        Modifier.size(40.dp).clip(CircleShape).background(tone.accent),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("${i + 1}", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.surface)
                    }
                    Text(
                        step,
                        style = MaterialTheme.typography.titleLarge,
                        color = tone.onContainer,
                        modifier = Modifier.weight(1f).padding(top = 2.dp),
                    )
                }
            }
            Button(onClick = { onListen(tip) }, modifier = Modifier.height(56.dp)) {
                Text("음성으로 듣기", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Preview(widthDp = 1682, heightDp = 1210)
@Composable
private fun CarTipsPreview() {
    SideCoachTheme { CarTipsScreen(CarTip.WINDSHIELD_FOG, onSelect = {}, onListen = {}, onClose = {}) }
}

@Preview(widthDp = 800, heightDp = 1400)
@Composable
private fun CarTipsNarrowPreview() {
    SideCoachTheme { CarTipsScreen(CarTip.AIR_CONDITIONER, onSelect = {}, onListen = {}, onClose = {}) }
}

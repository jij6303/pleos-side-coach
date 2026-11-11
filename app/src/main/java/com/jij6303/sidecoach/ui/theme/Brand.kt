package com.jij6303.sidecoach.ui.theme

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.jij6303.sidecoach.R

/**
 * 앱 아이콘과 같은 모양의 브랜드 마크: 남색 둥근 사각형 안에 노란 핸들 얼굴과 새싹.
 * 런처 아이콘 전경(ic_launcher_foreground)을 그대로 써서 아이콘과 화면이 어긋나지 않게 한다.
 */
@Composable
fun BrandMark(size: Dp = 56.dp, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(size)
            .clip(RoundedCornerShape(size * 0.28f))
            .background(Brush.linearGradient(listOf(Navy700, Navy800, Navy900))),
        contentAlignment = Alignment.Center,
    ) {
        // 전경은 108dp 캔버스 중 가운데 약 2/3에 그려져 있어 바깥 여백만큼 키워 꽉 채운다
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            modifier = Modifier.requiredSize(size * 1.5f),
        )
    }
}

/** 아이콘 전경만(바탕 없이). 히어로 카드의 장식용. */
@Composable
fun BrandGlyph(size: Dp, modifier: Modifier = Modifier, alpha: Float = 1f) {
    Image(
        painter = painterResource(R.drawable.ic_launcher_foreground),
        contentDescription = null,
        alpha = alpha,
        modifier = modifier.size(size),
    )
}

/** 아이콘 바탕처럼 남색 그라데이션. 대기 코칭 카드 등 브랜드를 드러내는 큰 카드에 쓴다. */
object Hero {
    val brush: Brush = Brush.linearGradient(listOf(Navy700, Navy800, Navy900))
    val title: Color = Color.White
    val body: Color = Navy200
    val accent: Color = Sun500
}

/**
 * 화면 머리: 브랜드 마크 + 제목(+ 부제) + 오른쪽 동작들.
 * 대시보드·성적표·도움말이 같은 머리를 써서 화면이 바뀌어도 같은 앱으로 보이게 한다.
 */
@Composable
fun ScreenHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    // 좁은 창(앱 창 1개)에서는 오른쪽 동작들이 다음 줄로 넘어간다
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalArrangement = Arrangement.spacedBy(12.dp),
        itemVerticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BrandMark(size = 64.dp)
            Column {
                Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onBackground)
                if (subtitle != null) {
                    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            content = actions,
        )
    }
}

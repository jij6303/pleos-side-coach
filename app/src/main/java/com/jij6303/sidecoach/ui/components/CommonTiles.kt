package com.jij6303.sidecoach.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
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
import androidx.compose.ui.unit.dp
import com.jij6303.sidecoach.ui.theme.SignalTone
import java.util.Locale
import kotlin.math.roundToInt

// 여러 화면이 같이 쓰는 카드·칩·통계 타일.

internal val TileShape = RoundedCornerShape(28.dp)
internal val TilePadding = 28.dp

/** 흰 카드 바탕의 타일. [unavailableReason]이 있으면 흐리게 하고 사유를 아래에 적는다. */
@Composable
fun Tile(
    title: String,
    modifier: Modifier = Modifier,
    unavailableReason: String? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier,
        shape = TileShape,
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
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

@Composable
fun Chip(text: String, tone: SignalTone, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(tone.accent.copy(alpha = 0.16f))
            .border(1.dp, tone.accent.copy(alpha = 0.35f), CircleShape)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Box(Modifier.size(10.dp).clip(CircleShape).background(tone.accent))
        Text(text, style = MaterialTheme.typography.labelLarge, color = tone.onContainer)
    }
}

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

fun percent(rate: Float?): String = rate?.let { String.format(Locale.US, "%d%%", (it * 100).roundToInt()) } ?: "—"

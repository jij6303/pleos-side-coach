package com.jij6303.sidecoach.ui.driving

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import com.jij6303.sidecoach.ui.theme.SideCoachTheme

/** 연수 강사 얼굴 표정. 코칭 유형마다 하나. 주행 중 애니메이션 금지라 표정은 바뀌기만 하고 움직이지 않는다. */
enum class CoachMood {
    /** 대기·안내: 잔잔한 미소 */
    CALM,
    /** 급제동·과속: 단호한 눈썹, 일자 입 */
    STERN,
    /** 깜빡이 누락: 눈썹을 올리고 입을 동그랗게 */
    ALERT,
    /** 칭찬: 웃는 눈, 활짝 연 입 */
    HAPPY,
}

private val FaceFill = Color(0xFFFFDDB8)
private val Hair = Color(0xFF3B2A20)
private val Ink = Color(0xFF2B1D14)
private val Cheek = Color(0xFFFF8A80)

/**
 * 동그란 강사 얼굴. 바깥 고리는 코칭 신호색([ring])이라 표정과 색이 같은 뜻을 전한다.
 * 그림은 전부 Canvas로 그려 리소스가 필요 없다.
 */
@Composable
fun CoachFace(mood: CoachMood, ring: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = center
        val outer = size.minDimension / 2
        val r = outer * 0.88f
        drawCircle(ring, outer, c)
        drawCircle(FaceFill, r, c)

        // 머리카락: 얼굴 원 안쪽 위만 칠한다
        val faceClip = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c, r)) }
        clipPath(faceClip) {
            drawOval(Hair, Offset(c.x - r * 1.15f, c.y - r * 1.62f), Size(r * 2.3f, r * 1.15f))
        }

        val line = Stroke(width = r * 0.07f, cap = StrokeCap.Round)
        val eyeY = c.y + r * 0.02f
        val eyeDx = r * 0.36f

        // 볼
        val cheekAlpha = if (mood == CoachMood.HAPPY) 0.55f else 0.3f
        listOf(-1f, 1f).forEach { s ->
            drawCircle(Cheek.copy(alpha = cheekAlpha), r * 0.13f, Offset(c.x + s * r * 0.55f, c.y + r * 0.25f))
        }

        // 눈
        listOf(-1f, 1f).forEach { s ->
            val e = Offset(c.x + s * eyeDx, eyeY)
            when (mood) {
                CoachMood.HAPPY -> drawArc(
                    Ink, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                    topLeft = Offset(e.x - r * 0.13f, e.y - r * 0.08f), size = Size(r * 0.26f, r * 0.22f), style = line,
                )
                CoachMood.ALERT -> {
                    drawCircle(Color.White, r * 0.13f, e)
                    drawCircle(Ink, r * 0.08f, e)
                }
                else -> drawCircle(Ink, r * 0.09f, e)
            }
        }

        // 눈썹: (안쪽 끝 높이, 바깥 끝 높이)
        val (inner, outerB) = when (mood) {
            CoachMood.CALM -> -0.30f to -0.33f
            CoachMood.STERN -> -0.22f to -0.36f
            CoachMood.ALERT -> -0.42f to -0.40f
            CoachMood.HAPPY -> -0.36f to -0.33f
        }
        listOf(-1f, 1f).forEach { s ->
            drawLine(
                Ink,
                Offset(c.x + s * r * 0.20f, c.y + r * inner),
                Offset(c.x + s * r * 0.52f, c.y + r * outerB),
                strokeWidth = line.width, cap = StrokeCap.Round,
            )
        }

        drawMouth(mood, c, r, line)
    }
}

private fun DrawScope.drawMouth(mood: CoachMood, c: Offset, r: Float, line: Stroke) {
    val y = c.y + r * 0.42f
    when (mood) {
        CoachMood.CALM -> drawArc(
            Ink, startAngle = 20f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(c.x - r * 0.28f, y - r * 0.2f), size = Size(r * 0.56f, r * 0.3f), style = line,
        )
        CoachMood.HAPPY -> drawArc(
            Ink, startAngle = 0f, sweepAngle = 180f, useCenter = true,
            topLeft = Offset(c.x - r * 0.3f, y - r * 0.2f), size = Size(r * 0.6f, r * 0.42f),
        )
        CoachMood.STERN -> drawLine(
            Ink, Offset(c.x - r * 0.2f, y), Offset(c.x + r * 0.2f, y),
            strokeWidth = line.width, cap = StrokeCap.Round,
        )
        CoachMood.ALERT -> drawOval(
            Ink, topLeft = Offset(c.x - r * 0.1f, y - r * 0.1f), size = Size(r * 0.2f, r * 0.24f),
        )
    }
}

@Preview
@Composable
private fun CoachFacePreview() {
    SideCoachTheme {
        val s = SideCoachTheme.signal
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CoachFace(CoachMood.CALM, s.neutral.accent, Modifier.size(160.dp))
            CoachFace(CoachMood.STERN, s.brake.accent, Modifier.size(160.dp))
            CoachFace(CoachMood.ALERT, s.turn.accent, Modifier.size(160.dp))
            CoachFace(CoachMood.HAPPY, s.praise.accent, Modifier.size(160.dp))
        }
    }
}

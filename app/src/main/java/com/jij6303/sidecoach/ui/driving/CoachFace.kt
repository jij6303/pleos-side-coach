package com.jij6303.sidecoach.ui.driving

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
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

private val SkinLight = Color(0xFFFFEEDF)
private val Skin = Color(0xFFFFDCC0)
private val SkinShade = Color(0xFFE8B48E)
private val HairLight = Color(0xFF6B4A3A)
private val HairDark = Color(0xFF2A1B15)
private val Ink = Color(0xFF2B1D14)
private val Lip = Color(0xFFD45A68)
private val Blush = Color(0xFFFF8F9B)
private val Blazer = Color(0xFF2E3B55)

/**
 * 여성 연수 강사 초상. 원형 배지 안에 머리·어깨까지 그리고, 그라데이션 음영·하이라이트·바닥 그림자로 입체감을 낸다(2.5D).
 * 바깥 고리는 코칭 신호색([ring])이라 표정과 색이 같은 뜻을 전한다. 전부 Canvas로 그려 리소스가 필요 없다.
 */
@Composable
fun CoachFace(mood: CoachMood, ring: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = center
        val outer = size.minDimension * 0.47f
        val disc = outer * 0.9f

        // 바닥 그림자: 배지가 떠 있는 느낌
        drawCircle(
            Brush.radialGradient(
                listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent),
                center = c + Offset(0f, outer * 0.08f), radius = outer * 1.06f,
            ),
            radius = outer * 1.06f, center = c + Offset(0f, outer * 0.08f),
        )
        // 고리: 왼쪽 위가 밝은 구슬처럼
        drawCircle(
            Brush.linearGradient(
                listOf(lerp(ring, Color.White, 0.35f), ring, lerp(ring, Color.Black, 0.25f)),
                start = c - Offset(outer, outer), end = c + Offset(outer, outer),
            ),
            radius = outer, center = c,
        )
        // 배경 원
        drawCircle(
            Brush.radialGradient(
                listOf(Color.White, lerp(ring, Color.White, 0.8f)),
                center = c - Offset(disc * 0.3f, disc * 0.4f), radius = disc * 1.6f,
            ),
            radius = disc, center = c,
        )

        clipPath(Path().apply { addOval(Rect(c, disc)) }) {
            drawPortrait(c, disc, mood, ring)
        }

        // 고리 광택
        drawArc(
            Color.White.copy(alpha = 0.45f), startAngle = 200f, sweepAngle = 60f, useCenter = false,
            topLeft = c - Offset(outer * 0.95f, outer * 0.95f), size = Size(outer * 1.9f, outer * 1.9f),
            style = Stroke(width = outer * 0.04f, cap = StrokeCap.Round),
        )
    }
}

private fun DrawScope.drawPortrait(c: Offset, R: Float, mood: CoachMood, ring: Color) {
    fun p(x: Float, y: Float) = Offset(c.x + x * R, c.y + y * R)
    fun sz(w: Float, h: Float) = Size(w * R, h * R)
    val hairBrush = Brush.verticalGradient(listOf(HairLight, HairDark), startY = c.y - 0.8f * R, endY = c.y + 0.9f * R)

    // 뒷머리: 어깨까지 내려오는 단발
    drawRoundRect(hairBrush, p(-0.64f, -0.74f), sz(1.28f, 1.5f), androidx.compose.ui.geometry.CornerRadius(0.6f * R))

    // 어깨·재킷
    drawOval(
        Brush.verticalGradient(listOf(lerp(Blazer, Color.White, 0.15f), Blazer), startY = c.y + 0.55f * R, endY = c.y + R),
        p(-0.9f, 0.58f), sz(1.8f, 1.0f),
    )
    // 셔츠 깃(V)
    drawPath(
        Path().apply { moveTo(p(-0.2f, 0.6f).x, p(0f, 0.6f).y); lineTo(p(0f, 0.86f).x, p(0f, 0.86f).y); lineTo(p(0.2f, 0.6f).x, p(0f, 0.6f).y); close() },
        Color.White,
    )
    // 넥 배지: 신호색 작은 점
    drawCircle(ring, 0.05f * R, p(0.42f, 0.78f))

    // 목
    drawRect(Brush.verticalGradient(listOf(SkinShade, Skin), startY = c.y + 0.3f * R, endY = c.y + 0.62f * R), p(-0.13f, 0.3f), sz(0.26f, 0.34f))

    // 귀 + 귀걸이
    listOf(-1f, 1f).forEach { s ->
        drawOval(SkinShade, p(s * 0.49f - 0.07f, -0.06f), sz(0.14f, 0.22f))
        drawCircle(Color.White, 0.035f * R, p(s * 0.49f, 0.2f))
    }

    // 얼굴: 왼쪽 위에서 빛이 오는 방사형 음영
    val faceTopLeft = p(-0.48f, -0.55f)
    val faceSize = sz(0.96f, 1.12f)
    drawOval(
        Brush.radialGradient(listOf(SkinLight, Skin, SkinShade), center = p(-0.14f, -0.22f), radius = 0.85f * R),
        faceTopLeft, faceSize,
    )

    // 앞머리: 옆으로 넘긴 형태
    val bangs = Path().apply {
        val a = p(-0.52f, -0.08f)
        moveTo(a.x, a.y)
        p(-0.3f, -0.42f).let { m -> p(0.05f, -0.36f).let { e -> quadraticTo(m.x, m.y, e.x, e.y) } }
        p(0.35f, -0.32f).let { m -> p(0.52f, -0.12f).let { e -> quadraticTo(m.x, m.y, e.x, e.y) } }
        // 정수리는 둥글게
        val (r1, r2, top) = Triple(p(0.62f, -0.6f), p(0.36f, -0.84f), p(0f, -0.84f))
        cubicTo(r1.x, r1.y, r2.x, r2.y, top.x, top.y)
        val (l1, l2) = p(-0.36f, -0.84f) to p(-0.62f, -0.6f)
        cubicTo(l1.x, l1.y, l2.x, l2.y, a.x, a.y)
        close()
    }
    drawPath(bangs, hairBrush)
    // 머리 윤기
    drawArc(
        Color.White.copy(alpha = 0.22f), startAngle = 200f, sweepAngle = 70f, useCenter = false,
        topLeft = p(-0.5f, -0.72f), size = sz(1.0f, 0.9f),
        style = Stroke(width = 0.06f * R, cap = StrokeCap.Round),
    )

    val line = Stroke(width = 0.035f * R, cap = StrokeCap.Round)

    // 볼 홍조
    val blushAlpha = if (mood == CoachMood.HAPPY) 0.6f else 0.35f
    listOf(-1f, 1f).forEach { s ->
        val bc = p(s * 0.27f, 0.16f)
        drawCircle(
            Brush.radialGradient(listOf(Blush.copy(alpha = blushAlpha), Color.Transparent), center = bc, radius = 0.12f * R),
            radius = 0.12f * R, center = bc,
        )
    }

    // 눈썹: (안쪽 끝 y, 바깥 끝 y)
    val (inner, outerB) = when (mood) {
        CoachMood.CALM -> -0.15f to -0.17f
        CoachMood.STERN -> -0.10f to -0.19f
        CoachMood.ALERT -> -0.23f to -0.21f
        CoachMood.HAPPY -> -0.19f to -0.17f
    }
    listOf(-1f, 1f).forEach { s ->
        drawLine(HairDark, p(s * 0.08f, inner), p(s * 0.3f, outerB), strokeWidth = 0.03f * R, cap = StrokeCap.Round)
    }

    // 눈
    listOf(-1f, 1f).forEach { s ->
        val e = p(s * 0.19f, 0.0f)
        when (mood) {
            CoachMood.HAPPY -> drawArc(
                Ink, startAngle = 200f, sweepAngle = 140f, useCenter = false,
                topLeft = Offset(e.x - 0.08f * R, e.y - 0.04f * R), size = sz(0.16f, 0.12f), style = line,
            )
            else -> {
                val big = mood == CoachMood.ALERT
                val w = if (big) 0.1f else 0.08f
                val h = if (big) 0.13f else 0.1f
                if (big) drawOval(Color.White, Offset(e.x - 0.075f * R, e.y - 0.085f * R), sz(0.15f, 0.17f))
                drawOval(Ink, Offset(e.x - w / 2 * R, e.y - h / 2 * R), sz(w, h))
                // 눈 반짝임
                drawCircle(Color.White, 0.018f * R, Offset(e.x - 0.015f * R, e.y - 0.025f * R))
                // 바깥쪽 속눈썹
                drawLine(
                    Ink, Offset(e.x + s * w / 2 * R, e.y - h / 4 * R), Offset(e.x + s * (w / 2 + 0.035f) * R, e.y - (h / 2) * R),
                    strokeWidth = 0.016f * R, cap = StrokeCap.Round,
                )
            }
        }
    }

    // 코: 옅은 그림자 한 획
    drawArc(
        SkinShade, startAngle = 20f, sweepAngle = 140f, useCenter = false,
        topLeft = p(-0.05f, 0.1f), size = sz(0.1f, 0.08f), style = Stroke(width = 0.025f * R, cap = StrokeCap.Round),
    )

    // 입
    val my = 0.3f
    when (mood) {
        CoachMood.CALM -> drawArc(
            Lip, startAngle = 20f, sweepAngle = 140f, useCenter = false,
            topLeft = p(-0.13f, my - 0.08f), size = sz(0.26f, 0.13f), style = Stroke(width = 0.04f * R, cap = StrokeCap.Round),
        )
        CoachMood.HAPPY -> {
            drawArc(Lip, startAngle = 0f, sweepAngle = 180f, useCenter = true, topLeft = p(-0.15f, my - 0.09f), size = sz(0.3f, 0.22f))
            drawArc(Color.White, startAngle = 0f, sweepAngle = 180f, useCenter = true, topLeft = p(-0.11f, my - 0.07f), size = sz(0.22f, 0.07f))
        }
        CoachMood.STERN -> drawLine(Lip, p(-0.09f, my), p(0.09f, my), strokeWidth = 0.04f * R, cap = StrokeCap.Round)
        CoachMood.ALERT -> drawOval(Lip, p(-0.05f, my - 0.05f), sz(0.1f, 0.12f))
    }
}

@Preview
@Composable
private fun CoachFacePreview() {
    SideCoachTheme {
        val s = SideCoachTheme.signal
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            CoachFace(CoachMood.CALM, s.neutral.accent, Modifier.size(200.dp))
            CoachFace(CoachMood.STERN, s.brake.accent, Modifier.size(200.dp))
            CoachFace(CoachMood.ALERT, s.turn.accent, Modifier.size(200.dp))
            CoachFace(CoachMood.HAPPY, s.praise.accent, Modifier.size(200.dp))
        }
    }
}

package com.jij6303.sidecoach.ui.driving

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
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
    /** 급제동·과속: 내려간 눈썹, 일자 입 */
    STERN,
    /** 깜빡이 누락: 눈썹을 올리고 눈을 크게, 입을 동그랗게 */
    ALERT,
    /** 칭찬: 웃는 눈, 활짝 연 입 */
    HAPPY,
}

private val SkinLight = Color(0xFFFFF1E6)
private val Skin = Color(0xFFFFDFC8)
private val SkinShade = Color(0xFFF0C3A2)
private val HairLight = Color(0xFF6A4434)
private val HairDark = Color(0xFF2E1C16)
private val Ink = Color(0xFF2B1D14)
private val EyeBrown = Color(0xFF6B3E26)
private val Lip = Color(0xFFE0707A)
private val Blush = Color(0xFFFF8F9B)

/**
 * 여성 연수 강사 초상(풀뱅 앞머리·긴 생머리·통통한 볼·큰 눈의 밝은 캐릭터). 원형 배지 안에 머리·어깨까지 그리고,
 * 그라데이션 음영·하이라이트·바닥 그림자로 입체감을 낸다(2.5D).
 * 바깥 고리와 옷 색은 코칭 신호색([ring])이라 표정과 색이 같은 뜻을 전한다. 전부 Canvas로 그려 리소스가 필요 없다.
 */
@Composable
fun CoachFace(mood: CoachMood, ring: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val c = center
        val outer = size.minDimension * 0.47f
        val disc = outer * 0.9f

        // 바닥 그림자: 배지가 떠 있는 느낌
        val shadowC = c + Offset(0f, outer * 0.08f)
        drawCircle(
            Brush.radialGradient(listOf(Color.Black.copy(alpha = 0.22f), Color.Transparent), center = shadowC, radius = outer * 1.06f),
            radius = outer * 1.06f, center = shadowC,
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
    val hairBrush = Brush.verticalGradient(listOf(HairLight, HairDark), startY = c.y - 0.8f * R, endY = c.y + R)

    // 뒷머리: 어깨 아래까지 내려오는 긴 머리
    drawRoundRect(hairBrush, p(-0.68f, -0.8f), sz(1.36f, 1.95f), CornerRadius(0.64f * R))

    // 목·어깨(신호색 파스텔 상의)
    drawRect(
        Brush.verticalGradient(listOf(SkinShade, Skin), startY = c.y + 0.4f * R, endY = c.y + 0.7f * R),
        p(-0.1f, 0.42f), sz(0.2f, 0.3f),
    )
    drawOval(
        Brush.verticalGradient(
            listOf(lerp(ring, Color.White, 0.55f), lerp(ring, Color.White, 0.25f)),
            startY = c.y + 0.66f * R, endY = c.y + R,
        ),
        p(-0.8f, 0.66f), sz(1.6f, 0.9f),
    )
    drawPath(
        Path().apply {
            val a = p(-0.14f, 0.67f); val b = p(0f, 0.86f); val d = p(0.14f, 0.67f)
            moveTo(a.x, a.y); lineTo(b.x, b.y); lineTo(d.x, d.y); close()
        },
        Color.White,
    )
    // 앞으로 넘어온 머리카락이 어깨를 살짝 덮는다
    drawRoundRect(hairBrush, p(-0.68f, 0.1f), sz(0.22f, 0.78f), CornerRadius(0.11f * R))
    drawRoundRect(hairBrush, p(0.46f, 0.1f), sz(0.22f, 0.78f), CornerRadius(0.11f * R))

    // 얼굴: 동그랗고 부드럽게, 왼쪽 위에서 빛
    drawOval(
        Brush.radialGradient(listOf(SkinLight, Skin, SkinShade), center = p(-0.12f, -0.15f), radius = 0.9f * R),
        p(-0.53f, -0.46f), sz(1.06f, 0.98f),
    )

    // 풀뱅 앞머리: 눈썹 바로 위에서 일자로 떨어지고, 옆머리가 볼을 감싼다
    val bang = Path().apply {
        fun mv(o: Offset) = moveTo(o.x, o.y)
        fun ln(o: Offset) = lineTo(o.x, o.y)
        fun cu(a: Offset, b: Offset, e: Offset) = cubicTo(a.x, a.y, b.x, b.y, e.x, e.y)
        mv(p(-0.5f, 0.2f))
        // 왼쪽 옆머리 안쪽 선 → 앞머리 끝선(가운데가 살짝 내려온 일자)
        cu(p(-0.5f, 0.0f), p(-0.48f, -0.14f), p(-0.42f, -0.15f))
        cu(p(-0.2f, -0.12f), p(0.2f, -0.12f), p(0.42f, -0.15f))
        cu(p(0.48f, -0.14f), p(0.5f, 0.0f), p(0.5f, 0.2f))
        // 바깥 윤곽: 둥근 정수리
        ln(p(0.66f, 0.2f))
        cu(p(0.72f, -0.5f), p(0.42f, -0.8f), p(0f, -0.8f))
        cu(p(-0.42f, -0.8f), p(-0.72f, -0.5f), p(-0.66f, 0.2f))
        close()
    }
    drawPath(bang, hairBrush)
    // 앞머리 결: 아래로 떨어지는 옅은 선 몇 가닥
    for (x in listOf(-0.26f, -0.08f, 0.1f, 0.28f)) {
        drawLine(
            HairDark.copy(alpha = 0.35f), p(x - 0.02f, -0.38f), p(x, -0.16f),
            strokeWidth = 0.012f * R, cap = StrokeCap.Round,
        )
    }
    // 머리 윤기
    drawArc(
        Color.White.copy(alpha = 0.25f), startAngle = 205f, sweepAngle = 55f, useCenter = false,
        topLeft = p(-0.56f, -0.74f), size = sz(1.12f, 1.0f),
        style = Stroke(width = 0.05f * R, cap = StrokeCap.Round),
    )

    // 볼 홍조
    val blushAlpha = if (mood == CoachMood.HAPPY) 0.75f else 0.6f
    for (s in listOf(-1f, 1f)) {
        val bc = p(s * 0.3f, 0.26f)
        drawOval(
            Brush.radialGradient(listOf(Blush.copy(alpha = blushAlpha), Color.Transparent), center = bc, radius = 0.12f * R),
            Offset(bc.x - 0.12f * R, bc.y - 0.07f * R), sz(0.24f, 0.14f),
        )
    }

    // 눈썹: 얇고 부드러운 호. 표정마다 높이·안쪽 기울기만 바꾼다
    val (browY, tilt) = when (mood) {
        CoachMood.CALM -> -0.08f to 0f
        CoachMood.HAPPY -> -0.09f to 0f
        CoachMood.ALERT -> -0.11f to -0.01f
        CoachMood.STERN -> -0.06f to 0.035f
    }
    for (s in listOf(-1f, 1f)) {
        val inner = p(s * 0.1f, browY + tilt)
        val mid = p(s * 0.2f, browY - 0.03f)
        val out = p(s * 0.3f, browY)
        drawPath(
            Path().apply { moveTo(inner.x, inner.y); quadraticTo(mid.x, mid.y, out.x, out.y) },
            HairLight, style = Stroke(width = 0.022f * R, cap = StrokeCap.Round),
        )
    }

    // 눈
    val lash = Stroke(width = 0.03f * R, cap = StrokeCap.Round)
    for (s in listOf(-1f, 1f)) {
        val e = p(s * 0.21f, 0.09f)
        if (mood == CoachMood.HAPPY) {
            drawArc(
                Ink, startAngle = 195f, sweepAngle = 150f, useCenter = false,
                topLeft = Offset(e.x - 0.075f * R, e.y - 0.04f * R), size = sz(0.15f, 0.12f), style = lash,
            )
            continue
        }
        val w = if (mood == CoachMood.ALERT) 0.15f else 0.135f
        val h = if (mood == CoachMood.ALERT) 0.19f else 0.17f
        drawOval(
            Brush.verticalGradient(listOf(Ink, EyeBrown), startY = e.y - h / 2 * R, endY = e.y + h / 2 * R),
            Offset(e.x - w / 2 * R, e.y - h / 2 * R), sz(w, h),
        )
        drawCircle(Color.White, 0.03f * R, Offset(e.x - 0.02f * R, e.y - 0.035f * R))
        drawCircle(Color.White.copy(alpha = 0.85f), 0.014f * R, Offset(e.x + 0.025f * R, e.y + 0.035f * R))
        // 윗눈꺼풀 선 + 바깥 꼬리
        drawArc(
            Ink, startAngle = 200f, sweepAngle = 140f, useCenter = false,
            topLeft = Offset(e.x - (w / 2 + 0.012f) * R, e.y - (h / 2 + 0.008f) * R), size = sz(w + 0.024f, h * 0.9f), style = lash,
        )
        drawLine(
            Ink,
            Offset(e.x + s * (w / 2 + 0.005f) * R, e.y - h * 0.22f * R),
            Offset(e.x + s * (w / 2 + 0.035f) * R, e.y - h * 0.36f * R),
            strokeWidth = 0.022f * R, cap = StrokeCap.Round,
        )
    }

    // 코: 작은 그림자 점
    drawCircle(SkinShade, 0.016f * R, p(0f, 0.25f))

    // 입
    val my = 0.36f
    when (mood) {
        CoachMood.CALM -> drawArc(
            Lip, startAngle = 25f, sweepAngle = 130f, useCenter = false,
            topLeft = p(-0.1f, my - 0.07f), size = sz(0.2f, 0.11f), style = Stroke(width = 0.028f * R, cap = StrokeCap.Round),
        )
        CoachMood.HAPPY -> {
            drawArc(Lip, startAngle = 0f, sweepAngle = 180f, useCenter = true, topLeft = p(-0.11f, my - 0.07f), size = sz(0.22f, 0.17f))
            drawArc(Color.White, startAngle = 0f, sweepAngle = 180f, useCenter = true, topLeft = p(-0.085f, my - 0.055f), size = sz(0.17f, 0.05f))
        }
        CoachMood.STERN -> drawLine(Lip, p(-0.055f, my), p(0.055f, my), strokeWidth = 0.026f * R, cap = StrokeCap.Round)
        CoachMood.ALERT -> drawOval(Lip, p(-0.035f, my - 0.03f), sz(0.07f, 0.08f))
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

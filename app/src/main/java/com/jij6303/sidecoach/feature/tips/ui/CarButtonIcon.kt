package com.jij6303.sidecoach.feature.tips.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.jij6303.sidecoach.feature.tips.CarButton
import com.jij6303.sidecoach.ui.theme.SideCoachTheme
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/** 차량 하단 공조 바처럼 검은 바탕에 흰 선. */
private val ButtonBackground = Color(0xFF111111)
private val ButtonInk = Color.White

/**
 * 차량 버튼 모양 타일 (#29). 차량 UI 아이콘(선 굵기·모양)을 참고해 Canvas로 직접 그린다.
 * 시스템 앱의 그림 파일은 저작권 문제로 쓰지 않는다.
 */
@Composable
fun CarButtonTile(button: CarButton, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.width(140.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Surface(shape = RoundedCornerShape(20.dp), color = ButtonBackground, modifier = Modifier.size(112.dp)) {
            Box(contentAlignment = Alignment.Center) {
                if (button == CarButton.AIR_CONDITIONER) {
                    Text("A/C", fontSize = 34.sp, fontWeight = FontWeight.Medium, color = ButtonInk)
                } else {
                    Canvas(Modifier.size(80.dp)) { drawCarButton(button) }
                }
            }
        }
        Text(
            button.label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

/** 버튼 타일 여러 개를 한 줄로. */
@Composable
fun CarButtonRow(buttons: List<CarButton>, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        buttons.forEach { CarButtonTile(it) }
    }
}

private fun DrawScope.drawCarButton(button: CarButton) {
    val stroke = Stroke(width = size.width * 0.055f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (button) {
        CarButton.FRONT_DEFROST -> {
            // 위가 넓고 둥근 앞유리 + 위로 향하는 화살표 세 개
            val glass = Path().apply {
                moveTo(x(0.24f), y(0.80f))
                lineTo(x(0.76f), y(0.80f))
                lineTo(x(0.90f), y(0.28f))
                quadraticTo(x(0.50f), y(0.14f), x(0.10f), y(0.28f))
                close()
            }
            drawPath(glass, ButtonInk, style = stroke)
            listOf(0.36f, 0.50f, 0.64f).forEach { heatArrow(it, 0.74f, 0.36f, stroke) }
        }
        CarButton.REAR_DEFROST -> {
            drawRoundRect(
                ButtonInk,
                topLeft = Offset(x(0.12f), y(0.24f)),
                size = Size(x(0.76f), y(0.56f)),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(x(0.04f)),
                style = stroke,
            )
            listOf(0.36f, 0.50f, 0.64f).forEach { heatArrow(it, 0.72f, 0.36f, stroke) }
        }
        CarButton.RECIRCULATION -> {
            carSide(stroke)
            // 지붕선 끝에서 안쪽으로 말려 돌아 나가는 화살표 (⊂→)
            val curl = Path().apply {
                moveTo(x(0.70f), y(0.30f))
                arcTo(Rect(x(0.62f), y(0.30f), x(0.78f), y(0.48f)), -90f, -180f, false)
                lineTo(x(0.86f), y(0.48f))
            }
            drawPath(curl, ButtonInk, style = stroke)
            arrowHead(Offset(x(0.90f), y(0.48f)), Offset(x(0.80f), y(0.48f)), stroke)
        }
        CarButton.FRESH_AIR -> {
            carSide(stroke)
            // 바깥에서 차 안으로 들어오는 화살표
            val air = Path().apply {
                moveTo(x(0.96f), y(0.12f))
                quadraticTo(x(0.70f), y(0.12f), x(0.62f), y(0.44f))
            }
            drawPath(air, ButtonInk, style = stroke)
            arrowHead(Offset(x(0.61f), y(0.48f)), Offset(x(0.66f), y(0.26f)), stroke)
        }
        CarButton.CHILD_LOCK -> {
            // 아이 옆얼굴(머리 곡선·귀·앞머리) + 자물쇠
            drawArc(
                ButtonInk, startAngle = 100f, sweepAngle = 270f, useCenter = false,
                topLeft = Offset(x(0.14f), y(0.22f)), size = Size(x(0.56f), y(0.56f)), style = stroke,
            )
            drawCircle(ButtonInk, radius = x(0.05f), center = Offset(x(0.16f), y(0.50f)), style = stroke)
            val curl = Path().apply {
                moveTo(x(0.42f), y(0.22f))
                quadraticTo(x(0.36f), y(0.10f), x(0.46f), y(0.10f))
                quadraticTo(x(0.52f), y(0.14f), x(0.46f), y(0.18f))
            }
            drawPath(curl, ButtonInk, style = stroke)
            drawCircle(ButtonInk, radius = x(0.025f), center = Offset(x(0.50f), y(0.44f)))
            // 자물쇠
            drawRect(ButtonInk, topLeft = Offset(x(0.60f), y(0.60f)), size = Size(x(0.26f), y(0.22f)))
            drawArc(
                ButtonInk, startAngle = 180f, sweepAngle = 180f, useCenter = false,
                topLeft = Offset(x(0.64f), y(0.48f)), size = Size(x(0.18f), y(0.22f)), style = stroke,
            )
        }
        CarButton.AIR_CONDITIONER -> Unit // 글자로 그린다
    }
}

private fun DrawScope.x(f: Float) = size.width * f
private fun DrawScope.y(f: Float) = size.height * f

/** 왼쪽을 향한 차 옆모습 (앞범퍼·보닛·앞유리·지붕선·앞바퀴). 뒤쪽은 열어 둔다. */
private fun DrawScope.carSide(stroke: Stroke) {
    val body = Path().apply {
        moveTo(x(0.24f), y(0.72f))
        lineTo(x(0.12f), y(0.72f))
        quadraticTo(x(0.06f), y(0.70f), x(0.07f), y(0.62f))
        lineTo(x(0.08f), y(0.56f))
        lineTo(x(0.30f), y(0.50f))
        lineTo(x(0.46f), y(0.30f))
        lineTo(x(0.70f), y(0.30f))
    }
    drawPath(body, ButtonInk, style = stroke)
    drawLine(ButtonInk, Offset(x(0.44f), y(0.72f)), Offset(x(0.70f), y(0.72f)), strokeWidth = stroke.width, cap = StrokeCap.Round)
    drawCircle(ButtonInk, radius = x(0.09f), center = Offset(x(0.34f), y(0.74f)), style = stroke)
}

/** 아래는 짧게 지그재그(열선), 위는 곧게 올라가는 화살표. */
private fun DrawScope.heatArrow(cx: Float, fromY: Float, toY: Float, stroke: Stroke) {
    val zig = x(0.025f)
    val path = Path().apply {
        moveTo(x(cx), y(fromY))
        lineTo(x(cx) - zig, y(fromY - 0.05f))
        lineTo(x(cx) + zig, y(fromY - 0.10f))
        lineTo(x(cx), y(fromY - 0.14f))
        lineTo(x(cx), y(toY))
    }
    drawPath(path, ButtonInk, style = stroke)
    arrowHead(Offset(x(cx), y(toY)), Offset(x(cx), y(toY + 0.1f)), stroke)
}

/** [from]에서 [tip] 방향 화살촉. */
private fun DrawScope.arrowHead(tip: Offset, from: Offset, stroke: Stroke) {
    val angle = atan2(tip.y - from.y, tip.x - from.x)
    val len = size.width * 0.09f
    val spread = Math.toRadians(38.0).toFloat()
    listOf(angle + Math.PI.toFloat() - spread, angle + Math.PI.toFloat() + spread).forEach { a ->
        drawLine(ButtonInk, tip, Offset(tip.x + len * cos(a), tip.y + len * sin(a)), strokeWidth = stroke.width, cap = StrokeCap.Round)
    }
}

@Preview(widthDp = 1000, heightDp = 220)
@Composable
private fun CarButtonsPreview() {
    SideCoachTheme {
        Surface { CarButtonRow(CarButton.entries, Modifier.padding(16.dp)) }
    }
}

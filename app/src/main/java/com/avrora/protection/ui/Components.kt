package com.avrora.protection.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.roundToInt

/**
 * Круговой индикатор как в HUD-референсах: 24 сегмента по кругу,
 * закрашенные по проценту, и число в центре.
 */
@Composable
fun RadialGauge(score: Int, color: Color, modifier: Modifier = Modifier) {
    Box(modifier = modifier.size(170.dp), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val stroke = 12.dp.toPx()
            val inset = stroke / 2 + 6.dp.toPx()
            val arc = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val segments = 24
            val step = 360f / segments
            val filled = (score.coerceIn(0, 100) / 100f * segments).roundToInt()

            for (i in 0 until segments) {
                drawArc(
                    color = if (i < filled) color else AuroraCyan.copy(alpha = 0.15f),
                    startAngle = -90f + i * step + 2f,
                    sweepAngle = step - 4f,
                    useCenter = false,
                    topLeft = topLeft,
                    size = arc,
                    style = Stroke(width = stroke, cap = StrokeCap.Butt)
                )
            }
            drawCircle(
                color = AuroraCyan.copy(alpha = 0.35f),
                radius = arc.width / 2 - stroke,
                center = center,
                style = Stroke(width = 1.dp.toPx())
            )
        }
        Text(
            text = "$score",
            color = color,
            fontSize = 40.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/** Оранжевая плашка-заголовок, как «CIRCLE» и «HUD FUI» в референсах. */
@Composable
fun HudTag(text: String, color: Color = AuroraOrange, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .background(color)
            .padding(horizontal = 12.dp, vertical = 4.dp)
    ) {
        Text(
            text = text,
            color = AuroraBackground,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace
        )
    }
}

/** Полоса из отдельных сегментов, как индикаторы загрузки в референсах. */
@Composable
fun SegmentBar(progress: Float, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier = modifier.fillMaxWidth().height(14.dp)) {
        val segments = 20
        val gap = 3.dp.toPx()
        val w = (size.width - gap * (segments - 1)) / segments
        val filled = (progress.coerceIn(0f, 1f) * segments).roundToInt()
        for (i in 0 until segments) {
            drawRect(
                color = if (i < filled) color else AuroraCyan.copy(alpha = 0.12f),
                topLeft = Offset(i * (w + gap), 0f),
                size = Size(w, size.height)
            )
        }
    }
}

package com.avrora.protection.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.unit.dp

// Цвета из референсов: чёрный фон, неоновый голубой, оранжевый акцент HUD.
val AuroraBackground = Color(0xFF050A0F)
val AuroraPanel = Color(0xFF0B1620)
val AuroraCyan = Color(0xFF00E5FF)
val AuroraOrange = Color(0xFFFF8A00)
val AuroraText = Color(0xFFD6F6FF)
val AuroraMuted = Color(0xFF6F8FA0)

val VerdictGreen = Color(0xFF3CFF9A)
val VerdictYellow = Color(0xFFFFD000)
val VerdictRed = Color(0xFFFF3B5C)

private val AuroraDarkColors = darkColorScheme(
    background = AuroraBackground,
    surface = AuroraPanel,
    primary = AuroraCyan,
    secondary = AuroraOrange,
    onBackground = AuroraText,
    onSurface = AuroraText,
    onPrimary = AuroraBackground
)

@Composable
fun AuroraTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = AuroraDarkColors, content = content)
}

/**
 * HUD-рамка: тонкая линия с срезанными углами и засечками сверху.
 */
fun Modifier.hudFrame(color: Color = AuroraCyan): Modifier = this.drawBehind {
    val stroke = Stroke(width = 1.5.dp.toPx())
    val cut = 14.dp.toPx()
    val w = size.width
    val h = size.height
    val path = Path().apply {
        moveTo(cut, 0f)
        lineTo(w - cut, 0f)
        lineTo(w, cut)
        lineTo(w, h - cut)
        lineTo(w - cut, h)
        lineTo(cut, h)
        lineTo(0f, h - cut)
        lineTo(0f, cut)
        close()
    }
    drawPath(path, color = color, style = stroke)
    val tick = 4.dp.toPx()
    drawLine(color, Offset(w * 0.3f, 0f), Offset(w * 0.3f, -tick), strokeWidth = stroke.width)
    drawLine(color, Offset(w * 0.7f, 0f), Offset(w * 0.7f, -tick), strokeWidth = stroke.width)
}

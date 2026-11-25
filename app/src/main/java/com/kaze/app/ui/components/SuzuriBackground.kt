package com.kaze.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.dp
import com.kaze.app.ui.theme.SuzuriColors

/**
 * Draws the subtle Suzuri dark inkstone 20dp technical grid behind any screen or sheet.
 */
fun Modifier.suzuriGridBackground(): Modifier = this.drawBehind {
    drawRect(color = SuzuriColors.Base)
    val step = 20.dp.toPx()
    val lineColor = SuzuriColors.Ivory.copy(alpha = 0.032f)
    var x = 0f
    while (x <= size.width) {
        drawLine(
            color = lineColor,
            start = Offset(x, 0f),
            end = Offset(x, size.height),
            strokeWidth = 1f
        )
        x += step
    }
    var y = 0f
    while (y <= size.height) {
        drawLine(
            color = lineColor,
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f
        )
        y += step
    }
}

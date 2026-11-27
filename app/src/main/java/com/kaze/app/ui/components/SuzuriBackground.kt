package com.kaze.app.ui.components

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kaze.app.ui.theme.SuzuriColors

/**
 * Draws the subtle Suzuri dark inkstone 20dp technical grid behind any screen or sheet,
 * cached as a single Path per canvas size via drawWithCache.
 */
fun Modifier.suzuriGridBackground(): Modifier = this.drawWithCache {
    val step = 20.dp.toPx()
    val lineColor = SuzuriColors.Ivory.copy(alpha = 0.032f)
    val gridPath = Path().apply {
        var x = 0f
        while (x <= size.width) {
            moveTo(x, 0f)
            lineTo(x, size.height)
            x += step
        }
        var y = 0f
        while (y <= size.height) {
            moveTo(0f, y)
            lineTo(size.width, y)
            y += step
        }
    }
    val gridStroke = Stroke(width = 1f)

    onDrawBehind {
        drawRect(color = SuzuriColors.Base)
        drawPath(path = gridPath, color = lineColor, style = gridStroke)
    }
}

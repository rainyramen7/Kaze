package com.kaze.app.ui.components

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kaze.app.ui.theme.SuzuriColors

/** Prebuilt native Path/Stroke set for zero-allocation draws inside animated Canvas views. */
class CachedKakiGeometry(origin: Offset, boxSize: Float) {
    private val s = boxSize / 24f
    private val ox = origin.x
    private val oy = origin.y

    val stemStart = Offset(ox + 12f * s, oy + 3.2f * s)
    val stemEnd = Offset(ox + 12f * s, oy + 6.4f * s)
    val stemWidth = (1.3f * s).coerceAtLeast(1.4f)

    val calyx = Path().apply {
        moveTo(ox + 7.8f * s, oy + 7.3f * s)
        quadraticTo(ox + 12f * s, oy + 5.3f * s, ox + 16.2f * s, oy + 7.3f * s)
        lineTo(ox + 14.0f * s, oy + 8.8f * s)
        lineTo(ox + 10.0f * s, oy + 8.8f * s)
        close()
    }

    val fruit = Path().apply {
        moveTo(ox + 12f * s, oy + 7.6f * s)
        cubicTo(
            ox + 7.0f * s, oy + 7.6f * s,
            ox + 5.0f * s, oy + 10.6f * s,
            ox + 5.4f * s, oy + 14.5f * s
        )
        cubicTo(
            ox + 5.7f * s, oy + 18.4f * s,
            ox + 8.8f * s, oy + 20.8f * s,
            ox + 12f * s, oy + 20.8f * s
        )
        cubicTo(
            ox + 15.2f * s, oy + 20.8f * s,
            ox + 18.3f * s, oy + 18.4f * s,
            ox + 18.6f * s, oy + 14.5f * s
        )
        cubicTo(
            ox + 19.0f * s, oy + 10.6f * s,
            ox + 17.0f * s, oy + 7.6f * s,
            ox + 12f * s, oy + 7.6f * s
        )
        close()
    }

    val gloss = Path().apply {
        moveTo(ox + 8.5f * s, oy + 11.4f * s)
        cubicTo(
            ox + 7.9f * s, oy + 12.6f * s,
            ox + 7.9f * s, oy + 14.4f * s,
            ox + 8.6f * s, oy + 15.6f * s
        )
    }

    val glossStroke = Stroke(width = (1.2f * s).coerceAtLeast(1.1f), cap = StrokeCap.Round)
    val glossColor = SuzuriColors.PersimmonHighlight.copy(alpha = 0.65f)

    fun draw(scope: DrawScope) {
        scope.drawLine(
            color = SuzuriColors.Celadon,
            start = stemStart,
            end = stemEnd,
            strokeWidth = stemWidth,
            cap = StrokeCap.Round
        )
        scope.drawPath(calyx, color = SuzuriColors.Celadon)
        scope.drawPath(fruit, color = SuzuriColors.Persimmon)
        scope.drawPath(gloss, color = glossColor, style = glossStroke)
    }
}

/**
 * Minimalist geometric Persimmon (柿) motif — Persimmon #E05A3A oblate fruit + Celadon #7FA88E 4-lobed calyx.
 * Cached with drawWithCache for zero per-draw allocations.
 */
@Composable
fun KakiMotif(
    modifier: Modifier = Modifier,
    size: Dp = 18.dp
) {
    Spacer(
        modifier = modifier
            .size(size)
            .drawWithCache {
                val geometry = CachedKakiGeometry(Offset.Zero, this.size.minDimension)
                onDrawBehind {
                    geometry.draw(this)
                }
            }
    )
}

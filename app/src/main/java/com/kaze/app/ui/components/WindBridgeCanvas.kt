package com.kaze.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.app.net.ConnectionStatus
import com.kaze.app.ui.theme.SuzuriColors

/**
 * Animated Suzuri hardware conduit linking your MacBook Pro and Android phone.
 * Only runs infinite animations when connected, defers animated State reads into the draw phase
 * (zero composable recompositions per frame), and caches static geometry via drawWithCache.
 */
@Composable
fun WindBridgeCard(
    macName: String,
    status: ConnectionStatus,
    latencyMs: Int?,
    isBursting: Boolean,
    modifier: Modifier = Modifier
) {
    val isConnected = status == ConnectionStatus.CONNECTED

    // Only drive infinite transitions while actively connected (0 Hz idle GPU/CPU when disconnected)
    val phaseState: State<Float>
    val pulseState: State<Float>
    if (isConnected) {
        val infinite = rememberInfiniteTransition(label = "windBridge")
        phaseState = infinite.animateFloat(
            initialValue = 180f,
            targetValue = 0f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = if (isBursting) 750 else 2800, easing = LinearEasing),
                repeatMode = RepeatMode.Restart
            ),
            label = "windPhase"
        )
        pulseState = infinite.animateFloat(
            initialValue = 0.88f,
            targetValue = 1.12f,
            animationSpec = infiniteRepeatable(
                animation = tween(durationMillis = 1200, easing = LinearEasing),
                repeatMode = RepeatMode.Reverse
            ),
            label = "kakiPulse"
        )
    } else {
        phaseState = rememberUpdatedState(0f)
        pulseState = rememberUpdatedState(1f)
    }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(SuzuriColors.Surface)
            .border(
                width = 1.dp,
                color = if (isBursting) SuzuriColors.Persimmon else SuzuriColors.Hairline,
                shape = RoundedCornerShape(14.dp)
            )
            .padding(horizontal = 14.dp, vertical = 14.dp)
    ) {
        Spacer(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
                .drawWithCache {
                    val w = size.width
                    val h = size.height
                    val cy = h * 0.48f
                    val leftCx = w * 0.16f
                    val rightCx = w * 0.84f
                    val ringRadius = 28.dp.toPx()
                    val tick = 7.dp.toPx()

                    val dashedStroke = Stroke(
                        width = 1.2.dp.toPx(),
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
                    )
                    val deviceOutlineStroke = Stroke(width = 1.4.dp.toPx())
                    val ringOutlineStroke = Stroke(width = 1.2.dp.toPx())
                    val upperGuideStroke = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round)
                    val lowerGuideStroke = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round)

                    val mbW = 30.dp.toPx()
                    val mbH = 19.dp.toPx()
                    val mbTopLeft = Offset(leftCx - mbW / 2f, cy - mbH / 2f - 2.dp.toPx())
                    val mbSize = Size(mbW, mbH)
                    val mbCorner = CornerRadius(3.dp.toPx())
                    val mbBaseY = cy + mbH / 2f + 1.5.dp.toPx()
                    val mbBaseWidth = 1.8.dp.toPx()

                    val phW = 19.dp.toPx()
                    val phH = 32.dp.toPx()
                    val phTopLeft = Offset(rightCx - phW / 2f, cy - phH / 2f)
                    val phSize = Size(phW, phH)
                    val phCorner = CornerRadius(4.dp.toPx())

                    val x1 = leftCx + ringRadius + 4.dp.toPx()
                    val x2 = rightCx - ringRadius - 4.dp.toPx()
                    val span = x2 - x1

                    val upperTrack = Path().apply {
                        moveTo(x1, cy - 7.dp.toPx())
                        cubicTo(
                            x1 + span * 0.32f, cy - 20.dp.toPx(),
                            x1 + span * 0.68f, cy + 8.dp.toPx(),
                            x2, cy - 7.dp.toPx()
                        )
                    }
                    val lowerTrack = Path().apply {
                        moveTo(x1, cy + 7.dp.toPx())
                        cubicTo(
                            x1 + span * 0.34f, cy + 19.dp.toPx(),
                            x1 + span * 0.66f, cy - 8.dp.toPx(),
                            x2, cy + 7.dp.toPx()
                        )
                    }

                    val center = Offset(w / 2f, cy)
                    val kakiSize = 15.dp.toPx()
                    val kakiGeometry = CachedKakiGeometry(
                        origin = Offset(center.x - kakiSize / 2f, center.y - kakiSize / 2f),
                        boxSize = kakiSize
                    )
                    val upperDash = floatArrayOf(44f, 136f)
                    val lowerDash = floatArrayOf(30f, 150f)
                    val upperStreamWidth = if (isBursting) 2.6.dp.toPx() else 1.8.dp.toPx()
                    val lowerStreamWidth = 1.5.dp.toPx()
                    val baseBadgeRadius = (if (isBursting) 12.5.dp else 10.5.dp).toPx()

                    onDrawBehind {
                        // Corner blueprint ticks
                        drawLine(SuzuriColors.Hairline, Offset.Zero, Offset(tick, 0f), 1.5f)
                        drawLine(SuzuriColors.Hairline, Offset.Zero, Offset(0f, tick), 1.5f)
                        drawLine(SuzuriColors.Hairline, Offset(w, 0f), Offset(w - tick, 0f), 1.5f)
                        drawLine(SuzuriColors.Hairline, Offset(w, 0f), Offset(w, tick), 1.5f)

                        // Kamon dotted circles
                        drawCircle(SuzuriColors.Hairline, radius = ringRadius, center = Offset(leftCx, cy), style = dashedStroke)
                        drawCircle(SuzuriColors.Hairline, radius = ringRadius, center = Offset(rightCx, cy), style = dashedStroke)

                        // MacBook silhouette
                        drawRoundRect(SuzuriColors.Base, mbTopLeft, mbSize, mbCorner)
                        drawRoundRect(SuzuriColors.Ivory, mbTopLeft, mbSize, mbCorner, style = deviceOutlineStroke)
                        drawLine(
                            SuzuriColors.Ivory,
                            Offset(leftCx - mbW * 0.62f, mbBaseY),
                            Offset(leftCx + mbW * 0.62f, mbBaseY),
                            mbBaseWidth,
                            StrokeCap.Round
                        )
                        drawCircle(
                            if (isConnected) SuzuriColors.Persimmon else SuzuriColors.Faint,
                            2.4.dp.toPx(),
                            Offset(leftCx, cy - 2.dp.toPx())
                        )

                        // Android silhouette
                        drawRoundRect(SuzuriColors.Base, phTopLeft, phSize, phCorner)
                        drawRoundRect(SuzuriColors.Ivory, phTopLeft, phSize, phCorner, style = deviceOutlineStroke)
                        drawLine(
                            SuzuriColors.Faint,
                            Offset(rightCx - 3.dp.toPx(), cy - phH / 2f + 4.dp.toPx()),
                            Offset(rightCx + 3.dp.toPx(), cy - phH / 2f + 4.dp.toPx()),
                            1.2.dp.toPx(),
                            StrokeCap.Round
                        )
                        drawCircle(
                            if (isConnected) SuzuriColors.Celadon else SuzuriColors.Faint,
                            2.4.dp.toPx(),
                            Offset(rightCx, cy + 2.dp.toPx())
                        )

                        // Quiet guide rails
                        drawPath(upperTrack, SuzuriColors.Hairline, style = upperGuideStroke)
                        drawPath(lowerTrack, SuzuriColors.Hairline, style = lowerGuideStroke)

                        // Animated streams (read animated state only inside draw phase!)
                        if (isConnected) {
                            val phase = phaseState.value
                            drawPath(
                                upperTrack,
                                color = SuzuriColors.Persimmon,
                                style = Stroke(
                                    width = upperStreamWidth,
                                    cap = StrokeCap.Round,
                                    pathEffect = PathEffect.dashPathEffect(upperDash, phase)
                                )
                            )
                            drawPath(
                                lowerTrack,
                                color = SuzuriColors.Celadon,
                                style = Stroke(
                                    width = lowerStreamWidth,
                                    cap = StrokeCap.Round,
                                    pathEffect = PathEffect.dashPathEffect(lowerDash, (phase + 75f) % 180f)
                                )
                            )
                        }

                        // Center Kaki (Persimmon) emblem medallion
                        val badgeR = baseBadgeRadius * pulseState.value
                        drawCircle(SuzuriColors.Base, badgeR, center)
                        drawCircle(
                            if (isBursting) SuzuriColors.Persimmon else SuzuriColors.HairlineStrong,
                            badgeR,
                            center,
                            style = ringOutlineStroke
                        )
                        kakiGeometry.draw(this)
                    }
                }
        )

        Spacer(modifier = Modifier.height(4.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = macName.uppercase().take(14),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                color = SuzuriColors.Mist
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(99.dp))
                    .background(SuzuriColors.Base)
                    .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(99.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(
                            when (status) {
                                ConnectionStatus.CONNECTED -> SuzuriColors.Celadon
                                ConnectionStatus.CONNECTING -> SuzuriColors.Persimmon
                                else -> SuzuriColors.Faint
                            }
                        )
                )
                val pillText = when (status) {
                    ConnectionStatus.CONNECTED -> "P-256 · ${latencyMs ?: 2}ms"
                    ConnectionStatus.CONNECTING -> "HANDSHAKE..."
                    ConnectionStatus.DISCOVERING -> "SEARCHING LAN"
                    else -> "UNPAIRED"
                }
                Text(
                    text = pillText,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isConnected) SuzuriColors.Celadon else SuzuriColors.Mist
                )
            }

            Text(
                text = "THIS PHONE",
                fontFamily = FontFamily.Monospace,
                fontSize = 9.5.sp,
                color = SuzuriColors.Mist
            )
        }
    }
}

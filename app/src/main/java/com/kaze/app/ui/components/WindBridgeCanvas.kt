package com.kaze.app.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
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
 * Animated Suzuri hardware conduit linking your MacBook Pro and Android phone
 * with flowing persimmon/celadon wind streamlines and a resting Kaki persimmon packet.
 */
@Composable
fun WindBridgeCard(
    macName: String,
    status: ConnectionStatus,
    latencyMs: Int?,
    isBursting: Boolean,
    modifier: Modifier = Modifier
) {
    val infinite = rememberInfiniteTransition(label = "windBridge")
    val phase by infinite.animateFloat(
        initialValue = 180f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = if (isBursting) 750 else 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "windPhase"
    )
    val pulse by infinite.animateFloat(
        initialValue = 0.85f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "kakiPulse"
    )

    val isConnected = status == ConnectionStatus.CONNECTED

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
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(82.dp)
        ) {
            val w = size.width
            val h = size.height
            val cy = h * 0.48f
            val leftCx = w * 0.16f
            val rightCx = w * 0.84f
            val ringRadius = 28.dp.toPx()

            // Corner blueprint ticks
            val tick = 7.dp.toPx()
            drawLine(SuzuriColors.Hairline, Offset(0f, 0f), Offset(tick, 0f), 1.5f)
            drawLine(SuzuriColors.Hairline, Offset(0f, 0f), Offset(0f, tick), 1.5f)
            drawLine(SuzuriColors.Hairline, Offset(w, 0f), Offset(w - tick, 0f), 1.5f)
            drawLine(SuzuriColors.Hairline, Offset(w, 0f), Offset(w, tick), 1.5f)

            // Kamon dotted circles
            val dashed = Stroke(
                width = 1.2.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f), 0f)
            )
            drawCircle(SuzuriColors.Hairline, radius = ringRadius, center = Offset(leftCx, cy), style = dashed)
            drawCircle(SuzuriColors.Hairline, radius = ringRadius, center = Offset(rightCx, cy), style = dashed)

            // MacBook silhouette (Left)
            val mbW = 30.dp.toPx()
            val mbH = 19.dp.toPx()
            drawRoundRect(
                color = SuzuriColors.Base,
                topLeft = Offset(leftCx - mbW / 2f, cy - mbH / 2f - 2.dp.toPx()),
                size = Size(mbW, mbH),
                cornerRadius = CornerRadius(3.dp.toPx())
            )
            drawRoundRect(
                color = SuzuriColors.Ivory,
                topLeft = Offset(leftCx - mbW / 2f, cy - mbH / 2f - 2.dp.toPx()),
                size = Size(mbW, mbH),
                cornerRadius = CornerRadius(3.dp.toPx()),
                style = Stroke(width = 1.4.dp.toPx())
            )
            drawLine(
                color = SuzuriColors.Ivory,
                start = Offset(leftCx - mbW * 0.62f, cy + mbH / 2f + 1.5.dp.toPx()),
                end = Offset(leftCx + mbW * 0.62f, cy + mbH / 2f + 1.5.dp.toPx()),
                strokeWidth = 1.8.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = if (isConnected) SuzuriColors.Persimmon else SuzuriColors.Faint,
                radius = 2.4.dp.toPx(),
                center = Offset(leftCx, cy - 2.dp.toPx())
            )

            // Android Phone silhouette (Right)
            val phW = 19.dp.toPx()
            val phH = 32.dp.toPx()
            drawRoundRect(
                color = SuzuriColors.Base,
                topLeft = Offset(rightCx - phW / 2f, cy - phH / 2f),
                size = Size(phW, phH),
                cornerRadius = CornerRadius(4.dp.toPx())
            )
            drawRoundRect(
                color = SuzuriColors.Ivory,
                topLeft = Offset(rightCx - phW / 2f, cy - phH / 2f),
                size = Size(phW, phH),
                cornerRadius = CornerRadius(4.dp.toPx()),
                style = Stroke(width = 1.4.dp.toPx())
            )
            drawLine(
                color = SuzuriColors.Faint,
                start = Offset(rightCx - 3.dp.toPx(), cy - phH / 2f + 4.dp.toPx()),
                end = Offset(rightCx + 3.dp.toPx(), cy - phH / 2f + 4.dp.toPx()),
                strokeWidth = 1.2.dp.toPx(),
                cap = StrokeCap.Round
            )
            drawCircle(
                color = if (isConnected) SuzuriColors.Celadon else SuzuriColors.Faint,
                radius = 2.4.dp.toPx(),
                center = Offset(rightCx, cy + 2.dp.toPx())
            )

            // Wind streamlines
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

            // Quiet guide rails
            drawPath(upperTrack, SuzuriColors.Hairline, style = Stroke(width = 1.2.dp.toPx(), cap = StrokeCap.Round))
            drawPath(lowerTrack, SuzuriColors.Hairline, style = Stroke(width = 1.1.dp.toPx(), cap = StrokeCap.Round))

            if (isConnected) {
                drawPath(
                    upperTrack,
                    color = SuzuriColors.Persimmon,
                    style = Stroke(
                        width = if (isBursting) 2.6.dp.toPx() else 1.8.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(44f, 136f), phase)
                    )
                )
                drawPath(
                    lowerTrack,
                    color = SuzuriColors.Celadon,
                    style = Stroke(
                        width = 1.5.dp.toPx(),
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(30f, 150f), (phase + 75f) % 180f)
                    )
                )
            }

            // Center Kaki (Persimmon) emblem medallion
            val center = Offset(w / 2f, cy)
            val badgeR = (if (isBursting) 12.5.dp else 10.5.dp).toPx() * (if (isConnected) pulse else 1f)
            drawCircle(color = SuzuriColors.Base, radius = badgeR, center = center)
            drawCircle(
                color = if (isBursting) SuzuriColors.Persimmon else SuzuriColors.HairlineStrong,
                radius = badgeR,
                center = center,
                style = Stroke(width = 1.2.dp.toPx())
            )
            val kakiSize = 15.dp.toPx()
            drawKakiMotif(
                origin = Offset(center.x - kakiSize / 2f, center.y - kakiSize / 2f),
                boxSize = kakiSize
            )
        }

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

            // Center telemetry pill
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

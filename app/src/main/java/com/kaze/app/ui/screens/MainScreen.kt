package com.kaze.app.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileUpload
import androidx.compose.material.icons.outlined.QrCodeScanner
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.app.net.BreezeDirection
import com.kaze.app.net.ConnectionStatus
import com.kaze.app.net.KazeUiState
import com.kaze.app.net.SessionBreeze
import com.kaze.app.ui.components.KakiMotif
import com.kaze.app.ui.components.WindBridgeCard
import com.kaze.app.ui.components.suzuriGridBackground
import com.kaze.app.ui.theme.SuzuriColors

@Composable
fun MainScreen(
    state: KazeUiState,
    onPushClipboard: () -> Unit,
    onPickFile: () -> Unit,
    onOpenPairing: () -> Unit,
    onOpenSettings: () -> Unit,
    onReconnect: () -> Unit,
    onClearHistory: () -> Unit,
    onApproveQuarantine: (String) -> Unit,
    onDiscardQuarantine: (String) -> Unit
) {
    val context = LocalContext.current
    val jp = state.showJapaneseLabels
    val isConnected = state.status == ConnectionStatus.CONNECTED

    Box(
        modifier = Modifier
            .fillMaxSize()
            .suzuriGridBackground()
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // 1. Suzuri Header with tiny Kaki (Persimmon) maker's mark
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "KAZE",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 3.sp,
                                color = SuzuriColors.Ivory
                            )
                            if (jp) {
                                Text(
                                    text = "風",
                                    fontSize = 13.sp,
                                    color = SuzuriColors.Faint
                                )
                            }
                            KakiMotif(size = 17.dp)
                        }

                        Spacer(modifier = Modifier.height(5.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(7.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when (state.status) {
                                            ConnectionStatus.CONNECTED -> SuzuriColors.Celadon
                                            ConnectionStatus.CONNECTING -> SuzuriColors.Persimmon
                                            else -> SuzuriColors.Faint
                                        }
                                    )
                            )
                            val sub = when (state.status) {
                                ConnectionStatus.CONNECTED -> {
                                    val mac = state.pairedMac?.deviceName ?: "MacBook"
                                    if (jp) "接続中 · Paired with $mac" else "Paired with $mac"
                                }
                                ConnectionStatus.CONNECTING -> "Establishing P-256 session..."
                                ConnectionStatus.DISCOVERING -> {
                                    val mac = state.pairedMac?.deviceName ?: "Mac"
                                    "Looking for $mac on Wi-Fi"
                                }
                                else -> if (jp) "未ペアリング · Pair your Mac to begin" else "Pair your Mac to begin"
                            }
                            Text(
                                text = sub,
                                fontSize = 12.sp,
                                color = SuzuriColors.Mist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        IconButton(
                            onClick = onOpenPairing,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SuzuriColors.Surface)
                                .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                imageVector = if (state.pairedMac == null) Icons.Outlined.QrCodeScanner else Icons.Outlined.Shield,
                                contentDescription = "Pairing and Security",
                                tint = if (state.pairedMac == null) SuzuriColors.Persimmon else SuzuriColors.Mist,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        IconButton(
                            onClick = onOpenSettings,
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SuzuriColors.Surface)
                                .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(10.dp))
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.Tune,
                                contentDescription = "Settings",
                                tint = SuzuriColors.Mist,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                }

                // Optional status toast banner
                if (state.statusMessage != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SuzuriColors.Surface)
                            .border(
                                1.dp,
                                if (state.statusIsError) SuzuriColors.Persimmon.copy(alpha = 0.7f) else SuzuriColors.Celadon.copy(alpha = 0.6f),
                                RoundedCornerShape(10.dp)
                            )
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .clip(CircleShape)
                                .background(if (state.statusIsError) SuzuriColors.Persimmon else SuzuriColors.Celadon)
                        )
                        Text(
                            text = state.statusMessage,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = SuzuriColors.Ivory,
                            maxLines = 2
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 2. Animated Wind-Bridge Stage
                WindBridgeCard(
                    macName = state.pairedMac?.deviceName ?: "MACBOOK",
                    status = state.status,
                    latencyMs = state.latencyMs,
                    isBursting = state.isBursting,
                    modifier = Modifier.clickable {
                        if (state.pairedMac == null) onOpenPairing()
                        else if (!isConnected) onReconnect()
                    }
                )

                Spacer(modifier = Modifier.height(14.dp))

                // 3. Primary Manual Push Actions (or Pair Mac CTA if unpaired)
                if (state.pairedMac == null) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(SuzuriColors.Persimmon)
                            .clickable(onClick = onOpenPairing)
                            .padding(horizontal = 16.dp, vertical = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (jp) "ペアリング · ZERO-TRUST QR" else "ZERO-TRUST QR PAIRING",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = SuzuriColors.Base.copy(alpha = 0.78f)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = "Scan KazeMac QR Code",
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuzuriColors.Base
                                )
                                Text(
                                    text = "Or pair with 6-digit PIN on local Wi-Fi",
                                    fontSize = 11.sp,
                                    color = SuzuriColors.Base.copy(alpha = 0.8f)
                                )
                            }
                            Icon(
                                imageVector = Icons.Outlined.QrCodeScanner,
                                contentDescription = null,
                                tint = SuzuriColors.Base,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        // Push Clipboard (Persimmon primary CTA)
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (isConnected) SuzuriColors.Persimmon else SuzuriColors.SurfaceAlt)
                                .clickable(enabled = isConnected, onClick = onPushClipboard)
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (jp) "送る · CLIP" else "PUSH CLIP",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = if (isConnected) SuzuriColors.Base.copy(alpha = 0.78f) else SuzuriColors.Faint
                                )
                                Icon(
                                    imageVector = Icons.AutoMirrored.Outlined.ArrowForward,
                                    contentDescription = null,
                                    tint = if (isConnected) SuzuriColors.Base else SuzuriColors.Faint,
                                    modifier = Modifier.size(15.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Push Clipboard",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isConnected) SuzuriColors.Base else SuzuriColors.Mist
                            )
                            Text(
                                text = if (isConnected) "Manual · 1 tap" else "Tap bridge to retry",
                                fontSize = 10.5.sp,
                                color = if (isConnected) SuzuriColors.Base.copy(alpha = 0.8f) else SuzuriColors.Faint
                            )
                        }

                        // Quick Drop File
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(SuzuriColors.Surface)
                                .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(14.dp))
                                .clickable(enabled = isConnected, onClick = onPickFile)
                                .padding(14.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (jp) "添付 · FILE" else "DROP FILE",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = SuzuriColors.Faint
                                )
                                Icon(
                                    imageVector = Icons.Outlined.FileUpload,
                                    contentDescription = null,
                                    tint = if (isConnected) SuzuriColors.Persimmon else SuzuriColors.Faint,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            Text(
                                text = "Quick Drop",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = if (isConnected) SuzuriColors.Ivory else SuzuriColors.Mist
                            )
                            Text(
                                text = "Photo, PDF, note",
                                fontSize = 10.5.sp,
                                color = SuzuriColors.Mist
                            )
                        }
                    }
                }

                // Active Transfer Progress Bar
                val progress = state.transferProgress
                if (progress != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SuzuriColors.Surface)
                            .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(12.dp))
                            .padding(12.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { progress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(4.dp)
                                .clip(RoundedCornerShape(99.dp)),
                            color = SuzuriColors.Persimmon,
                            trackColor = SuzuriColors.SurfaceAlt
                        )
                        Spacer(modifier = Modifier.height(6.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Streaming AES-256-GCM...",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = SuzuriColors.Mist
                            )
                            Text(
                                text = "${(progress * 100).toInt()}%",
                                fontFamily = FontFamily.Monospace,
                                fontSize = 10.sp,
                                color = SuzuriColors.Persimmon
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 4. Session Breezes (Ephemeral RAM-only)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (jp) "履歴 · SESSION BREEZES" else "SESSION BREEZES",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        letterSpacing = 1.4.sp,
                        color = SuzuriColors.Faint
                    )
                    if (state.sessionHistory.isNotEmpty()) {
                        Text(
                            text = "RAM ONLY · CLEAR",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = SuzuriColors.Mist,
                            modifier = Modifier.clickable(onClick = onClearHistory)
                        )
                    } else {
                        Text(
                            text = "EPHEMERAL RAM",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = SuzuriColors.Faint
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                if (state.sessionHistory.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(SuzuriColors.Surface)
                            .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(12.dp))
                            .padding(16.dp)
                    ) {
                        Text(
                            text = "No breezes yet this session. Push from Mac with ⌘⇧K or share any photo/link via Android's Share Sheet.",
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                            color = SuzuriColors.Faint
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        state.sessionHistory.forEachIndexed { idx, item ->
                            SessionBreezeCard(
                                item = item,
                                showJapanese = jp,
                                highlightPersimmon = idx == 0 && state.isBursting,
                                onCopy = { text ->
                                    val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    cm.setPrimaryClip(ClipData.newPlainText("Kaze", text))
                                    Toast.makeText(context, "Copied to clipboard", Toast.LENGTH_SHORT).show()
                                },
                                onApproveQuarantine = { onApproveQuarantine(item.id) },
                                onDiscardQuarantine = { onDiscardQuarantine(item.id) }
                            )
                        }
                    }
                }
            }

            // 5. Security Telemetry Footer
            Column(modifier = Modifier.fillMaxWidth().padding(top = 20.dp)) {
                HorizontalDivider(color = SuzuriColors.Hairline, thickness = 1.dp)
                Spacer(modifier = Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PINNED P-256 · MANUAL PUSH",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = SuzuriColors.Faint
                    )
                    Text(
                        text = "0 B CLOUD",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = SuzuriColors.Faint
                    )
                }
            }
        }
    }
}

@Composable
private fun SessionBreezeCard(
    item: SessionBreeze,
    showJapanese: Boolean,
    highlightPersimmon: Boolean,
    onCopy: (String) -> Unit,
    onApproveQuarantine: () -> Unit,
    onDiscardQuarantine: () -> Unit
) {
    val fromMac = item.direction == BreezeDirection.FROM_MAC
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SuzuriColors.Surface)
            .border(
                width = 1.dp,
                color = if (highlightPersimmon) SuzuriColors.Persimmon else SuzuriColors.Hairline,
                shape = RoundedCornerShape(12.dp)
            )
            .padding(horizontal = 12.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = "${if (fromMac) "↓ MAC" else "↑ SENT"} · ${item.badge}",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.5.sp,
                    color = if (fromMac) SuzuriColors.Persimmon else SuzuriColors.Mist,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(if (fromMac) SuzuriColors.PersimmonSoft else SuzuriColors.SurfaceAlt)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
                if (showJapanese) {
                    Text(
                        text = item.jpMeta,
                        fontSize = 10.sp,
                        color = SuzuriColors.Faint
                    )
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = item.title,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp,
                color = SuzuriColors.Ivory,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(2.dp))

            Text(
                text = item.meta,
                fontSize = 10.5.sp,
                color = if (item.quarantinedFile != null) SuzuriColors.Persimmon else SuzuriColors.Mist,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Spacer(modifier = Modifier.width(8.dp))

        when {
            item.quarantinedFile != null -> {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SuzuriColors.Celadon)
                            .clickable(onClick = onApproveQuarantine)
                            .padding(horizontal = 9.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "Save",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuzuriColors.Base
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(SuzuriColors.SurfaceAlt)
                            .clickable(onClick = onDiscardQuarantine)
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "✕",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.sp,
                            color = SuzuriColors.Mist
                        )
                    }
                }
            }
            item.textPayload != null -> {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SuzuriColors.Base)
                        .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(8.dp))
                        .clickable { onCopy(item.textPayload) },
                    contentAlignment = Alignment.Center
                ) {
                    if (highlightPersimmon) {
                        KakiMotif(size = 15.dp)
                    } else {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = "Copy",
                            tint = SuzuriColors.Mist,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                }
            }
        }
    }
}

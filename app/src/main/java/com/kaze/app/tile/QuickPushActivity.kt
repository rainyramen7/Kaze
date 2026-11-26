package com.kaze.app.tile

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.app.KazeApplication
import com.kaze.app.net.ConnectionStatus
import com.kaze.app.ui.components.KakiMotif
import com.kaze.app.ui.screens.ClipboardPreviewState
import com.kaze.app.ui.theme.KazeTheme
import com.kaze.app.ui.theme.SuzuriColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * Translucent Suzuri bottom sheet launched from the Quick Settings Tile so you always preview
 * your clipboard contents before explicitly confirming the push to your Mac.
 */
class QuickPushActivity : ComponentActivity() {
    private val previewFlow = MutableStateFlow<ClipboardPreviewState>(ClipboardPreviewState.Empty)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = (application as KazeApplication).connectionManager
        manager.acquireForegroundSession()

        setContent {
            KazeTheme {
                val state by manager.uiState.collectAsState()
                val preview by previewFlow.collectAsState()
                var pushed by remember { mutableStateOf(false) }

                LaunchedEffect(pushed) {
                    if (pushed) {
                        delay(700)
                        finish()
                    }
                }

                val isConnected = state.status == ConnectionStatus.CONNECTED

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.45f))
                        .clickable { finish() },
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .background(SuzuriColors.Base)
                            .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
                            .clickable(enabled = false) {}
                            .navigationBarsPadding()
                            .padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (state.showJapaneseLabels) "確認して送る · Preview Clipboard" else "Preview Clipboard",
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuzuriColors.Ivory
                                )
                                Text(
                                    text = when (state.status) {
                                        ConnectionStatus.CONNECTED -> "Ready → ${state.pairedMac?.deviceName ?: "Mac"}"
                                        ConnectionStatus.CONNECTING, ConnectionStatus.DISCOVERING -> "Connecting to Mac on Wi-Fi..."
                                        else -> "Open Kaze to pair your Mac first"
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    color = if (isConnected) SuzuriColors.Celadon else SuzuriColors.Mist
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(9.dp))
                                    .background(SuzuriColors.Surface)
                                    .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(9.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                KakiMotif(size = 16.dp)
                            }
                        }

                        // Clipboard preview box
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .background(SuzuriColors.Surface)
                                .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(12.dp))
                                .padding(12.dp)
                        ) {
                            when (val p = preview) {
                                ClipboardPreviewState.Empty -> Text(
                                    text = "Clipboard is empty.",
                                    fontSize = 12.sp,
                                    color = SuzuriColors.Faint
                                )
                                ClipboardPreviewState.SensitiveBlocked -> Text(
                                    text = "Sensitive / password-manager clip — blocked from push.",
                                    fontSize = 12.sp,
                                    color = SuzuriColors.Persimmon
                                )
                                is ClipboardPreviewState.Text -> Text(
                                    text = p.content,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    lineHeight = 17.sp,
                                    color = SuzuriColors.Ivory,
                                    maxLines = 5,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            val textToSend = (preview as? ClipboardPreviewState.Text)?.content
                            val canPush = isConnected && textToSend != null && !pushed
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (canPush) SuzuriColors.Persimmon else SuzuriColors.SurfaceAlt)
                                    .clickable(enabled = canPush) {
                                        manager.pushClipboardText(textToSend!!)
                                        pushed = true
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (pushed) "Sent · Kaki Stamp ✓" else "Push to Mac",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (canPush) SuzuriColors.Base else SuzuriColors.Mist
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SuzuriColors.Surface)
                                    .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(12.dp))
                                    .clickable { finish() }
                                    .padding(horizontal = 18.dp, vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("Cancel", fontSize = 13.sp, color = SuzuriColors.Mist)
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (!hasFocus) return
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) {
            previewFlow.value = ClipboardPreviewState.Empty
            return
        }
        val isSensitive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
        } else {
            false
        }
        if (isSensitive) {
            previewFlow.value = ClipboardPreviewState.SensitiveBlocked
            return
        }
        val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty().trim()
        previewFlow.value = if (text.isEmpty()) {
            ClipboardPreviewState.Empty
        } else {
            ClipboardPreviewState.Text(text, text.startsWith("http://") || text.startsWith("https://"))
        }
    }

    override fun onDestroy() {
        (application as KazeApplication).connectionManager.releaseForegroundSession()
        super.onDestroy()
    }
}

package com.kaze.app.share

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaze.app.KazeApplication
import com.kaze.app.net.ConnectionStatus
import com.kaze.app.ui.components.KakiMotif
import com.kaze.app.ui.theme.KazeTheme
import com.kaze.app.ui.theme.SuzuriColors
import kotlinx.coroutines.delay

/**
 * Translucent Suzuri bottom sheet invoked from Android's Share Sheet (`ACTION_SEND`).
 */
class ShareDropActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val manager = (application as KazeApplication).connectionManager

        val sharedText = intent.getStringExtra(Intent.EXTRA_TEXT)
        val sharedUri: Uri? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            @Suppress("DEPRECATION")
            intent.getParcelableExtra(Intent.EXTRA_STREAM)
        }

        setContent {
            KazeTheme {
                val state by manager.uiState.collectAsStateWithLifecycle()
                var sentResult by remember { mutableStateOf<String?>(null) }
                var sending by remember { mutableStateOf(false) }

                LaunchedEffect(sentResult) {
                    if (sentResult != null) {
                        delay(1_100)
                        finish()
                    }
                }

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
                            .clip(RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                            .background(SuzuriColors.Base)
                            .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(topStart = 26.dp, topEnd = 26.dp))
                            .clickable(enabled = false) {}
                            .navigationBarsPadding()
                            .padding(horizontal = 22.dp, vertical = 18.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.CenterHorizontally)
                                .size(width = 36.dp, height = 4.dp)
                                .clip(RoundedCornerShape(99.dp))
                                .background(SuzuriColors.Hairline)
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = if (state.showJapaneseLabels) "風で送る · Drop to Mac" else "Drop to Mac",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = SuzuriColors.Ivory
                                )
                                Text(
                                    text = when {
                                        sharedUri != null -> "Encrypted LAN file stream"
                                        sharedText != null -> sharedText.take(44)
                                        else -> "Nothing to send"
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 11.sp,
                                    color = SuzuriColors.Mist,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                            Box(
                                modifier = Modifier
                                    .size(34.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SuzuriColors.Surface)
                                    .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(10.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                KakiMotif(size = 18.dp)
                            }
                        }

                        // Target Mac Card
                        val isConnected = state.status == ConnectionStatus.CONNECTED
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(SuzuriColors.Surface)
                                .border(
                                    1.dp,
                                    if (isConnected) SuzuriColors.Persimmon else SuzuriColors.Hairline,
                                    RoundedCornerShape(14.dp)
                                )
                                .padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = state.pairedMac?.deviceName ?: "No Paired Mac",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SuzuriColors.Ivory
                                    )
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(6.dp)
                                                .clip(CircleShape)
                                                .background(if (isConnected) SuzuriColors.Celadon else SuzuriColors.Faint)
                                        )
                                        Text(
                                            text = if (isConnected) "~/Downloads/Kaze · P-256" else "Open Kaze on same Wi-Fi",
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            color = if (isConnected) SuzuriColors.Celadon else SuzuriColors.Mist
                                        )
                                    }
                                }
                                Text(
                                    text = state.latencyMs?.let { "${it}ms" } ?: "LAN",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.5.sp,
                                    color = SuzuriColors.Persimmon
                                )
                            }

                            val progress = state.transferProgress
                            if (progress != null || sentResult != null) {
                                LinearProgressIndicator(
                                    progress = { if (sentResult != null) 1f else (progress ?: 0f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(5.dp)
                                        .clip(RoundedCornerShape(99.dp)),
                                    color = SuzuriColors.Persimmon,
                                    trackColor = SuzuriColors.SurfaceAlt
                                )
                                Text(
                                    text = sentResult ?: "Streaming encrypted breeze... ${((progress ?: 0f) * 100).toInt()}%",
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 10.sp,
                                    color = if (sentResult != null) SuzuriColors.Celadon else SuzuriColors.Mist
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isConnected && !sending) SuzuriColors.Persimmon else SuzuriColors.SurfaceAlt)
                                    .clickable(enabled = isConnected && !sending) {
                                        sending = true
                                        if (sharedUri != null) {
                                            manager.sendFileUri(sharedUri) { ok, msg ->
                                                sentResult = if (ok) "Delivered · Kaki Stamp ✓" else msg
                                                sending = false
                                            }
                                        } else if (!sharedText.isNullOrBlank()) {
                                            manager.pushClipboardText(sharedText)
                                            sentResult = "Delivered · Kaki Stamp ✓"
                                            sending = false
                                        }
                                    }
                                    .padding(vertical = 12.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (state.showJapaneseLabels) "送る · Send Now" else "Send Now",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isConnected && !sending) SuzuriColors.Base else SuzuriColors.Faint
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
                                Text(
                                    text = "Done",
                                    fontSize = 13.sp,
                                    color = SuzuriColors.Mist
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        (application as KazeApplication).connectionManager.acquireForegroundSession()
    }

    override fun onStop() {
        (application as KazeApplication).connectionManager.releaseForegroundSession()
        super.onStop()
    }
}

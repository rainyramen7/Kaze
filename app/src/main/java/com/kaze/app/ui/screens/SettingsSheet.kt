package com.kaze.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.app.net.KazeUiState
import com.kaze.app.ui.components.KakiMotif
import com.kaze.app.ui.theme.SuzuriColors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsSheet(
    state: KazeUiState,
    hasOverlayPermission: Boolean,
    onToggleBackgroundListen: (Boolean) -> Unit,
    onToggleJapanese: (Boolean) -> Unit,
    onOpenOverlaySettings: () -> Unit,
    onOpenPairing: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = SuzuriColors.Surface,
        contentColor = SuzuriColors.Ivory
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 22.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = if (state.showJapaneseLabels) "設定 · Settings" else "Settings",
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    color = SuzuriColors.Ivory
                )
                KakiMotif(size = 15.dp)
            }

            // Opt-in Background Listen toggle (OFF by default -> zero persistent notification!)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SuzuriColors.Base)
                    .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text("Background Listen", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SuzuriColors.Ivory)
                    Text(
                        text = if (state.backgroundListenEnabled) {
                            "Active · Keeps LAN socket open (requires Android notification)"
                        } else {
                            "Off · Connects on-demand only (0% idle battery, no notification)"
                        },
                        fontSize = 11.sp,
                        color = if (state.backgroundListenEnabled) SuzuriColors.Persimmon else SuzuriColors.Celadon
                    )
                }
                Switch(
                    checked = state.backgroundListenEnabled,
                    onCheckedChange = onToggleBackgroundListen,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SuzuriColors.Base,
                        checkedTrackColor = SuzuriColors.Persimmon,
                        uncheckedThumbColor = SuzuriColors.Mist,
                        uncheckedTrackColor = SuzuriColors.SurfaceAlt
                    )
                )
            }

            // Bilingual JP toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SuzuriColors.Base)
                    .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(12.dp))
                    .padding(horizontal = 14.dp, vertical = 11.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Japanese Micro-Labels", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SuzuriColors.Ivory)
                    Text("風 · 送る · 受信 · 履歴 accents", fontSize = 11.sp, color = SuzuriColors.Mist)
                }
                Switch(
                    checked = state.showJapaneseLabels,
                    onCheckedChange = onToggleJapanese,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = SuzuriColors.Base,
                        checkedTrackColor = SuzuriColors.Persimmon,
                        uncheckedThumbColor = SuzuriColors.Mist,
                        uncheckedTrackColor = SuzuriColors.SurfaceAlt
                    )
                )
            }

            // Floating Incoming Breeze Overlay permission
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SuzuriColors.Base)
                    .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(12.dp))
                    .clickable(onClick = onOpenOverlaySettings)
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("Incoming Breeze Overlay", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SuzuriColors.Ivory)
                    Text(
                        text = if (hasOverlayPermission) "Enabled · Floating toast over apps" else "Tap to allow floating toast over apps",
                        fontSize = 11.sp,
                        color = if (hasOverlayPermission) SuzuriColors.Celadon else SuzuriColors.Persimmon
                    )
                }
                Text(
                    text = if (hasOverlayPermission) "ON" else "SETUP",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.5.sp,
                    color = if (hasOverlayPermission) SuzuriColors.Celadon else SuzuriColors.Persimmon
                )
            }

            // Pairing & Keys shortcut
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(SuzuriColors.Base)
                    .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(12.dp))
                    .clickable {
                        onDismiss()
                        onOpenPairing()
                    }
                    .padding(horizontal = 14.dp, vertical = 13.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Pairing & Pinned Keys", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = SuzuriColors.Ivory)
                        Text("QR scan, 6-digit PIN, or Forget Mac", fontSize = 11.sp, color = SuzuriColors.Mist)
                    }
                    Text("→", fontFamily = FontFamily.Monospace, color = SuzuriColors.Persimmon)
                }
            }

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

package com.kaze.app.ui.screens

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaze.app.crypto.KazeCrypto
import com.kaze.app.net.DiscoveredMac
import com.kaze.app.net.KazeUiState
import com.kaze.app.ui.components.KakiMotif
import com.kaze.app.ui.components.QrCameraViewfinder
import com.kaze.app.ui.components.suzuriGridBackground
import com.kaze.app.ui.theme.SuzuriColors

@Composable
fun PairingScreen(
    state: KazeUiState,
    hasCameraPermission: Boolean,
    onRequestCameraPermission: () -> Unit,
    onQrScanned: (String) -> Unit,
    onPairWithPin: (host: String, port: Int, pin: String, nameHint: String, fpPrefix: String?) -> Unit,
    onUnpair: () -> Unit,
    onClose: () -> Unit
) {
    val jp = state.showJapaneseLabels
    var selectedMac by remember(state.discoveredMacs) {
        mutableStateOf<DiscoveredMac?>(state.discoveredMacs.firstOrNull())
    }
    var manualHost by remember { mutableStateOf("") }
    var manualPort by remember { mutableStateOf("51820") }
    var pinCode by remember { mutableStateOf("") }

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
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp)
                    ) {
                        Text(
                            text = if (jp) "ペアリング · Pair Mac" else "Pair Mac",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuzuriColors.Ivory
                        )
                        KakiMotif(size = 15.dp)
                    }
                    Text(
                        text = "Scan the 60s QR in KazeMac’s menu bar",
                        fontSize = 11.5.sp,
                        color = SuzuriColors.Mist
                    )
                }

                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(SuzuriColors.Surface)
                        .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(8.dp))
                        .clickable(onClick = onClose)
                        .padding(horizontal = 12.dp, vertical = 7.dp)
                ) {
                    Text(
                        text = "Close",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = SuzuriColors.Mist
                    )
                }
            }

            // QR Viewfinder or Camera Permission prompt
            if (hasCameraPermission) {
                QrCameraViewfinder(
                    onQrScanned = { uri ->
                        onQrScanned(uri)
                        onClose()
                    }
                )
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(176.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(SuzuriColors.Surface)
                        .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(16.dp))
                        .padding(20.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "Camera needed to scan KazeMac QR",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = SuzuriColors.Ivory
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Or use the 6-digit fallback code below",
                        fontSize = 11.sp,
                        color = SuzuriColors.Mist
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(SuzuriColors.Persimmon)
                            .clickable(onClick = onRequestCameraPermission)
                            .padding(horizontal = 16.dp, vertical = 9.dp)
                    ) {
                        Text(
                            text = "Enable Camera Scanner",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = SuzuriColors.Base
                        )
                    }
                }
            }

            // 6-Digit PIN Fallback Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SuzuriColors.Surface)
                    .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "6-DIGIT PIN FALLBACK",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = SuzuriColors.Mist
                    )
                    Text(
                        text = if (state.discoveredMacs.isNotEmpty()) "${state.discoveredMacs.size} Mac found on LAN" else "Enter LAN IP",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = if (state.discoveredMacs.isNotEmpty()) SuzuriColors.Celadon else SuzuriColors.Faint
                    )
                }

                // Discovered Macs list
                if (state.discoveredMacs.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        state.discoveredMacs.forEach { mac ->
                            val isSelected = selectedMac?.serviceName == mac.serviceName
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SuzuriColors.Base)
                                    .border(
                                        1.dp,
                                        if (isSelected) SuzuriColors.Persimmon else SuzuriColors.Hairline,
                                        RoundedCornerShape(10.dp)
                                    )
                                    .clickable { selectedMac = mac }
                                    .padding(horizontal = 11.dp, vertical = 9.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column {
                                    Text(
                                        text = mac.serviceName,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = SuzuriColors.Ivory
                                    )
                                    Text(
                                        text = "${mac.host}:${mac.port}",
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 10.sp,
                                        color = SuzuriColors.Mist
                                    )
                                }
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(if (isSelected) SuzuriColors.Persimmon else SuzuriColors.Hairline)
                                )
                            }
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = manualHost,
                            onValueChange = { manualHost = it },
                            label = { Text("Mac LAN IP", fontSize = 11.sp) },
                            placeholder = { Text("192.168.1.42", fontSize = 11.sp) },
                            singleLine = true,
                            modifier = Modifier.weight(2f),
                            colors = suzuriTextFieldColors()
                        )
                        OutlinedTextField(
                            value = manualPort,
                            onValueChange = { manualPort = it.filter(Char::isDigit).take(5) },
                            label = { Text("Port", fontSize = 11.sp) },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f),
                            colors = suzuriTextFieldColors()
                        )
                    }
                }

                OutlinedTextField(
                    value = pinCode,
                    onValueChange = { pinCode = it.filter(Char::isDigit).take(6) },
                    label = { Text("6-Digit Code from KazeMac", fontSize = 11.sp) },
                    placeholder = { Text("482917", fontFamily = FontFamily.Monospace) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.fillMaxWidth(),
                    colors = suzuriTextFieldColors()
                )

                val targetHost = selectedMac?.host ?: manualHost.trim()
                val targetPort = selectedMac?.port ?: (manualPort.toIntOrNull() ?: 51820)
                val canSubmit = targetHost.isNotEmpty() && pinCode.length == 6

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (canSubmit) SuzuriColors.Persimmon else SuzuriColors.SurfaceAlt)
                        .clickable(enabled = canSubmit) {
                            onPairWithPin(
                                targetHost,
                                targetPort,
                                pinCode,
                                selectedMac?.serviceName ?: "Mac",
                                selectedMac?.fingerprintPrefix
                            )
                            onClose()
                        }
                        .padding(vertical = 11.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "Verify & Pin Mac",
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (canSubmit) SuzuriColors.Base else SuzuriColors.Faint
                    )
                }
            }

            // Keystore Identity & Pinned Mac Security Card
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(SuzuriColors.Surface)
                    .border(1.dp, SuzuriColors.Hairline, RoundedCornerShape(14.dp))
                    .padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "THIS PHONE · KEYSTORE P-256",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = SuzuriColors.Mist
                    )
                    Text(
                        text = "HARDWARE",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = SuzuriColors.Celadon
                    )
                }
                Text(
                    text = state.deviceShortFingerprint,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    color = SuzuriColors.Ivory
                )

                val paired = state.pairedMac
                if (paired != null) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "PINNED MAC · ${paired.deviceName.uppercase()}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        color = SuzuriColors.Mist
                    )
                    Text(
                        text = KazeCrypto.formatShortFingerprint(paired.fingerprintHex),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        color = SuzuriColors.Celadon
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(SuzuriColors.PersimmonSoft)
                            .border(1.dp, SuzuriColors.Persimmon.copy(alpha = 0.45f), RoundedCornerShape(10.dp))
                            .clickable(onClick = onUnpair)
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Forget Paired Mac",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = SuzuriColors.Persimmon
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun suzuriTextFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = SuzuriColors.Persimmon,
    unfocusedBorderColor = SuzuriColors.Hairline,
    focusedTextColor = SuzuriColors.Ivory,
    unfocusedTextColor = SuzuriColors.Ivory,
    focusedLabelColor = SuzuriColors.Persimmon,
    unfocusedLabelColor = SuzuriColors.Mist,
    cursorColor = SuzuriColors.Persimmon
)

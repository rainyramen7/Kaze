package com.kaze.app

import android.Manifest
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.kaze.app.net.KazeConnectionService
import com.kaze.app.ui.screens.MainScreen
import com.kaze.app.ui.screens.PairingScreen
import com.kaze.app.ui.screens.SettingsSheet
import com.kaze.app.ui.theme.KazeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val manager = (application as KazeApplication).connectionManager
        KazeConnectionService.start(this)

        setContent {
            KazeTheme {
                val state by manager.uiState.collectAsState()
                var showPairing by remember { mutableStateOf(false) }
                var showSettings by remember { mutableStateOf(false) }
                var hasCameraPerm by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) ==
                            PackageManager.PERMISSION_GRANTED
                    )
                }
                var hasOverlayPerm by remember {
                    mutableStateOf(Settings.canDrawOverlays(this))
                }

                val notifPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { /* optional */ }

                val cameraPermissionLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.RequestPermission()
                ) { granted ->
                    hasCameraPerm = granted
                }

                val filePickerLauncher = rememberLauncherForActivityResult(
                    ActivityResultContracts.OpenDocument()
                ) { uri: Uri? ->
                    if (uri != null) {
                        manager.sendFileUri(uri)
                    }
                }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.POST_NOTIFICATIONS) !=
                        PackageManager.PERMISSION_GRANTED
                    ) {
                        notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                    }
                }

                if (showPairing) {
                    PairingScreen(
                        state = state,
                        hasCameraPermission = hasCameraPerm,
                        onRequestCameraPermission = {
                            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
                        },
                        onQrScanned = { uri -> manager.pairWithQrUri(uri) },
                        onPairWithPin = { host, port, pin, nameHint, fpPrefix ->
                            manager.pairWithPin(host, port, pin, nameHint, fpPrefix)
                        },
                        onUnpair = { manager.unpairMac() },
                        onClose = { showPairing = false }
                    )
                } else {
                    MainScreen(
                        state = state,
                        onPushClipboard = { readAndPushClipboard(manager) },
                        onPickFile = { filePickerLauncher.launch(arrayOf("*/*")) },
                        onOpenPairing = { showPairing = true },
                        onOpenSettings = {
                            hasOverlayPerm = Settings.canDrawOverlays(this)
                            showSettings = true
                        },
                        onReconnect = { manager.connectToPairedMac() },
                        onClearHistory = { manager.clearSessionHistory() },
                        onApproveQuarantine = { manager.approveQuarantinedFile(it) },
                        onDiscardQuarantine = { manager.discardQuarantinedFile(it) }
                    )
                }

                if (showSettings) {
                    SettingsSheet(
                        state = state,
                        hasOverlayPermission = hasOverlayPerm,
                        onToggleJapanese = { manager.setShowJapaneseLabels(it) },
                        onOpenOverlaySettings = {
                            val intent = Intent(
                                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                Uri.parse("package:$packageName")
                            )
                            startActivity(intent)
                        },
                        onOpenPairing = { showPairing = true },
                        onDismiss = { showSettings = false }
                    )
                }
            }
        }
    }

    private fun readAndPushClipboard(manager: com.kaze.app.net.KazeConnectionManager) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) {
            Toast.makeText(this, "Clipboard is empty", Toast.LENGTH_SHORT).show()
            return
        }
        val isSensitive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
        } else {
            false
        }
        if (isSensitive) {
            Toast.makeText(this, "Blocked: Sensitive password clipboard item", Toast.LENGTH_SHORT).show()
            return
        }
        val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty().trim()
        manager.pushClipboardText(text)
    }
}

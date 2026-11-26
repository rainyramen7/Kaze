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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import com.kaze.app.net.KazeConnectionService
import com.kaze.app.ui.screens.ClipboardPreviewState
import com.kaze.app.ui.screens.MainScreen
import com.kaze.app.ui.screens.PairingScreen
import com.kaze.app.ui.screens.SettingsSheet
import com.kaze.app.ui.theme.KazeTheme
import kotlinx.coroutines.flow.MutableStateFlow

class MainActivity : ComponentActivity() {
    private val clipboardPreviewFlow = MutableStateFlow<ClipboardPreviewState>(ClipboardPreviewState.Empty)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val manager = (application as KazeApplication).connectionManager
        if (manager.uiState.value.backgroundListenEnabled) {
            KazeConnectionService.start(this)
        } else {
            KazeConnectionService.stop(this)
        }

        setContent {
            KazeTheme {
                val state by manager.uiState.collectAsState()
                val clipboardPreview by clipboardPreviewFlow.collectAsState()
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
                ) { /* optional when Background Listen is enabled */ }

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
                        clipboardPreview = clipboardPreview,
                        onRefreshClipboard = { refreshClipboardPreview() },
                        onPushPreviewText = { text -> manager.pushClipboardText(text) },
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
                        onToggleBackgroundListen = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                                PackageManager.PERMISSION_GRANTED
                            ) {
                                notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
                            manager.setBackgroundListenEnabled(enabled)
                        },
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

    override fun onStart() {
        super.onStart()
        (application as KazeApplication).connectionManager.acquireForegroundSession()
    }

    override fun onStop() {
        (application as KazeApplication).connectionManager.releaseForegroundSession()
        super.onStop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            refreshClipboardPreview()
        }
    }

    private fun refreshClipboardPreview() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = cm.primaryClip
        if (clip == null || clip.itemCount == 0) {
            clipboardPreviewFlow.value = ClipboardPreviewState.Empty
            return
        }
        val isSensitive = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description?.extras?.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false) == true
        } else {
            false
        }
        if (isSensitive) {
            clipboardPreviewFlow.value = ClipboardPreviewState.SensitiveBlocked
            return
        }
        val text = clip.getItemAt(0).coerceToText(this)?.toString().orEmpty().trim()
        if (text.isEmpty()) {
            clipboardPreviewFlow.value = ClipboardPreviewState.Empty
        } else {
            val isUrl = text.startsWith("http://") || text.startsWith("https://")
            clipboardPreviewFlow.value = ClipboardPreviewState.Text(content = text, isUrl = isUrl)
        }
    }
}

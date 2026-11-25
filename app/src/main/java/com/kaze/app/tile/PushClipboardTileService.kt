package com.kaze.app.tile

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.service.quicksettings.Tile
import android.service.quicksettings.TileService
import com.kaze.app.KazeApplication
import com.kaze.app.net.ConnectionStatus

/**
 * Android Quick Settings Tile ("Push to Mac") for 1-tap manual clipboard push from anywhere.
 */
class PushClipboardTileService : TileService() {

    override fun onStartListening() {
        super.onStartListening()
        val manager = (application as KazeApplication).connectionManager
        val connected = manager.uiState.value.status == ConnectionStatus.CONNECTED
        qsTile?.apply {
            state = if (connected) Tile.STATE_ACTIVE else Tile.STATE_INACTIVE
            label = "Push to Mac"
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                subtitle = if (connected) manager.uiState.value.pairedMac?.deviceName ?: "Kaze" else "Not connected"
            }
            updateTile()
        }
    }

    override fun onClick() {
        super.onClick()
        val intent = Intent(this, QuickPushActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val pi = PendingIntent.getActivity(
                this,
                0,
                intent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            startActivityAndCollapse(pi)
        } else {
            @Suppress("DEPRECATION")
            startActivityAndCollapse(intent)
        }
    }
}

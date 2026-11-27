package com.kaze.app.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import androidx.core.app.NotificationCompat
import com.kaze.app.KazeApplication
import com.kaze.app.MainActivity
import com.kaze.app.R
import com.kaze.app.overlay.BreezeOverlayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Opt-in foreground service (OFF by default) for users who want all-day floating Mac toasts.
 * Updates its foreground notification ONLY when connection status or paired Mac name changes.
 */
class KazeConnectionService : Service() {
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private lateinit var overlayController: BreezeOverlayController
    private lateinit var notificationManager: NotificationManager

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        createChannels()
        overlayController = BreezeOverlayController(applicationContext)

        val manager = (application as KazeApplication).connectionManager

        // Distinct collection avoids Binder notification storms during file chunk streaming or pings
        serviceScope.launch {
            manager.uiState
                .map { it.status to it.pairedMac?.deviceName }
                .distinctUntilChanged()
                .collect { (status, macName) ->
                    notificationManager.notify(NOTIF_ID_SERVICE, buildForegroundNotification(status, macName))
                }
        }

        serviceScope.launch {
            manager.incomingToasts.collect { event ->
                if (Settings.canDrawOverlays(this@KazeConnectionService)) {
                    overlayController.show(event, manager.uiState.value.showJapaneseLabels)
                } else {
                    postFallbackToastNotification(event)
                }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val manager = (application as KazeApplication).connectionManager
        val state = manager.uiState.value
        val notification = buildForegroundNotification(state.status, state.pairedMac?.deviceName)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(NOTIF_ID_SERVICE, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            startForeground(NOTIF_ID_SERVICE, notification)
        }
        manager.startDiscoveryAndConnect()
        return START_STICKY
    }

    /** Android 15+ (targetSdk >= 35) requirement for dataSync foreground services to stop cleanly on timeout. */
    override fun onTimeout(startId: Int, fgsType: Int) {
        val manager = (application as KazeApplication).connectionManager
        manager.setBackgroundListenEnabled(false)
        stopSelf()
    }

    override fun onDestroy() {
        overlayController.dismiss()
        serviceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildForegroundNotification(status: ConnectionStatus, macName: String?): Notification {
        val openIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val contentText = when (status) {
            ConnectionStatus.CONNECTED ->
                "Paired with ${macName ?: "Mac"} · P-256 LAN"
            ConnectionStatus.CONNECTING -> "Handshaking with Mac on LAN..."
            ConnectionStatus.DISCOVERING -> "Listening for paired Mac on local Wi-Fi"
            else -> "Open Kaze to pair your Mac"
        }
        return NotificationCompat.Builder(this, CHANNEL_SERVICE)
            .setSmallIcon(R.drawable.ic_tile_kaze)
            .setContentTitle("Kaze · 風")
            .setContentText(contentText)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(openIntent)
            .build()
    }

    private fun postFallbackToastNotification(event: IncomingToastEvent) {
        val openIntent = PendingIntent.getActivity(
            this,
            event.id.hashCode(),
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val notif = NotificationCompat.Builder(this, CHANNEL_BREEZE)
            .setSmallIcon(R.drawable.ic_tile_kaze)
            .setContentTitle("Breeze from ${event.macName}")
            .setContentText(event.text.take(120))
            .setAutoCancel(true)
            .setContentIntent(openIntent)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()
        notificationManager.notify(event.id.hashCode(), notif)
    }

    private fun createChannels() {
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_SERVICE, "Kaze LAN Link", NotificationManager.IMPORTANCE_LOW)
        )
        notificationManager.createNotificationChannel(
            NotificationChannel(CHANNEL_BREEZE, "Incoming Breezes", NotificationManager.IMPORTANCE_HIGH)
        )
    }

    companion object {
        private const val CHANNEL_SERVICE = "kaze_lan_service"
        private const val CHANNEL_BREEZE = "kaze_incoming_breeze"
        private const val NOTIF_ID_SERVICE = 4201

        fun start(context: Context) {
            val intent = Intent(context, KazeConnectionService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, KazeConnectionService::class.java))
        }
    }
}

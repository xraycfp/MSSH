package com.mssh.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.mssh.MainActivity

/**
 * Foreground service to keep SSH connections alive when the app is in the background.
 */
class SshForegroundService : Service() {

    private val binder = LocalBinder()
    private var activeSessions = 0

    inner class LocalBinder : Binder() {
        fun getService(): SshForegroundService = this@SshForegroundService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_START -> {
                activeSessions = intent.getIntExtra(EXTRA_SESSION_COUNT, 1)
                startForeground(NOTIFICATION_ID, buildNotification())
            }
            ACTION_UPDATE -> {
                activeSessions = intent.getIntExtra(EXTRA_SESSION_COUNT, 0)
                if (activeSessions > 0) {
                    val manager = getSystemService(NotificationManager::class.java)
                    manager.notify(NOTIFICATION_ID, buildNotification())
                } else {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                    stopSelf()
                }
            }
            ACTION_STOP -> {
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "SSH Sessions",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Active SSH connections"
            setShowBadge(false)
        }
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("MSSH")
            .setContentText(
                "$activeSessions active session${if (activeSessions != 1) "s" else ""}"
            )
            .setSmallIcon(android.R.drawable.ic_menu_manage)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "mssh_sessions"
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.mssh.START_SERVICE"
        const val ACTION_UPDATE = "com.mssh.UPDATE_SERVICE"
        const val ACTION_STOP = "com.mssh.STOP_SERVICE"
        const val EXTRA_SESSION_COUNT = "session_count"
    }
}

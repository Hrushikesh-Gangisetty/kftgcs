package com.example.kftgcs.videorelay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.example.kftgcs.MainActivity
import com.example.kftgcs.R

/**
 * Helper object for creating and managing the foreground service notification
 * used by VideoRelayService.
 *
 * This is a new isolated component — it does NOT modify any existing notification logic.
 */
object VideoRelayNotification {

    const val CHANNEL_ID = "video_relay_channel"
    const val NOTIFICATION_ID = 9001 // Unique ID that won't conflict with other notifications

    /**
     * Create the notification channel (required on API 26+).
     * Safe to call multiple times — no-op if already created.
     */
    fun createNotificationChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Video Relay",
            NotificationManager.IMPORTANCE_LOW // Low importance = no sound, minimal visual
        ).apply {
            description = "Notification for the live video relay service"
            setShowBadge(false)
        }
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.createNotificationChannel(channel)
    }

    /**
     * Build the ongoing foreground notification for the video relay service.
     *
     * @param context     Application or service context
     * @param contentText Status text to display (e.g., "Relaying video on port 5000 → 6000")
     */
    fun buildNotification(context: Context, contentText: String): Notification {
        // Tapping the notification opens the main activity
        val openIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            context,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // Stop action intent — uses action string constant
        val stopIntent = Intent(context, com.example.kftgcs.videorelay.VideoRelayService::class.java).apply {
            action = com.example.kftgcs.videorelay.VideoRelayService.ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            context,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Video Relay Active")
            .setContentText(contentText)
            .setSmallIcon(R.drawable.ic_launcher_foreground) // Uses existing app icon
            .setOngoing(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop Relay",
                stopPendingIntent
            )
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .build()
    }
}


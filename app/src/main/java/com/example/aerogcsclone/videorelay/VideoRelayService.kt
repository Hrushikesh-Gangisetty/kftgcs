package com.example.aerogcsclone.videorelay

import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.ServiceCompat
import timber.log.Timber

/**
 * Android Foreground Service that manages the UDP video relay pipeline.
 *
 * Receives camera stream packets on a configurable UDP port and forwards them
 * byte-for-byte to a configurable destination (GCS). Runs independently of all
 * other app services and does not interfere with existing sockets or threads.
 *
 * Start: Send an intent with config extras (or use VideoRelayManager).
 * Stop:  Send an intent with ACTION_STOP, or call context.stopService().
 */
class VideoRelayService : Service() {

    companion object {
        const val ACTION_STOP = "com.example.aerogcsclone.videorelay.ACTION_STOP"
        private const val TAG = "VideoRelayService"
    }

    private val forwarder = VideoForwarder()

    override fun onCreate() {
        super.onCreate()
        VideoRelayNotification.createNotificationChannel(this)
        Timber.d("$TAG: Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Handle stop action
        if (intent?.action == ACTION_STOP) {
            Timber.d("$TAG: Stop action received")
            stopRelay()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // Read configuration from intent extras (or use defaults)
        val config = VideoRelayConfig(
            listenPort = intent?.getIntExtra(
                VideoRelayConfig.EXTRA_LISTEN_PORT,
                VideoRelayConfig.DEFAULT_LISTEN_PORT
            ) ?: VideoRelayConfig.DEFAULT_LISTEN_PORT,
            destIp = intent?.getStringExtra(VideoRelayConfig.EXTRA_DEST_IP)
                ?: VideoRelayConfig.DEFAULT_DEST_IP,
            destPort = intent?.getIntExtra(
                VideoRelayConfig.EXTRA_DEST_PORT,
                VideoRelayConfig.DEFAULT_DEST_PORT
            ) ?: VideoRelayConfig.DEFAULT_DEST_PORT,
            bufferSize = intent?.getIntExtra(
                VideoRelayConfig.EXTRA_BUFFER_SIZE,
                VideoRelayConfig.DEFAULT_BUFFER_SIZE
            ) ?: VideoRelayConfig.DEFAULT_BUFFER_SIZE
        )

        // Start as foreground service with notification
        val notification = VideoRelayNotification.buildNotification(
            this,
            "Relaying UDP :${config.listenPort} → ${config.destIp}:${config.destPort}"
        )
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ServiceCompat.startForeground(
                this,
                VideoRelayNotification.NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(VideoRelayNotification.NOTIFICATION_ID, notification)
        }

        // Start the forwarding engine
        forwarder.start(config)

        // Update the manager's state
        VideoRelayManager.onServiceStarted(forwarder)

        Timber.d("$TAG: Relay started — listening on :${config.listenPort}, forwarding to ${config.destIp}:${config.destPort}")

        return START_STICKY
    }

    override fun onDestroy() {
        stopRelay()
        VideoRelayManager.onServiceStopped()
        Timber.d("$TAG: Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun stopRelay() {
        try {
            forwarder.stop()
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Error stopping forwarder")
        }
    }
}


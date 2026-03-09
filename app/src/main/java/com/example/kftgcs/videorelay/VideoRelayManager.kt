package com.example.kftgcs.videorelay

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber

/**
 * Singleton manager for the Video Relay pipeline.
 *
 * Provides a simple API to start/stop the VideoRelayService and observe
 * its running state and forwarding statistics.
 *
 * Usage:
 *   VideoRelayManager.start(context, config)
 *   VideoRelayManager.stop(context)
 *   VideoRelayManager.isRunning.collect { ... }
 *   VideoRelayManager.relayStats.collect { ... }
 */
object VideoRelayManager {

    private const val TAG = "VideoRelayManager"

    // Observable state
    private val _isRunning = MutableStateFlow(false)
    val isRunning: StateFlow<Boolean> = _isRunning.asStateFlow()

    private val _relayStats = MutableStateFlow(VideoForwarder.RelayStats())
    val relayStats: StateFlow<VideoForwarder.RelayStats> = _relayStats.asStateFlow()

    private val _forwarderState = MutableStateFlow(VideoForwarder.ForwarderState.STOPPED)
    val forwarderState: StateFlow<VideoForwarder.ForwarderState> = _forwarderState.asStateFlow()

    // Reference to the active forwarder (set by the service)
    private var activeForwarder: VideoForwarder? = null
    private var statsScope: CoroutineScope? = null

    /**
     * Start the video relay service with the given configuration.
     * The service runs as a foreground service and will survive activity lifecycle changes.
     */
    fun start(context: Context, config: VideoRelayConfig) {
        if (_isRunning.value) {
            Timber.w("$TAG: Relay is already running")
            return
        }

        // Persist config for future reference
        VideoRelayConfig.saveToPreferences(context, config)

        val intent = Intent(context, VideoRelayService::class.java).apply {
            putExtra(VideoRelayConfig.EXTRA_LISTEN_PORT, config.listenPort)
            putExtra(VideoRelayConfig.EXTRA_DEST_IP, config.destIp)
            putExtra(VideoRelayConfig.EXTRA_DEST_PORT, config.destPort)
            putExtra(VideoRelayConfig.EXTRA_BUFFER_SIZE, config.bufferSize)
        }

        try {
            ContextCompat.startForegroundService(context, intent)
            Timber.d("$TAG: Start command sent to VideoRelayService")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to start VideoRelayService")
        }
    }

    /**
     * Stop the video relay service.
     */
    fun stop(context: Context) {
        val intent = Intent(context, VideoRelayService::class.java).apply {
            action = VideoRelayService.ACTION_STOP
        }

        try {
            context.startService(intent)
            Timber.d("$TAG: Stop command sent to VideoRelayService")
        } catch (e: Exception) {
            Timber.e(e, "$TAG: Failed to send stop command")
            // Fallback: just stop the service directly
            try {
                context.stopService(Intent(context, VideoRelayService::class.java))
            } catch (e2: Exception) {
                Timber.e(e2, "$TAG: Failed to stop service directly")
            }
        }
    }

    /**
     * Called by VideoRelayService when the forwarder is started.
     * Wires up the stats and state flows.
     */
    internal fun onServiceStarted(forwarder: VideoForwarder) {
        activeForwarder = forwarder
        _isRunning.value = true

        // Cancel any previous stats collection scope
        statsScope?.cancel()
        statsScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

        // Bridge forwarder's flows to manager's flows
        statsScope?.launch {
            forwarder.stats.collect { stats ->
                _relayStats.value = stats
            }
        }
        statsScope?.launch {
            forwarder.state.collect { state ->
                _forwarderState.value = state
                if (state == VideoForwarder.ForwarderState.STOPPED ||
                    state == VideoForwarder.ForwarderState.ERROR
                ) {
                    _isRunning.value = false
                }
            }
        }
    }

    /**
     * Called by VideoRelayService when the service is destroyed.
     */
    internal fun onServiceStopped() {
        activeForwarder = null
        statsScope?.cancel()
        statsScope = null
        _isRunning.value = false
        _forwarderState.value = VideoForwarder.ForwarderState.STOPPED
    }
}


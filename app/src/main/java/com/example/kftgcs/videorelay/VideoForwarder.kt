package com.example.kftgcs.videorelay

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import timber.log.Timber
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.net.SocketException
import java.io.IOException

/**
 * Core UDP packet forwarding engine.
 *
 * Receives UDP datagrams on the configured listen port and forwards them
 * byte-for-byte (zero re-encoding) to the configured destination IP:port.
 *
 * Runs on a dedicated IO coroutine. Handles WiFi loss with retry/backoff.
 * Thread-safe start/stop with coroutine scope management.
 */
class VideoForwarder {

    private val tag = "VideoForwarder"

    /**
     * Live statistics about the relay pipeline.
     */
    data class RelayStats(
        val packetsForwarded: Long = 0,
        val bytesForwarded: Long = 0,
        val lastPacketTimestamp: Long = 0,
        val errorCount: Int = 0,
        val lastError: String? = null
    )

    /**
     * Possible states of the forwarder.
     */
    enum class ForwarderState {
        STOPPED,
        STARTING,
        RUNNING,
        ERROR,
        STOPPING
    }

    // State flows for external observation
    private val _stats = MutableStateFlow(RelayStats())
    val stats: StateFlow<RelayStats> = _stats.asStateFlow()

    private val _state = MutableStateFlow(ForwarderState.STOPPED)
    val state: StateFlow<ForwarderState> = _state.asStateFlow()

    // Internal state
    @Volatile
    private var running = false

    private var receiveSocket: DatagramSocket? = null
    private var sendSocket: DatagramSocket? = null
    private var forwardJob: Job? = null
    private var scope: CoroutineScope? = null

    /**
     * Start the UDP forwarding loop with the given configuration.
     * Non-blocking: launches the work on Dispatchers.IO.
     */
    fun start(config: VideoRelayConfig) {
        if (running) {
            Timber.w("$tag: Already running, ignoring start()")
            return
        }

        _state.value = ForwarderState.STARTING
        running = true

        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        forwardJob = scope?.launch {
            runForwardingLoop(config)
        }
    }

    /**
     * Stop the forwarding loop and release all resources.
     */
    fun stop() {
        if (!running) return
        _state.value = ForwarderState.STOPPING
        running = false

        // Close sockets to unblock any blocking receive() call
        try {
            receiveSocket?.close()
        } catch (e: Exception) {
            // Expected during shutdown
        }
        try {
            sendSocket?.close()
        } catch (e: Exception) {
            // Expected during shutdown
        }

        forwardJob?.cancel()
        scope?.cancel()
        forwardJob = null
        scope = null
        receiveSocket = null
        sendSocket = null

        _state.value = ForwarderState.STOPPED
        Timber.d("$tag: Stopped")
    }

    /**
     * Main forwarding loop. Binds receive socket, creates send socket,
     * and continuously forwards packets.
     */
    private suspend fun runForwardingLoop(config: VideoRelayConfig) {
        var retryCount = 0
        val maxRetries = 5
        val retryDelayMs = 1000L

        while (running && retryCount < maxRetries) {
            try {
                // Create and bind sockets
                receiveSocket = DatagramSocket(config.listenPort).apply {
                    reuseAddress = true
                    soTimeout = 3000 // 3-second timeout to allow periodic running check
                }
                sendSocket = DatagramSocket().apply {
                    reuseAddress = true
                }

                val destAddress = InetAddress.getByName(config.destIp)
                val buffer = ByteArray(config.bufferSize)
                val receivePacket = DatagramPacket(buffer, buffer.size)

                _state.value = ForwarderState.RUNNING
                retryCount = 0 // Reset retry count on successful bind
                Timber.d("$tag: Listening on UDP port ${config.listenPort}, forwarding to ${config.destIp}:${config.destPort}")

                while (running) {
                    try {
                        // Blocking receive (with soTimeout)
                        receiveSocket?.receive(receivePacket)

                        // Forward the exact received bytes to destination
                        val forwardPacket = DatagramPacket(
                            receivePacket.data,
                            receivePacket.offset,
                            receivePacket.length,
                            destAddress,
                            config.destPort
                        )
                        sendSocket?.send(forwardPacket)

                        // Update stats
                        val currentStats = _stats.value
                        _stats.value = currentStats.copy(
                            packetsForwarded = currentStats.packetsForwarded + 1,
                            bytesForwarded = currentStats.bytesForwarded + receivePacket.length,
                            lastPacketTimestamp = System.currentTimeMillis()
                        )

                    } catch (e: java.net.SocketTimeoutException) {
                        // Timeout is expected — allows us to re-check `running` flag
                        continue
                    } catch (e: SocketException) {
                        if (!running) {
                            // Socket was closed intentionally via stop()
                            break
                        }
                        throw e // Re-throw for outer retry handler
                    }
                }

            } catch (e: SocketException) {
                if (!running) {
                    Timber.d("$tag: Socket closed during shutdown (expected)")
                    break
                }
                retryCount++
                Timber.e(e, "$tag: Socket error (retry $retryCount/$maxRetries)")
                updateErrorStats("Socket error: ${e.message}")
                cleanupSockets()
                if (retryCount < maxRetries && running) {
                    delay(retryDelayMs)
                }

            } catch (e: IOException) {
                retryCount++
                Timber.e(e, "$tag: IO error (retry $retryCount/$maxRetries)")
                updateErrorStats("IO error: ${e.message}")
                cleanupSockets()
                if (retryCount < maxRetries && running) {
                    delay(retryDelayMs)
                }

            } catch (e: Exception) {
                Timber.e(e, "$tag: Unexpected error")
                updateErrorStats("Unexpected error: ${e.message}")
                cleanupSockets()
                break
            }
        }

        if (retryCount >= maxRetries) {
            _state.value = ForwarderState.ERROR
            Timber.e("$tag: Max retries reached, stopping forwarder")
        }

        cleanupSockets()
        if (_state.value != ForwarderState.STOPPED) {
            _state.value = ForwarderState.STOPPED
        }
    }

    private fun updateErrorStats(error: String) {
        val current = _stats.value
        _stats.value = current.copy(
            errorCount = current.errorCount + 1,
            lastError = error
        )
    }

    private fun cleanupSockets() {
        try { receiveSocket?.close() } catch (_: Exception) {}
        try { sendSocket?.close() } catch (_: Exception) {}
        receiveSocket = null
        sendSocket = null
    }

    /**
     * Reset accumulated statistics.
     */
    fun resetStats() {
        _stats.value = RelayStats()
    }
}


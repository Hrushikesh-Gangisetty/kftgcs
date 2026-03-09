package com.example.kftgcs.videorelay

import android.content.Context
import android.content.SharedPreferences

/**
 * Configuration for the Video Relay pipeline.
 * All values are externally configurable via SharedPreferences or intent extras.
 *
 * @param listenPort  UDP port to listen on for incoming camera stream (default 5000)
 * @param destIp      Destination IP address to forward packets to (GCS IP)
 * @param destPort    Destination UDP port to forward packets to (default 6000)
 * @param bufferSize  Max UDP datagram buffer size in bytes (default 65535)
 */
data class VideoRelayConfig(
    val listenPort: Int = DEFAULT_LISTEN_PORT,
    val destIp: String = DEFAULT_DEST_IP,
    val destPort: Int = DEFAULT_DEST_PORT,
    val bufferSize: Int = DEFAULT_BUFFER_SIZE
) {
    companion object {
        const val DEFAULT_LISTEN_PORT = 5000
        const val DEFAULT_DEST_IP = "127.0.0.1"
        const val DEFAULT_DEST_PORT = 6000
        const val DEFAULT_BUFFER_SIZE = 65535

        // SharedPreferences keys
        private const val PREFS_NAME = "video_relay_prefs"
        private const val KEY_LISTEN_PORT = "listen_port"
        private const val KEY_DEST_IP = "dest_ip"
        private const val KEY_DEST_PORT = "dest_port"
        private const val KEY_BUFFER_SIZE = "buffer_size"

        // Intent extra keys (used by VideoRelayService)
        const val EXTRA_LISTEN_PORT = "extra_listen_port"
        const val EXTRA_DEST_IP = "extra_dest_ip"
        const val EXTRA_DEST_PORT = "extra_dest_port"
        const val EXTRA_BUFFER_SIZE = "extra_buffer_size"

        /**
         * Load configuration from SharedPreferences, falling back to defaults.
         */
        fun fromPreferences(context: Context): VideoRelayConfig {
            val prefs: SharedPreferences =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            return VideoRelayConfig(
                listenPort = prefs.getInt(KEY_LISTEN_PORT, DEFAULT_LISTEN_PORT),
                destIp = prefs.getString(KEY_DEST_IP, DEFAULT_DEST_IP) ?: DEFAULT_DEST_IP,
                destPort = prefs.getInt(KEY_DEST_PORT, DEFAULT_DEST_PORT),
                bufferSize = prefs.getInt(KEY_BUFFER_SIZE, DEFAULT_BUFFER_SIZE)
            )
        }

        /**
         * Save configuration to SharedPreferences for persistence.
         */
        fun saveToPreferences(context: Context, config: VideoRelayConfig) {
            val prefs: SharedPreferences =
                context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs.edit()
                .putInt(KEY_LISTEN_PORT, config.listenPort)
                .putString(KEY_DEST_IP, config.destIp)
                .putInt(KEY_DEST_PORT, config.destPort)
                .putInt(KEY_BUFFER_SIZE, config.bufferSize)
                .apply()
        }
    }
}


package com.example.aerogcsclone.telemetry

import android.util.Log
import com.example.aerogcsclone.GCSApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

/**
 * Monitors connection status and triggers RTL if drone disconnects during flight.
 * This provides failsafe protection when connection is lost mid-flight.
 */
class DisconnectionRTLMonitor(
    private val repository: MavlinkTelemetryRepository,
    private val scope: CoroutineScope
) {
    // Track previous connection state to detect disconnection events
    private var wasConnected = false

    // Track if drone was in flight before disconnection
    private var wasInFlight = false
    private var lastKnownAltitude = 0f
    private var lastKnownArmed = false

    // Flag to track if RTL was already sent for this disconnection
    private var rtlSentForCurrentDisconnection = false

    /**
     * Start monitoring connection and flight status
     */
    fun startMonitoring(telemetryState: StateFlow<com.example.aerogcsclone.Telemetry.TelemetryState>) {
        scope.launch {
            telemetryState.collect { state ->
                val isConnected = state.connected
                val isArmed = state.armed
                val altitude = state.altitudeRelative ?: 0f

                // Update flight status tracking
                if (isConnected) {
                    wasInFlight = isArmed && altitude > 0.5f
                    lastKnownAltitude = altitude
                    lastKnownArmed = isArmed

                    // Reset RTL flag when connected
                    if (!wasConnected) {
                        rtlSentForCurrentDisconnection = false
                        Log.i("DisconnectionRTL", "Connection established - monitoring active")
                    }
                }

                // Detect disconnection event
                if (wasConnected && !isConnected) {
                    handleDisconnection()
                }

                wasConnected = isConnected
            }
        }
    }

    /**
     * Handle disconnection event - trigger RTL if drone was in flight
     */
    private fun handleDisconnection() {
        Log.w("DisconnectionRTL", "========== DISCONNECTION DETECTED ==========")
        Log.w("DisconnectionRTL", "Was in flight: $wasInFlight")
        Log.w("DisconnectionRTL", "Last altitude: ${lastKnownAltitude}m")
        Log.w("DisconnectionRTL", "Was armed: $lastKnownArmed")

        // Check if we should trigger RTL
        if (wasInFlight && !rtlSentForCurrentDisconnection) {
            Log.w("DisconnectionRTL", "🚨 DRONE WAS IN FLIGHT - ATTEMPTING EMERGENCY RTL 🚨")

            // Mark that we've sent RTL for this disconnection
            rtlSentForCurrentDisconnection = true

            // Update global app state
            GCSApplication.isDroneInFlight = false // Prevent crash handler from also sending RTL

            // Attempt to send RTL command
            scope.launch {
                try {
                    // Try to reconnect briefly and send RTL
                    val rtlSent = attemptEmergencyRTL()

                    if (rtlSent) {
                        Log.i("DisconnectionRTL", "✓ Emergency RTL command sent successfully")
                    } else {
                        Log.e("DisconnectionRTL", "❌ Failed to send emergency RTL command")
                    }
                } catch (e: Exception) {
                    Log.e("DisconnectionRTL", "❌ Exception while sending RTL", e)
                }
            }
        } else if (!wasInFlight) {
            Log.i("DisconnectionRTL", "Drone was not in flight - no RTL needed")
        } else {
            Log.i("DisconnectionRTL", "RTL already sent for this disconnection")
        }

        Log.w("DisconnectionRTL", "============================================")
    }

    /**
     * Attempt to send emergency RTL command
     * Returns true if command was sent, false otherwise
     */
    private suspend fun attemptEmergencyRTL(): Boolean {
        return try {
            Log.i("DisconnectionRTL", "Sending RTL mode command (mode 6)...")

            // Send RTL command directly through repository
            // Mode 6 = RTL for ArduPilot
            repository.changeMode(6u)

            Log.i("DisconnectionRTL", "RTL command sent")
            true
        } catch (e: Exception) {
            Log.e("DisconnectionRTL", "Failed to send RTL command", e)
            false
        }
    }

    /**
     * Stop monitoring (cleanup)
     */
    fun stopMonitoring() {
        Log.i("DisconnectionRTL", "Monitoring stopped")
        wasConnected = false
        wasInFlight = false
        rtlSentForCurrentDisconnection = false
    }
}


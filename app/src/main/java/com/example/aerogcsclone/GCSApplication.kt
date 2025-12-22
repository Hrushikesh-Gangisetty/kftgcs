package com.example.aerogcsclone

import android.app.Application
import android.util.Log

/**
 * Custom Application class to handle app-level initialization and crash detection.
 * Implements crash handler that triggers RTL when app crashes during flight.
 */
class GCSApplication : Application() {

    private var defaultExceptionHandler: Thread.UncaughtExceptionHandler? = null

    companion object {
        @Volatile
        private var instance: GCSApplication? = null

        fun getInstance(): GCSApplication? = instance

        // Flag to track if drone is currently in flight
        @Volatile
        var isDroneInFlight: Boolean = false

        // Flag to track if we're connected to drone
        @Volatile
        var isConnectedToDrone: Boolean = false

        // Callback to trigger RTL
        @Volatile
        var onTriggerEmergencyRTL: (() -> Unit)? = null
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        // Setup global crash handler
        setupCrashHandler()

        Log.i("GCSApplication", "✓ Application initialized with crash handler")
    }

    /**
     * Setup global uncaught exception handler to trigger RTL on crash
     */
    private fun setupCrashHandler() {
        // Save the default exception handler
        defaultExceptionHandler = Thread.getDefaultUncaughtExceptionHandler()

        // Set custom exception handler
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            Log.e("GCSApplication", "========== APP CRASH DETECTED ==========")
            Log.e("GCSApplication", "Thread: ${thread.name}")
            Log.e("GCSApplication", "Error: ${throwable.message}", throwable)
            Log.e("GCSApplication", "Drone in flight: $isDroneInFlight")
            Log.e("GCSApplication", "Connected: $isConnectedToDrone")

            // If drone is in flight and connected, trigger emergency RTL
            if (isDroneInFlight && isConnectedToDrone) {
                Log.w("GCSApplication", "🚨 TRIGGERING EMERGENCY RTL DUE TO APP CRASH 🚨")

                try {
                    // Trigger RTL synchronously before app dies
                    onTriggerEmergencyRTL?.invoke()

                    // Give some time for RTL command to be sent
                    Thread.sleep(500)

                    Log.i("GCSApplication", "✓ Emergency RTL command sent")
                } catch (e: Exception) {
                    Log.e("GCSApplication", "❌ Failed to send emergency RTL", e)
                }
            } else {
                Log.i("GCSApplication", "No emergency RTL needed (not in flight or not connected)")
            }

            Log.e("GCSApplication", "=========================================")

            // Call the default handler to properly crash the app
            defaultExceptionHandler?.uncaughtException(thread, throwable)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        instance = null
    }
}

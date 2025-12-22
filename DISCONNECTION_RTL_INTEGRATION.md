# Integration Guide: Disconnection RTL Handler

## Quick Integration Steps

Add this single line to the `connect()` function in `SharedViewModel.kt`, right after `newRepo.start()`:

### Location: SharedViewModel.kt, line ~527

```kotlin
val newRepo = MavlinkTelemetryRepository(provider, this@SharedViewModel)
repo = newRepo
newRepo.start()

// ADD THIS LINE:
DisconnectionRTLHandler.startMonitoring(_telemetryState, newRepo, viewModelScope)
```

### Complete Code Block (for reference):

```kotlin
fun connect() {
    viewModelScope.launch {
        val provider: MavConnectionProvider? = when (_connectionType.value) {
            ConnectionType.TCP -> {
                val portInt = port.value.toIntOrNull()
                if (portInt != null) {
                    TcpConnectionProvider(ipAddress.value, portInt)
                } else {
                    Log.e("SharedVM", "Invalid port number.")
                    null
                }
            }
            ConnectionType.BLUETOOTH -> {
                selectedDevice.value?.device?.let {
                    BluetoothConnectionProvider(it)
                } ?: run {
                    Log.e("SharedVM", "No Bluetooth device selected.")
                    null
                }
            }
        }

        if (provider == null) {
            Log.e("SharedVM", "Failed to create connection provider.")
            return@launch
        }

        // If there's an old repo, close its connection first
        repo?.closeConnection()

        val newRepo = MavlinkTelemetryRepository(provider, this@SharedViewModel)
        repo = newRepo
        newRepo.start()
        
        // ✅ START DISCONNECTION RTL MONITORING
        DisconnectionRTLHandler.startMonitoring(_telemetryState, newRepo, viewModelScope)
        
        viewModelScope.launch {
            newRepo.state.collect { repoState ->
                // ... existing code ...
            }
        }
        
        // ... rest of existing code ...
    }
}
```

## What This Does

The `DisconnectionRTLHandler.startMonitoring()` call:
1. Monitors the telemetry state for connection status changes
2. Tracks if drone is in flight (armed + altitude > 0.5m)
3. Detects when connection drops during flight
4. Automatically sends RTL command (mode 6) if disconnection occurs mid-flight
5. Prevents duplicate RTL commands from crash handler

## Cleanup (Optional)

Add cleanup in `cancelConnection()` function:

```kotlin
suspend fun cancelConnection() {
    repo?.let {
        try {
            it.closeConnection()
        } catch (e: Exception) {
            Log.e("SharedVM", "Error closing connection", e)
        }
    }
    
    // Stop disconnection monitoring
    DisconnectionRTLHandler.stopMonitoring()
    
    repo = null
    _telemetryState.value = TelemetryState()
}
```

That's it! The disconnection RTL feature will now be active.


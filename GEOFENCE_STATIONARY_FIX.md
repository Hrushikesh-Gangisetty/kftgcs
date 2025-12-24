# Geofence Fix - Stationary Geofence Implementation

## Problem
When geofence was enabled on the MainPage in manual mode, the geofence was moving along with the drone instead of remaining stationary. The drone should move within the fence boundaries, and when it crosses the fence, it should trigger RTL (Return to Launch).

## Root Cause
The geofence was being recalculated on every telemetry state update (which includes drone position changes):
1. In the `init` block, `updateGeofencePolygon()` was called every time telemetry state changed
2. The `updateGeofencePolygon()` function was including the current drone position in the geofence calculation

This caused the geofence to continuously update and move with the drone.

## Solution

### Changes Made to `SharedViewModel.kt`

#### 1. Removed Dynamic Geofence Updates (Line ~1992)
**Before:**
```kotlin
init {
    viewModelScope.launch {
        telemetryState.collect { state ->
            checkGeofenceViolation(state)
            updateGeofencePolygon() // <-- This was causing the fence to move
        }
    }
}
```

**After:**
```kotlin
init {
    viewModelScope.launch {
        telemetryState.collect { state ->
            checkGeofenceViolation(state)
            // Do NOT update geofence polygon on every position change - it should stay stationary
        }
    }
}
```

#### 2. Removed Current Drone Position from Geofence Calculation (Line ~751)
**Before:**
```kotlin
// Always include current drone position
val droneLatitude = _telemetryState.value.latitude
val droneLongitude = _telemetryState.value.longitude
if (droneLatitude != null && droneLongitude != null) {
    val dronePos = LatLng(droneLatitude, droneLongitude)
    allWaypoints.add(dronePos)
    Log.d("Geofence", "Added current drone position to geofence: $dronePos")
}
```

**After:**
```kotlin
// DO NOT include current drone position - geofence should remain stationary
// The drone should move within the fence, not the fence move with the drone
```

## How It Works Now

1. **Geofence Creation**: When geofence is enabled, it captures the home position (drone's position at that moment) and creates a stationary fence around:
   - Home position (where drone was when geofence was enabled)
   - Uploaded mission waypoints
   - Planning waypoints
   - Survey polygon points
   - Grid waypoints

2. **Stationary Fence**: The geofence polygon remains fixed and does not update as the drone moves

3. **Violation Detection**: The `checkGeofenceViolation()` function continuously monitors if the drone is inside the geofence polygon

4. **RTL Trigger**: When the drone crosses the geofence boundary, it automatically:
   - Detects the violation
   - Adds a warning notification
   - Switches to RTL (Return to Launch) mode
   - Logs the event

5. **Return to Safe Zone**: When drone returns inside the geofence, it clears the violation flag and logs the event

## Testing

To test the fix:
1. Start the app and connect to the drone
2. Upload a mission with waypoints
3. Enable geofence in MainPage
4. Switch to manual mode
5. Fly the drone within the geofence - it should move freely inside
6. Try to fly outside the geofence boundary - RTL should trigger automatically
7. Verify the geofence polygon stays stationary on the map while the drone moves

## Files Modified
- `app/src/main/java/com/example/aerogcsclone/telemetry/SharedViewModel.kt`

## Date
December 24, 2025


# Geofence Pre-emptive BRAKE + RTL Implementation

## Overview

This implementation addresses the issue where drones would continue moving forward due to residual speed/momentum when a geofence breach was detected, potentially crashing beyond the fence boundary.

## Problem

When the drone reaches/breaches the geofence boundary:
1. RTL was triggered immediately
2. But the drone's momentum would carry it forward several meters before the RTL took effect
3. This could result in the drone crashing into obstacles outside the fence

## Solution: Combined Approach

We implemented a **two-pronged solution**:

### 1. Pre-emptive Detection (Early Warning Buffer)
- RTL triggers when drone is **within 3 meters of the fence** (not just when crossing)
- This gives time for the brake and RTL to take effect before actual breach
- Buffer distance: `GEOFENCE_WARNING_BUFFER_METERS = 3.0` meters

### 2. Emergency BRAKE Mode First
- When approaching/breaching the fence, we first switch to **BRAKE mode** (ArduPilot mode 17)
- BRAKE mode immediately stops all horizontal movement
- After 1 second (for drone to stabilize), we switch to RTL
- This eliminates momentum-induced overshoot

## Files Modified

### 1. TelemetryRepository.kt
- Added `BRAKE` mode constant: `const val BRAKE: UInt = 17u`
- Added BRAKE mode recognition in `changeMode()` function

### 2. GeofenceUtils.kt
- Added `haversineDistance()` - Calculate distance between two LatLng points in meters
- Added `distanceToPolygonEdge()` - Calculate distance from drone to nearest fence edge
- Added `distanceToLineSegment()` - Helper for edge distance calculation

### 3. SharedViewModel.kt
- Added `_geofenceWarningTriggered` state to track pre-emptive warnings
- Reduced check interval from 1000ms to 500ms for faster response
- Added `GEOFENCE_WARNING_BUFFER_METERS` constant (3 meters)
- Added `triggerEmergencyBrakeAndRTL()` function for the BRAKE → RTL sequence
- Updated `checkGeofenceViolation()` with:
  - Distance-to-fence calculation
  - Pre-emptive warning zone detection
  - Hysteresis to prevent oscillation (must be > 6m inside to clear warning)

## Sequence of Events

### When Drone Approaches Fence (Within 3m Buffer)
```
1. Drone Position Check (every 500ms)
2. Calculate distance to nearest fence edge
3. If distance ≤ 3m AND inside fence:
   - Log: "GEOFENCE WARNING! Drone approaching fence"
   - Notification: "APPROACHING GEOFENCE"
   - Execute: triggerEmergencyBrakeAndRTL()
```

### When Drone Breaches Fence
```
1. If drone crosses outside fence:
   - Log: "GEOFENCE BREACH!"
   - Notification: "GEOFENCE BREACH"
   - Execute: triggerEmergencyBrakeAndRTL()
```

### Emergency BRAKE + RTL Procedure
```
1. Send BRAKE mode command (mode 17)
2. Notification: "BRAKE ENGAGED"
3. Wait 1000ms for drone to stabilize
4. Send RTL mode command (mode 6)
5. Notification: "RTL ACTIVATED"
6. TTS Announcement: "Geofence breach detected..."
7. Fallback: If BRAKE fails, try direct RTL
```

### When Drone Returns to Safe Zone
```
1. If distance > 6m (double buffer) AND inside fence:
   - Clear violation flags
   - Log: "Drone returned to safe zone"
   - Notification: "GEOFENCE CLEAR"
```

## Key Constants

| Constant | Value | Description |
|----------|-------|-------------|
| `GEOFENCE_WARNING_BUFFER_METERS` | 3.0 | Pre-emptive trigger distance |
| `geofenceCheckInterval` | 500ms | Position check frequency |
| `MavMode.BRAKE` | 17u | ArduPilot BRAKE mode ID |
| BRAKE delay before RTL | 1000ms | Time for drone to fully stop |
| Hysteresis clear distance | 6m | Must be this far inside to clear warning |

## ArduPilot BRAKE Mode

BRAKE mode (mode 17) is specifically designed to:
- Immediately stop all horizontal movement
- Maintain current altitude
- Hold position using GPS
- Takes effect within ~100ms

This is much more effective than directly switching to RTL because:
- RTL first calculates return path, then starts returning
- BRAKE immediately arrests all movement
- Prevents any forward momentum from carrying drone outside fence

## TTS Announcements

When geofence action is triggered:
> "Geofence breach detected. Emergency brake engaged. Returning to home."

## Testing Recommendations

1. **Simulation Test**: Test in SITL first to verify BRAKE → RTL sequence
2. **Buffer Distance Test**: Verify pre-emptive trigger at 3m from fence
3. **Speed Test**: Test at various drone speeds (5, 10, 15 m/s)
4. **Hysteresis Test**: Verify drone doesn't oscillate at fence edge
5. **Fallback Test**: Disconnect during BRAKE to verify RTL fallback

## Future Improvements

1. **Dynamic Buffer**: Adjust buffer distance based on drone speed
   - Faster drone = larger buffer needed
   - Could use: `buffer = baseBuffer + (speed * reactionTime)`

2. **Gradual Deceleration**: Instead of hard BRAKE, slow down gradually as approaching fence

3. **Configurable Settings**: Allow user to adjust:
   - Warning buffer distance
   - BRAKE duration before RTL
   - Enable/disable BRAKE (for drones without BRAKE mode)


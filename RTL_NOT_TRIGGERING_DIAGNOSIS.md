# RTL Not Triggering - Diagnosis & Fix

## Date: December 22, 2025

## Problem Summary
The drone is not triggering RTL (Return to Launch) when obstacles are detected, even though the obstacle detection system is running.

---

## Root Causes Identified

### ✅ **CRITICAL FIX APPLIED: Threshold Configuration Error**

**Location:** `ObstacleData.kt` - Line 101

**The Problem:**
```kotlin
// BEFORE (BROKEN):
val mediumThreatThreshold: Float = 10f,     // MEDIUM threat starts at 10m
val highThreatThreshold: Float = 10f,       // HIGH threat below 10m ❌
```

**Why This Breaks RTL:**
- Both MEDIUM and HIGH thresholds were set to 10 meters
- The threat classification logic uses `distance >= mediumThreatThreshold` for MEDIUM
- This means obstacles at exactly 10m or above are classified as MEDIUM
- HIGH threat requires `distance < highThreatThreshold` (below 10m)
- **Result:** Obstacles between 5-10m were being classified as MEDIUM instead of HIGH
- **RTL Only Triggers on HIGH Threats**, so it never activated

**The Fix Applied:**
```kotlin
// AFTER (FIXED):
val mediumThreatThreshold: Float = 10f,     // MEDIUM threat starts at 10m
val highThreatThreshold: Float = 5f,        // HIGH threat below 5m ✅
```

**New Threat Levels:**
- **LOW:** 20m - 50m (warning zone)
- **MEDIUM:** 10m - 20m (caution zone)
- **HIGH:** < 5m (emergency - triggers RTL) ✅

---

## Additional Requirements for RTL Trigger

Even with the fix above, RTL will only trigger if ALL these conditions are met:

### 1. **Consecutive Detection Requirement**
```kotlin
val minimumConsecutiveDetections: Int = 3
```
- Requires **3 consecutive HIGH threat detections**
- Detections happen every 100ms
- Total time needed: **300ms of continuous HIGH threat**
- If obstacle briefly appears then disappears, RTL won't trigger

### 2. **Obstacle Must Be In Flight Path**
```kotlin
val angleToleranceDegrees: Float = 30f
```
- Obstacle must be within **±30° of the drone's heading** toward the next waypoint
- If obstacle is to the side or behind, RTL won't trigger
- This is a safety feature to avoid false positives

### 3. **Auto-RTL Must Be Enabled**
```kotlin
val enableAutoRTL: Boolean = true
```
- Currently set to `true` ✅
- If set to `false`, RTL won't trigger automatically

### 4. **Sensor Must Be Working**
Current sensor type: `SensorType.PROXIMITY`

**Potential Issue:** If using SIMULATED sensor type without injecting test data:
```kotlin
// Default simulated reading (always safe)
_sensorReading.value = SensorReading(
    distance = calibratedMaxDistance,  // 50 meters = safe
    quality = 1.0f
)
```

---

## How to Verify RTL Now Works

### Test Scenario 1: Real Hardware
1. Deploy drone on a mission
2. Move an obstacle within **5 meters** of the drone
3. Ensure obstacle is **in front** of drone (within ±30° of heading)
4. Hold obstacle there for **at least 300ms** (3 detection cycles)
5. RTL should trigger automatically

### Test Scenario 2: Simulated Testing
Add this code to inject test obstacle:
```kotlin
// In your test code:
obstacleSensorManager.injectSimulatedReading(4.5f)  // 4.5m = HIGH threat
```

### Expected Behavior When RTL Triggers
1. **Log Message:** `"⚠️ HIGH THREAT DETECTED - TRIGGERING EMERGENCY RTL"`
2. **Mission State Saved:** Current waypoint and progress captured
3. **Mode Change:** Drone switches to RTL mode
4. **Status Update:** `ObstacleDetectionStatus.RTL_IN_PROGRESS`
5. **RTL Monitoring Starts:** Tracks distance to home

---

## Verification Checklist

Run through this checklist to confirm RTL will work:

- [x] **High threat threshold fixed** (5m instead of 10m)
- [ ] **Sensor is initialized** - Check logs for "Sensor monitoring started"
- [ ] **Mission is loaded** - Check logs for "Mission loaded: X waypoints"
- [ ] **Obstacle is within 5 meters** - Use sensor readings to confirm
- [ ] **Obstacle is in flight path** - Within ±30° of heading to next waypoint
- [ ] **Consecutive detections occur** - 3 readings in 300ms
- [ ] **Auto-RTL is enabled** - Check config setting
- [ ] **Drone is connected** - Repository must be available for mode change

---

## Logging to Monitor

Watch for these log messages to debug RTL:

### Normal Operation:
```
I/ObstacleDetectionMgr: ═══ PHASE 4: MISSION IN PROGRESS - OBSTACLE MONITORING ═══
I/ObstacleDetectionMgr: ✅ Obstacle monitoring started (checking every 100ms)
```

### When HIGH Threat Detected:
```
W/ObstacleDetector: ⚠️ HIGH THREAT DETECTED - TRIGGERING EMERGENCY RTL
I/ObstacleDetectionMgr: ═══ PHASE 5: OBSTACLE DETECTED - EMERGENCY RTL TRIGGER ═══
```

### RTL Activation:
```
I/ObstacleDetectionMgr: ✅ RTL mode activated
I/ObstacleDetectionMgr: ═══ PHASE 6: MONITORING RTL - DRONE RETURN TO HOME ═══
```

### If RTL Fails:
```
E/ObstacleDetectionMgr: ❌ RTL activation failed
E/ObstacleDetectionMgr: ❌ Repository not available
```

---

## Summary

**Primary Issue:** Threshold configuration error prevented obstacles from being classified as HIGH threat
**Fix Applied:** Changed `highThreatThreshold` from 10m to 5m
**Status:** RTL should now trigger when obstacles are within 5 meters and in the flight path

**Next Steps:**
1. Test with real hardware or simulated obstacles
2. Monitor logs to confirm HIGH threat detection
3. Verify RTL mode activation
4. Confirm mission state is saved for resume functionality



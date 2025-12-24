# Geofence Testing Guide

## Quick Test Steps

### 1. Basic Geofence Test (Manual Mode)
1. **Connect to drone** - Ensure drone is connected and GPS lock is acquired
2. **Upload mission** - Go to Plan screen and upload a mission with waypoints
3. **Enable geofence** - In MainPage, toggle geofence ON
4. **Verify geofence display** - You should see a red polygon around your mission waypoints
5. **Switch to manual mode** - Change flight mode to STABILIZE or LOITER
6. **Fly within fence** - Manually fly the drone inside the geofence boundary
   - ✅ Drone should move freely
   - ✅ Geofence should stay stationary (NOT move with drone)
7. **Cross the fence** - Try to fly outside the geofence boundary
   - ✅ App should detect violation
   - ✅ Warning notification should appear
   - ✅ Drone should automatically switch to RTL mode

### 2. Geofence Without Mission
1. **Connect to drone** - Get GPS lock
2. **Enable geofence** - Toggle geofence ON in MainPage
3. **Verify behavior** - Since no waypoints exist:
   - Geofence will be created around home position only
   - A small fence around where drone was when geofence was enabled

### 3. Expected Behavior

#### ✅ CORRECT Behavior (After Fix)
- Geofence polygon is drawn once when enabled
- Geofence stays in fixed position on map
- Drone marker moves freely on map
- When drone crosses fence boundary → RTL triggers
- Geofence vertices don't move as drone flies

#### ❌ INCORRECT Behavior (Before Fix)
- Geofence moves along with drone
- Fence boundary continuously updates
- Drone can never cross fence (fence follows drone)

### 4. Visual Indicators

**Geofence Polygon:**
- Red boundary lines
- Semi-transparent red fill (20% alpha)
- Orange/Red markers at vertices (if adjustment mode enabled)

**Drone:**
- Quadcopter icon
- Moves independently of geofence
- Heading indicator shows direction

### 5. Notifications to Watch For

**Geofence Violation:**
```
GEOFENCE VIOLATION: Drone crossed boundary! Switching to RTL mode
```

**RTL Activation:**
```
RTL ACTIVATED: Return to Launch mode activated due to geofence violation
```

**Return to Safe Zone:**
```
GEOFENCE CLEAR: Drone returned to safe zone
```

### 6. Debugging Tips

**Check Logs:**
```
Tag: Geofence
- "Home position captured: lat, lon"
- "Generating square/polygon geofence with X points"
- "Geofence generated successfully with X vertices"
- "GEOFENCE VIOLATION DETECTED!"
- "Drone returned to safe zone"
```

**If Geofence Not Appearing:**
- Ensure mission is uploaded first
- Check geofence toggle is ON
- Verify GPS lock is acquired
- Check logcat for "Geofence" tag messages

**If Geofence Still Moves:**
- Check that changes to SharedViewModel.kt are applied
- Rebuild the project (Build → Clean Project → Rebuild Project)
- Restart the app

### 7. Advanced Testing

**Test Geofence Adjustment:**
1. Enable geofence in MainPage
2. Drag geofence vertices to adjust boundary
3. Fly drone - fence should stay in adjusted position
4. Cross adjusted boundary - RTL should trigger

**Test Multiple Scenarios:**
- Grid mission with geofence
- Linear mission with geofence
- Polygon survey with geofence
- Manual flight only (no mission uploaded)

### 8. Performance Check

Monitor for:
- Smooth map rendering
- No lag when drone moves
- Geofence polygon remains stable
- RTL triggers within 1 second of boundary crossing

## Expected Outcome

✅ **The geofence should behave like a physical fence:**
- Stays in one place on the ground
- Drone flies within or outside the fence
- Crossing the fence boundary triggers automatic safety response (RTL)
- The fence does NOT follow the drone around

---
**Date:** December 24, 2025


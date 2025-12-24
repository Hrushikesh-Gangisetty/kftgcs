# Geofence Manual Adjustment - Current Implementation Status

## Current Status: ✅ IMPLEMENTED (Needs Testing)

### How It Currently Works

#### In MainPage:
1. **Enable Geofence** - Toggle geofence ON
2. **Markers Appear** - Orange/Red markers appear at geofence vertices
3. **Click to Select** - Tap a marker to select it (turns yellow)
4. **Drag to Adjust** - Drag the selected marker to new position
5. **Auto-Save** - Changes are automatically saved via `updateGeofencePolygonManually()`

#### In PlanScreen:
1. **Enable Geofence** - Toggle geofence ON while planning
2. **Markers Appear** - Orange/Red markers at geofence vertices
3. **Click to Select** - Tap a marker to select it (turns yellow)
4. **Drag to Adjust** - Drag to adjust the fence boundary
5. **Local Updates** - If planning, updates local polygon; otherwise updates shared polygon

### Implementation Details

#### Code Location: `GcsMap.kt` (Lines 256-290)

**Geofence Markers:**
```kotlin
if (geofenceAdjustmentEnabled) {
    geofencePolygon.forEachIndexed { index, point ->
        val markerState = rememberMarkerState(position = point)
        
        // Auto-detect drag and callback
        LaunchedEffect(markerState.position) {
            if (markerState.position != point) {
                onGeofencePointDrag(index, markerState.position)
            }
        }
        
        Marker(
            state = markerState,
            title = "GF${index + 1}",
            icon = if (selected) yellowMarker else orangeMarker,
            draggable = true,  // ✅ Dragging enabled
            onClick = { onGeofencePointClick(index); true }
        )
    }
}
```

### Visual Indicators

| State | Marker Color | Description |
|-------|-------------|-------------|
| **Normal** | 🟠 Orange/Red | Default geofence vertex |
| **Selected** | 🟡 Yellow | Clicked vertex ready to drag |
| **Waypoint** | 🔵 Blue | Mission waypoint |
| **Polygon** | 🟣 Purple | Survey polygon point |

### Activation Requirements

For geofence markers to appear:
- ✅ `geofenceEnabled` must be `true`
- ✅ `geofenceAdjustmentEnabled` must be `true` (auto-set when geofence enabled)
- ✅ `geofencePolygon` must have at least 3 points

### Current Behavior

**MainPage:**
```kotlin
geofenceAdjustmentEnabled = geofenceEnabled  // ✅ Enabled when geofence is ON
```

**PlanScreen:**
```kotlin
geofenceAdjustmentEnabled = geofenceEnabled  // ✅ Enabled when geofence is ON
```

## Testing Status

### ❓ Needs User Testing

The feature is **implemented in code** but needs verification:

1. **Markers Visible?**
   - [ ] Do orange/red markers appear at geofence vertices when enabled?
   - [ ] Are markers clearly visible and distinguishable from waypoints?

2. **Selection Works?**
   - [ ] Does tapping a marker select it (turn yellow)?
   - [ ] Can you see the color change?

3. **Dragging Works?**
   - [ ] Can you drag a selected marker?
   - [ ] Does the marker follow your finger/mouse?
   - [ ] Does the geofence polygon update in real-time?

4. **Persistence Works?**
   - [ ] Do changes persist after releasing the marker?
   - [ ] Does the fence stay in new position?

## Potential Issues

### Issue 1: Markers Not Visible
**Symptom:** No orange markers appear when geofence enabled

**Possible Causes:**
- Geofence polygon is empty
- Less than 3 vertices in polygon
- Markers rendered behind other elements
- Geofence adjustment mode not properly enabled

**Solution:**
- Verify `geofencePolygon.size >= 3`
- Check logcat for "Geofence generated with X vertices"
- Ensure geofence toggle is ON

### Issue 2: Dragging Not Working
**Symptom:** Markers visible but can't drag them

**Possible Causes:**
- Google Maps Compose `draggable` property not working
- Marker state not updating
- Touch events intercepted by map

**Solution:**
- Try tapping marker first to select
- Then long-press and drag
- Check if drag callbacks are triggered in logs

### Issue 3: Long-Press Not Required
**Note:** Current implementation uses immediate dragging, NOT long-press

**User Expectation:** Long-press to activate drag mode
**Current Behavior:** Tap to select, then drag immediately

**Enhancement Needed:** Add long-press detection for better UX

## Recommended Enhancements

### Enhancement 1: Add Long-Press Detection
```kotlin
// Add to marker state
var pressStartTime by remember { mutableStateOf(0L) }

Marker(
    onPress = {
        pressStartTime = System.currentTimeMillis()
    },
    onClick = {
        val pressDuration = System.currentTimeMillis() - pressStartTime
        if (pressDuration > 500) { // Long press = 500ms
            onGeofencePointClick(index) // Enable drag mode
            showToast("Drag to adjust geofence")
        }
        true
    }
)
```

### Enhancement 2: Add Visual Feedback
```kotlin
// Show instruction text when geofence enabled
if (geofenceEnabled && geofencePolygon.isNotEmpty()) {
    Text(
        "Tap geofence vertices to adjust",
        modifier = Modifier
            .align(Alignment.TopCenter)
            .background(Color.Black.copy(alpha = 0.7f))
            .padding(8.dp),
        color = Color.White
    )
}
```

### Enhancement 3: Add Confirmation Dialog
```kotlin
// After dragging, confirm changes
if (geofenceModified) {
    AlertDialog(
        title = "Save Geofence Changes?",
        confirmButton = { Button("Save") { saveChanges() } },
        dismissButton = { Button("Cancel") { revertChanges() } }
    )
}
```

## Testing Checklist

### MainPage Testing
- [ ] Connect to drone
- [ ] Upload mission
- [ ] Enable geofence toggle
- [ ] Verify orange markers appear at vertices
- [ ] Tap a marker - verify it turns yellow
- [ ] Drag the yellow marker to new position
- [ ] Verify polygon boundary updates in real-time
- [ ] Release marker - verify new position persists
- [ ] Repeat for multiple vertices

### PlanScreen Testing
- [ ] Create mission plan with waypoints
- [ ] Enable geofence toggle
- [ ] Verify orange markers appear
- [ ] Tap and drag vertices to adjust
- [ ] Verify changes affect planning fence
- [ ] Upload mission
- [ ] Verify fence persists in MainPage

## Current Implementation Files

| File | Purpose | Status |
|------|---------|--------|
| `GcsMap.kt` | Renders geofence markers | ✅ Implemented |
| `MainPage.kt` | Passes drag callbacks | ✅ Implemented |
| `PlanScreen.kt` | Handles local/shared updates | ✅ Implemented |
| `SharedViewModel.kt` | `updateGeofencePolygonManually()` | ✅ Implemented |

## Next Steps

1. **Test Current Implementation**
   - Build and run the app
   - Follow testing checklist above
   - Document what works and what doesn't

2. **If Markers Not Visible**
   - Add debug logs to verify marker rendering
   - Check z-index/rendering order
   - Verify marker size is appropriate

3. **If Dragging Not Working**
   - Consider adding long-press requirement
   - Add visual feedback for drag mode
   - Implement confirmation dialog

4. **Enhancement Priority**
   - 🔴 High: Fix marker visibility if broken
   - 🟡 Medium: Add long-press detection
   - 🟢 Low: Add confirmation dialog

---
**Status:** Code implemented ✅ | User testing needed ❓
**Date:** December 24, 2025
**Action Required:** Test the current implementation and report findings


# Adjustable Geofence Implementation

## Overview
This document describes the implementation of the adjustable geofence feature in the Plan Screen, which allows users to manually adjust the geofence boundary by long-pressing and dragging geofence vertices.

## Feature Description
When planning a mission in the Plan Screen:
1. User clicks "Automatic Mission" and goes to the Plan Screen
2. User plans waypoints (either regular waypoints or grid survey)
3. User enables geofence (via settings or UI control)
4. A geofence polygon is automatically generated with a buffer around the waypoints
5. **NEW**: User can now long-press and drag any geofence vertex to manually adjust the boundary
6. Geofence vertices appear as **orange markers** when geofence is enabled
7. Selected vertex turns **yellow** to indicate it's being adjusted
8. The adjusted geofence polygon is maintained throughout the planning session

## Implementation Details

### 1. GcsMap.kt Changes

#### New Parameters Added
```kotlin
// Geofence point drag callback
onGeofencePointDrag: (index: Int, newPosition: LatLng) -> Unit = { _, _ -> },

// Geofence point selection
selectedGeofencePointIndex: Int? = null,
onGeofencePointClick: (index: Int) -> Unit = {},

// Geofence adjustment mode
geofenceAdjustmentEnabled: Boolean = false
```

#### Draggable Geofence Markers
When `geofenceAdjustmentEnabled` is `true`, the map displays draggable markers at each geofence polygon vertex:

- **Default color**: Orange (indicating geofence boundary)
- **Selected color**: Yellow (indicating the vertex is selected for adjustment)
- **Title**: "GF1", "GF2", etc. (Geofence point numbers)
- **Behavior**: 
  - Long-press to select
  - Drag to adjust position
  - Click fires `onGeofencePointClick` callback
  - Position change fires `onGeofencePointDrag` callback

### 2. PlanScreen.kt Changes

#### New State Variables
```kotlin
// Selected geofence point tracking for adjustment
var selectedGeofencePointIndex by remember { mutableStateOf<Int?>(null) }
```

#### Geofence Adjustment Callbacks
```kotlin
onGeofencePointDrag = { index, newPosition ->
    if (hasStartedPlanning) {
        // Update the local geofence polygon (during planning)
        if (index in localGeofencePolygon.indices) {
            localGeofencePolygon = localGeofencePolygon.toMutableList().apply {
                this[index] = newPosition
            }
        }
    } else {
        // Update the shared view model geofence polygon (after upload)
        if (index in geofencePolygon.indices) {
            val updatedPolygon = geofencePolygon.toMutableList().apply {
                this[index] = newPosition
            }
            telemetryViewModel.updateGeofencePolygonManually(updatedPolygon)
        }
    }
},

onGeofencePointClick = { index ->
    selectedGeofencePointIndex = index
    selectedWaypointIndex = null // Clear waypoint selection
    selectedPolygonPointIndex = null // Clear polygon selection
},

geofenceAdjustmentEnabled = geofenceEnabled
```

The implementation handles two scenarios:
1. **During Planning** (`hasStartedPlanning = true`): Updates `localGeofencePolygon` which is used for preview
2. **After Upload** (`hasStartedPlanning = false`): Updates the shared ViewModel's geofence polygon

### 3. SharedViewModel.kt Changes

#### New Function Added
```kotlin
/**
 * Manually update the geofence polygon (for user adjustments via dragging)
 */
fun updateGeofencePolygonManually(polygon: List<LatLng>) {
    if (_geofenceEnabled.value && polygon.size >= 3) {
        _geofencePolygon.value = polygon
        Log.i("Geofence", "Geofence polygon manually updated with ${polygon.size} vertices")
    }
}
```

This function allows direct updates to the geofence polygon, bypassing the automatic generation logic. It ensures:
- Geofence must be enabled
- Polygon must have at least 3 vertices (valid polygon)
- Updates are logged for debugging

## User Workflow

### Planning a Mission with Adjustable Geofence

1. **Start Planning**
   - Click "Automatic Mission" → Go to Plan Screen
   - Choose grid survey or regular waypoints

2. **Add Waypoints**
   - For grid survey: Click to add polygon boundary points
   - For waypoints: Use "Add Point" button

3. **Enable Geofence**
   - Toggle geofence on in settings
   - Automatic geofence boundary is generated around waypoints

4. **Adjust Geofence Boundary**
   - Orange markers appear at each geofence vertex
   - Long-press any orange marker
   - Drag to desired position
   - Selected marker turns yellow
   - Release to confirm new position
   - Repeat for any vertices you want to adjust

5. **Upload Mission**
   - Click "Upload Mission"
   - Adjusted geofence boundary is included

## Visual Indicators

| Element | Color | Description |
|---------|-------|-------------|
| Geofence Polygon Fill | Red (20% opacity) | Semi-transparent area showing geofence boundary |
| Geofence Polygon Border | Red (solid) | Thick red line showing geofence perimeter |
| Geofence Vertex (default) | Orange | Draggable marker at each polygon vertex |
| Geofence Vertex (selected) | Yellow | Currently selected vertex for adjustment |
| Waypoint | Blue | Mission waypoints |
| Survey Polygon | Purple/Magenta | Grid survey boundary |
| Grid Waypoints | Green (start), Orange (middle), Red (end) | Generated grid survey waypoints |

## Technical Considerations

### State Management
- **Local State**: `localGeofencePolygon` in PlanScreen for planning phase
- **Shared State**: `_geofencePolygon` in SharedViewModel for uploaded missions
- Clear separation ensures planning changes don't affect active missions

### Validation
- Minimum 3 vertices required for valid polygon
- Geofence must be enabled for adjustments
- Changes are immediately reflected in the UI

### Performance
- Efficient updates using mutable list operations
- No unnecessary recompositions
- Marker state tracked independently

### Logging
- All manual updates logged with "Geofence" tag
- Includes vertex count for debugging
- Helps track user adjustments

## Testing Recommendations

1. **Basic Adjustment**
   - Enable geofence
   - Drag a single vertex
   - Verify polygon updates correctly

2. **Multiple Adjustments**
   - Drag multiple vertices in sequence
   - Verify each adjustment is preserved

3. **Selection Handling**
   - Select a geofence vertex (turns yellow)
   - Select a waypoint (geofence selection clears)
   - Verify only one selection active at a time

4. **Grid Survey Mode**
   - Create grid survey
   - Enable geofence
   - Adjust geofence while grid regenerates
   - Verify adjustments persist

5. **Upload Integration**
   - Adjust geofence boundary
   - Upload mission
   - Verify adjusted boundary is used

## Future Enhancements

Potential improvements for future versions:
1. **Add/Remove Vertices**: Allow adding new vertices or removing existing ones
2. **Reset to Auto**: Button to reset geofence to automatically generated boundary
3. **Minimum Distance Validation**: Ensure geofence maintains minimum distance from waypoints
4. **Undo/Redo**: Support for reverting adjustments
5. **Snap to Grid**: Option to snap vertices to grid lines or specific angles
6. **Save Custom Boundaries**: Save frequently used geofence shapes as templates

## Related Files

- `app/src/main/java/com/example/aerogcsclone/uimain/GcsMap.kt`
- `app/src/main/java/com/example/aerogcsclone/uimain/PlanScreen.kt`
- `app/src/main/java/com/example/aerogcsclone/telemetry/SharedViewModel.kt`
- `app/src/main/java/com/example/aerogcsclone/utils/GeofenceUtils.kt`

## Implementation Date
December 24, 2025


# Plan Mission UI Refactoring Summary

## Overview
The Plan Mission UI has been refactored to provide a better user experience with a clear two-step selection process and improved grid workflow.

## Changes Made

### New Dialog Flow
When user clicks on "Plan Mission", they now go through a multi-step selection process:

1. **Initial Choice Dialog** (MissionChoiceDialog - existing)
   - "Load Existing Template" - Opens template selection
   - "Create New Mission" - **Now leads to Mission Type Selection**

2. **Mission Type Selection Dialog** (NEW: MissionTypeSelectionDialog.kt)
   - "Grid" - For auto-generated grid survey patterns
   - "Waypoint" - For manual waypoint placement

3. **Grid Source Selection Dialog** (NEW: GridSourceSelectionDialog.kt)
   - "Import KML File" - Load boundary from KML/KMZ file (placeholder for now)
   - "Map" - Draw boundary points manually on the map
   - "Place with Drone" - Use drone position to mark boundary corners

### New Grid Workflow
The grid mission creation has been refactored to separate plot definition from grid generation:

1. **Plot Definition Mode**
   - User defines 4-5 boundary points first
   - Boundary is shown as connected lines with area visualization
   - Blue info card shows: "Define Plot Boundary - Add X more points, then tap 'Generate Grid'"
   - No grid lines shown yet

2. **Generate Grid Button**
   - Appears when 3+ boundary points are defined
   - Green "Generate Grid" button on the right side
   - Clicking generates the grid and shows grid parameters panel

3. **Grid Parameter Adjustment**
   - Grid controls panel appears after "Generate Grid" is clicked
   - User can adjust line spacing, angle, altitude, speed, etc.
   - Grid regenerates automatically when parameters change

4. **Save and Upload**
   - Save Mission button appears after grid is generated
   - Normal save/upload workflow continues

### Files Created
- `app/src/main/java/com/example/aerogcsclone/ui/components/MissionTypeSelectionDialog.kt`
- `app/src/main/java/com/example/aerogcsclone/ui/components/GridSourceSelectionDialog.kt`

### Files Modified
- `app/src/main/java/com/example/aerogcsclone/uimain/PlanScreen.kt`
  - Added imports for new dialogs
  - Added state variables: `showMissionTypeDialog`, `showGridSourceDialog`, `isPlotDefinitionMode`, `isGridGenerated`
  - Updated dialog flow to show new selection dialogs
  - Added "Generate Grid" button for grid workflow
  - Updated grid controls visibility (only shows after grid is generated)
  - Added plot definition mode info card
  - Updated map click handler, polygon drag handler, and delete button to respect plot definition mode

### Preserved Functionality
- All existing waypoint mission functionality unchanged
- Template loading works correctly (sets `isGridGenerated = true` for grid templates)
- Split Plan mode still works
- Obstacle avoidance still works
- Geofence settings still work
- Save/Upload workflow unchanged

## User Flow Diagram

```
Plan Mission → Load Existing Template → Select Template → (loads with grid generated)
           ↓
      Create New Mission
           ↓
   ┌───────────────────┐
   │ Mission Type      │
   │   • Grid          │ → Grid Source Selection → Map/KML/Drone → Plot Definition Mode
   │   • Waypoint      │ → Normal Waypoint Mode                           ↓
   └───────────────────┘                                         Add 3+ boundary points
                                                                           ↓
                                                                 "Generate Grid" button
                                                                           ↓
                                                                 Grid Parameters Panel
                                                                           ↓
                                                                    Save/Upload
```

## Notes
- KML import currently shows a toast "coming soon" and proceeds to map mode
- "Place with Drone" mode works by using the Add Boundary button to add drone position as points
- The grid toggle button on the left sidebar has been updated to toggle grid controls panel when grid is already generated

## UI Fixes (December 30, 2025)

### Grid Lines Visibility Fix
- **Issue**: When closing the grid parameters panel, grid lines were being affected
- **Root Cause**: The grid toggle button was resetting `isGridGenerated = false` when clicked, even if grid was already generated
- **Fix**: Updated the grid toggle button behavior:
  - If grid is already generated: Clicking the button now toggles the grid controls panel visibility (show/hide)
  - If in grid mode but grid not generated: Clicking exits grid mode
  - If not in grid mode: Clicking enters plot definition mode
- **Result**: Grid lines now remain visible even when the parameters panel is closed

### Waypoint Connection Fix
- **Issue**: Not all waypoints were being connected with the polyline in normal waypoint mode
- **Root Cause**: The polyline key was using `key(points)` which might not trigger proper recomposition
- **Fix**: Changed to `key("waypoint_polyline_${points.size}_${points.hashCode()}")` with `points.toList()` to ensure proper updates
- **Result**: All waypoints are now properly connected with the blue polyline

## Obstacle Workflow Refactoring (December 30, 2025)

### Overview
The obstacle avoidance workflow has been significantly refactored to allow obstacle definition BEFORE grid generation, and grid lines now automatically escape (go around) obstacles.

### Key Changes

1. **Obstacle Button Visibility**
   - Obstacle button now only appears in grid mode BEFORE grid is generated
   - This allows users to define obstacles during the plot definition phase
   - After grid is generated, obstacle button is hidden (obstacles should be defined first)

2. **Adding Obstacles with Crosshair**
   - When in obstacle adding mode, the "Add Point" button changes to "Add Obstacle (X/4)"
   - Button color changes to red to indicate obstacle mode
   - Users can add 4 points using the crosshair center + Add button
   - Map tap also works for adding obstacle points

3. **Obstacle Boundary Parameter**
   - New slider in the obstacle info card: "Boundary" (1m to 5m with 0.5m increments)
   - This controls the buffer distance the grid maintains from obstacles
   - Default value: 2 meters

4. **Obstacle Deletion**
   - Click on an obstacle to select it
   - "Delete Obstacle X" button appears to delete the selected obstacle
   - "Clear All" button to remove all obstacles at once

5. **Grid Lines Escape Obstacles**
   - When generating grid, lines are automatically split around obstacle zones
   - The obstacle boundary buffer is applied to expand obstacles before checking intersections
   - Grid lines that would pass through obstacles are broken into segments that go around them

### Technical Implementation

**GridSurveyParams Updates:**
- Added `obstacles: List<List<LatLng>>` - List of obstacle polygons
- Added `obstacleBoundary: Float` - Buffer distance from obstacles (1-5m)

**GridGenerator Updates:**
- New function: `splitLineAroundObstacles()` - Splits grid lines that intersect obstacles
- New function: `expandPolygon()` - Expands obstacle polygon by buffer distance
- Grid lines are now processed to avoid obstacle zones

**PlanScreen Updates:**
- New state: `selectedObstaclePointIndex` - For future point-level editing
- New state: `obstacleBoundary` - Buffer distance control (default 2m)
- Updated left sidebar to show obstacle button only in plot definition mode
- Updated Add Point button to handle obstacle mode
- Updated obstacle info card with boundary slider and delete buttons

### User Flow for Obstacles

1. Enter Grid mode → Plot Definition starts
2. Add boundary points for the plot
3. (Optional) Click Obstacle button to enter obstacle adding mode
4. Add 4 points for each obstacle using crosshair + "Add Obstacle" button
5. Adjust obstacle boundary buffer if needed (1-5m)
6. Click "Generate Grid" - grid lines automatically avoid obstacles
7. Adjust grid parameters as needed
8. Save/Upload mission

## Obstacle Avoidance Algorithm Fixes (December 30, 2025)

### Issues Fixed

1. **Hourglass Polygon Rendering**
   - Added `orderPointsClockwise()` function to sort polygon vertices by angle from centroid
   - Obstacle points are now automatically ordered when added (both via map click and Add button)
   - This prevents the "hourglass" or "bowtie" visual when points are added in random order

2. **Incorrect expandPolygon Formula**
   - **Old approach**: Simple scaling from centroid which produced incorrect buffer distances
   - **New approach**: Proper offset polygon algorithm that:
     - Orders polygon points clockwise first
     - Calculates outward normals for each edge
     - Computes angle bisector at each vertex
     - Applies correct buffer distance in meters considering latitude correction
     - Handles corner offsets correctly (moves further on sharp angles)

3. **Improved splitLineAroundObstacles Function**
   - Pre-processes obstacles: orders points and expands by buffer BEFORE checking
   - Increased sampling from 100 to 200 points for better accuracy
   - Added minimum segment length requirement (at least 3 sample points, 1+ meter)
   - Better edge case handling:
     - All points valid → returns original line
     - All points invalid → returns empty list
     - Mixed → returns valid segments only

### Algorithm Details

**Polygon Expansion (Buffer):**
```
For each vertex:
1. Calculate edge directions for adjacent edges
2. Compute outward normals (perpendicular to edges, pointing outward)
3. Calculate bisector direction (average of normals)
4. Calculate offset multiplier based on angle (sharper corners need more offset)
5. Convert meters to lat/lng degrees with latitude correction
6. Apply offset along bisector direction
```

**Grid Line Splitting:**
```
1. Pre-expand all obstacles by buffer distance
2. Sample 200 points along the grid line
3. For each point, check if inside any expanded obstacle
4. Build segments from consecutive valid (outside obstacle) points
5. Filter segments: minimum 3 sample points, 1+ meter length
6. Return valid segments or empty if entirely inside obstacle
```

### Files Modified
- `GridGenerator.kt` - Fixed `expandPolygon()` and `splitLineAroundObstacles()` functions
- `GridUtils.kt` - Added `orderPointsClockwise()` function
- `PlanScreen.kt` - Uses `orderPointsClockwise()` when completing obstacles


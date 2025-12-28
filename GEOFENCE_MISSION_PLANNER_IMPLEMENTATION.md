# Geofence Implementation - Mission Planner Style

## Overview
Updated the geofence implementation to use Mission Planner's cross-track distance formula and ArduPilot's dynamic FENCE_MARGIN behavior for more accurate drone stopping before the fence boundary.

## Changes Made

### 1. GeofenceUtils.kt - Distance Calculation

#### New Functions Added:

**`getBearing(p1: LatLng, p2: LatLng): Double`**
- Calculates bearing from point p1 to p2 in degrees (0-360)
- Matches Mission Planner's GetBearing function

**`distanceToPolygonEdge(point: LatLng, polygon: List<LatLng>): Double`**
- Uses Mission Planner's cross-track distance formula
- Algorithm:
  1. For each fence segment (line from point A to point B):
     - Calculate `lineDist` (distance from lineStart to lineEnd)
     - Calculate `distToLocation` (distance from lineStart to drone)
     - Calculate `bearToLocation` (bearing from lineStart to drone)
     - Calculate `lineBear` (bearing from lineStart to lineEnd)
     - Calculate angle difference
     - Calculate `alongLine = cos(angle) * distToLocation` (projection along line)
     - If `alongLine` is within segment bounds: cross-track distance = `sin(angle) * distToLocation`
  2. Check distance to each vertex (corner distance check - important for corners where perpendicular doesn't hit any segment)
  3. Return minimum of all distances

**`isPointInPolygon(point: LatLng, polygon: List<LatLng>): Boolean`**
- Ray casting algorithm (public function)
- Matches Mission Planner's PolygonTools.isInside function

**`checkGeofenceDistance(point: LatLng, polygon: List<LatLng>): Double`**
- Complete geofence check matching Mission Planner behavior
- Returns 0 if breach (outside inclusion polygon)
- Returns distance to nearest edge if inside

### 2. SharedViewModel.kt - Dynamic Buffer Calculation

#### New Constants:
```kotlin
companion object {
    // Minimum buffer distance - triggers even when stationary
    private const val MIN_BUFFER_METERS = 1.0  // 1m minimum buffer
    
    // Maximum buffer distance - caps the dynamic buffer
    private const val MAX_BUFFER_METERS = 10.0  // 10m maximum buffer
    
    // Maximum deceleration capability (m/s²)
    private const val MAX_DECEL_M_S2 = 2.5
    
    // Safety factor for stopping distance calculation
    private const val STOPPING_DISTANCE_SAFETY_FACTOR = 1.5
    
    // Monitor interval: 10ms = 100 checks per second
    private const val GEOFENCE_MONITOR_INTERVAL_MS = 10L
}
```

#### Dynamic Buffer Calculation:
```kotlin
private fun calculateDynamicBuffer(currentSpeedMs: Float): Double {
    if (currentSpeedMs <= 0) return MIN_BUFFER_METERS
    
    // Physics: stopping distance = v² / (2a)
    val stoppingDistance = (currentSpeedMs * currentSpeedMs) / (2 * MAX_DECEL_M_S2)
    
    // Apply safety factor for latencies
    val safeStoppingDistance = stoppingDistance * STOPPING_DISTANCE_SAFETY_FACTOR
    
    // Ensure buffer is within bounds
    return maxOf(MIN_BUFFER_METERS, minOf(MAX_BUFFER_METERS, safeStoppingDistance))
}
```

#### Example Buffer Distances at Different Speeds:
| Speed (m/s) | Stopping Distance | With Safety Factor | Final Buffer |
|-------------|-------------------|-------------------|--------------|
| 0 (stationary) | 0 m | 0 m | 1.0 m (minimum) |
| 2 m/s | 0.8 m | 1.2 m | 1.2 m |
| 5 m/s | 5.0 m | 7.5 m | 7.5 m |
| 7 m/s | 9.8 m | 14.7 m | 10.0 m (capped) |
| 10 m/s | 20.0 m | 30.0 m | 10.0 m (capped) |

### 3. Updated `checkGeofenceNow()` Function
- Uses `GeofenceUtils.checkGeofenceDistance()` for unified breach detection
- Uses `calculateDynamicBuffer()` for speed-based buffer
- Returns 0 if outside fence (BREACH), distance to edge if inside
- Triggers BRAKE + RTL if:
  - Distance is 0 (outside fence)
  - OR distance is within dynamic buffer

## How It Works

### Visual Representation:
```
┌─────────────────────────────────────────────────────────┐
│     Actual Fence Boundary                               │
│  ┌───────────────────────────────────────────────────┐  │
│  │   Dynamic Buffer Zone (speed-dependent)           │  │
│  │   - Expands when drone moves faster               │  │
│  │   - Contracts when drone slows down               │  │
│  │  ┌─────────────────────────────────────────────┐  │  │
│  │  │                                             │  │  │
│  │  │   Safe flying zone                          │  │  │
│  │  │                                             │  │  │
│  │  └─────────────────────────────────────────────┘  │  │
│  └───────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────┘
```

### Cross-Track Distance Illustration:
```
       lineStart (A) ─────────────────── lineEnd (B)
                     \         │
                      \        │ Cross-track distance
                       \       │ (perpendicular)
                        \      │
                         \     ▼
                          ● Drone position
                           \
                            alongLine (projection)
```

## Benefits Over Previous Implementation

1. **More Accurate Distance Calculation**: Cross-track distance provides true perpendicular distance to fence segments, not just nearest point distance.

2. **Corner Handling**: Explicitly checks distance to vertices (corners) for cases where perpendicular projection doesn't hit any segment.

3. **Speed-Based Buffer**: Dynamic buffer increases with drone speed, ensuring adequate stopping distance.

4. **Physics-Based**: Uses actual physics formulas (v²/2a) for stopping distance calculation.

5. **Safety Margin**: 1.5x safety factor accounts for:
   - GPS update latency
   - Control system response time
   - Communication delays

## Testing Recommendations

1. **Stationary Test**: With drone stationary near fence edge, verify 1m buffer trigger.

2. **Low Speed Test**: At 2 m/s, buffer should be ~1.2m.

3. **Medium Speed Test**: At 5 m/s, buffer should be ~7.5m.

4. **High Speed Test**: At 10+ m/s, buffer should cap at 10m.

5. **Corner Approach Test**: Approach fence corner diagonally to verify corner distance check.

6. **Parallel Approach Test**: Fly parallel to fence edge to verify cross-track distance calculation.


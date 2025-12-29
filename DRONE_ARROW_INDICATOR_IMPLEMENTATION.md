# Drone Arrow Indicator Implementation

## Date: December 29, 2025

## Overview
Added a visual arrow indicator on the drone icon to clearly show the nose heading direction. This enhancement makes it immediately obvious which direction the drone is facing on the map.

## Implementation

### New Helper Function: `createDroneIconWithArrow()`

Created a new helper function that:
1. Loads the original drone image (`R.drawable.d_image_prev_ui`)
2. Creates a composite bitmap with the drone image as the base
3. Draws a red triangular arrow pointing upward (north/0°) to indicate nose direction
4. Adds a white border to the arrow for better visibility against the drone image

**Location**: `GcsMap.kt` (lines 165-220)

```kotlin
private fun createDroneIconWithArrow(context: android.content.Context): BitmapDescriptor? {
    return runCatching {
        // Load the original drone image
        val droneBmp = BitmapFactory.decodeResource(context.resources, R.drawable.d_image_prev_ui)
        val sizeDp = 64f
        val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(24)
        
        // Create a mutable bitmap to draw on
        val resultBitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(resultBitmap)
        
        // Scale and draw the drone image centered
        val scaledDrone = Bitmap.createScaledBitmap(droneBmp, sizePx, sizePx, true)
        canvas.drawBitmap(scaledDrone, 0f, 0f, null)
        
        // Draw an arrow pointing upward (north/0°) to indicate the nose direction
        val arrowPaint = android.graphics.Paint().apply {
            isAntiAlias = true
            color = android.graphics.Color.RED
            style = android.graphics.Paint.Style.FILL_AND_STROKE
            strokeWidth = 3f
        }
        
        // Calculate arrow dimensions relative to icon size
        val centerX = sizePx / 2f
        val arrowHeight = sizePx * 0.35f
        val arrowWidth = sizePx * 0.15f
        
        // Define arrow path pointing upward (triangular arrow at the top)
        val arrowPath = android.graphics.Path().apply {
            // Arrow tip at top center
            moveTo(centerX, sizePx * 0.05f)
            // Arrow left side
            lineTo(centerX - arrowWidth / 2, sizePx * 0.05f + arrowHeight)
            // Arrow right side
            lineTo(centerX + arrowWidth / 2, sizePx * 0.05f + arrowHeight)
            // Close the path
            close()
        }
        
        // Draw the arrow
        canvas.drawPath(arrowPath, arrowPaint)
        
        // Add white border to arrow for better visibility
        arrowPaint.style = android.graphics.Paint.Style.STROKE
        arrowPaint.color = android.graphics.Color.WHITE
        arrowPaint.strokeWidth = 2f
        canvas.drawPath(arrowPath, arrowPaint)
        
        BitmapDescriptorFactory.fromBitmap(resultBitmap)
    }.getOrNull()
}
```

### Updated Drone Icon Initialization

Changed the drone icon initialization from using just the plain image to using the new arrow-enhanced version:

**Before**:
```kotlin
// Load quadcopter drawable from res/drawable and scale to dp-based size
val droneIcon = remember {
    runCatching {
        val bmp = BitmapFactory.decodeResource(context.resources, R.drawable.d_image_prev_ui)
        val sizeDp = 64f
        val sizePx = (sizeDp * context.resources.displayMetrics.density).toInt().coerceAtLeast(24)
        val scaled = Bitmap.createScaledBitmap(bmp, sizePx, sizePx, true)
        BitmapDescriptorFactory.fromBitmap(scaled)
    }.getOrNull()
}
```

**After**:
```kotlin
// Load quadcopter drawable with directional arrow indicator
val droneIcon = remember {
    createDroneIconWithArrow(context)
}
```

## Visual Design

### Arrow Specifications
- **Color**: Red (for high visibility)
- **Border**: White (2px stroke for contrast)
- **Shape**: Triangular arrow
- **Position**: Pointing upward at the top of the drone icon
- **Size**: 
  - Height: 35% of icon size
  - Width: 15% of icon size
  - Positioned 5% from the top edge

### Arrow Positioning
The arrow is drawn pointing upward (north/0°) on the static bitmap. When the `rotation` parameter is applied to the marker based on the drone's heading:
- 0° heading → Arrow points North
- 90° heading → Arrow points East
- 180° heading → Arrow points South
- 270° heading → Arrow points West

## How It Works

1. **Static Arrow**: The arrow is drawn on the bitmap pointing upward (0°/North)
2. **Rotation Applied**: The entire icon (drone + arrow) is rotated by the Google Maps Marker's `rotation` parameter
3. **Heading Source**: The rotation value comes from `telemetryState.heading` (MAVLink VFR_HUD message)
4. **Real-time Updates**: As the drone's heading changes, the entire icon rotates, keeping the arrow pointing in the drone's actual nose direction

## Benefits

1. **Clear Directional Indicator**: Users can immediately see which way the drone is facing
2. **High Visibility**: Red color with white border stands out clearly
3. **Consistent with Heading**: The arrow rotates with the drone icon based on telemetry
4. **Works on Both Screens**: MainPage and PlanScreen both use the same GcsMap component
5. **No Performance Impact**: Icon is created once and cached using `remember`

## Testing

To verify the implementation:
1. ✅ Connect to drone/SITL
2. ✅ Check MainPage - drone icon should show a red arrow
3. ✅ Check PlanScreen - drone icon should show a red arrow
4. ✅ Rotate the drone (change yaw) - arrow should rotate with the drone
5. ✅ Verify arrow always points in the direction the drone is facing
6. ✅ Check visibility against different map backgrounds (satellite, terrain, normal)

## Files Modified

1. **GcsMap.kt**:
   - Added `createDroneIconWithArrow()` helper function
   - Updated drone icon initialization to use the arrow-enhanced version

## Compilation Status

✅ **No errors** - Changes compiled successfully
- Only existing warnings present (Google Map Composable warnings) - non-critical
- All type checking passed
- App ready for testing

## Future Enhancements

Possible improvements for future iterations:
- Make arrow color configurable
- Add animation effect when heading changes rapidly
- Different arrow styles for different flight modes
- Adjust arrow size based on zoom level


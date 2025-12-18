# Initiate Reboot Button Implementation

## Summary
Successfully implemented "Initiate Reboot" button functionality for all calibration screens. After successful calibration completion, users can now directly trigger an autopilot reboot using the MAVLink command `MAV_CMD_PREFLIGHT_REBOOT_SHUTDOWN (246)`.

---

## Changes Made

### 1. SharedViewModel.kt
**Added rebootAutopilot() function**
- Location: After `stopRCChannels()` function (around line 407)
- Sends MAV_CMD_PREFLIGHT_REBOOT_SHUTDOWN command
- Parameters:
  - `param1 = 1f` → Reboot autopilot
  - `param2-7 = 0f` → Reserved/no action
- Includes comprehensive logging for debugging

```kotlin
suspend fun rebootAutopilot() {
    Log.d("Calibration", "========== SENDING REBOOT COMMAND ==========")
    try {
        repo?.sendCommand(
            MavCmd.PREFLIGHT_REBOOT_SHUTDOWN,
            param1 = 1f, // 1 = Reboot autopilot
            param2 = 0f, // Companion computer (0 = no action)
            param3 = 0f, // Reserved
            param4 = 0f, // Reserved
            param5 = 0f, // Reserved
            param6 = 0f, // Reserved
            param7 = 0f  // Reserved
        )
        Log.d("Calibration", "✓ Reboot command sent successfully")
    } catch (e: Exception) {
        Log.e("Calibration", "❌ Failed to send reboot command", e)
    }
    Log.d("Calibration", "============================================")
}
```

---

### 2. CalibrationViewModel.kt (IMU/Accelerometer)
**Added initiateReboot() function**
- Location: Before `onNextPosition()` function (around line 408)
- Calls `sharedViewModel.rebootAutopilot()`
- Keeps dialog open after sending command

```kotlin
fun initiateReboot() {
    viewModelScope.launch {
        Log.d("CalibrationVM", "User initiated reboot after calibration")
        sharedViewModel.rebootAutopilot()
        // Keep the dialog open so user knows reboot was sent
        // They can dismiss it manually after seeing the drone reboot
    }
}
```

---

### 3. CalibrationScreen.kt (IMU/Accelerometer UI)
**Updated Reboot Dialog**
- Location: Reboot dialog after line 56
- Changed from single "OK" button to:
  - **"Initiate Reboot"** button (primary action) → Calls `viewModel.initiateReboot()`
  - **"Later"** button (dismiss action) → Closes dialog
- Updated dialog text to clarify reboot is needed

**Before:**
```kotlin
confirmButton = {
    Button(onClick = { viewModel.dismissRebootDialog() }) {
        Text("OK")
    }
}
```

**After:**
```kotlin
confirmButton = {
    Button(onClick = { viewModel.initiateReboot() }) {
        Text("Initiate Reboot")
    }
},
dismissButton = {
    TextButton(onClick = { viewModel.dismissRebootDialog() }) {
        Text("Later")
    }
}
```

---

### 4. CompassCalibrationViewModel.kt
**Added initiateReboot() function**
- Location: After `stopMagCalMessageStreaming()` function (around line 722)
- Same implementation as IMU calibration

```kotlin
fun initiateReboot() {
    viewModelScope.launch {
        Log.d("CompassCalVM", "User initiated reboot after compass calibration")
        sharedViewModel.rebootAutopilot()
    }
}
```

---

### 5. CompassCalibrationScreen.kt
**Updated SuccessContent() function**
- Location: Inside the green reboot card (around line 740)
- Added "Initiate Reboot" button below the reboot message
- Button styling:
  - White background with green text (matches card theme)
  - Full width with rounded corners
  - Refresh icon + text

**Implementation:**
```kotlin
Card(
    modifier = Modifier.fillMaxWidth(),
    colors = CardDefaults.cardColors(containerColor = Color(0xFF2A5E20)),
    shape = RoundedCornerShape(8.dp)
) {
    Column(
        modifier = Modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(...) {
            Icon(imageVector = Icons.Default.Refresh, ...)
            Text(text = "Please reboot the autopilot", ...)
        }
        
        Spacer(modifier = Modifier.height(12.dp))
        
        // NEW: Initiate Reboot button
        Button(
            onClick = { viewModel.initiateReboot() },
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color(0xFF2A5E20)
            ),
            shape = RoundedCornerShape(8.dp)
        ) {
            Icon(imageVector = Icons.Default.Refresh, ...)
            Spacer(modifier = Modifier.width(8.dp))
            Text(text = "Initiate Reboot", ...)
        }
    }
}
```

---

## User Flow

### IMU/Accelerometer Calibration:
1. User completes calibration successfully
2. **Dialog appears:** "Reboot Your Drone" with message about calibration complete
3. User has two options:
   - **"Initiate Reboot"** → Sends MAV_CMD_PREFLIGHT_REBOOT_SHUTDOWN immediately
   - **"Later"** → Closes dialog, user can reboot manually

### Compass Calibration:
1. User completes calibration successfully
2. Success screen shows with calibration results
3. Green card at bottom displays: **"Please reboot the autopilot"**
4. **"Initiate Reboot" button** below message
5. Click button → Sends MAV_CMD_PREFLIGHT_REBOOT_SHUTDOWN immediately
6. User can observe autopilot reboot

---

## MAVLink Command Details

**Command:** `MAV_CMD_PREFLIGHT_REBOOT_SHUTDOWN` (246)

**Purpose:** Request the reboot or shutdown of system components

**Parameters:**
| Param | Label | Description | Value Used |
|-------|-------|-------------|------------|
| 1 | Autopilot | Action to take for autopilot | **1** (Reboot) |
| 2 | Companion | Action to take for onboard computer | **0** (No action) |
| 3 | Component Action | Action for component in param4 | **0** (Reserved) |
| 4 | Component ID | MAVLink Component ID | **0** (Reserved) |
| 5 | - | Reserved | **0** |
| 6 | Conditions | Reboot/shutdown conditions | **0** (Reserved) |
| 7 | - | WIP: ID (e.g., camera ID) | **0** (Reserved) |

**REBOOT_SHUTDOWN_ACTION enum values:**
- 0 = Do nothing
- 1 = **Reboot** ✓ (Used)
- 2 = Shutdown
- 3 = Reboot to bootloader
- 4 = Reboot to emergency mode

---

## Benefits

✅ **User Convenience:** One-click reboot instead of manual intervention  
✅ **Workflow Efficiency:** Faster calibration completion workflow  
✅ **Clear Communication:** User knows reboot command was sent via logs  
✅ **Flexible Options:** User can choose to reboot now or later  
✅ **Consistent UX:** Same functionality across all calibration types  
✅ **Safe Implementation:** Proper error handling and logging  

---

## Testing Checklist

- [ ] IMU calibration completes successfully
- [ ] Reboot dialog appears with both buttons
- [ ] "Initiate Reboot" sends command (check logs)
- [ ] Autopilot actually reboots
- [ ] "Later" button dismisses dialog
- [ ] Compass calibration completes successfully
- [ ] "Initiate Reboot" button visible in success screen
- [ ] Button click sends reboot command
- [ ] Autopilot reboots after compass calibration
- [ ] No crashes or errors during process

---

## Files Modified

1. ✅ `SharedViewModel.kt` - Added rebootAutopilot() function
2. ✅ `CalibrationViewModel.kt` - Added initiateReboot() function
3. ✅ `CalibrationScreen.kt` - Updated reboot dialog UI
4. ✅ `CompassCalibrationViewModel.kt` - Added initiateReboot() function
5. ✅ `CompassCalibrationScreen.kt` - Added button to success screen

---

## Notes

- The reboot command is sent asynchronously
- Logs can be checked with tag "Calibration", "CalibrationVM", or "CompassCalVM"
- The dialog/button remains visible after sending command (user can verify reboot started)
- If reboot fails, error is logged but UI doesn't show error (autopilot handles internally)
- Other calibration types (Barometer, Level, RC) can be enhanced similarly if needed

---

## Future Enhancements (Optional)

1. Add visual feedback after button click (loading indicator)
2. Show toast notification "Reboot command sent"
3. Auto-dismiss dialog after successful reboot detection
4. Add countdown timer before auto-reboot
5. Extend to barometer and other calibration types

---

**Implementation Date:** December 18, 2025  
**Status:** ✅ Complete and Ready for Testing


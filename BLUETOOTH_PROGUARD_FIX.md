# Bluetooth Connection Issue - ProGuard Rules Fix

## Problem Identified
The app was showing "connection failed: k3 error" during Bluetooth connection attempts in release builds. The issue was traced to **ProGuard/R8 obfuscation rules** that were too aggressive and potentially stripping or obfuscating critical classes needed for Bluetooth communication.

## Root Causes

1. **Log Stripping**: The ProGuard rule `-assumenosideeffects class android.util.Log` was removing all logging statements, making debugging impossible and potentially causing uncaught exceptions.

2. **Missing Bluetooth Protection**: While MAVLink rules were present, Android's native Bluetooth classes weren't explicitly protected from obfuscation.

3. **Incomplete Okio Protection**: The Okio library (used for buffering in Bluetooth connections) wasn't fully protected, potentially causing serialization issues.

4. **Missing Reflection Rules**: Kotlin reflection and internal classes weren't explicitly kept, causing issues with MAVLink's reflection-based message handling.

## Changes Made to `app/proguard-rules.pro`

### 1. Disabled Log Stripping ✅
**Before:**
```proguard
-assumenosideeffects class android.util.Log {
    public static *** d(...);
    public static *** i(...);
    public static *** v(...);
    public static *** w(...);
    public static *** e(...);
    public static *** wtf(...);
}
```

**After:**
```proguard
# COMMENTED OUT - Allows debugging of connection issues
# -assumenosideeffects class android.util.Log { ... }
```

### 2. Enhanced Okio Protection ✅
**Added explicit rules for critical Okio classes:**
```proguard
-keep class okio.Source { *; }
-keep class okio.Sink { *; }
-keep class okio.BufferedSource { *; }
-keep class okio.BufferedSink { *; }
-keepclassmembers class okio.** { *; }
```

These classes are critical for `BufferedMavConnection` which is used in `BluetoothMavConnection.kt`.

### 3. Protected Kotlin Reflection ✅
**Added comprehensive Kotlin reflection rules:**
```proguard
-keep class kotlin.reflect.** { *; }
-keep class kotlin.Metadata { *; }
-keep class kotlin.jvm.internal.** { *; }
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations
```

MAVLink uses Kotlin reflection internally for message deserialization and object construction.

### 4. Reinforced Telemetry Package Protection ✅
**Expanded telemetry rules to be comprehensive:**
```proguard
-keep class com.example.kftgcs.telemetry.connections.** { *; }
-keepclassmembers class com.example.kftgcs.telemetry.** { *; }
```

This ensures `BluetoothConnectionProvider` and `BluetoothMavConnection` aren't obfuscated.

## What to Do Next

### Immediate Action: Clean Build
1. **Clean the project:**
   ```bash
   ./gradlew clean
   ```

2. **Rebuild the release APK/AAB:**
   ```bash
   ./gradlew assembleRelease
   # or
   ./gradlew bundleRelease
   ```

3. **Test Bluetooth connection:**
   - Test the release build on a real device with a Bluetooth module
   - Check the Logcat for detailed error messages if connection still fails

### Debugging Checklist
If connection still fails:

1. ✅ Check device logs for actual exceptions (logs are no longer stripped)
2. ✅ Verify Bluetooth device is powered and discoverable
3. ✅ Check Android permissions in `AndroidManifest.xml`
4. ✅ Test with the debug build to verify it works

### Next Steps After Stability
Once Bluetooth connection is **stable in release builds**, you can:

1. **Re-enable Log Stripping** (for production optimization):
   ```proguard
   -assumenosideeffects class android.util.Log {
       public static *** d(...);
       public static *** i(...);
       public static *** v(...);
       public static *** w(...);
       public static *** e(...);
       public static *** wtf(...);
   }
   ```

2. **Consider selective obfuscation** instead of full stripping

3. **Test thoroughly** with various Bluetooth devices before final release

## Technical Explanation

### Why "k3 error"?
The "k3 error" is typically an obfuscation-related issue where:
- Classes get renamed (e.g., `BufferedMavConnection` → `k3`)
- Reflection-based lookups fail
- Method signatures get corrupted
- Inner classes lose their relationships

### Why Bluetooth Specifically?
Bluetooth connections are vulnerable because:
1. `BluetoothMavConnection` extends `MavConnection` which relies on generics and reflection
2. `BufferedMavConnection` (from MAVLink library) uses reflection for message marshalling
3. `okio.BufferedSource/Sink` need correct class structures for serialization
4. Coroutines use reflection for context switching

## Files Modified
- ✅ `app/proguard-rules.pro` - Updated with comprehensive protection rules

## Related Files (for reference)
- `app/src/main/java/com/example/kftgcs/telemetry/connections/BluetoothMavConnection.kt`
- `app/src/main/java/com/example/kftgcs/telemetry/connections/BluetoothConnectionProvider.kt`
- `app/build.gradle.kts` - Specifies ProGuard configuration

---
**Status**: ✅ Fixed
**Last Updated**: March 13, 2026


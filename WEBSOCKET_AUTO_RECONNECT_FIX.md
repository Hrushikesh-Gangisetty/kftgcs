# WebSocket Auto-Reconnect Fix

## Problem Analysis

Based on the logs, the issue was:

1. **WebSocket connects successfully** at `09:52:57.636`
2. **`session_ack` received** at `09:52:57.899` ✅
3. **WebSocket closes immediately** at `09:52:57.981` with code 1000
4. **Mission status skipped** at `09:52:58.481` because `connected=false, missionId=null`

### Root Cause

The **Django backend is closing the connection** with code 1000 (normal closure) right after sending `session_ack`. Looking at the backend code:

```python
except Exception as e:
    print(f"❌ SESSION_START ERROR: {e}", flush=True)
    logger.exception("SESSION_START failed")
    await self.close()  # <-- This closes the connection!
```

The most likely causes:

1. **Admin.DoesNotExist**: `admin_id=1` doesn't exist in the `Admin` table
2. **Pilot.DoesNotExist**: `pilot_id=45` doesn't exist in the `Pilot` table
3. **Database connection issue**

## Android Fix Applied

### 1. Auto-Reconnection Logic

Added automatic reconnection when the server closes unexpectedly:

```kotlin
// Auto-reconnection settings
private var shouldReconnect = false
private var reconnectAttempts = 0
private val maxReconnectAttempts = 5
private val reconnectDelayMs = 2000L
```

### 2. scheduleReconnect() Method

Schedules reconnection with exponential backoff (2s, 4s, 6s, 8s, 10s).

### 3. Key Changes

- `onClosed` now triggers auto-reconnection when server closes unexpectedly
- `onFailure` triggers auto-reconnection on connection failures  
- `disconnect()` disables auto-reconnection (deliberate disconnect)
- Added `isReadyForTelemetry()` and `reconnect()` helper methods

## Backend Fix Required

### Step 1: Check Your Database

Run these queries to verify the data exists:

```sql
-- Check if Admin exists
SELECT * FROM pavaman_gcs_app_admin WHERE id = 1;

-- Check if Pilot exists  
SELECT * FROM pavaman_gcs_app_pilot WHERE id = 45;

-- If they don't exist, create them:
INSERT INTO pavaman_gcs_app_admin (id, name, email) VALUES (1, 'Admin', 'admin@example.com');
INSERT INTO pavaman_gcs_app_pilot (id, name, email, admin_id) VALUES (45, 'Pilot', 'pilot@example.com', 1);
```

### Step 2: Check Django Admin Panel

1. Go to `http://65.0.76.31:8000/admin/`
2. Check if Admin with id=1 exists
3. Check if Pilot with id=45 exists
4. Create them if missing

### Step 3: Better Error Handling (Optional)

Update `consumers.py` to send error instead of closing:

```python
except Admin.DoesNotExist:
    await self.send(json.dumps({
        "type": "error",
        "message": f"Admin with id={admin_id} not found"
    }))
    return  # Don't close, let client handle

except Pilot.DoesNotExist:
    await self.send(json.dumps({
        "type": "error", 
        "message": f"Pilot with id={pilot_id} not found"
    }))
    return

except Exception as e:
    await self.send(json.dumps({
        "type": "error",
        "message": str(e)
    }))
    # Don't call self.close() - let client decide
```

## Debugging Steps

### 1. Check Backend Logs

When the app connects, look for these messages in the Daphne console:

```
✅ session_ack sent
📋 vehicle=SITL_DRONE_001, pilot=45, admin=1, plot=mission1
❌ SESSION_START ERROR: ...   <-- Look for this error!
```

### 2. Common Errors

| Error | Cause | Fix |
|-------|-------|-----|
| `Admin matching query does not exist` | admin_id=1 not in DB | Create Admin record |
| `Pilot matching query does not exist` | pilot_id=45 not in DB | Create Pilot record |
| `UNIQUE constraint failed` | Duplicate mission | Check mission_id |

## Files Modified

### Android
- `app/src/main/java/com/example/aerogcsclone/telemetry/WebSocketManager.kt`
  - Added auto-reconnection logic with exponential backoff
  - Added `shouldReconnect` flag
  - Added `scheduleReconnect()` method
  - Added `isReadyForTelemetry()` and `reconnect()` helpers

## Testing

1. **Check backend logs** for the actual error message
2. **Fix database** - ensure Admin(id=1) and Pilot(id=45) exist
3. **Restart Daphne** and try again
4. App should now auto-reconnect if connection drops

## Quick Verification Commands

```bash
# Django shell - check if records exist
python manage.py shell
>>> from pavaman_gcs_app.models import Admin, Pilot
>>> Admin.objects.filter(id=1).exists()
>>> Pilot.objects.filter(id=45).exists()

# Create if missing
>>> from pavaman_gcs_app.models import Admin, Pilot
>>> admin, _ = Admin.objects.get_or_create(id=1, defaults={'name': 'Admin'})
>>> pilot, _ = Pilot.objects.get_or_create(id=45, defaults={'name': 'Pilot', 'admin': admin})
```

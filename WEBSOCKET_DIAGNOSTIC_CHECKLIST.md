# WebSocket Backend Diagnostic Checklist

## 🎉 ALL TESTS PASSED - January 29, 2026

### Python Test Script ✅
```
✅ WebSocket Connected: YES
✅ session_ack received: YES  
✅ mission_created received: YES
✅ Mission ID: d34b5f25-02b1-40e1-9b2a-a6b77f1b7207
✅ Telemetry sent: YES
```

### Android App Test ✅
```
✅ WebSocket CONNECTED (Response Code: 101)
✅ session_start sent: pilot_id=45, admin_id=1
✅ SESSION_ACK RECEIVED FROM BACKEND
✅ MISSION CREATED BY BACKEND  
✅ Mission ID: f7fd4b78-67b0-4051-a7ee-1e6bc560577f
✅ TelemetryConsumer.receive() triggered successfully
```

**🎉 Your Django backend AND Android app are fully working!**

---

## ✅ Your Configuration Status

### Backend Files - ALL CORRECT ✅
| File | Status | Notes |
|------|--------|-------|
| `asgi.py` | ✅ Correct | Has ProtocolTypeRouter with websocket routing |
| `routing.py` | ✅ Correct | Maps `^ws/telemetry/$` to TelemetryConsumer |
| `consumers.py` | ✅ Correct | Has connect(), receive(), disconnect() handlers |
| `db.py` | ✅ Correct | Creates asyncpg pool with connect_db() |
| `settings.py` | ✅ Correct | Has ASGI_APPLICATION and CHANNEL_LAYERS |

### Android Files - ALL CORRECT ✅
| File | Status | Notes |
|------|--------|-------|
| `WebSocketManager.kt` | ✅ Correct | URL = `ws://65.0.76.31:8000/ws/telemetry/` |
| `MainActivity.kt` | ✅ Correct | Sets pilotId, adminId from SessionManager |
| `SharedViewModel.kt` | ✅ Correct | Calls wsManager.connect() on mission start |

---

## 🔍 How to Verify the Flow

### Step 1: Check Django Server is Running with Daphne

On your AWS EC2 server, run:
```bash
daphne -b 0.0.0.0 -p 8000 pavaman_gcs.asgi:application
```

You should see:
```
🔌 Initializing database connection pool...
🔗 Connecting to DB: postgres://...
✅ DB pool initialized
✅✅✅ DB POOL READY ✅✅✅
Starting server at tcp:port=8000:interface=0.0.0.0
```

### Step 2: Test with Python Script

From your local machine:
```bash
pip install websocket-client
python test_websocket_connection.py
```

Expected output:
```
🔥 WEBSOCKET CONNECTED!
📤 Sending session_start...
📩 MESSAGE FROM BACKEND: {"type":"session_ack"}
✅✅✅ SESSION_ACK RECEIVED! ✅✅✅
📩 MESSAGE FROM BACKEND: {"type":"mission_created","mission_id":"..."}
🚀🚀🚀 MISSION CREATED! 🚀🚀🚀
```

### Step 3: Check Android Logcat

Filter by `WebSocketManager`:
```
adb logcat | findstr WebSocketManager
```

When mission starts, you should see:
```
🔥 connect() method CALLED
📋 Connecting with pilotId=7, adminId=1
🔥🔥🔥 WebSocket CONNECTED to: ws://65.0.76.31:8000/ws/telemetry/ 🔥🔥🔥
📡 Response Code: 101
📤📤📤 Sending session_start to Django backend 📤📤📤
📩📩📩 MESSAGE FROM DJANGO BACKEND 📩📩📩
📩 Raw message: {"type":"session_ack"}
✅✅✅ SESSION_ACK RECEIVED FROM BACKEND - TELEMETRY ENABLED ✅✅✅
```

### Step 4: Check Django Server Logs

On AWS EC2, you should see:
```
🔥 WebSocket connected (Django Channels)
✅ DB CONNECTION OK: 1
📩 RAW MESSAGE: {"type":"session_start"...
📩 MESSAGE TYPE: session_start
✅ session_ack sent
✅ Mission inserted into DB
🚀 mission_created sent: <mission-uuid>
```

---

## 🔴 Common Issues & Solutions

### Issue 1: "Connection Refused" or Timeout
```
❌ WebSocket error: Connection refused
```

**Causes:**
1. Daphne not running
2. Port 8000 blocked by AWS Security Group
3. Wrong IP address

**Solutions:**
```bash
# Check if Daphne is running
ps aux | grep daphne

# Start Daphne if not running
daphne -b 0.0.0.0 -p 8000 pavaman_gcs.asgi:application

# Check AWS Security Group has port 8000 open for inbound TCP
```

### Issue 2: No "session_ack" Received
```
⚠️ Backend didn't send session_ack within 3000ms
```

**Causes:**
1. consumers.py has an error
2. db.pool is None
3. Database connection failed

**Solutions:**
Check Django logs for errors:
```bash
# Look for these error messages:
❌ DB POOL IS NULL - Cannot save data!
❌ SESSION_START DB ERROR: ...
```

### Issue 3: Connection Closes Immediately (Code 1006)
```
🔌 WebSocket closed: 1006
```

**Causes:**
1. asgi.py not configured correctly
2. routing.py URL pattern mismatch
3. Consumer throws exception in connect()

**Solutions:**
```python
# Verify routing.py pattern matches the URL:
# URL:     ws://65.0.76.31:8000/ws/telemetry/  (with trailing slash)
# Pattern: ^ws/telemetry/$                       (with trailing slash)
```

### Issue 4: "pilotId not set" Error
```
⛔ Cannot connect - pilotId not set! Please login first.
```

**Cause:** User not logged in, SessionManager has no pilotId

**Solution:** Make sure user logs in before starting mission

---

## 🧪 Quick Test Commands

### Test from Windows (PowerShell)
```powershell
# Install websocket client
pip install websocket-client

# Run test script
python test_websocket_connection.py
```

### Test from Linux/Mac
```bash
# Using wscat
npm install -g wscat
wscat -c ws://65.0.76.31:8000/ws/telemetry/

# Then send:
{"type":"session_start","vehicle_name":"DRONE_01","admin_id":1,"pilot_id":7,"drone_uid":"TEST","plot_name":"Test"}
```

### Check PostgreSQL Data
```sql
-- Check if missions are being created
SELECT * FROM pavaman_gcs_app_mission ORDER BY start_time DESC LIMIT 5;

-- Check telemetry data
SELECT * FROM pavaman_gcs_app_telemetry_position ORDER BY ts DESC LIMIT 10;
```

---

## 📊 Expected Message Flow

```
┌─────────────────┐         ┌─────────────────┐         ┌─────────────────┐
│  Android App    │         │  Django Server  │         │   PostgreSQL    │
│ WebSocketManager│         │ TelemetryConsumer│        │    Database     │
└────────┬────────┘         └────────┬────────┘         └────────┬────────┘
         │                           │                           │
         │ 1. WS Connect             │                           │
         │ ──────────────────────────>                           │
         │                           │                           │
         │                           │ connect() called          │
         │                           │ DB pool check             │
         │                           │                           │
         │ 2. session_start          │                           │
         │ {"type":"session_start",  │                           │
         │  "pilot_id":7,...}        │                           │
         │ ──────────────────────────>                           │
         │                           │                           │
         │                           │ 3. INSERT mission         │
         │                           │ ─────────────────────────>│
         │                           │                           │
         │ 4. session_ack            │                           │
         │ <──────────────────────────                           │
         │                           │                           │
         │ 5. mission_created        │                           │
         │ {"mission_id":"uuid"}     │                           │
         │ <──────────────────────────                           │
         │                           │                           │
         │ 6. telemetry              │                           │
         │ {"type":"telemetry",...}  │                           │
         │ ──────────────────────────>                           │
         │                           │                           │
         │                           │ 7. INSERT telemetry       │
         │                           │ ─────────────────────────>│
         │                           │                           │
```

---

## ✅ Success Criteria

If everything is working, you should see:
1. ✅ Python test script: "ALL TESTS PASSED"
2. ✅ Android Logcat: "SESSION_ACK RECEIVED FROM BACKEND"
3. ✅ Django logs: "Mission inserted into DB", "Telemetry saved (all tables)"
4. ✅ PostgreSQL: New rows in mission and telemetry tables


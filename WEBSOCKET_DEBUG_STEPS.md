# WebSocket Debug Steps

## 🔴 Problem Summary
- Server log shows: `WSCONNECT` then `WSDISCONNECT` after 2.5 minutes
- **NO messages received** by Django `receive()` method
- This means Android connects but doesn't send `session_start`

---

## 🔍 Step 1: Run Android App with Logcat Filter

Filter Logcat with tag: `WS_DEBUG`

### Expected Log Sequence (if working correctly):
```
🔥🔥🔥 connect() method CALLED 🔥🔥🔥
📋 Thread: main
📋 pilotId=7, adminId=1, droneUid='...'
📡 Target URL: ws://65.0.76.31:8000/ws/telemetry/
📡 Request built, calling client.newWebSocket()...
✅ client.newWebSocket() called - waiting for onOpen/onFailure
🔥🔥🔥 onOpen() CALLED — preparing session_start 🔥🔥🔥
📤 About to send session_start payload: {...}
📤📤📤 session_start send result = true 📤📤📤
✅ send() returned TRUE - message should reach server
```

### Red Flags to Look For:
| Log | Meaning |
|-----|---------|
| `❌ ABORT: pilotId=... is invalid` | pilotId not set before connect() |
| `❌❌❌ onFailure() CALLED` | Connection failed |
| `⚠️⚠️⚠️ onClosing() CALLED` immediately | Socket closed before message sent |
| `send result = false` | OkHttp couldn't send the message |
| No `onOpen()` log | Callback never fired |

---

## 🔍 Step 2: Check Server Live Logs (EC2)

```bash
sudo journalctl -u daphne -f
```

If working, you should see:
```
WSCONNECT /ws/telemetry/
🔥 RECEIVE() HIT 🔥
RAW: {"type":"session_start",...}
📩 MESSAGE TYPE: session_start
```

---

## 🔍 Step 3: Test from PC (Bypass Android)

```bash
# Install wscat
npm install -g wscat

# Connect and send message manually
wscat -c ws://65.0.76.31:8000/ws/telemetry/
```

Then type:
```json
{"type":"session_start","pilot_id":7,"admin_id":1,"vehicle_name":"DRONE_01","drone_uid":"TEST1","plot_name":"Test"}
```

If this works → **Backend is OK, problem is Android**

---

## 🔧 Common Fixes

### Fix 1: pilotId Not Set
```kotlin
// In your login/session code, ensure:
WebSocketManager.getInstance().pilotId = sessionManager.pilotId
WebSocketManager.getInstance().adminId = sessionManager.adminId
```

### Fix 2: WebSocket Closes Too Early
Check if Activity lifecycle destroys WebSocket:
- `onPause()` or `onDestroy()` calling `disconnect()`
- App going to background

### Fix 3: URL Mismatch
Ensure trailing slash matches:
- Android: `ws://65.0.76.31:8000/ws/telemetry/` (WITH `/`)
- Django routing: `r"^ws/telemetry/$"` (WITH `$`)

---

## 📱 Add to Backend consumers.py (for debugging)

```python
async def receive(self, text_data):
    print("🔥 RECEIVE() HIT 🔥", flush=True)
    print("RAW:", text_data, flush=True)
    # ... rest of code
```

Restart Daphne after adding this.

---

## ✅ Checklist
- [ ] Logcat shows `connect() method CALLED`
- [ ] Logcat shows `onOpen() CALLED`
- [ ] Logcat shows `send result = true`
- [ ] Server shows `RECEIVE() HIT`
- [ ] No `onFailure` or `onClosing` immediately after connect


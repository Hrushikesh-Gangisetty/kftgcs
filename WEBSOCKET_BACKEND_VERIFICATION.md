# WebSocket Backend Connection Verification Guide

## 📡 Connection Flow

When the Android app connects to your Django backend, here's the expected flow:

```
┌─────────────────────┐
│   Android App       │
│  WebSocketManager   │
└──────────┬──────────┘
           │ 1. Connect to ws://65.0.76.31:8000/ws/telemetry/
           ▼
┌─────────────────────┐
│   Django Backend    │
│      asgi.py        │ ◄─── ASGI_APPLICATION = "pavaman_gcs.asgi.application"
└──────────┬──────────┘
           │ 2. Route WebSocket to routing.py
           ▼
┌─────────────────────┐
│     routing.py      │
│ websocket_urlpatterns│ ◄─── re_path(r"^ws/telemetry/$", TelemetryConsumer.as_asgi())
└──────────┬──────────┘
           │ 3. Trigger TelemetryConsumer
           ▼
┌─────────────────────┐
│    consumers.py     │
│  TelemetryConsumer  │ ◄─── async def connect(), receive(), disconnect()
└──────────┬──────────┘
           │ 4. Save to PostgreSQL
           ▼
┌─────────────────────┐
│   PostgreSQL DB     │
└─────────────────────┘
```

## ✅ What You Need to Verify

### 1. ASGI Application Configuration (asgi.py)

Your `asgi.py` file MUST look like this:

```python
# pavaman_gcs/asgi.py
import os
from django.core.asgi import get_asgi_application
from channels.routing import ProtocolTypeRouter, URLRouter
from channels.auth import AuthMiddlewareStack

os.environ.setdefault('DJANGO_SETTINGS_MODULE', 'pavaman_gcs.settings')

# Initialize Django ASGI application early
django_asgi_app = get_asgi_application()

# Import routing AFTER Django is initialized
from pavaman_gcs_app.routing import websocket_urlpatterns

application = ProtocolTypeRouter({
    "http": django_asgi_app,
    "websocket": AuthMiddlewareStack(
        URLRouter(
            websocket_urlpatterns
        )
    ),
})
```

**⚠️ IMPORTANT:** If your `asgi.py` only has the HTTP router and not the WebSocket router, the WebSocket connection will fail!

### 2. Verify routing.py is Correct

Your `routing.py` looks correct:
```python
from django.urls import re_path
from .consumers import TelemetryConsumer

websocket_urlpatterns = [
    re_path(r"^ws/telemetry/$", TelemetryConsumer.as_asgi()),
]
```

**Note:** The pattern `^ws/telemetry/$` expects the URL `ws://host:port/ws/telemetry/` with trailing slash.

### 3. Run the Server with Daphne (NOT runserver)

Django Channels requires an ASGI server. Use Daphne:

```bash
# On your AWS EC2 server
daphne -b 0.0.0.0 -p 8000 pavaman_gcs.asgi:application
```

**⚠️ DO NOT use `python manage.py runserver` for WebSocket connections!**

### 4. Check AWS Security Group

Ensure port 8000 is open in your EC2 Security Group for inbound traffic.

## 🧪 Testing the Connection

### Option 1: Python Test Script (from your local machine)

```bash
pip install websocket-client
python test_websocket_connection.py
```

### Option 2: Using wscat (command line)

```bash
npm install -g wscat
wscat -c ws://65.0.76.31:8000/ws/telemetry/
```

Then send:
```json
{"type":"session_start","vehicle_name":"DRONE_01","admin_id":1,"pilot_id":7,"drone_uid":"TEST_DRONE_001","plot_name":"Test Field"}
```

### Option 3: Check Server Logs

On your AWS EC2 server, watch the Daphne logs:
```bash
daphne -b 0.0.0.0 -p 8000 pavaman_gcs.asgi:application 2>&1 | tee websocket.log
```

You should see:
```
🔥 WebSocket connected (Django Channels)
✅ DB CONNECTION OK: 1
📩 RAW MESSAGE: {"type":"session_start"...
📩 MESSAGE TYPE: session_start
✅ session_ack sent
✅ Mission inserted into DB
🚀 mission_created sent: <uuid>
```

## 🔧 Common Issues & Fixes

### Issue 1: Connection Refused
```
❌ Error: Connection refused
```
**Fix:** Server is not running or port is blocked.
- Start Daphne: `daphne -b 0.0.0.0 -p 8000 pavaman_gcs.asgi:application`
- Open port 8000 in AWS Security Group

### Issue 2: WebSocket Closes Immediately
```
❌ WebSocket closed: 1006
```
**Fix:** ASGI application not configured correctly.
- Check `asgi.py` has `ProtocolTypeRouter` with both "http" and "websocket"
- Verify routing.py is imported correctly

### Issue 3: DB Pool is NULL
```
❌ DB POOL IS NULL - Cannot save data!
```
**Fix:** Database connection pool not initialized.
- Check `db.py` has `db.init_pool()` in the startup
- Verify `.env` has correct database credentials

### Issue 4: No session_ack Received
```
⚠️ Backend didn't send session_ack within 3000ms
```
**Fix:** Consumer is not processing messages.
- Check consumers.py has no syntax errors
- Verify PostgreSQL connection in consumers.py

### Issue 5: 404 Not Found
```
❌ WebSocket connection failed (404)
```
**Fix:** URL pattern mismatch.
- Android URL: `ws://65.0.76.31:8000/ws/telemetry/` (with trailing slash)
- routing.py pattern: `r"^ws/telemetry/$"` (with trailing slash)
- They MUST match!

## 📱 Android App Log Messages to Look For

When the app connects successfully, you should see in Logcat:

```
🔥 connect() method CALLED
📋 Connecting with pilotId=7, adminId=1
🔥🔥🔥 WebSocket CONNECTED to: ws://65.0.76.31:8000/ws/telemetry/ 🔥🔥🔥
📡 Response Code: 101
📡 Response Message: Switching Protocols
📤📤📤 Sending session_start to Django backend 📤📤📤
📤 Payload: {"type":"session_start",...}
📤 Send result: true
📩📩📩 MESSAGE FROM DJANGO BACKEND 📩📩📩
📩 Raw message: {"type":"session_ack"}
✅✅✅ SESSION_ACK RECEIVED FROM BACKEND - TELEMETRY ENABLED ✅✅✅
📩📩📩 MESSAGE FROM DJANGO BACKEND 📩📩📩
📩 Raw message: {"type":"mission_created","mission_id":"..."}
🚀🚀🚀 MISSION CREATED BY BACKEND 🚀🚀🚀
```

## 📂 Files to Check

| File | Location | Purpose |
|------|----------|---------|
| `asgi.py` | `pavaman_gcs/asgi.py` | ASGI entry point with WebSocket routing |
| `routing.py` | `pavaman_gcs_app/routing.py` | WebSocket URL patterns |
| `consumers.py` | `pavaman_gcs_app/consumers.py` | WebSocket message handlers |
| `db.py` | `pavaman_gcs_app/db.py` | Database connection pool |
| `settings.py` | `pavaman_gcs/settings.py` | Django Channels configuration |

## 🚀 Quick Start Checklist

- [ ] `asgi.py` has ProtocolTypeRouter with websocket routing
- [ ] `routing.py` imports TelemetryConsumer correctly
- [ ] Daphne is running (not runserver)
- [ ] Port 8000 is open in AWS Security Group
- [ ] Database pool is initialized
- [ ] Python test script connects successfully
- [ ] Android app shows "SESSION_ACK RECEIVED" in logs


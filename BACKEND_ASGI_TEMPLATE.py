"""
ASGI config for pavaman_gcs project.

It exposes the ASGI callable as a module-level variable named ``application``.

For more information on this file, see
https://docs.djangoproject.com/en/6.0/howto/deployment/asgi/

YOUR ACTUAL BACKEND asgi.py - THIS IS CORRECT ✅
"""

import os
import django
import asyncio
from django.core.asgi import get_asgi_application
from channels.routing import ProtocolTypeRouter, URLRouter
from channels.auth import AuthMiddlewareStack

os.environ.setdefault("DJANGO_SETTINGS_MODULE", "pavaman_gcs.settings")
django.setup()

# Import db AFTER django.setup()
from pavaman_gcs_app import db
from pavaman_gcs_app.routing import websocket_urlpatterns

# ✅ CRITICAL: initialize DB pool on startup
print("🔌 Initializing database connection pool...")
try:
    loop = asyncio.get_event_loop()
except RuntimeError:
    loop = asyncio.new_event_loop()
    asyncio.set_event_loop(loop)

loop.run_until_complete(db.connect_db())
print("✅✅✅ DB POOL READY ✅✅✅")

application = ProtocolTypeRouter({
    "http": get_asgi_application(),
    "websocket": AuthMiddlewareStack(
        URLRouter(websocket_urlpatterns)
    ),
})


"""
Django Channels Consumer — Updated for offline-sync deduplication
Copy this to your Django backend: pavaman_gcs_app/consumers.py

Changes from BACKEND_CONSUMERS_WITH_CROP_TYPE.py:
1. mission_event handler: reads client_id, skips if already exists (dedup)
2. mission_summary handler: reads client_id, stores it
3. mission_status handler: logs client_id for traceability
4. All handlers tolerate the extra client_id field gracefully
5. Added 'alerts_count' missing field handling

REQUIRES: BACKEND_MODELS_WITH_OFFLINE_SYNC.py to be deployed first
    python manage.py makemigrations
    python manage.py migrate
"""

import json
import uuid
import logging
import sys
from datetime import datetime

from asgiref.sync import sync_to_async
from channels.generic.websocket import AsyncWebsocketConsumer
from django.utils.timezone import now, make_aware

from .models import (
    Admin,
    Pilot,
    Vehicle,
    Mission,
    TelemetryPosition,
    TelemetryBattery,
    TelemetryAttitude,
    TelemetryGPS,
    TelemetryStatus,
    TelemetrySpray,
    MissionEvent,
    MissionSummary,
)

# Configure logging to show in console
logging.basicConfig(
    level=logging.INFO,
    format='%(asctime)s %(levelname)-8s %(message)s',
    handlers=[logging.StreamHandler(sys.stdout)]
)
logger = logging.getLogger(__name__)


class TelemetryConsumer(AsyncWebsocketConsumer):

    # --------------------------------------------------
    # CONNECT
    # --------------------------------------------------
    async def connect(self):
        await self.accept()
        self.session = {}

        print("=" * 60, flush=True)
        print("🔥 WebSocket connected (Django Channels)", flush=True)
        print("=" * 60, flush=True)
        logger.info("WebSocket connected")

    # --------------------------------------------------
    # RECEIVE
    # --------------------------------------------------
    async def receive(self, text_data):
        print(f"📩 RAW MESSAGE: {text_data}", flush=True)
        logger.info(f"RAW MESSAGE: {text_data}")

        try:
            data = json.loads(text_data)
        except Exception as e:
            print(f"❌ Invalid JSON: {e}", flush=True)
            logger.exception("Invalid JSON")
            await self.send(json.dumps({"type": "error", "message": f"Invalid JSON: {e}"}))
            return

        msg_type = data.get("type")
        print(f"📩 MESSAGE TYPE: {msg_type}", flush=True)
        logger.info(f"MESSAGE TYPE: {msg_type}")

        # ==================================================
        # SESSION START
        # ==================================================
        if msg_type == "session_start":
            await self.send(json.dumps({"type": "session_ack"}))
            print("✅ session_ack sent", flush=True)

            try:
                vehicle_id = data.get("drone_uid") or "SITL_DRONE_001"
                vehicle_name = data.get("vehicle_name", vehicle_id)
                pilot_id = data.get("pilot_id")
                admin_id = data.get("admin_id")
                plot_name = data.get("plot_name")

                # Mission configuration fields
                flight_mode = data.get("flight_mode", "AUTOMATIC")
                mission_type = data.get("mission_type", "NONE")
                grid_setup_source = data.get("grid_setup_source", "NONE")

                print(
                    f"📋 vehicle={vehicle_id}, pilot={pilot_id}, admin={admin_id}, plot={plot_name}, "
                    f"flight_mode={flight_mode}, mission_type={mission_type}, grid_source={grid_setup_source}",
                    flush=True
                )

                admin = await sync_to_async(Admin.objects.get)(id=admin_id)
                pilot = await sync_to_async(Pilot.objects.get)(id=pilot_id)

                vehicle, _ = await sync_to_async(Vehicle.objects.get_or_create)(
                    vehicle_id=vehicle_id,
                    defaults={
                        "vehicle_name": vehicle_name,
                        "admin": admin,
                        "pilot": pilot,
                    }
                )

                mission = await sync_to_async(Mission.objects.create)(
                    mission_id=uuid.uuid4(),
                    vehicle=vehicle,
                    admin=admin,
                    pilot=pilot,
                    start_time=now(),
                    status=Mission.STATUS_CREATED,
                    plot_name=plot_name,
                    flight_mode=flight_mode,
                    mission_type=mission_type,
                    grid_setup_source=grid_setup_source,
                )

                self.session = {
                    "mission": mission,
                    "vehicle": vehicle,
                    "admin": admin,
                    "pilot": pilot,
                }

                await self.send(json.dumps({
                    "type": "mission_created",
                    "mission_id": str(mission.mission_id),
                }))

                print(f"🚀 Mission created: {mission.mission_id}", flush=True)
                logger.info(f"Mission created {mission.mission_id}")

            except Admin.DoesNotExist:
                error_msg = f"Admin with id={admin_id} not found in database."
                print(f"❌ {error_msg}", flush=True)
                logger.error(error_msg)
                await self.send(json.dumps({"type": "error", "message": error_msg}))
                return

            except Pilot.DoesNotExist:
                error_msg = f"Pilot with id={pilot_id} not found in database."
                print(f"❌ {error_msg}", flush=True)
                logger.error(error_msg)
                await self.send(json.dumps({"type": "error", "message": error_msg}))
                return

            except Exception as e:
                print(f"❌ SESSION_START ERROR: {e}", flush=True)
                logger.exception("SESSION_START failed")
                await self.send(json.dumps({"type": "error", "message": str(e)}))

        # ==================================================
        # TELEMETRY  (high-frequency, no dedup needed)
        # ==================================================
        elif msg_type == "telemetry":
            mission = self.session.get("mission")
            if not mission:
                print("❌ Telemetry before session_start", flush=True)
                return

            try:
                ts = make_aware(datetime.fromtimestamp(data["ts"] / 1000))

                await sync_to_async(TelemetryPosition.objects.create)(
                    mission=mission,
                    vehicle=self.session["vehicle"],
                    admin=self.session["admin"],
                    pilot=self.session["pilot"],
                    ts=ts,
                    lat=data["position"]["lat"],
                    lng=data["position"]["lng"],
                    alt=data["position"]["alt"],
                    speed=data["gps"].get("speed"),
                )

                await sync_to_async(TelemetryBattery.objects.create)(
                    mission=mission,
                    vehicle=self.session["vehicle"],
                    admin=self.session["admin"],
                    pilot=self.session["pilot"],
                    ts=ts,
                    voltage=data["battery"].get("voltage"),
                    current=data["battery"].get("current"),
                    remaining=data["battery"].get("remaining"),
                )

                await sync_to_async(TelemetryAttitude.objects.create)(
                    mission=mission,
                    vehicle=self.session["vehicle"],
                    admin=self.session["admin"],
                    pilot=self.session["pilot"],
                    ts=ts,
                    roll=data["attitude"]["roll"],
                    pitch=data["attitude"]["pitch"],
                    yaw=data["attitude"]["yaw"],
                )

                await sync_to_async(TelemetryGPS.objects.create)(
                    mission=mission,
                    vehicle=self.session["vehicle"],
                    admin=self.session["admin"],
                    pilot=self.session["pilot"],
                    ts=ts,
                    satellites=data["gps"]["satellites"],
                    hdop=data["gps"]["hdop"],
                )

                await sync_to_async(TelemetryStatus.objects.create)(
                    mission=mission,
                    vehicle=self.session["vehicle"],
                    admin=self.session["admin"],
                    pilot=self.session["pilot"],
                    ts=ts,
                    flight_mode=data["status"]["flight_mode"],
                    armed=data["status"]["armed"],
                    failsafe=data["status"]["failsafe"],
                )

                spray = data.get("spray")
                if spray:
                    await sync_to_async(TelemetrySpray.objects.create)(
                        mission=mission,
                        vehicle=self.session["vehicle"],
                        admin=self.session["admin"],
                        pilot=self.session["pilot"],
                        ts=ts,
                        spray_on=spray["on"],
                        spray_rate_lpm=spray.get("rate_lpm"),
                        flow_pulse=spray.get("flow_pulse"),
                        tank_level_liters=spray.get("tank_level"),
                    )

                print("✅ Telemetry saved", flush=True)
                logger.info("Telemetry saved")

            except Exception as e:
                print(f"❌ TELEMETRY ERROR: {e}", flush=True)
                logger.exception("Telemetry failed")

        # ==================================================
        # MISSION STATUS  (offline-queue may replay this)
        # ==================================================
        elif msg_type == "mission_status":
            mission = self.session.get("mission")
            if not mission:
                return

            client_id = data.get("client_id")
            status = data.get("status")

            mission.status = status

            if status == Mission.STATUS_PAUSED:
                mission.paused_at = now()
            elif status == Mission.STATUS_RESUMED:
                mission.resumed_at = now()
            elif status == Mission.STATUS_ENDED:
                mission.end_time = now()

            await sync_to_async(mission.save)()

            print(f"✅ Mission status updated → {status} (client_id={client_id})", flush=True)
            logger.info(f"Mission status updated → {status} (client_id={client_id})")

        # ==================================================
        # MISSION EVENT  (offline-queue: dedup by client_id)
        # ==================================================
        elif msg_type == "mission_event":
            mission = self.session.get("mission")
            if not mission:
                return

            client_id = data.get("client_id")

            # ── Deduplication ────────────────────────────────────
            # The Android offline queue may flush the same event twice
            # (e.g. SyncWorker + WebSocket reconnect race). If the
            # client_id already exists for this mission, skip it.
            if client_id:
                already_exists = await sync_to_async(
                    MissionEvent.objects.filter(
                        mission=mission,
                        client_id=client_id,
                    ).exists
                )()
                if already_exists:
                    print(f"⏭️ Duplicate mission_event skipped (client_id={client_id})", flush=True)
                    logger.info(f"Duplicate mission_event skipped (client_id={client_id})")
                    return

            await sync_to_async(MissionEvent.objects.create)(
                mission=mission,
                vehicle=self.session["vehicle"],
                admin=self.session["admin"],
                pilot=self.session["pilot"],
                event_type=data.get("event_type"),
                event_status=data.get("event_status"),
                event_description=data.get("description", ""),
                client_id=client_id,
            )

            print(f"✅ Mission event saved (client_id={client_id})", flush=True)
            logger.info(f"Mission event saved (client_id={client_id})")

        # ==================================================
        # DRONE UID UPDATE (when real UID received from FC)
        # ==================================================
        elif msg_type == "drone_uid_update":
            mission = self.session.get("mission")
            if not mission:
                print("❌ drone_uid_update before session_start", flush=True)
                return

            try:
                new_drone_uid = data.get("drone_uid")
                if not new_drone_uid:
                    print("⚠️ drone_uid_update: No drone_uid provided", flush=True)
                    return

                print(f"🔥 Updating drone UID to: {new_drone_uid}", flush=True)

                admin = self.session["admin"]
                pilot = self.session["pilot"]
                old_vehicle = self.session["vehicle"]

                new_vehicle, created = await sync_to_async(Vehicle.objects.get_or_create)(
                    vehicle_id=new_drone_uid,
                    defaults={
                        "vehicle_name": new_drone_uid,
                        "admin": admin,
                        "pilot": pilot,
                    }
                )

                mission.vehicle = new_vehicle
                await sync_to_async(mission.save)()

                self.session["vehicle"] = new_vehicle

                print(f"✅ Vehicle ID updated: {old_vehicle.vehicle_id} → {new_drone_uid}", flush=True)
                logger.info(f"Vehicle ID updated from {old_vehicle.vehicle_id} to {new_drone_uid}")

            except Exception as e:
                print(f"❌ DRONE_UID_UPDATE ERROR: {e}", flush=True)
                logger.exception("drone_uid_update failed")

        # ==================================================
        # MISSION SUMMARY  (offline-queue: dedup via update_or_create)
        #
        # MissionSummary uses OneToOneField(mission), so
        # update_or_create already prevents duplicates.
        # client_id is stored for audit/traceability.
        # ==================================================
        elif msg_type == "mission_summary":
            mission = self.session.get("mission")
            if not mission:
                print("❌ Mission summary before session_start", flush=True)
                return

            try:
                client_id = data.get("client_id")
                project_name = data.get("project_name", "")
                plot_name = data.get("plot_name", "")
                crop_type = data.get("crop_type", "")

                print(
                    f"📋 Mission summary: project={project_name}, plot={plot_name}, "
                    f"crop={crop_type}, client_id={client_id}",
                    flush=True
                )

                summary, created = await sync_to_async(MissionSummary.objects.update_or_create)(
                    mission=mission,
                    defaults={
                        "vehicle": self.session["vehicle"],
                        "admin": self.session["admin"],
                        "pilot": self.session["pilot"],
                        "total_acres": data.get("total_acres"),
                        "total_sprayed_acres": data.get("total_sprayed_acres"),
                        "total_spray_used_liters": data.get("total_spray_used"),
                        "flying_time_sec": data.get("flying_time_minutes", 0) * 60 if data.get("flying_time_minutes") else None,
                        "battery_start": data.get("battery_start"),
                        "battery_end": data.get("battery_end"),
                        "alerts_count": data.get("alerts_count", 0),
                        "status": data.get("status", "COMPLETED"),
                        "project_name": project_name,
                        "plot_name": plot_name,
                        "crop_type": crop_type,
                        "client_id": client_id,
                    }
                )

                action = "created" if created else "updated"
                print(
                    f"✅ Mission summary {action} (client_id={client_id})",
                    flush=True
                )
                logger.info(f"Mission summary {action} (client_id={client_id})")

            except Exception as e:
                print(f"❌ MISSION SUMMARY ERROR: {e}", flush=True)
                logger.exception("Mission summary failed")

        else:
            print(f"⚠️ Unknown message type: {msg_type}", flush=True)

    # --------------------------------------------------
    # DISCONNECT
    # --------------------------------------------------
    async def disconnect(self, close_code):
        print(f"❌ WebSocket disconnected: {close_code}", flush=True)
        logger.info(f"WebSocket disconnected {close_code}")

        mission = self.session.get("mission")
        if mission and not mission.end_time:
            mission.status = Mission.STATUS_ENDED
            mission.end_time = now()
            await sync_to_async(mission.save)()
            print("✅ Mission closed safely", flush=True)


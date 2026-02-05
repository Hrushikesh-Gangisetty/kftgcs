"""
Run this script in Django shell to check and create missing Pilot/Admin

Usage:
1. SSH to your server: ssh -i your-key.pem ubuntu@65.0.76.31
2. cd to your Django project
3. Activate virtualenv: source venv/bin/activate
4. Run: python manage.py shell
5. Copy-paste the code below
"""

# === PASTE THIS IN DJANGO SHELL ===

from pavaman_gcs_app.models import Admin, Pilot

print("=" * 50)
print("CHECKING DATABASE RECORDS")
print("=" * 50)

# Check Admins
print("\n📋 ALL ADMINS:")
for admin in Admin.objects.all():
    print(f"  - Admin id={admin.id}")

# Check Pilots
print("\n📋 ALL PILOTS:")
for pilot in Pilot.objects.all():
    print(f"  - Pilot id={pilot.id}, email={getattr(pilot, 'email', 'N/A')}")

print("\n" + "=" * 50)

# Check if required IDs exist
admin_id = 1
pilot_id = 1

admin_exists = Admin.objects.filter(id=admin_id).exists()
pilot_exists = Pilot.objects.filter(id=pilot_id).exists()

print(f"Admin(id={admin_id}) exists: {admin_exists}")
print(f"Pilot(id={pilot_id}) exists: {pilot_exists}")

# === CREATE MISSING RECORDS ===

if not admin_exists:
    print(f"\n⚠️ Admin(id={admin_id}) does not exist!")
    print("To create it, run:")
    print(f"  Admin.objects.create(id={admin_id}, ...other_required_fields...)")

if not pilot_exists:
    print(f"\n⚠️ Pilot(id={pilot_id}) does not exist!")
    print("To create it, run:")
    print(f"  Pilot.objects.create(id={pilot_id}, email='test@example.com', ...other_required_fields...)")

if admin_exists and pilot_exists:
    print("\n✅ All required records exist! The app should work now.")


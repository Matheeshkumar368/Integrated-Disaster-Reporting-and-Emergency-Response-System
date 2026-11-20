import requests
import json
import sys

BASE_URL = "http://localhost:8080"

def test_pages():
    print("\n--- 1. Testing HTML Page Availability ---")
    pages = [
        "/admin-users.html",
        "/admin-notifications.html",
        "/citizen-notifications.html",
        "/responder-notifications.html"
    ]
    for p in pages:
        r = requests.get(BASE_URL + p)
        assert r.status_code == 200, f"Expected 200 for {p}, got {r.status_code}"
        print(f"  [PASS] {p} returned 200 OK (size: {len(r.text)} bytes)")

def test_api():
    print("\n--- 2. Testing Authentication & Role Tokens ---")
    
    # 1. Login Admin
    r = requests.post(f"{BASE_URL}/api/auth/login", json={"email": "admin@disaster.gov.in", "password": "admin123"})
    assert r.status_code == 200, f"Admin login failed: {r.text}"
    admin_token = r.json().get("token")
    assert admin_token, "No token returned for admin"
    print("  [PASS] Admin authenticated successfully.")

    # 2. Login Citizen
    r = requests.post(f"{BASE_URL}/api/auth/login", json={"email": "citizen@gmail.com", "password": "citizen123"})
    assert r.status_code == 200, f"Citizen login failed: {r.text}"
    citizen_token = r.json().get("token")
    citizen_email = r.json().get("email")
    print(f"  [PASS] Citizen ({citizen_email}) authenticated successfully.")

    # 3. Login Responder
    r = requests.post(f"{BASE_URL}/api/auth/login", json={"email": "responder@disaster.gov.in", "password": "responder123"})
    assert r.status_code == 200, f"Responder login failed: {r.text}"
    responder_token = r.json().get("token")
    responder_email = r.json().get("email")
    print(f"  [PASS] Responder ({responder_email}) authenticated successfully.")

    print("\n--- 3. Testing Admin User Directory (/api/auth/admin/users) ---")
    headers_admin = {"Authorization": f"Bearer {admin_token}", "Content-Type": "application/json"}
    r = requests.get(f"{BASE_URL}/api/auth/admin/users", headers=headers_admin)
    assert r.status_code == 200, f"Expected 200 from /api/auth/admin/users, got {r.status_code}: {r.text}"
    data = r.json()
    assert data.get("status") == "success"
    users = data.get("users", [])
    assert len(users) > 0, "Users list should not be empty"
    print(f"  [PASS] Retrieved {len(users)} users (Total: {data.get('totalUsers')}, Responders: {data.get('responderCount')}, Citizens: {data.get('citizenCount')}, Admins: {data.get('adminCount')})")

    # Find a responder to test zone assignment
    responder_user = next((u for u in users if u.get("role") == "Responder"), None)
    assert responder_user is not None, "At least one responder must exist"
    print(f"  [PASS] Found test responder: {responder_user.get('email')} in zone: {responder_user.get('assignedZone')}")

    print("\n--- 4. Testing Responder Zone Assignment (/api/auth/assign-zone) ---")
    r = requests.post(
        f"{BASE_URL}/api/auth/assign-zone",
        headers=headers_admin,
        json={"responderEmail": responder_user.get("email"), "zone": "Karur"}
    )
    assert r.status_code == 200, f"Assign zone failed: {r.text}"
    print(f"  [PASS] Assigned zone 'Karur' to responder {responder_user.get('email')}")

    print("\n--- 5. Testing User Deactivation and Reactivation ---")
    # Find a citizen to test deactivation (do not deactivate admin)
    citizen_user = next((u for u in users if u.get("role") == "Citizen"), None)
    assert citizen_user is not None, "At least one citizen must exist"
    c_id = citizen_user.get("id")
    
    # Deactivate
    r = requests.put(f"{BASE_URL}/api/auth/users/{c_id}/deactivate", headers=headers_admin, json={"reason": "Testing user lifecycle"})
    assert r.status_code == 200, f"Deactivation failed: {r.text}"
    print(f"  [PASS] Successfully deactivated user ID {c_id} ({citizen_user.get('email')})")

    # Reactivate
    r = requests.put(f"{BASE_URL}/api/auth/users/{c_id}/reactivate", headers=headers_admin)
    assert r.status_code == 200, f"Reactivation failed: {r.text}"
    print(f"  [PASS] Successfully reactivated user ID {c_id} ({citizen_user.get('email')})")

    print("\n--- 6. Testing Notifications for all 3 Roles ---")
    for role_name, token in [("Admin", admin_token), ("Citizen", citizen_token), ("Responder", responder_token)]:
        h = {"Authorization": f"Bearer {token}"}
        r = requests.get(f"{BASE_URL}/api/notifications", headers=h)
        assert r.status_code == 200, f"Get notifications failed for {role_name}: {r.text}"
        notifs = r.json()
        assert isinstance(notifs, list), f"Expected list of notifications for {role_name}"
        assert len(notifs) > 0, f"Expected at least 1 notification for {role_name}"
        print(f"  [PASS] {role_name} has {len(notifs)} notification(s). Latest: '{notifs[0].get('title')}' [Category: {notifs[0].get('category')}, Priority: {notifs[0].get('priority')}]")
        
        # Test mark single as read
        notif_id = notifs[0].get("id")
        r_read = requests.put(f"{BASE_URL}/api/notifications/{notif_id}/read", headers=h)
        assert r_read.status_code == 200, f"Mark as read failed: {r_read.text}"

    print("\n[ALL TESTS PASSED SUCCESSFULLY!]")

if __name__ == "__main__":
    try:
        test_pages()
        test_api()
        sys.exit(0)
    except Exception as e:
        print(f"\n[FAIL] {e}", file=sys.stderr)
        sys.exit(1)

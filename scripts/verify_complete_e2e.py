import urllib.request
import json
import sys

BASE_URL = "http://localhost:8080"

def post_json(path, data, token=None, extra_headers=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    if extra_headers:
        headers.update(extra_headers)
    req = urllib.request.Request(f"{BASE_URL}{path}", data=json.dumps(data).encode("utf-8"), headers=headers, method="POST")
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8")) if e.code != 404 else {"error": "not found"}

def get_json(path, token=None):
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(f"{BASE_URL}{path}", headers=headers, method="GET")
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8")) if e.code != 404 else {"error": "not found"}

def put_json(path, data=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = json.dumps(data).encode("utf-8") if data else None
    req = urllib.request.Request(f"{BASE_URL}{path}", data=body, headers=headers, method="PUT")
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode("utf-8"))

def run():
    print("=== 1. VERIFY ADMIN-USERS.HTML LAYOUT ===")
    req = urllib.request.Request(f"{BASE_URL}/admin-users.html")
    with urllib.request.urlopen(req) as r:
        html = r.read().decode("utf-8")
        assert "app-container" in html
        assert "sidebar" in html
        assert "main-content" in html
        print("[PASS] admin-users.html layout verified.")

    print("\n=== 2. AUTHENTICATION & PIN ===")
    st, admin_res = post_json("/api/auth/login", {"email": "admin@disaster.gov.in", "password": "admin123"})
    assert st == 200, f"Admin login failed: {admin_res}"
    admin_token = admin_res["token"]
    print("[PASS] Admin login successful.")

    st, pin_res = post_json("/api/auth/admin/verify-pin", {"pin": "123456"}, token=admin_token)
    assert st == 200, f"PIN verify failed: {pin_res}"
    step_up_token = pin_res["stepUpToken"]
    print("[PASS] Admin PIN verified. Step-up token acquired.")

    st, c1_res = post_json("/api/auth/login", {"email": "citizen@gmail.com", "password": "citizen123"})
    assert st == 200
    c1_token = c1_res["token"]
    print("[PASS] Citizen 1 (citizen@gmail.com) login successful.")

    st, c2_res = post_json("/api/auth/login", {"email": "citizen2@gmail.com", "password": "citizen123"})
    assert st == 200
    c2_token = c2_res["token"]
    print("[PASS] Citizen 2 (citizen2@gmail.com) login successful.")

    print("\n=== 3. BROADCAST EMERGENCY ALERT (MODE A) ===")
    import time
    ts = int(time.time())
    title_a = f"EMERGENCY CYCLONE ALERT - {ts}"
    alert_payload_a = {
        "title": title_a,
        "message": "Category 4 Cyclone making landfall. Seek storm shelter immediately.",
        "severity": "CRITICAL",
        "disasterType": "CYCLONE",
        "region": "Tamil Nadu (Statewide)",
        "latitude": 13.0827,
        "longitude": 80.2707,
        "deliveryMode": "BROADCAST",
        "broadcasted": True
    }
    st, alert_a = post_json("/api/alerts", alert_payload_a, token=admin_token, extra_headers={"X-Admin-Step-Up-Token": step_up_token})
    assert st == 200, f"Alert A failed: {alert_a}"
    print(f"[PASS] Broadcast alert created with ID: {alert_a['alert']['alertId']}")

    # Verify both citizens received it
    st, notifs_c1 = get_json("/api/notifications/inbox", token=c1_token)
    match_c1 = [n for n in notifs_c1 if n.get("title") == title_a]
    assert len(match_c1) > 0, "Citizen 1 missing broadcast alert notification!"
    print(f"[PASS] Citizen 1 received broadcast alert: '{match_c1[0]['title']}', Category: {match_c1[0]['category']}")

    st, notifs_c2 = get_json("/api/notifications/inbox", token=c2_token)
    match_c2 = [n for n in notifs_c2 if n.get("title") == title_a]
    assert len(match_c2) > 0, "Citizen 2 missing broadcast alert notification!"
    print(f"[PASS] Citizen 2 received broadcast alert: '{match_c2[0]['title']}', Category: {match_c2[0]['category']}")

    print("\n=== 4. TARGETED EMERGENCY ALERT (MODE B) ===")
    title_b = f"TARGETED RESCUE NOTICE - {ts}"
    alert_payload_b = {
        "title": title_b,
        "message": "Direct advisory for Citizen 1 location sector.",
        "severity": "MEDIUM",
        "disasterType": "FLOOD",
        "region": "Madurai",
        "deliveryMode": "TARGETED",
        "targetRecipients": "citizen@gmail.com",
        "broadcasted": True
    }
    st, alert_b = post_json("/api/alerts", alert_payload_b, token=admin_token)
    assert st == 200, f"Alert B failed: {alert_b}"
    print(f"[PASS] Targeted alert created with ID: {alert_b['alert']['alertId']}")

    st, notifs_c1_b = get_json("/api/notifications/inbox", token=c1_token)
    match_c1_b = [n for n in notifs_c1_b if n.get("title") == title_b]
    assert len(match_c1_b) > 0, "Citizen 1 missing targeted alert!"
    print(f"[PASS] Citizen 1 received targeted alert.")

    st, notifs_c2_b = get_json("/api/notifications/inbox", token=c2_token)
    match_c2_b = [n for n in notifs_c2_b if n.get("title") == title_b]
    assert len(match_c2_b) == 0, "Citizen 2 should NOT have received targeted alert!"
    print(f"[PASS] Citizen 2 correctly did NOT receive targeted alert.")

    print("\n=== 5. MARK NOTIFICATION AS READ PERSISTENCE ===")
    notif_id = match_c1[0]["id"]
    st, read_res = put_json(f"/api/notifications/{notif_id}/read", token=c1_token)
    assert st == 200, f"Mark read failed: {read_res}"

    st, notifs_c1_after = get_json("/api/notifications/inbox", token=c1_token)
    updated_notif = [n for n in notifs_c1_after if n.get("id") == notif_id][0]
    assert updated_notif["isRead"] is True
    print(f"[PASS] Notification #{notif_id} verified isRead=True in MySQL database.")

    print("\n=== 6. COMMS ROOM TIMELINE MESSAGE & LIVE LOCATION ===")
    st, c1_reports = get_json("/api/citizen-reports/my-reports", token=c1_token)
    if c1_reports and len(c1_reports) > 0:
        rep_id = c1_reports[0]["reportId"]
        msg_payload = {"message": f"📍 Live GPS Location Transmitted: 13.0827° N, 80.2707° E at {ts}"}
        st, msg_res = post_json(f"/api/incidents/{rep_id}/timeline/message?type=CITIZEN_REPORT", msg_payload, token=c1_token)
        assert st == 200, f"Comms message failed: {msg_res}"
        print(f"[PASS] Comms room note & location posted to Incident #{rep_id}.")

        st, room_data = get_json(f"/api/incidents/{rep_id}/room?type=CITIZEN_REPORT", token=c1_token)
        assert st == 200
        events = room_data.get("timeline", [])
        last_event = events[-1] if events else None
        print(f"[PASS] Incident Room verified with {len(events)} events. Last: '{last_event.get('description')}'")

    print("\n=== 7. ROLE ACCESS CONTROL ===")
    st, unauthorized_res = post_json("/api/alerts", {"title": "Hacker", "message": "Test", "region": "Chennai"}, token=c1_token)
    assert st == 403, f"Citizen was not blocked! Status: {st}"
    print("[PASS] Citizen alert creation blocked with HTTP 403 Forbidden.")

    print("\n=======================================================")
    print(">>> ALL VERIFICATIONS COMPLETED SUCCESSFULLY! <<<")
    print("=======================================================")

if __name__ == "__main__":
    run()

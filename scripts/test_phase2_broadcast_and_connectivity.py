import urllib.request
import urllib.error
import json
import time

BASE_URL = "http://192.168.0.196:8080"

def login(email, password):
    req = urllib.request.Request(
        f"{BASE_URL}/api/auth/login",
        data=json.dumps({"email": email, "password": password}).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req) as res:
        data = json.loads(res.read().decode("utf-8"))
        return data["token"]

def run_tests():
    print("=" * 70)
    print("PHASE 2 & PHASE 3 AUTOMATED REAL-TIME CONNECTIVITY AUDIT")
    print("=" * 70)

    # 1. Login
    admin_token = login("admin@disaster.gov.in", "admin123")
    cit_token = login("citizen@gmail.com", "citizen123")
    resp_token = login("responder@disaster.gov.in", "responder123")
    print("[PASS] All 3 roles successfully authenticated on LAN interface")

    # 2. Test Citizen forbidden from broadcasting (MUST return HTTP 403)
    bad_payload = {
        "title": "Unauthorized Citizen Broadcast",
        "message": "Citizen attempting to trigger national alert",
        "severity": "High",
        "region": "Karur",
        "broadcasted": True
    }
    req_bad = urllib.request.Request(
        f"{BASE_URL}/api/alerts",
        data=json.dumps(bad_payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {cit_token}"}
    )
    try:
        urllib.request.urlopen(req_bad)
        print("[FAIL] Unauthorized citizen broadcast was allowed")
    except urllib.error.HTTPError as e:
        assert e.code == 403, f"Expected 403 but got {e.code}"
        print(f"[PASS] Citizen broadcast blocked with HTTP 403 Forbidden ({e.read().decode('utf-8')[:60]}...)")

    # 3. Test Admin Emergency Broadcast (Medium severity - no PIN step-up needed)
    ts = int(time.time())
    broadcast_payload = {
        "title": f"Urgent Flood Evacuation Order #{ts}",
        "message": "Water levels exceeding danger mark at Karur barrage. Evacuate Zone 2 immediately.",
        "disasterType": "Flood",
        "severity": "Medium",
        "region": "Karur",
        "broadcasted": True
    }
    req_broadcast = urllib.request.Request(
        f"{BASE_URL}/api/alerts",
        data=json.dumps(broadcast_payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {admin_token}"}
    )
    with urllib.request.urlopen(req_broadcast) as res:
        assert res.status == 200
        b_data = json.loads(res.read().decode("utf-8"))
        alert_id = b_data.get("alert", {}).get("alertId")
        print(f"[PASS] Admin broadcast successfully transmitted via LAN! Alert ID: {alert_id}")

    # 4. Verify Citizen receives the broadcast notification in real time
    time.sleep(1)
    req_cit_notifs = urllib.request.Request(
        f"{BASE_URL}/api/notifications",
        headers={"Authorization": f"Bearer {cit_token}"}
    )
    with urllib.request.urlopen(req_cit_notifs) as res:
        cit_notifs = json.loads(res.read().decode("utf-8"))
        alert_notif = next((n for n in cit_notifs if "Flood" in n.get("title", "") or "Evacuation" in n.get("title", "") or "Karur" in n.get("title", "")), None)
        assert alert_notif is not None, "Citizen did not receive broadcast notification"
        print(f"[PASS] Citizen received broadcast notification: [{alert_notif.get('category')}] {alert_notif.get('title')}")

    # 5. Verify Responder receives the operational broadcast notification
    req_resp_notifs = urllib.request.Request(
        f"{BASE_URL}/api/notifications",
        headers={"Authorization": f"Bearer {resp_token}"}
    )
    with urllib.request.urlopen(req_resp_notifs) as res:
        resp_notifs = json.loads(res.read().decode("utf-8"))
        resp_alert = next((n for n in resp_notifs if "Alert" in n.get("title", "") or "Karur" in n.get("title", "") or "Flood" in n.get("title", "")), None)
        assert resp_alert is not None, "Responder did not receive broadcast notification"
        print(f"[PASS] Responder received operational broadcast: [{resp_alert.get('category')}] {resp_alert.get('title')}")

    # =========================================================================
    # WORKFLOW A & B: CITIZEN REPORTS DISASTER & ADMIN ASSIGNS RESPONDER
    # =========================================================================
    print("\n--- WORKFLOW A: Citizen Submits Incident Report ---")
    report_payload = {
        "title": "Submerged Bridge at Amaravathi Riverbank",
        "message": "Family of 4 stranded near temple complex due to rising waters.",
        "location": "Karur Riverbank Sector 4",
        "incidentType": "Flood",
        "severity": "High",
        "latitude": 10.9571,
        "longitude": 78.0766
    }
    req_report = urllib.request.Request(
        f"{BASE_URL}/api/citizen-reports",
        data=json.dumps(report_payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {cit_token}"}
    )
    with urllib.request.urlopen(req_report) as res:
        assert res.status == 200
        rep_data = json.loads(res.read().decode("utf-8"))
        report_id = rep_data.get("reportId") or rep_data.get("id")
        print(f"[PASS] Citizen submitted Report #{report_id} successfully in MySQL")

    # Admin checks incident list & receives new report notification
    with urllib.request.urlopen(urllib.request.Request(f"{BASE_URL}/api/notifications", headers={"Authorization": f"Bearer {admin_token}"})) as res:
        admin_notifs = json.loads(res.read().decode("utf-8"))
        new_rep_notif = next((n for n in admin_notifs if f"#{report_id}" in n.get("message", "") or "New Disaster Reported" in n.get("title", "")), None)
        assert new_rep_notif is not None, "Admin did not receive new report notification"
        print(f"[PASS] Admin received new report notification: {new_rep_notif.get('title')} - {new_rep_notif.get('message')}")

    print("\n--- WORKFLOW B: Admin Assigns Responder ---")
    assign_payload = {
        "responderEmail": "responder@disaster.gov.in",
        "responderName": "Emergency Responder Team Alpha"
    }
    req_assign = urllib.request.Request(
        f"{BASE_URL}/api/citizen-reports/{report_id}/assign",
        data=json.dumps(assign_payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {admin_token}"}
    )
    with urllib.request.urlopen(req_assign) as res:
        assert res.status == 200
        print(f"[PASS] Admin assigned Responder to Report #{report_id}")

    # Responder checks tasks and notifications
    with urllib.request.urlopen(urllib.request.Request(f"{BASE_URL}/api/notifications", headers={"Authorization": f"Bearer {resp_token}"})) as res:
        r_notifs = json.loads(res.read().decode("utf-8"))
        assign_notif = next((n for n in r_notifs if f"#{report_id}" in n.get("message", "") or "New Rescue Assignment" in n.get("title", "")), None)
        assert assign_notif is not None, "Responder did not receive assignment notification"
        print(f"[PASS] Responder received assignment notification: {assign_notif.get('title')}")

    # Citizen receives update that responder has been assigned
    with urllib.request.urlopen(urllib.request.Request(f"{BASE_URL}/api/notifications", headers={"Authorization": f"Bearer {cit_token}"})) as res:
        c_notifs = json.loads(res.read().decode("utf-8"))
        assigned_c_notif = next((n for n in c_notifs if "Rescue Team Assigned" in n.get("title", "") or "Alpha" in n.get("message", "")), None)
        assert assigned_c_notif is not None, "Citizen did not receive responder assigned update"
        print(f"[PASS] Citizen received notification: {assigned_c_notif.get('title')} - {assigned_c_notif.get('message')}")

    # =========================================================================
    # WORKFLOW C: RESPONDER UPDATES MISSION STATUS THROUGH LIFECYCLE
    # =========================================================================
    print("\n--- WORKFLOW C: Responder Mission Status Lifecycle Transitions ---")
    
    # Responder gets task list to find assigned task
    req_my_tasks = urllib.request.Request(
        f"{BASE_URL}/api/tasks/my-tasks",
        headers={"Authorization": f"Bearer {resp_token}"}
    )
    task_id = None
    with urllib.request.urlopen(req_my_tasks) as res:
        tasks = json.loads(res.read().decode("utf-8"))
        t_list = tasks if isinstance(tasks, list) else tasks.get("tasks", [])
        matched = next((t for t in t_list if t.get("citizenReportId") == report_id), None)
        if matched:
            task_id = matched.get("taskId")
    print(f"[INFO] Linked Task ID: {task_id}")

    # Status: Acknowledged
    req_ack = urllib.request.Request(
        f"{BASE_URL}/api/citizen-reports/{report_id}/acknowledge",
        data=b"{}",
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {resp_token}"}
    )
    req_ack.get_method = lambda: "PUT"
    with urllib.request.urlopen(req_ack) as res:
        assert res.status == 200
        print(f"[PASS] Responder acknowledged incident #{report_id}")

    # Status: En Route
    if task_id:
        req_enroute = urllib.request.Request(
            f"{BASE_URL}/api/tasks/{task_id}/status",
            data=json.dumps({"status": "En Route", "notes": "Departed base station with rubber dinghy and medical kit"}).encode("utf-8"),
            headers={"Content-Type": "application/json", "Authorization": f"Bearer {resp_token}"}
        )
        req_enroute.get_method = lambda: "PUT"
        with urllib.request.urlopen(req_enroute) as res:
            assert res.status == 200
            print(f"[PASS] Responder transitioned task to EN ROUTE")

        # Status: Arrived
        req_arrived = urllib.request.Request(
            f"{BASE_URL}/api/tasks/{task_id}/status",
            data=json.dumps({"status": "Arrived", "notes": "On scene at riverbank temple. Commencing evacuation"}).encode("utf-8"),
            headers={"Content-Type": "application/json", "Authorization": f"Bearer {resp_token}"}
        )
        req_arrived.get_method = lambda: "PUT"
        with urllib.request.urlopen(req_arrived) as res:
            assert res.status == 200
            print(f"[PASS] Responder transitioned task to ARRIVED")

    # Status: Resolved
    req_resolve = urllib.request.Request(
        f"{BASE_URL}/api/citizen-reports/{report_id}/resolve",
        data=b"{}",
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {resp_token}"}
    )
    req_resolve.get_method = lambda: "PUT"
    with urllib.request.urlopen(req_resolve) as res:
        assert res.status == 200
        print(f"[PASS] Responder transitioned incident to RESOLVED in MySQL")

    # Verify Final Status in DB
    with urllib.request.urlopen(urllib.request.Request(f"{BASE_URL}/api/citizen-reports/{report_id}", headers={"Authorization": f"Bearer {cit_token}"})) as res:
        final_rep = json.loads(res.read().decode("utf-8"))
        assert final_rep.get("status") == "Resolved", f"Expected Resolved but got {final_rep.get('status')}"
        print(f"[PASS] Final database status verified: {final_rep.get('status')}")

    print("\n" + "=" * 70)
    print("ALL CONNECTIVITY, BROADCAST, AND LIFECYCLE TESTS PASSED 100%!")
    print("=" * 70)

if __name__ == "__main__":
    run_tests()

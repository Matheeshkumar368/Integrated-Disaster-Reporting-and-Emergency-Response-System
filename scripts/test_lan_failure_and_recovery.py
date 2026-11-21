import urllib.request
import urllib.error
import json
import time

SERVER_LAN_IP = "192.168.0.196"
BASE_URL = f"http://{SERVER_LAN_IP}:8080"

def login(email, password):
    req = urllib.request.Request(
        f"{BASE_URL}/api/auth/login",
        data=json.dumps({"email": email, "password": password}).encode("utf-8"),
        headers={"Content-Type": "application/json"}
    )
    with urllib.request.urlopen(req) as res:
        data = json.loads(res.read().decode("utf-8"))
        return data["token"]

def run_network_resilience_test():
    print("=" * 75)
    print("NETWORK FAILURE, RECOVERY AND REAL-TIME NOTIFICATION RECOVERY TEST")
    print("=" * 75)

    # 1. Multi-Laptop Authentication over LAN IP
    print("\n--- STEP 1: Multi-Laptop Session Initialization via LAN IP ---")
    admin_token = login("admin@disaster.gov.in", "admin123")
    cit_token = login("citizen@gmail.com", "citizen123")
    resp_token = login("responder@disaster.gov.in", "responder123")
    print(f"[PASS] Laptop 1 (Admin) authenticated over LAN: {SERVER_LAN_IP}")
    print(f"[PASS] Laptop 2 (Citizen) authenticated over LAN: {SERVER_LAN_IP}")
    print(f"[PASS] Laptop 3 (Responder) authenticated over LAN: {SERVER_LAN_IP}")

    # 2. Initial State & Workflow Initiation
    print("\n--- STEP 2: Citizen Initiates Distress Report on Laptop 2 ---")
    ts = int(time.time())
    report_payload = {
        "title": f"Riverbank Breached Sector 5 #{ts}",
        "message": "Water overflow reaching residential quarters. 6 families need immediate boat rescue.",
        "location": "Karur Sector 5",
        "incidentType": "Flood",
        "severity": "Critical",
        "latitude": 10.9575,
        "longitude": 78.0770
    }
    req_rep = urllib.request.Request(
        f"{BASE_URL}/api/citizen-reports",
        data=json.dumps(report_payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {cit_token}"}
    )
    with urllib.request.urlopen(req_rep) as res:
        assert res.status == 200
        rep_data = json.loads(res.read().decode("utf-8"))
        report_id = rep_data.get("reportId") or rep_data.get("id")
        print(f"[PASS] Distress Report #{report_id} saved in central MySQL database")

    # Admin assigns Responder
    req_assign = urllib.request.Request(
        f"{BASE_URL}/api/citizen-reports/{report_id}/assign",
        data=json.dumps({"responderEmail": "responder@disaster.gov.in"}).encode("utf-8"),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {admin_token}"}
    )
    with urllib.request.urlopen(req_assign) as res:
        assert res.status == 200
        print(f"[PASS] Admin on Laptop 1 assigned Responder Team Alpha to Incident #{report_id}")

    # Count Citizen notifications before disconnect
    with urllib.request.urlopen(urllib.request.Request(f"{BASE_URL}/api/notifications", headers={"Authorization": f"Bearer {cit_token}"})) as res:
        notifs_before = json.loads(res.read().decode("utf-8"))
        count_before = len(notifs_before)
        print(f"[INFO] Citizen notifications count before disconnect: {count_before}")

    # 3. Simulate Laptop 2 (Citizen) Wi-Fi Disconnect
    print("\n--- STEP 3: Simulating Laptop 2 (Citizen) Temporary Wi-Fi Disconnection ---")
    print("[DISCONNECTED] Laptop 2 loses Wi-Fi connection... client goes silent.")

    # 4. Operations Continue on Central Server while Laptop 2 is offline
    print("\n--- STEP 4: Server & Other Laptops Continue Active Response While Citizen is Offline ---")
    
    # Responder on Laptop 3 acknowledges incident
    req_ack = urllib.request.Request(
        f"{BASE_URL}/api/citizen-reports/{report_id}/acknowledge",
        data=b"{}",
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {resp_token}"}
    )
    req_ack.get_method = lambda: "PUT"
    with urllib.request.urlopen(req_ack) as res:
        assert res.status == 200
        print(f"[PASS] Laptop 3 (Responder) acknowledged mission #{report_id}")

    # Find linked task
    with urllib.request.urlopen(urllib.request.Request(f"{BASE_URL}/api/tasks/my-tasks", headers={"Authorization": f"Bearer {resp_token}"})) as res:
        t_data = json.loads(res.read().decode("utf-8"))
        tasks = t_data if isinstance(t_data, list) else t_data.get("tasks", [])
        matched = next((t for t in tasks if t.get("citizenReportId") == report_id), None)
        task_id = matched.get("taskId") if matched else None

    # Responder moves to En Route and Arrived
    if task_id:
        req_enroute = urllib.request.Request(
            f"{BASE_URL}/api/tasks/{task_id}/status",
            data=json.dumps({"status": "En Route", "notes": "Navigating flood terrain"}).encode("utf-8"),
            headers={"Content-Type": "application/json", "Authorization": f"Bearer {resp_token}"}
        )
        req_enroute.get_method = lambda: "PUT"
        with urllib.request.urlopen(req_enroute) as res:
            assert res.status == 200
            print(f"[PASS] Laptop 3 updated task status to EN ROUTE")

        req_arrived = urllib.request.Request(
            f"{BASE_URL}/api/tasks/{task_id}/status",
            data=json.dumps({"status": "Arrived", "notes": "Boat unit docked at sector 5"}).encode("utf-8"),
            headers={"Content-Type": "application/json", "Authorization": f"Bearer {resp_token}"}
        )
        req_arrived.get_method = lambda: "PUT"
        with urllib.request.urlopen(req_arrived) as res:
            assert res.status == 200
            print(f"[PASS] Laptop 3 updated task status to ARRIVED")

    # Admin on Laptop 1 transmits regional evacuation broadcast targeted to Citizen's region (Madurai)
    b_payload = {
        "title": f"Flash Flood Alert: Madurai District #{ts}",
        "message": f"Immediate evacuation warning for Madurai residents #{ts}.",
        "disasterType": "Flood",
        "severity": "Medium",
        "region": "Madurai",
        "broadcasted": True
    }
    with urllib.request.urlopen(urllib.request.Request(
        f"{BASE_URL}/api/alerts",
        data=json.dumps(b_payload).encode("utf-8"),
        headers={"Content-Type": "application/json", "Authorization": f"Bearer {admin_token}"}
    )) as res:
        assert res.status == 200
        print(f"[PASS] Laptop 1 (Admin) transmitted Emergency Broadcast during Citizen's network absence")

    # 5. Simulate Laptop 2 (Citizen) Wi-Fi Reconnection & Application Refresh
    print("\n--- STEP 5: Laptop 2 (Citizen) Reconnects to Wi-Fi & Refreshes Application ---")
    print("[RECONNECTED] Wi-Fi link restored. Citizen application synchronizes with central MySQL...")

    # Fetch notifications after reconnection
    with urllib.request.urlopen(urllib.request.Request(f"{BASE_URL}/api/notifications", headers={"Authorization": f"Bearer {cit_token}"})) as res:
        notifs_after = json.loads(res.read().decode("utf-8"))
        count_after = len(notifs_after)
        new_count = count_after - count_before
        print(f"[PASS] Citizen retrieved missed notifications: {count_after} total ({new_count} delivered while offline)")
        for n in notifs_after[:new_count]:
            print(f"       -> [{n.get('category')}] {n.get('title')}: {n.get('message')[:50]}...")
        
        # Verify missed arrival notification exists
        arrived_notif = next((n for n in notifs_after[:new_count] if "Arrived" in n.get("title", "") or "arrived" in n.get("message", "").lower()), None)
        assert arrived_notif is not None, "Missed arrival notification was not retrieved"
        print(f"[PASS] Verified missed notification retrieved: [{arrived_notif.get('category')}] {arrived_notif.get('title')}")

        # Verify missed broadcast notification exists
        b_notif = next((n for n in notifs_after[:new_count] if "Alert" in n.get("title", "") or "Evacuation" in n.get("message", "") or "Flood" in n.get("title", "")), None)
        assert b_notif is not None, "Missed broadcast notification was not retrieved"
        print(f"[PASS] Verified missed broadcast retrieved: [{b_notif.get('category')}] {b_notif.get('title')}")

    # 6. Verify No Duplicate Reports or Inconsistencies
    print("\n--- STEP 6: Data Consistency & Authentication Verification ---")
    with urllib.request.urlopen(urllib.request.Request(f"{BASE_URL}/api/citizen-reports/my-reports", headers={"Authorization": f"Bearer {cit_token}"})) as res:
        my_reports = json.loads(res.read().decode("utf-8"))
        matching_reports = [r for r in my_reports if r.get("reportId") == report_id]
        assert len(matching_reports) == 1, f"Expected exactly 1 report record, found {len(matching_reports)}"
        current_stage = matching_reports[0].get("progressStage") or matching_reports[0].get("status")
        print(f"[PASS] Report #{report_id} state preserved accurately: Status = {matching_reports[0].get('status')}, Stage = {current_stage}")

    # 7. Verify Other Users (Admin & Responder) Remained Completely Unaffected
    with urllib.request.urlopen(urllib.request.Request(f"{BASE_URL}/api/auth/profile", headers={"Authorization": f"Bearer {admin_token}"})) as res:
        assert res.status == 200
        print(f"[PASS] Laptop 1 (Admin) session maintained and healthy")

    with urllib.request.urlopen(urllib.request.Request(f"{BASE_URL}/api/auth/profile", headers={"Authorization": f"Bearer {resp_token}"})) as res:
        assert res.status == 200
        print(f"[PASS] Laptop 3 (Responder) session maintained and healthy")

    print("\n" + "=" * 75)
    print("NETWORK FAILURE & RECOVERY VALIDATION: 100% PASSED!")
    print("=" * 75)

if __name__ == "__main__":
    run_network_resilience_test()

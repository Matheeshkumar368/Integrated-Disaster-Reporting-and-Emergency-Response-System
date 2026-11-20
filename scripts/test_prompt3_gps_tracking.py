"""
Test Suite: PROMPT 3 - Real-Time GPS Tracking & Emergency Response Monitoring
End-to-End Three-Role GPS Integration Verification
"""

import requests
import json
import time
import sys

BASE_URL = "http://localhost:8080"

def log_step(msg):
    print(f"\n[STEP] {msg}")

def assert_true(cond, msg):
    if cond:
        print(f"  [PASS] {msg}")
    else:
        print(f"  [FAIL] {msg}")
        sys.exit(1)

def main():
    print("=" * 70)
    print("PROMPT 3: REAL-TIME GPS TRACKING & MONITORING INTEGRATION TEST")
    print("=" * 70)

    # 1. Login all roles
    log_step("1. Authenticating Citizen, Admin, and Responder")
    
    # Citizen
    res = requests.post(f"{BASE_URL}/api/auth/login", json={"email": "citizen@gmail.com", "password": "citizen123"})
    assert_true(res.status_code == 200, f"Citizen login (HTTP {res.status_code})")
    citizen_token = res.json()["token"]

    # Admin
    res = requests.post(f"{BASE_URL}/api/auth/login", json={"email": "admin@disaster.gov.in", "password": "admin123"})
    assert_true(res.status_code == 200, f"Admin login (HTTP {res.status_code})")
    admin_token = res.json()["token"]

    # Responder
    res = requests.post(f"{BASE_URL}/api/auth/login", json={"email": "responder@disaster.gov.in", "password": "responder123"})
    assert_true(res.status_code == 200, f"Responder login (HTTP {res.status_code})")
    responder_token = res.json()["token"]

    # Another citizen for unauthorized privacy check
    res = requests.post(f"{BASE_URL}/api/auth/login", json={"email": "citizen2@gmail.com", "password": "citizen123"})
    if res.status_code != 200:
        requests.post(f"{BASE_URL}/api/auth/register", json={
            "name": "Priya Sharma",
            "email": "citizen2@gmail.com",
            "password": "citizen123",
            "role": "Citizen",
            "phone": "9876543219"
        })
        res = requests.post(f"{BASE_URL}/api/auth/login", json={"email": "citizen2@gmail.com", "password": "citizen123"})
    assert_true(res.status_code == 200, "Secondary Citizen authenticated for privacy audit")
    other_citizen_token = res.json()["token"]

    # 2. Citizen files a disaster report with real GPS coordinates (Karur: 10.9601, 78.0766)
    log_step("2. Citizen submits disaster report with real GPS coordinates")
    report_payload = {
        "title": "Severe Flash Flood - Amaravathi Riverbank",
        "message": "Water levels rising rapidly near Karur bridge. 3 families need evacuation assistance.",
        "location": "Amaravathi Riverbank, Karur, Tamil Nadu",
        "incidentType": "Flood",
        "severity": "High",
        "latitude": 10.9601,
        "longitude": 78.0766,
        "accuracy": 8.5
    }
    res = requests.post(f"{BASE_URL}/api/citizen-reports", 
                        headers={"Authorization": f"Bearer {citizen_token}"},
                        json=report_payload)
    assert_true(res.status_code == 200 or res.status_code == 201, f"Report created (HTTP {res.status_code})")
    report_data = res.json()
    report_id = report_data.get("reportId") or report_data.get("id")
    print(f"  Citizen Report ID: #{report_id}")
    assert_true(report_id is not None, "Report ID exists")

    # 3. Admin verifies report and assigns responder
    log_step("3. Admin assigns responder to incident report")
    assign_payload = {"responderEmail": "responder@disaster.gov.in"}
    res = requests.put(f"{BASE_URL}/api/citizen-reports/{report_id}/assign?responderEmail=responder@disaster.gov.in",
                       headers={"Authorization": f"Bearer {admin_token}"},
                       json=assign_payload)
    assert_true(res.status_code == 200, f"Responder assigned to report (HTTP {res.status_code})")
    assigned_data = res.json()
    task_id = assigned_data.get("rescueTaskId") or (assigned_data.get("report") or {}).get("rescueTaskId")
    if not task_id:
        r_rep = requests.get(f"{BASE_URL}/api/citizen-reports/{report_id}", headers={"Authorization": f"Bearer {admin_token}"}).json()
        task_id = r_rep.get("rescueTaskId")
    print(f"  Linked Rescue Task ID: #{task_id}")
    assert_true(task_id is not None, "Task ID generated")

    # 4. Test Coordinate Validation (Rejection of invalid bounds and 0.0, 0.0)
    log_step("4. Testing coordinate validation on GPS endpoints")
    
    # Invalid lat > 90
    bad_res = requests.post(f"{BASE_URL}/api/tracking/start",
                            headers={"Authorization": f"Bearer {responder_token}"},
                            json={"assignmentId": task_id, "latitude": 95.0, "longitude": 78.0, "accuracy": 5.0})
    assert_true(bad_res.status_code == 400, f"Out-of-bounds latitude (+95.0) rejected with 400 Bad Request (HTTP {bad_res.status_code})")

    # Invalid lng > 180
    bad_res2 = requests.post(f"{BASE_URL}/api/tracking/update",
                             headers={"Authorization": f"Bearer {responder_token}"},
                             json={"assignmentId": task_id, "latitude": 10.96, "longitude": 185.0, "accuracy": 5.0})
    assert_true(bad_res2.status_code == 400, f"Out-of-bounds longitude (+185.0) rejected with 400 Bad Request (HTTP {bad_res2.status_code})")

    # Null Island (0.0, 0.0)
    bad_res3 = requests.post(f"{BASE_URL}/api/responder/location/start",
                             headers={"Authorization": f"Bearer {responder_token}"},
                             json={"taskId": task_id, "latitude": 0.0, "longitude": 0.0, "accuracy": 10.0})
    assert_true(bad_res3.status_code == 400, f"Null Island (0.0, 0.0) rejected with 400 Bad Request (HTTP {bad_res3.status_code})")

    # 5. Responder starts live GPS telemetry broadcast
    log_step("5. Responder activates live GPS broadcast with genuine coordinates")
    # Coordinates starting ~3.5 km away from incident (e.g. Karur Depot: 10.9850, 78.0850)
    start_payload = {
        "taskId": task_id,
        "latitude": 10.9850,
        "longitude": 78.0850,
        "accuracy": 6.2
    }
    res = requests.post(f"{BASE_URL}/api/responder/location/start",
                        headers={"Authorization": f"Bearer {responder_token}"},
                        json=start_payload)
    assert_true(res.status_code == 200, f"Live GPS broadcast started (HTTP {res.status_code})")
    start_resp = res.json()
    assert_true(start_resp.get("status") == "success", "Status is success")
    assert_true(start_resp.get("sharingStatus") == "ACTIVE", "Sharing status is ACTIVE")

    # 6. Admin Mission Control queries active responders
    log_step("6. Admin Mission Control monitors active responders on radar")
    res = requests.get(f"{BASE_URL}/api/tracking/active",
                       headers={"Authorization": f"Bearer {admin_token}"})
    assert_true(res.status_code == 200, f"Admin queried /api/tracking/active (HTTP {res.status_code})")
    active_sessions = res.json()
    assert_true(len(active_sessions) >= 1, f"Found {len(active_sessions)} active tracking session(s)")
    
    # Locate our task's session
    our_session = next((s for s in active_sessions if s.get("assignmentId") == task_id), None)
    assert_true(our_session is not None, f"Mission #TASK-{task_id} present on Admin radar")
    print(f"  Admin Telemetry: Unit={our_session.get('responderName')}, Lat={our_session.get('latitude')}, Lng={our_session.get('longitude')}, OperationStatus={our_session.get('operationStatus')}")
    assert_true(our_session.get("isStale") == False, "Initial GPS fix is fresh (not stale)")

    # 7. Citizen tracks assigned responder
    log_step("7. Citizen queries live telemetry for their emergency report")
    res = requests.get(f"{BASE_URL}/api/tracking/latest/{report_id}",
                       headers={"Authorization": f"Bearer {citizen_token}"})
    assert_true(res.status_code == 200, f"Citizen retrieved tracking via reportId #{report_id} (HTTP {res.status_code})")
    citizen_telemetry = res.json()
    assert_true(citizen_telemetry.get("assignmentId") == task_id, "Telemetry correctly resolved to linked RescueTask")
    assert_true(citizen_telemetry.get("status") == "ACTIVE", "Telemetry indicates ACTIVE")
    print(f"  Citizen View: Status={citizen_telemetry.get('operationStatus')}, DistanceKm={citizen_telemetry.get('distanceKm')}, StraightLine={citizen_telemetry.get('isStraightLineEstimate')}")
    assert_true(citizen_telemetry.get("distanceKm") is not None or citizen_telemetry.get("distanceMeters") is not None, "Distance estimate is provided")

    # 8. Privacy & Authorization Audit: Unrelated citizen access denied
    log_step("8. Security Audit: Verifying that unrelated citizen cannot track this responder")
    unauth_res = requests.get(f"{BASE_URL}/api/tracking/latest/{report_id}",
                              headers={"Authorization": f"Bearer {other_citizen_token}"})
    assert_true(unauth_res.status_code == 403, f"Unrelated citizen blocked with 403 Forbidden (HTTP {unauth_res.status_code})")
    print(f"  Security Response: {unauth_res.json().get('message')}")

    # Also test /active blocked for non-admin/non-responder
    citizen_active_res = requests.get(f"{BASE_URL}/api/tracking/active",
                                      headers={"Authorization": f"Bearer {other_citizen_token}"})
    assert_true(citizen_active_res.status_code == 403, f"Citizen blocked from querying all active responder positions (HTTP {citizen_active_res.status_code})")

    # 9. Responder transmits waypoint update (moving closer to incident)
    log_step("9. Responder transmits waypoint update along response route")
    update_payload = {
        "taskId": task_id,
        "latitude": 10.9720,
        "longitude": 78.0805,
        "accuracy": 4.8,
        "heading": 210.0,
        "speed": 11.5 # ~41 km/h
    }
    res = requests.post(f"{BASE_URL}/api/responder/location/update",
                        headers={"Authorization": f"Bearer {responder_token}"},
                        json=update_payload)
    assert_true(res.status_code == 200, f"GPS waypoint transmitted (HTTP {res.status_code})")

    # 10. Verify update received by Citizen & Admin
    log_step("10. Verifying updated position in telemetry")
    res = requests.get(f"{BASE_URL}/api/tracking/latest/{task_id}",
                       headers={"Authorization": f"Bearer {citizen_token}"})
    updated_telemetry = res.json()
    assert_true(abs(updated_telemetry.get("latitude") - 10.9720) < 0.0001, "Latitude updated to 10.9720")
    assert_true(abs(updated_telemetry.get("longitude") - 78.0805) < 0.0001, "Longitude updated to 78.0805")

    # 11. Check Location History Breadcrumbs API
    log_step("11. Retrieving location history breadcrumbs")
    res = requests.get(f"{BASE_URL}/api/tracking/history/{task_id}",
                       headers={"Authorization": f"Bearer {admin_token}"})
    assert_true(res.status_code == 200, f"History retrieved (HTTP {res.status_code})")
    breadcrumbs = res.json()
    assert_true(len(breadcrumbs) >= 2, f"Found {len(breadcrumbs)} recorded GPS breadcrumbs")
    print(f"  Breadcrumbs recorded: {len(breadcrumbs)} points")

    # 12. Operational progression: Accepted -> En Route -> Arrived -> In Progress
    log_step("12. Operational progression: Accepted -> En Route -> Arrived -> In Progress")
    
    # 12a. Accepted
    res = requests.put(f"{BASE_URL}/api/tasks/{task_id}/status",
                       headers={"Authorization": f"Bearer {responder_token}"},
                       json={"status": "Accepted"})
    assert_true(res.status_code == 200, f"Updated to Accepted (HTTP {res.status_code})")

    # 12b. En Route
    res = requests.put(f"{BASE_URL}/api/tasks/{task_id}/status",
                       headers={"Authorization": f"Bearer {responder_token}"},
                       json={"status": "En Route"})
    assert_true(res.status_code == 200, f"Updated to En Route (HTTP {res.status_code})")

    # 12c. Arrived
    res = requests.put(f"{BASE_URL}/api/tasks/{task_id}/status",
                       headers={"Authorization": f"Bearer {responder_token}"},
                       json={"status": "Arrived"})
    assert_true(res.status_code == 200, f"Updated to Arrived (HTTP {res.status_code})")

    # 12d. In Progress
    res = requests.put(f"{BASE_URL}/api/tasks/{task_id}/status",
                       headers={"Authorization": f"Bearer {responder_token}"},
                       json={"status": "In Progress"})
    assert_true(res.status_code == 200, f"Updated to In Progress (HTTP {res.status_code})")

    # Check that operationStatus reflects IN_PROGRESS
    res = requests.get(f"{BASE_URL}/api/tracking/latest/{task_id}",
                       headers={"Authorization": f"Bearer {citizen_token}"})
    prog_telemetry = res.json()
    print(f"  Progression Telemetry Status: {prog_telemetry.get('operationStatus')}")
    assert_true(prog_telemetry.get("operationStatus") == "IN_PROGRESS", "OperationStatus is IN_PROGRESS")

    # 13. Responder completes rescue mission
    log_step("13. Responder marks mission Completed")
    res = requests.put(f"{BASE_URL}/api/tasks/{task_id}/status",
                       headers={"Authorization": f"Bearer {responder_token}"},
                       json={"status": "Completed", "notes": "All families safely evacuated to relief camp."})
    assert_true(res.status_code == 200, "Mission marked as Completed")

    # 14. Verify GPS tracking has automatically concluded
    log_step("14. Verifying tracking session has concluded appropriately")
    time.sleep(1) # Allow async broadcast and cache sync
    
    res = requests.get(f"{BASE_URL}/api/tracking/active",
                       headers={"Authorization": f"Bearer {admin_token}"})
    active_after = res.json()
    our_session_after = next((s for s in active_after if s.get("assignmentId") == task_id), None)
    assert_true(our_session_after is None, "Completed mission is no longer in active radar")

    # Citizen telemetry check
    res = requests.get(f"{BASE_URL}/api/tracking/latest/{task_id}",
                       headers={"Authorization": f"Bearer {citizen_token}"})
    final_telemetry = res.json()
    assert_true(final_telemetry.get("status") == "COMPLETED" or final_telemetry.get("sharingStatus") == "STOPPED",
                f"Final telemetry indicates COMPLETED/STOPPED (status={final_telemetry.get('status')})")
    assert_true("stopped" in final_telemetry.get("displayNotice", "").lower(), 
                f"Display notice states stopped: '{final_telemetry.get('displayNotice')}'")

    print("\n" + "=" * 70)
    print("ALL 14 E2E GPS TRACKING & MONITORING AUDIT STEPS PASSED SUCCESSFULLY!")
    print("=" * 70)

if __name__ == "__main__":
    main()

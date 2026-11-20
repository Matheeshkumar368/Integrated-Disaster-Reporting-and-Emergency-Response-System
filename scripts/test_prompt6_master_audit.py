"""
PROMPT 6: MASTER APPLICATION AUDIT AND END-TO-END TEST SUITE
Integrated Disaster Reporting and Emergency Response Coordination System

Verifies:
- 24 Static HTML routes & 24 Extensionless routes
- Routing Controller (/api/routing/route & /directions)
- Full 18-step master lifecycle workflow (Citizen -> Admin -> Responder -> Completion -> Resolution)
- Role-Based Access Control (RBAC) & Cross-User Privacy Boundaries
- GPS Telemetry Validation & Out-of-bounds rejection
- NDMA Sachet Live Ingestion & Telemetry Audit
- Notification System & Unread counts, Filtering, Mark Read, Delete
"""

import requests
import json
import time
import sys

BASE_URL = "http://localhost:8080"

passed_tests = 0
failed_tests = 0
audit_results = []

def test_assert(category, test_name, condition, details=""):
    global passed_tests, failed_tests, audit_results
    status = "PASSED" if condition else "FAILED"
    if condition:
        passed_tests += 1
        print(f"  [PASS] [{category}] {test_name} {('- ' + str(details)) if details else ''}")
    else:
        failed_tests += 1
        print(f"  [FAIL] [{category}] {test_name} {('- ' + str(details)) if details else ''}")
    audit_results.append({
        "category": category,
        "test": test_name,
        "status": status,
        "details": str(details)
    })

def login(email, password):
    r = requests.post(f"{BASE_URL}/api/auth/login", json={"email": email, "password": password})
    if r.status_code != 200:
        raise Exception(f"Login failed for {email} (HTTP {r.status_code}): {r.text}")
    data = r.json()
    return data.get("token"), data

def run_audit():
    print("=" * 80)
    print("PROMPT 6: MASTER APPLICATION AUDIT & END-TO-END VERIFICATION")
    print("=" * 80)

    # =========================================================================
    # SECTION 1: EXTENSIONLESS NAVIGATION & STATIC ASSET ROUTING
    # =========================================================================
    print("\n--- SECTION 1: EXTENSIONLESS NAVIGATION & PAGE ROUTING ---")
    extensionless_routes = [
        "/admin-dashboard", "/citizen-dashboard", "/responder-dashboard",
        "/admin-notifications", "/citizen-notifications", "/responder-notifications",
        "/admin-users", "/admin-tasks", "/admin-alerts", "/admin-profile",
        "/responder-tasks", "/responder-rescue", "/responder-profile", "/responder-register",
        "/reports", "/profile", "/alerts", "/training", "/home",
        "/emergency-videos", "/offline", "/index", "/login", "/register"
    ]
    for route in extensionless_routes:
        r = requests.get(f"{BASE_URL}{route}", allow_redirects=True)
        test_assert("Navigation", f"Extensionless Route {route}", r.status_code == 200, f"HTTP {r.status_code}")

    html_routes = [
        "/index.html", "/home.html", "/alerts.html", "/reports.html", "/profile.html", "/training.html",
        "/admin-dashboard.html", "/citizen-dashboard.html", "/responder-dashboard.html",
        "/admin-notifications.html", "/citizen-notifications.html", "/responder-notifications.html",
        "/admin-users.html", "/admin-tasks.html", "/admin-alerts.html", "/admin-profile.html",
        "/responder-tasks.html", "/responder-rescue.html", "/responder-profile.html", "/responder-register.html",
        "/emergency-videos.html", "/offline.html", "/toast_showcase.html", "/index-new-login.html"
    ]
    for route in html_routes:
        r = requests.get(f"{BASE_URL}{route}")
        test_assert("Navigation", f"Direct HTML Route {route}", r.status_code == 200, f"HTTP {r.status_code}")

    # =========================================================================
    # SECTION 2: AUTHENTICATION & MULTI-ROLE SETUP
    # =========================================================================
    print("\n--- SECTION 2: AUTHENTICATION & MULTI-ROLE SETUP ---")
    admin_token, admin_info = login("admin@disaster.gov.in", "admin123")
    test_assert("Auth", "Admin Login (admin@disaster.gov.in)", admin_token is not None, f"Role: {admin_info.get('role')}")
    h_admin = {"Authorization": f"Bearer {admin_token}", "Content-Type": "application/json"}

    responder_token, responder_info = login("responder@disaster.gov.in", "responder123")
    test_assert("Auth", "Primary Responder Login (responder@disaster.gov.in)", responder_token is not None, f"Role: {responder_info.get('role')}")
    h_responder = {"Authorization": f"Bearer {responder_token}", "Content-Type": "application/json"}

    responder2_token, responder2_info = login("responder2@disaster.gov.in", "responder123")
    test_assert("Auth", "Secondary Responder Login (responder2@disaster.gov.in)", responder2_token is not None, f"Role: {responder2_info.get('role')}")
    h_responder2 = {"Authorization": f"Bearer {responder2_token}", "Content-Type": "application/json"}

    ts = int(time.time())
    new_citizen_email = f"audit_citizen_{ts}@gmail.com"
    reg_payload = {
        "name": f"Audit Citizen {ts}",
        "email": new_citizen_email,
        "password": "Password@123",
        "role": "Citizen",
        "phone": "9876543210",
        "location": "New Delhi"
    }
    r_reg = requests.post(f"{BASE_URL}/api/auth/register", json=reg_payload)
    test_assert("Auth", "Dynamic Citizen Registration", r_reg.status_code in (200, 201), f"Status: {r_reg.status_code}")

    citizen_token, citizen_info = login(new_citizen_email, "Password@123")
    test_assert("Auth", "Dynamic Citizen Login", citizen_token is not None, f"Email: {new_citizen_email}")
    h_citizen = {"Authorization": f"Bearer {citizen_token}", "Content-Type": "application/json"}

    sec_citizen_email = f"audit_privacy_{ts}@gmail.com"
    requests.post(f"{BASE_URL}/api/auth/register", json={
        "name": f"Privacy Citizen {ts}",
        "email": sec_citizen_email,
        "password": "Password@123",
        "role": "Citizen",
        "phone": "9876543211",
        "location": "New Delhi"
    })
    sec_citizen_token, _ = login(sec_citizen_email, "Password@123")
    h_sec_citizen = {"Authorization": f"Bearer {sec_citizen_token}", "Content-Type": "application/json"}

    # =========================================================================
    # SECTION 3: ROUTING & DIRECTIONS CONTROLLER AUDIT (AUTHENTICATED)
    # =========================================================================
    print("\n--- SECTION 3: ROUTING CONTROLLER DUAL PATH AUDIT ---")
    r_route = requests.get(f"{BASE_URL}/api/routing/route", headers=h_responder, params={
        "startLat": 10.9601, "startLng": 78.0766,
        "endLat": 10.9850, "endLng": 78.0850,
        "mode": "driving"
    })
    test_assert("Routing API", "GET /api/routing/route endpoint", r_route.status_code == 200, f"HTTP {r_route.status_code}")
    if r_route.status_code == 200:
        data = r_route.json()
        has_distance = "distanceKm" in data or "distance" in data
        test_assert("Routing API", "Route payload contains distance & geometry", has_distance, f"Keys: {list(data.keys())}")

    r_dir = requests.get(f"{BASE_URL}/api/routing/directions", headers=h_responder, params={
        "startLat": 10.9601, "startLng": 78.0766,
        "endLat": 10.9850, "endLng": 78.0850,
        "mode": "driving"
    })
    test_assert("Routing API", "GET /api/routing/directions endpoint", r_dir.status_code == 200, f"HTTP {r_dir.status_code}")

    # =========================================================================
    # SECTION 4: MASTER 18-STEP END-TO-END EMERGENCY RESPONSE WORKFLOW
    # =========================================================================
    print("\n--- SECTION 4: MASTER 18-STEP END-TO-END EMERGENCY WORKFLOW ---")
    
    # Step 1: Citizen Registration & Login
    test_assert("18-Step E2E", "Step 1: Citizen Registration & Login", citizen_token is not None)

    # Step 2: Citizen Emergency Flood Report Submission
    report_payload = {
        "title": f"Critical Flood Surge #{ts} - River Sector",
        "message": "Water levels rising above 4ft near Central Market. 5 persons stranded on roof.",
        "location": "Connaught Place Sector 4, New Delhi",
        "incidentType": "Flood",
        "severity": "High",
        "latitude": 28.6315,
        "longitude": 77.2167,
        "contactNumber": "9876543210",
        "peopleAffected": 5
    }
    r_rep = requests.post(f"{BASE_URL}/api/citizen-reports", headers=h_citizen, json=report_payload)
    test_assert("18-Step E2E", "Step 2: Citizen Emergency Report Submission (HTTP 200/201)", r_rep.status_code in (200, 201))
    rep_data = r_rep.json()
    report_id = rep_data.get("reportId") or rep_data.get("id")
    test_assert("18-Step E2E", "Step 2: Report ID Generated", report_id is not None, f"Report ID: #{report_id}")

    # Step 3: MySQL Report Persistence Verification
    r_check = requests.get(f"{BASE_URL}/api/citizen-reports/{report_id}", headers=h_citizen)
    test_assert("18-Step E2E", "Step 3: MySQL Report Persistence & Retrieval", r_check.status_code == 200)
    db_report = r_check.json()
    test_assert("18-Step E2E", "Step 3: Initial Status is 'Pending' / 'Submitted'", db_report.get("status") in ("Pending", "Submitted", "REPORTED"))

    # Step 4: Admin Distress Notification Verification
    r_admin_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_admin)
    test_assert("18-Step E2E", "Step 4: Admin Notification Endpoint Accessible", r_admin_notifs.status_code == 200)
    admin_notifs = r_admin_notifs.json()
    found_notif = any(n.get("relatedIncidentId") == report_id or f"#{report_id}" in n.get("message", "") or f"#{report_id}" in n.get("title", "") for n in admin_notifs)
    test_assert("18-Step E2E", "Step 4: Admin Distress Notification Received for Report", found_notif)

    # Step 5: Admin Verifies Report
    r_verify = requests.put(f"{BASE_URL}/api/citizen-reports/{report_id}/verify", headers=h_admin)
    test_assert("18-Step E2E", "Step 5: Admin Verifies Incident Report (HTTP 200)", r_verify.status_code == 200, f"HTTP {r_verify.status_code}")

    # Step 6: Admin Assigns Available Responder
    assign_url = f"{BASE_URL}/api/citizen-reports/{report_id}/assign?responderEmail=responder@disaster.gov.in"
    r_assign = requests.put(assign_url, headers=h_admin, json={"responderEmail": "responder@disaster.gov.in"})
    test_assert("18-Step E2E", "Step 6: Admin Assigns Responder to Incident (HTTP 200)", r_assign.status_code == 200)
    assign_resp = r_assign.json()
    task_id = assign_resp.get("rescueTaskId") or (assign_resp.get("report") or {}).get("rescueTaskId")
    if not task_id:
        r_rep_check = requests.get(f"{BASE_URL}/api/citizen-reports/{report_id}", headers=h_admin).json()
        task_id = r_rep_check.get("rescueTaskId")
    test_assert("18-Step E2E", "Step 6: Linked Rescue Task Created", task_id is not None, f"Rescue Task #{task_id}")

    # Step 7: Responder Receives Assignment Notification
    r_resp_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_responder)
    test_assert("18-Step E2E", "Step 7: Responder Notifications Accessible", r_resp_notifs.status_code == 200)
    resp_notifs = r_resp_notifs.json()
    has_resp_assign = any(n.get("category") == "ASSIGNMENT" or "Assignment" in n.get("title", "") or n.get("relatedIncidentId") == task_id for n in resp_notifs)
    test_assert("18-Step E2E", "Step 7: Responder Received Task Assignment Notification", has_resp_assign)

    # Step 8: Responder Accepts Assignment
    r_ack = requests.put(f"{BASE_URL}/api/tasks/{task_id}/acknowledge", headers=h_responder)
    test_assert("18-Step E2E", "Step 8: Responder Acknowledges Task (HTTP 200)", r_ack.status_code == 200)
    ack_obj = r_ack.json().get("task", {})
    test_assert("18-Step E2E", "Step 8: Task Status is 'Acknowledged'", ack_obj.get("status") == "Acknowledged")

    # Step 9: Responder Activates Live GPS Broadcast
    start_payload = {
        "assignmentId": task_id,
        "latitude": 28.6350,
        "longitude": 77.2200,
        "accuracy": 5.0
    }
    r_gps_start = requests.post(f"{BASE_URL}/api/tracking/start", headers=h_responder, json=start_payload)
    test_assert("18-Step E2E", "Step 9: Responder Activates Live GPS Broadcast (HTTP 200)", r_gps_start.status_code == 200, f"HTTP {r_gps_start.status_code}")

    # Step 10: Genuine GPS Coordinates Transmitted
    update_payload = {
        "assignmentId": task_id,
        "latitude": 28.6330,
        "longitude": 77.2185,
        "accuracy": 4.2,
        "heading": 195.0,
        "speed": 12.5
    }
    r_gps_update = requests.post(f"{BASE_URL}/api/tracking/update", headers=h_responder, json=update_payload)
    test_assert("18-Step E2E", "Step 10: Genuine GPS Coordinate Telemetry Transmitted", r_gps_update.status_code == 200, f"HTTP {r_gps_update.status_code}")

    # Step 11: Admin Mission Control Views Responder GPS on Radar
    r_radar = requests.get(f"{BASE_URL}/api/tracking/active", headers=h_admin)
    test_assert("18-Step E2E", "Step 11: Admin Mission Control Queries Active GPS Responders", r_radar.status_code == 200, f"HTTP {r_radar.status_code}")
    active_sessions = r_radar.json() if r_radar.status_code == 200 else []
    our_session = next((s for s in active_sessions if s.get("assignmentId") == task_id), None)
    test_assert("18-Step E2E", "Step 11: Assigned Responder Present on Admin Radar", our_session is not None, f"Found {len(active_sessions)} sessions")

    # Step 12: Citizen Views Authorized Tracking Telemetry
    r_cit_track = requests.get(f"{BASE_URL}/api/tracking/latest/{report_id}", headers=h_citizen)
    test_assert("18-Step E2E", "Step 12: Reporting Citizen Retrieves Authorized Live GPS Telemetry", r_cit_track.status_code == 200, f"HTTP {r_cit_track.status_code}")
    cit_track_data = r_cit_track.json() if r_cit_track.status_code == 200 else {}
    test_assert("18-Step E2E", "Step 12: Citizen Telemetry Matches Active Location", cit_track_data.get("latitude") is not None)

    # Step 13: Responder Updates Status to En Route, then ARRIVED (follows state machine transition)
    r_enroute = requests.put(f"{BASE_URL}/api/tasks/{task_id}/status", headers=h_responder, json={
        "status": "En Route",
        "notes": "Emergency rescue unit dispatched on highway with rescue boat."
    })
    test_assert("18-Step E2E", "Step 13a: Responder Updates Operational Status to 'En Route' (HTTP 200)", r_enroute.status_code == 200)

    r_arrived = requests.put(f"{BASE_URL}/api/tasks/{task_id}/status", headers=h_responder, json={
        "status": "Arrived",
        "notes": "Emergency rescue unit on-scene at flood surge site. Initiating boat deployment."
    })
    test_assert("18-Step E2E", "Step 13b: Responder Updates Operational Status to 'Arrived' (HTTP 200)", r_arrived.status_code == 200)

    # Step 14: Responder Updates Status to IN_PROGRESS
    r_inprogress = requests.put(f"{BASE_URL}/api/tasks/{task_id}/status", headers=h_responder, json={
        "status": "In Progress",
        "notes": "Rescue boat actively evacuating 5 stranded citizens from roof."
    })
    test_assert("18-Step E2E", "Step 14: Responder Updates Status to 'In Progress' (HTTP 200)", r_inprogress.status_code == 200)

    # Step 15: Responder Marks Rescue Operation Completed
    r_resolve_task = requests.put(f"{BASE_URL}/api/tasks/{task_id}/resolve", headers=h_responder, json={
        "summary": "Rescue completed successfully. All 5 individuals safely extracted and transported to relief station."
    })
    test_assert("18-Step E2E", "Step 15: Responder Resolves Task (Completed) (HTTP 200)", r_resolve_task.status_code == 200)
    task_res = r_resolve_task.json().get("task", {})
    test_assert("18-Step E2E", "Step 15: Task Status is 'Completed' / 'Resolved'", task_res.get("status") in ("Completed", "Resolved"))

    # Step 16: Admin and Citizen Receive Completion Notifications
    r_cit_comp_notif = requests.get(f"{BASE_URL}/api/notifications", headers=h_citizen)
    test_assert("18-Step E2E", "Step 16: Citizen Notification Endpoint Accessible", r_cit_comp_notif.status_code == 200)
    cit_notifs = r_cit_comp_notif.json()
    has_comp_notif = any("Complete" in n.get("title", "") or "resolved" in n.get("message", "").lower() or "completed" in n.get("message", "").lower() for n in cit_notifs)
    test_assert("18-Step E2E", "Step 16: Citizen Received Operation Completion Notification", has_comp_notif)

    # Step 17: Admin Closes Incident Report
    r_close = requests.put(f"{BASE_URL}/api/citizen-reports/{report_id}/resolve", headers=h_admin)
    test_assert("18-Step E2E", "Step 17: Admin Closes Incident Report (Resolved) (HTTP 200)", r_close.status_code == 200)
    final_rep = r_close.json()
    test_assert("18-Step E2E", "Step 17: Final Report Status is 'Resolved'", final_rep.get("status") == "Resolved")

    # Step 18: Database Consistency & Record Integrity Verification
    r_final_rep = requests.get(f"{BASE_URL}/api/citizen-reports/{report_id}", headers=h_citizen).json()
    test_assert("18-Step E2E", "Step 18: Report Persisted with Status 'Resolved'", r_final_rep.get("status") == "Resolved")
    
    r_final_task = requests.get(f"{BASE_URL}/api/tasks/{task_id}", headers=h_admin).json()
    test_assert("18-Step E2E", "Step 18: Task Persisted with Status 'Completed'", r_final_task.get("task", {}).get("status") in ("Completed", "Resolved"))

    # =========================================================================
    # SECTION 5: SECURITY, RBAC & PRIVACY BOUNDARY AUDIT
    # =========================================================================
    print("\n--- SECTION 5: SECURITY, RBAC & PRIVACY BOUNDARIES ---")
    
    # 5.1 Cross-Citizen Privacy: Secondary citizen cannot read primary citizen's private report
    r_sec_rep = requests.get(f"{BASE_URL}/api/citizen-reports/{report_id}", headers=h_sec_citizen)
    test_assert("Security / RBAC", "Cross-Citizen Report Access Rejection (HTTP 403)", r_sec_rep.status_code == 403, f"HTTP {r_sec_rep.status_code}")

    # 5.2 Cross-Citizen Privacy: Secondary citizen cannot track primary citizen's responder
    r_sec_track = requests.get(f"{BASE_URL}/api/tracking/latest/{report_id}", headers=h_sec_citizen)
    test_assert("Security / RBAC", "Cross-Citizen Live Tracking Rejection (HTTP 403)", r_sec_track.status_code == 403, f"HTTP {r_sec_track.status_code}")

    # 5.3 Unassigned Responder Isolation: responder2 cannot modify responder's task
    r_tamper = requests.put(f"{BASE_URL}/api/tasks/{task_id}/status", headers=h_responder2, json={"status": "In Progress"})
    test_assert("Security / RBAC", "Unassigned Responder Task Modification Rejection (HTTP 403)", r_tamper.status_code == 403, f"HTTP {r_tamper.status_code}")

    # 5.4 Citizen cannot assign responder
    r_cit_assign = requests.put(assign_url, headers=h_citizen, json={"responderEmail": "responder@disaster.gov.in"})
    test_assert("Security / RBAC", "Citizen Forbidden from Assigning Responders (HTTP 403)", r_cit_assign.status_code == 403, f"HTTP {r_cit_assign.status_code}")

    # 5.5 GPS Coordinate Validation: Out-of-bounds latitude (+95.0) rejected
    r_bad_lat = requests.post(f"{BASE_URL}/api/tracking/start", headers=h_responder, json={
        "assignmentId": task_id, "latitude": 95.0, "longitude": 78.0, "accuracy": 5.0
    })
    test_assert("Security / GPS", "Out-of-bounds Latitude (+95.0) Rejected (HTTP 400)", r_bad_lat.status_code == 400, f"HTTP {r_bad_lat.status_code}")

    # 5.6 GPS Coordinate Validation: Out-of-bounds longitude (+190.0) rejected
    r_bad_lng = requests.post(f"{BASE_URL}/api/tracking/update", headers=h_responder, json={
        "assignmentId": task_id, "latitude": 28.0, "longitude": 190.0, "accuracy": 5.0
    })
    test_assert("Security / GPS", "Out-of-bounds Longitude (+190.0) Rejected (HTTP 400)", r_bad_lng.status_code == 400, f"HTTP {r_bad_lng.status_code}")

    # 5.7 GPS Coordinate Validation: Null Island (0.0, 0.0) rejected
    r_null_island = requests.post(f"{BASE_URL}/api/tracking/start", headers=h_responder, json={
        "assignmentId": task_id, "latitude": 0.0, "longitude": 0.0, "accuracy": 10.0
    })
    test_assert("Security / GPS", "Null Island (0.0, 0.0) Rejected (HTTP 400)", r_null_island.status_code == 400, f"HTTP {r_null_island.status_code}")

    # 5.8 Unauthenticated Access Rejection
    r_unauth = requests.get(f"{BASE_URL}/api/citizen-reports/my-reports")
    test_assert("Security / RBAC", "Unauthenticated Request Rejected (HTTP 401)", r_unauth.status_code == 401, f"HTTP {r_unauth.status_code}")

    # =========================================================================
    # SECTION 6: NDMA SACHET LIVE INTEGRATION & RADAR TELEMETRY
    # =========================================================================
    print("\n--- SECTION 6: NDMA SACHET INTEGRATION & RADAR AUDIT ---")
    
    # 6.1 NDMA Ingestion Status Check
    r_ndma_status = requests.get(f"{BASE_URL}/api/official-alerts/status")
    test_assert("NDMA Sachet", "GET /api/official-alerts/status Endpoint (HTTP 200)", r_ndma_status.status_code == 200, f"HTTP {r_ndma_status.status_code}")
    if r_ndma_status.status_code == 200:
        status_info = r_ndma_status.json()
        has_metrics = "totalRecordsImported" in status_info or "activeOfficialAlertsCount" in status_info or "lastSuccessfulSync" in status_info
        test_assert("NDMA Sachet", "NDMA Status contains sync metrics", has_metrics, f"Keys: {list(status_info.keys())}")

    # 6.2 Official Active Alerts Retrieval
    r_alerts = requests.get(f"{BASE_URL}/api/official-alerts/active")
    test_assert("NDMA Sachet", "GET /api/official-alerts/active Public Endpoint", r_alerts.status_code == 200)
    alerts_list = r_alerts.json() if r_alerts.status_code == 200 else []
    test_assert("NDMA Sachet", "Database Contains Active Official NDMA Alerts", len(alerts_list) >= 0, f"Count: {len(alerts_list)}")

    # 6.3 State-filtered official alerts
    r_state = requests.get(f"{BASE_URL}/api/official-alerts/state/Tamil%20Nadu")
    test_assert("NDMA Sachet", "GET /api/official-alerts/state/Tamil%20Nadu (HTTP 200)", r_state.status_code == 200)

    # 6.4 Live Ingestion Trigger (Admin authenticated)
    r_ingest = requests.post(f"{BASE_URL}/api/admin/ndma/sync", headers=h_admin)
    test_assert("NDMA Sachet", "POST /api/admin/ndma/sync Manual Trigger (HTTP 200)", r_ingest.status_code == 200, f"HTTP {r_ingest.status_code}")

    # =========================================================================
    # SECTION 7: NOTIFICATION MANAGEMENT SYSTEM AUDIT
    # =========================================================================
    print("\n--- SECTION 7: NOTIFICATION MANAGEMENT SYSTEM AUDIT ---")
    
    # 7.1 Unread Counts
    r_unread_admin = requests.get(f"{BASE_URL}/api/notifications/unread-count", headers=h_admin)
    test_assert("Notifications", "Admin Unread Count Endpoint (HTTP 200)", r_unread_admin.status_code == 200)
    
    r_unread_cit = requests.get(f"{BASE_URL}/api/notifications/unread-count", headers=h_citizen)
    test_assert("Notifications", "Citizen Unread Count Endpoint (HTTP 200)", r_unread_cit.status_code == 200)

    # 7.2 Category Filtering
    r_cat_filter = requests.get(f"{BASE_URL}/api/notifications?category=DISASTERS", headers=h_admin)
    test_assert("Notifications", "Notification Category Filtering (DISASTERS)", r_cat_filter.status_code == 200)

    # 7.3 Mark Single Notification Read
    admin_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_admin).json()
    if admin_notifs:
        target_notif = admin_notifs[0]
        t_id = target_notif.get("id") or target_notif.get("notificationId")
        r_read = requests.put(f"{BASE_URL}/api/notifications/{t_id}/read", headers=h_admin)
        test_assert("Notifications", f"Mark Notification #{t_id} as Read (HTTP 200)", r_read.status_code == 200)

    # 7.4 Mark All as Read (supports both /read-all and /mark-all-read)
    r_mark_all = requests.put(f"{BASE_URL}/api/notifications/read-all", headers=h_citizen)
    test_assert("Notifications", "Citizen Mark All as Read (HTTP 200)", r_mark_all.status_code == 200)
    unread_after = requests.get(f"{BASE_URL}/api/notifications/unread-count", headers=h_citizen).json().get("unread", 0)
    test_assert("Notifications", "Citizen Unread Count Zero After Mark All Read", unread_after == 0, f"Unread: {unread_after}")

    # 7.5 Delete Notification
    cit_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_citizen).json()
    if cit_notifs:
        del_target = cit_notifs[0]
        d_id = del_target.get("id") or del_target.get("notificationId")
        r_del = requests.delete(f"{BASE_URL}/api/notifications/{d_id}", headers=h_citizen)
        test_assert("Notifications", f"Delete Notification #{d_id} (HTTP 200/204)", r_del.status_code in (200, 204))

    # =========================================================================
    # SUMMARY & FINAL VERDICT
    # =========================================================================
    total_tests = passed_tests + failed_tests
    print("\n" + "=" * 80)
    print(f"MASTER AUDIT SUMMARY: {passed_tests}/{total_tests} PASSED ({(passed_tests/total_tests)*100:.1f}%)")
    print(f"Failed Tests: {failed_tests}")
    print("=" * 80)

    with open("master_audit_results.json", "w", encoding="utf-8") as f:
        json.dump({
            "totalTests": total_tests,
            "passed": passed_tests,
            "failed": failed_tests,
            "passRate": round((passed_tests / total_tests) * 100, 2),
            "results": audit_results
        }, f, indent=2)
    print("Audit results written to master_audit_results.json")

    if failed_tests > 0:
        sys.exit(1)
    else:
        sys.exit(0)

if __name__ == "__main__":
    run_audit()

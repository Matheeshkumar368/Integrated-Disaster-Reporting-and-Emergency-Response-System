import requests
import json
import sys
import time

BASE_URL = "http://localhost:8080"

passed = 0
failed = 0

def check(step_name, condition, details=""):
    global passed, failed
    if condition:
        passed += 1
        print(f"  [PASS] {step_name} {('- ' + str(details)) if details else ''}")
    else:
        failed += 1
        print(f"  [FAIL] {step_name} {('- ' + str(details)) if details else ''}")

def login(email, password):
    r = requests.post(f"{BASE_URL}/api/auth/login", json={"email": email, "password": password})
    assert r.status_code == 200, f"Login failed for {email}: {r.text}"
    data = r.json()
    return data["token"], data

def register_user(name, email, password, role, location, zone=None, group=None):
    payload = {
        "name": name,
        "email": email,
        "password": password,
        "phone": "+91 98765 43210",
        "location": location,
        "role": role
    }
    if zone:
        payload["assignedZone"] = zone
    if group:
        payload["groupClassification"] = group

    r = requests.post(f"{BASE_URL}/api/auth/register", json=payload)
    return r

def run_prompt4_tests():
    global passed, failed
    print("=" * 80)
    print("PROMPT 4: COMPLETE REAL-TIME NOTIFICATION SYSTEM & ROLE-BASED ALERTS")
    print("=" * 80)

    # 1. Login Admin
    print("\n[Section 1] Admin Authentication & Baseline Verification...")
    admin_token, admin_info = login("admin@disaster.gov.in", "admin123")
    check("Admin login successful", admin_token is not None, f"Role: {admin_info.get('role')}")
    h_admin = {"Authorization": f"Bearer {admin_token}", "Content-Type": "application/json"}

    # Admin checks baseline notifications
    r = requests.get(f"{BASE_URL}/api/notifications", headers=h_admin)
    check("Admin fetch notifications status 200", r.status_code == 200)
    admin_notifs = r.json()
    check("Admin has notifications in feed", len(admin_notifs) > 0, f"Count: {len(admin_notifs)}")

    # 2. User Registration Event Triggers (Citizen & Responder)
    print("\n[Section 2] User Registration Triggers Admin Notification...")
    ts = int(time.time())
    new_citizen_email = f"citizen_p4_{ts}@test.gov.in"
    new_responder_email = f"responder_p4_{ts}@test.gov.in"

    # Register Citizen
    r_cit_reg = register_user(f"Citizen P4 {ts}", new_citizen_email, "citizen123", "Citizen", "Karur District")
    check("Register Citizen API status 200/201", r_cit_reg.status_code in [200, 201])

    # Register Responder
    r_resp_reg = register_user(f"Responder P4 {ts}", new_responder_email, "responder123", "Responder", "Karur Delta", "Zone 1", "Search & Rescue")
    check("Register Responder API status 200/201", r_resp_reg.status_code in [200, 201])

    # Verify Admin received registration notifications
    r = requests.get(f"{BASE_URL}/api/notifications", headers=h_admin)
    admin_notifs_after_reg = r.json()
    user_reg_notif = any("New User Registered" in n.get("title", "") and new_citizen_email in n.get("message", "") for n in admin_notifs_after_reg)
    resp_reg_notif = any("New Responder Registered" in n.get("title", "") and new_responder_email in n.get("message", "") for n in admin_notifs_after_reg)
    check("Admin received 'New User Registered' notification", user_reg_notif)
    check("Admin received 'New Responder Registered' notification", resp_reg_notif)

    # Login registered Citizen & Responder
    citizen_token, _ = login(new_citizen_email, "citizen123")
    responder_token, _ = login(new_responder_email, "responder123")
    h_citizen = {"Authorization": f"Bearer {citizen_token}", "Content-Type": "application/json"}
    h_responder = {"Authorization": f"Bearer {responder_token}", "Content-Type": "application/json"}

    # 3. Citizen Submits Report -> Admin & Citizen Notifications
    print("\n[Section 3] Citizen Distress Submission Triggers...")
    report_payload = {
        "title": f"Flash Flood Alert Sector 4 #{ts}",
        "message": "Heavy torrential rains causing residential waterlogging and power cuts.",
        "location": "Karur District, Ward 12",
        "incidentType": "Flood",
        "severity": "High",
        "latitude": 10.9601,
        "longitude": 78.0766
    }
    r = requests.post(f"{BASE_URL}/api/citizen-reports", headers=h_citizen, json=report_payload)
    check("Citizen report submission status 200", r.status_code == 200)
    report = r.json()
    report_id = report.get("reportId")
    check("Report ID generated", report_id is not None, f"Report #{report_id}")

    # Citizen receives 'Report Received'
    r_cit_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_citizen)
    cit_notifs = r_cit_notifs.json()
    cit_received = any(f"#{report_id}" in n.get("message", "") for n in cit_notifs)
    check("Citizen received 'Report Received' confirmation notification", cit_received)

    # Admin receives 'New Disaster Reported'
    r_admin_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_admin)
    admin_report_notif = any(f"Incident #{report_id}" in n.get("message", "") for n in r_admin_notifs.json())
    check("Admin received 'New Disaster Reported' notification for Incident", admin_report_notif)

    # 4. Citizen Updates Report -> Admin Notification
    print("\n[Section 4] Citizen Report Details Update...")
    update_payload = {
        "message": "Water levels increased to 4 feet. Two families need emergency boat evacuation."
    }
    r = requests.put(f"{BASE_URL}/api/citizen-reports/{report_id}", headers=h_citizen, json=update_payload)
    check("Citizen report updated status 200", r.status_code == 200)

    # Admin receives 'Report Details Updated'
    r_admin_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_admin)
    admin_update_notif = any(f"Incident #{report_id}" in n.get("message", "") and "updated" in n.get("title", "").lower() for n in r_admin_notifs.json())
    check("Admin received 'Report Details Updated' notification", admin_update_notif)

    # 5. Admin Verifies Report -> Citizen Notification
    print("\n[Section 5] Admin Verifies Report...")
    r = requests.put(f"{BASE_URL}/api/citizen-reports/{report_id}/verify", headers=h_admin)
    check("Admin verify report status 200", r.status_code == 200)

    # Citizen receives 'Report Verified'
    r_cit_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_citizen)
    cit_verified = any("Report Verified" in n.get("title", "") and f"#{report_id}" in n.get("message", "") for n in r_cit_notifs.json())
    check("Citizen received 'Report Verified' notification", cit_verified)

    # 6. Admin Assigns Responder -> Responder & Citizen Notifications
    print("\n[Section 6] Admin Assigns Responder to Incident...")
    assign_payload = {"responderEmail": new_responder_email}
    r = requests.put(f"{BASE_URL}/api/citizen-reports/{report_id}/assign", headers=h_admin, json=assign_payload)
    check("Admin assign responder status 200", r.status_code == 200)
    assigned_data = r.json()
    rescue_task_id = assigned_data.get("report", {}).get("rescueTaskId")
    check("Rescue Task ID assigned", rescue_task_id is not None, f"TaskId: {rescue_task_id}")

    # Responder receives 'New Rescue Assignment'
    r_resp_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_responder)
    resp_assignment_notif = any("New Rescue Assignment" in n.get("title", "") and f"#{report_id}" in n.get("message", "") for n in r_resp_notifs.json())
    check("Responder received 'New Rescue Assignment' notification", resp_assignment_notif)

    # Citizen receives 'Rescue Team Assigned'
    r_cit_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_citizen)
    cit_assigned_notif = any("Rescue Team Assigned" in n.get("title", "") for n in r_cit_notifs.json())
    check("Citizen received 'Rescue Team Assigned' notification", cit_assigned_notif)

    # 7. Responder Operational Progression Lifecycle Notifications
    print("\n[Section 7] Responder Operational Lifecycle Notifications...")
    # Stage 1: Acknowledge/Accept
    r = requests.put(f"{BASE_URL}/api/tasks/{rescue_task_id}/acknowledge", headers=h_responder)
    check("Responder acknowledge task status 200", r.status_code == 200)

    # Citizen receives 'Rescue Team Deployed', Admin receives 'Incident Acknowledged'
    r_cit_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_citizen)
    cit_deployed = any("Rescue Team Deployed" in n.get("title", "") for n in r_cit_notifs.json())
    check("Citizen received 'Rescue Team Deployed' notification", cit_deployed)

    # Stage 2: EN_ROUTE
    r = requests.put(f"{BASE_URL}/api/tasks/{rescue_task_id}/status", headers=h_responder, json={"status": "En Route"})
    check("Responder updates operational status to EN_ROUTE", r.status_code == 200)
    r_cit_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_citizen)
    cit_en_route = any("En Route" in n.get("title", "") or "en route" in n.get("message", "").lower() for n in r_cit_notifs.json())
    check("Citizen received 'Unit En Route' notification", cit_en_route)

    # Stage 3: ARRIVED
    r = requests.put(f"{BASE_URL}/api/tasks/{rescue_task_id}/status", headers=h_responder, json={"status": "Arrived"})
    check("Responder updates operational status to ARRIVED", r.status_code == 200)
    r_cit_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_citizen)
    cit_arrived = any("Arrived" in n.get("title", "") or "arrived" in n.get("message", "").lower() for n in r_cit_notifs.json())
    check("Citizen received 'Unit Arrived on Scene' notification", cit_arrived)

    # Stage 4: COMPLETED
    r = requests.put(f"{BASE_URL}/api/tasks/{rescue_task_id}/status", headers=h_responder, json={"status": "Completed"})
    check("Responder marks mission as COMPLETED", r.status_code == 200)
    r_cit_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_citizen)
    cit_completed = any("Completed" in n.get("title", "") or "resolved" in n.get("message", "").lower() for n in r_cit_notifs.json())
    check("Citizen received 'Mission Completed' notification", cit_completed)

    # 8. Admin Broadcast Alert Notification
    print("\n[Section 8] Emergency Broadcast Alert Notifications...")
    alert_payload = {
        "title": f"Cyclone Warning #{ts}",
        "message": "Severe weather alert: Winds up to 80 km/h and coastal surges expected. Move to shelters.",
        "region": "Karur District",
        "severity": "Medium",
        "disasterType": "Cyclone"
    }
    r = requests.post(f"{BASE_URL}/api/alerts", headers=h_admin, json=alert_payload)
    check("Admin posts emergency alert broadcast status 200/201", r.status_code in [200, 201])

    # Citizens & Responders receive broadcast notifications
    r_cit_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_citizen)
    cit_alert = any("Cyclone Warning" in n.get("title", "") or "alert" in n.get("title", "").lower() for n in r_cit_notifs.json())
    check("Citizen received emergency broadcast alert notification", cit_alert)

    # 9. Notification Actions (Unread Count, Mark as Read, Mark All Read, Delete)
    print("\n[Section 9] Notification Interactive Actions...")
    # Get Unread Count
    r = requests.get(f"{BASE_URL}/api/notifications/unread-count", headers=h_citizen)
    check("Citizen unread count API status 200", r.status_code == 200)
    unread_data = r.json()
    initial_unread = unread_data.get("unreadCount", unread_data.get("unread", 0))
    check("Citizen has unread notifications", initial_unread > 0, f"Unread: {initial_unread}")

    # Mark single as read
    target_notif = cit_notifs[0]
    target_id = target_notif.get("id")
    r = requests.put(f"{BASE_URL}/api/notifications/{target_id}/read", headers=h_citizen)
    check(f"Mark notification #{target_id} as read status 200", r.status_code == 200)
    check("Notification response isRead is True", r.json().get("isRead") is True)

    # Security check: User B (Responder) cannot mark User A's (Citizen) notification as read
    r_sec = requests.put(f"{BASE_URL}/api/notifications/{target_id}/read", headers=h_responder)
    check("Cross-user unauthorized read rejected with 403 Forbidden", r_sec.status_code == 403)

    # Mark all as read
    r = requests.put(f"{BASE_URL}/api/notifications/read-all", headers=h_citizen)
    check("Mark all notifications as read status 200", r.status_code == 200)
    r_count = requests.get(f"{BASE_URL}/api/notifications/unread-count", headers=h_citizen)
    count_after_all_read = r_count.json().get("unreadCount", r_count.json().get("unread", 0))
    check("Unread count after mark-all-read is 0", count_after_all_read == 0)

    # Soft Delete notification
    r = requests.delete(f"{BASE_URL}/api/notifications/{target_id}", headers=h_citizen)
    check(f"Soft delete notification #{target_id} status 200", r.status_code == 200)

    # Verify deleted notification does not appear in user's active feed
    r_after_del = requests.get(f"{BASE_URL}/api/notifications", headers=h_citizen)
    deleted_present = any(n.get("id") == target_id for n in r_after_del.json())
    check(f"Deleted notification #{target_id} is hidden from user feed", not deleted_present)

    # Security check: User B cannot delete User A's notification
    r_sec_del = requests.delete(f"{BASE_URL}/api/notifications/{target_id}", headers=h_responder)
    check("Cross-user unauthorized delete rejected with 403 Forbidden", r_sec_del.status_code == 403)

    # 10. Role-Based Category Filters
    print("\n[Section 10] Category Filtering Endpoint Tests...")
    # Admin category filters: USERS, DISASTERS, RESPONDERS, SYSTEM
    for cat in ["USERS", "DISASTERS", "RESPONDERS", "SYSTEM"]:
        r_cat = requests.get(f"{BASE_URL}/api/notifications?category={cat}", headers=h_admin)
        check(f"Admin filter category={cat} status 200", r_cat.status_code == 200, f"Returned: {len(r_cat.json())}")

    # Responder category filters: ASSIGNMENTS, ALERTS, MESSAGES, UPDATES
    for cat in ["ASSIGNMENTS", "ALERTS", "MESSAGES", "UPDATES"]:
        r_cat = requests.get(f"{BASE_URL}/api/notifications?category={cat}", headers=h_responder)
        check(f"Responder filter category={cat} status 200", r_cat.status_code == 200, f"Returned: {len(r_cat.json())}")

    # Citizen category filters: ALERTS, UPDATES, RESPONSE, MESSAGES
    for cat in ["ALERTS", "UPDATES", "RESPONSE", "MESSAGES"]:
        r_cat = requests.get(f"{BASE_URL}/api/notifications?category={cat}", headers=h_citizen)
        check(f"Citizen filter category={cat} status 200", r_cat.status_code == 200, f"Returned: {len(r_cat.json())}")

    # Summary
    print("\n" + "=" * 80)
    print(f"PROMPT 4 TEST SUMMARY: {passed} PASSED, {failed} FAILED")
    print("=" * 80)

    if failed > 0:
        sys.exit(1)

if __name__ == "__main__":
    run_prompt4_tests()

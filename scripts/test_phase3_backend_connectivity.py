#!/usr/bin/env python3
"""
test_phase3_backend_connectivity.py
Complete End-to-End Three-Role Backend Connectivity and Validation Suite.

Executes:
TEST A: Citizen logs in and submits a disaster report.
TEST B: Admin receives report, verifies it and notification.
TEST C: Admin assigns Responder to report.
TEST D: Assigned Responder receives notification and queries assignment.
TEST E: Assigned Responder accepts/acknowledges assignment.
TEST F: Citizen and Admin receive status update notification & timeline logged.
TEST G: Responder updates progress to On the Way (En Route).
TEST H: Citizen and Admin see updated progress & notification.
TEST I: Responder marks incident as Resolved (Completed).
TEST J: All three roles display correct final incident status & notification history.
TEST K: Refresh every dashboard endpoint and verify data persistence across requests.
TEST L: Security validation - unauthorized cross-role access attempts rejected (403/401)
        and duplicate active alert prevention.
"""

import sys
import json
import urllib.request
import urllib.error
import time

BASE_URL = "http://localhost:8080"

passed = 0
failed = 0
results = []

def report(name, ok, details=""):
    global passed, failed
    if ok:
        passed += 1
        print(f"  [PASS] {name} {('- ' + details) if details else ''}")
        results.append({"test": name, "status": "PASS", "details": details})
    else:
        failed += 1
        print(f"  [FAIL] {name} {('- ' + details) if details else ''}")
        results.append({"test": name, "status": "FAIL", "details": details})

def api_request(path, method="GET", data=None, token=None):
    url = BASE_URL + path
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    body = json.dumps(data).encode("utf-8") if data is not None else None
    req = urllib.request.Request(url, data=body, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=10) as resp:
            code = resp.getcode()
            res_body = resp.read().decode("utf-8")
            try:
                parsed = json.loads(res_body)
            except Exception:
                parsed = res_body
            return code, parsed
    except urllib.error.HTTPError as e:
        err_body = e.read().decode("utf-8")
        try:
            parsed = json.loads(err_body)
        except Exception:
            parsed = err_body
        return e.code, parsed
    except Exception as e:
        return 0, str(e)

def login(email, password):
    code, data = api_request("/api/auth/login", method="POST", data={"email": email, "password": password})
    if code == 200 and isinstance(data, dict) and "token" in data:
        return data["token"], data
    raise RuntimeError(f"Login failed for {email}: code={code}, resp={data}")

print("======================================================================")
print("PHASE 3: THREE-ROLE BACKEND CONNECTIVITY AND VERIFICATION SUITE")
print("======================================================================")

# Authenticate all 3 roles
print("\n[Step 0] Authenticating Citizen, Admin, and Responders...")
citizen_token, citizen_user = login("citizen@gmail.com", "citizen123")
report("Citizen Login (citizen@gmail.com)", citizen_token is not None, f"Role: {citizen_user.get('role')}")

admin_token, admin_user = login("admin@disaster.gov.in", "admin123")
report("Admin Login (admin@disaster.gov.in)", admin_token is not None, f"Role: {admin_user.get('role')}")

responder_token, responder_user = login("responder@disaster.gov.in", "responder123")
report("Responder Login (responder@disaster.gov.in)", responder_token is not None, f"Role: {responder_user.get('role')}")

responder2_token, responder2_user = login("responder2@disaster.gov.in", "responder123")
report("Unassigned Responder Login (responder2@disaster.gov.in)", responder2_token is not None, f"Role: {responder2_user.get('role')}")

# Ensure responder is Available before starting test
api_request("/api/auth/responder/availability", method="PUT", data={"availabilityStatus": "Available"}, token=responder_token)

# TEST A
print("\n[TEST A] Citizen submits a real disaster report...")
ts = int(time.time())
report_payload = {
    "message": f"Critical flash flood near Karur North Bridge - water entering residential sector. [{ts}]",
    "incidentType": "Flood",
    "location": "Karur District",
    "severity": "High",
    "latitude": 10.9601,
    "longitude": 78.0766,
    "accuracy": 15.0
}
code_a, data_a = api_request("/api/citizen-reports", method="POST", data=report_payload, token=citizen_token)
report_obj = data_a if isinstance(data_a, dict) else {}
report_id = report_obj.get("reportId")
report("TEST A1: Report Submission HTTP 200/201", code_a in (200, 201) and report_id is not None, f"Report ID: #{report_id}")
report("TEST A2: Authenticated Citizen Association", report_obj.get("citizenEmail") == "citizen@gmail.com", f"Citizen: {report_obj.get('citizenEmail')}")
report("TEST A3: Initial Report Status & Stage", report_obj.get("status") == "Pending" and report_obj.get("progressStage") == "Submitted", f"Status: {report_obj.get('status')}, Stage: {report_obj.get('progressStage')}")
report("TEST A4: GPS Coordinates Persisted", report_obj.get("latitude") == 10.9601 and report_obj.get("longitude") == 78.0766, f"Lat: {report_obj.get('latitude')}, Lng: {report_obj.get('longitude')}")

# Verify in MySQL via GET
code_a_get, data_a_get = api_request(f"/api/citizen-reports/{report_id}", method="GET", token=citizen_token)
report("TEST A5: Report Persisted in MySQL", code_a_get == 200 and data_a_get.get("reportId") == report_id)

# TEST B
print("\n[TEST B] Admin receives and verifies report...")
code_b_admin, data_b_admin = api_request(f"/api/citizen-reports/{report_id}", method="GET", token=admin_token)
report("TEST B1: Admin Can Access Report Directly", code_b_admin == 200 and data_b_admin.get("reportId") == report_id)

# Admin checks notifications
code_b_notif, data_b_notif = api_request("/api/notifications", method="GET", token=admin_token)
admin_notifications = data_b_notif if isinstance(data_b_notif, list) else data_b_notif.get("notifications", [])
admin_has_report_notif = any(n.get("relatedIncidentId") == report_id and n.get("category") == "DISASTER" for n in admin_notifications)
report("TEST B2: Admin Notification Center Receives Report Alert", admin_has_report_notif, f"Found in {len(admin_notifications)} notifications")

# TEST C
print("\n[TEST C] Admin assigns Responder to report...")
assign_payload = {"responderEmail": "responder@disaster.gov.in"}
code_c, data_c = api_request(f"/api/citizen-reports/{report_id}/assign", method="POST", data=assign_payload, token=admin_token)
report_c_ok = code_c == 200 and data_c.get("status") == "success"
assigned_report = data_c.get("report", {}) if report_c_ok else {}
linked_task_id = assigned_report.get("rescueTaskId")
report("TEST C1: Admin Assign Responder HTTP 200", report_c_ok, f"Assigned to: {assigned_report.get('assignedResponderEmail')}")
report("TEST C2: Report Status Updated to Assigned", assigned_report.get("status") == "Assigned" and assigned_report.get("progressStage") == "Assigned")
report("TEST C3: Linked RescueTask Generated", linked_task_id is not None, f"Task ID: #{linked_task_id}")

# Verify RescueTask in MySQL
code_c_task, data_c_task = api_request(f"/api/tasks/{linked_task_id}", method="GET", token=admin_token)
task_obj = data_c_task.get("task", {}) if code_c_task == 200 else {}
report("TEST C4: RescueTask Created & Linked in MySQL", code_c_task == 200 and task_obj.get("citizenReportId") == report_id and task_obj.get("assignedTo") == "responder@disaster.gov.in")

# TEST D
print("\n[TEST D] Assigned Responder receives notification & queries task...")
code_d_notif, data_d_notif = api_request("/api/notifications", method="GET", token=responder_token)
resp_notifications = data_d_notif if isinstance(data_d_notif, list) else data_d_notif.get("notifications", [])
resp_has_assign_notif = any(
    (n.get("relatedIncidentId") == report_id or n.get("relatedIncidentId") == linked_task_id) and n.get("category") == "ASSIGNMENT"
    for n in resp_notifications
)
report("TEST D1: Responder Receives Assignment Notification", resp_has_assign_notif, f"Found in {len(resp_notifications)} notifications")

# Responder queries their assigned tasks
code_d_tasks, data_d_tasks = api_request("/api/tasks", method="GET", token=responder_token)
responder_tasks = data_d_tasks.get("tasks", []) if code_d_tasks == 200 else []
task_in_responder_view = any(t.get("taskId") == linked_task_id for t in responder_tasks)
report("TEST D2: Task Visible in Responder Dashboard", task_in_responder_view, f"Total assigned tasks: {len(responder_tasks)}")

# TEST E
print("\n[TEST E] Responder accepts/acknowledges the assignment...")
code_e, data_e = api_request(f"/api/tasks/{linked_task_id}/acknowledge", method="POST", token=responder_token)
report("TEST E1: Responder Acknowledge Task HTTP 200", code_e == 200 and data_e.get("status") == "success")

# Verify task and report status
code_e_task, data_e_task = api_request(f"/api/tasks/{linked_task_id}", method="GET", token=responder_token)
t_e = data_e_task.get("task", {})
report("TEST E2: Task Status Changed to Acknowledged", t_e.get("status") == "Acknowledged" and t_e.get("acknowledgedAt") is not None, f"AcknowledgedAt: {t_e.get('acknowledgedAt')}")

code_e_rep, data_e_rep = api_request(f"/api/citizen-reports/{report_id}", method="GET", token=citizen_token)
report("TEST E3: Citizen Report Status Synced to Acknowledged", data_e_rep.get("status") == "Acknowledged")

# TEST F
print("\n[TEST F] Citizen and Admin receive status update notification...")
code_f_cit_notif, data_f_cit_notif = api_request("/api/notifications", method="GET", token=citizen_token)
cit_notifs = data_f_cit_notif if isinstance(data_f_cit_notif, list) else data_f_cit_notif.get("notifications", [])
cit_has_ack = any(n.get("relatedIncidentId") == report_id and "Deployed" in n.get("title", "") for n in cit_notifs)
report("TEST F1: Citizen Receives 'Rescue Team Deployed' Notification", cit_has_ack, f"Found in {len(cit_notifs)} citizen notifications")

code_f_adm_notif, data_f_adm_notif = api_request("/api/notifications", method="GET", token=admin_token)
adm_notifs = data_f_adm_notif if isinstance(data_f_adm_notif, list) else data_f_adm_notif.get("notifications", [])
adm_has_ack = any(n.get("relatedIncidentId") == linked_task_id and "Acknowledged" in n.get("title", "") for n in adm_notifs)
report("TEST F2: Admin Receives 'Task Acknowledged' Notification", adm_has_ack, f"Found in {len(adm_notifs)} admin notifications")

# Check timeline event via IncidentRoom endpoint
code_f_tl, data_f_tl = api_request(f"/api/incidents/{report_id}/timeline?type=CITIZEN_REPORT", method="GET", token=admin_token)
tl_events = data_f_tl if isinstance(data_f_tl, list) else []
report("TEST F3: Incident Timeline Event Recorded", len(tl_events) >= 2, f"Total timeline events: {len(tl_events)}")

# TEST G
print("\n[TEST G] Responder updates progress to On the Way (En Route)...")
code_g, data_g = api_request(
    f"/api/tasks/{linked_task_id}/status",
    method="PUT",
    data={"status": "En Route", "notes": "Emergency rescue vehicle deployed with safety boat and first aid kit"},
    token=responder_token
)
report("TEST G1: Responder Updates Status to En Route HTTP 200", code_g == 200 and data_g.get("status") == "success")

code_g_task, data_g_task = api_request(f"/api/tasks/{linked_task_id}", method="GET", token=responder_token)
t_g = data_g_task.get("task", {})
report("TEST G2: Task En Route Timestamp Recorded", t_g.get("status") == "En Route" and t_g.get("enRouteAt") is not None, f"EnRouteAt: {t_g.get('enRouteAt')}")

# TEST H
print("\n[TEST H] Citizen and Admin see updated progress & notification...")
code_h_cit_notif, data_h_cit_notif = api_request("/api/notifications", method="GET", token=citizen_token)
cit_notifs_h = data_h_cit_notif if isinstance(data_h_cit_notif, list) else data_h_cit_notif.get("notifications", [])
cit_has_enroute = any(n.get("relatedIncidentId") == report_id and "En Route" in n.get("title", "") for n in cit_notifs_h)
report("TEST H1: Citizen Receives 'Rescue Unit En Route' Notification", cit_has_enroute)

code_h_adm_notif, data_h_adm_notif = api_request("/api/notifications", method="GET", token=admin_token)
adm_notifs_h = data_h_adm_notif if isinstance(data_h_adm_notif, list) else data_h_adm_notif.get("notifications", [])
adm_has_enroute = any(n.get("relatedIncidentId") == linked_task_id and "En Route" in n.get("title", "") for n in adm_notifs_h)
report("TEST H2: Admin Receives 'Unit En Route' Notification", adm_has_enroute)

code_h_rep, data_h_rep = api_request(f"/api/citizen-reports/{report_id}", method="GET", token=citizen_token)
report("TEST H3: Citizen Report Reflects Progress Stage En Route", data_h_rep.get("progressStage") == "En Route" or data_h_rep.get("status") == "In Progress")

# TEST I
print("\n[TEST I] Responder marks incident as Resolved / Completed...")
# First Arrived
api_request(
    f"/api/tasks/{linked_task_id}/status",
    method="PUT",
    data={"status": "Arrived", "notes": "Arrived at north bridge site, commencing evacuation"},
    token=responder_token
)

# Then Completed / Resolved
code_i, data_i = api_request(
    f"/api/tasks/{linked_task_id}/status",
    method="PUT",
    data={"status": "Completed", "notes": "All 14 stranded residents evacuated successfully to designated shelter."},
    token=responder_token
)
report("TEST I1: Responder Completes Task HTTP 200", code_i == 200 and data_i.get("status") == "success")

code_i_task, data_i_task = api_request(f"/api/tasks/{linked_task_id}", method="GET", token=responder_token)
t_i = data_i_task.get("task", {})
report("TEST I2: Task CompletedAt Timestamp Recorded", t_i.get("status") == "Completed" and t_i.get("completedAt") is not None, f"CompletedAt: {t_i.get('completedAt')}")

code_i_rep, data_i_rep = api_request(f"/api/citizen-reports/{report_id}", method="GET", token=citizen_token)
report("TEST I3: Citizen Report Status Synced to Resolved", data_i_rep.get("status") == "Resolved", f"Report Status: {data_i_rep.get('status')}")

# Verify responder availability returned to Available
code_i_resp_prof, data_i_resp_prof = api_request("/api/auth/profile", method="GET", token=responder_token)
avail_status = data_i_resp_prof.get("availabilityStatus") if isinstance(data_i_resp_prof, dict) else None
report("TEST I4: Responder Availability Reset to 'Available'", avail_status == "Available", f"Availability: {avail_status}")

# TEST J
print("\n[TEST J] All 3 roles display correct final incident status and notification history...")
# Citizen notifications
code_j_cit_notif, data_j_cit_notif = api_request("/api/notifications", method="GET", token=citizen_token)
cit_notifs_j = data_j_cit_notif if isinstance(data_j_cit_notif, list) else []
cit_has_resolved = any(n.get("relatedIncidentId") == report_id and "Completed" in n.get("title", "") for n in cit_notifs_j)
report("TEST J1: Citizen Notification Center Shows 'Mission Completed'", cit_has_resolved)

# Admin notifications
code_j_adm_notif, data_j_adm_notif = api_request("/api/notifications", method="GET", token=admin_token)
adm_notifs_j = data_j_adm_notif if isinstance(data_j_adm_notif, list) else []
adm_has_resolved = any(n.get("relatedIncidentId") == linked_task_id and "Completed" in n.get("title", "") for n in adm_notifs_j)
report("TEST J2: Admin Notification Center Shows 'Mission Completed'", adm_has_resolved)

# Responder notifications
code_j_rsp_notif, data_j_rsp_notif = api_request("/api/notifications", method="GET", token=responder_token)
rsp_notifs_j = data_j_rsp_notif if isinstance(data_j_rsp_notif, list) else []
report("TEST J3: Responder Notification Center Retains Assignment History", len(rsp_notifs_j) >= 1)

# TEST K
print("\n[TEST K] Refresh simulation: query every dashboard record to verify absolute persistence...")
code_k_rep, data_k_rep = api_request(f"/api/citizen-reports/{report_id}", method="GET", token=citizen_token)
report("TEST K1: Citizen Report Persisted & Resolved", code_k_rep == 200 and data_k_rep.get("status") == "Resolved")

code_k_tsk, data_k_tsk = api_request(f"/api/tasks/{linked_task_id}", method="GET", token=admin_token)
report("TEST K2: Rescue Task Persisted & Completed", code_k_tsk == 200 and data_k_tsk.get("task", {}).get("status") == "Completed")

code_k_all_rep, data_k_all_rep = api_request("/api/citizen-reports/my-reports", method="GET", token=citizen_token)
my_reports = data_k_all_rep if isinstance(data_k_all_rep, list) else []
report("TEST K3: Citizen 'My Reports' Endpoint Returns Persisted Report", any(r.get("reportId") == report_id for r in my_reports))

code_k_unreads, data_k_unreads = api_request("/api/notifications/unread-count", method="GET", token=citizen_token)
report("TEST K4: Notification Unread Count Functional", code_k_unreads == 200 and "unreadCount" in data_k_unreads)

# TEST L
print("\n[TEST L] Security and Role-Based Authorization Tests...")
# L1: Citizen tries to assign responder to report -> 403 Forbidden
code_l1, data_l1 = api_request(f"/api/citizen-reports/{report_id}/assign", method="POST", data={"responderEmail": "responder@disaster.gov.in"}, token=citizen_token)
report("TEST L1: Citizen Forbidden from Assigning Responders", code_l1 == 403, f"HTTP Code: {code_l1}")

# L2: Citizen tries to update rescue task status -> 403 Forbidden
code_l2, data_l2 = api_request(f"/api/tasks/{linked_task_id}/status", method="PUT", data={"status": "En Route"}, token=citizen_token)
report("TEST L2: Citizen Forbidden from Modifying Rescue Tasks", code_l2 == 403, f"HTTP Code: {code_l2}")

# L3: Unassigned responder (responder2) tries to modify assignment/task status -> 403 Forbidden
code_l3, data_l3 = api_request(f"/api/tasks/{linked_task_id}/status", method="PUT", data={"status": "En Route"}, token=responder2_token)
report("TEST L3: Unassigned Responder Forbidden from Modifying Task", code_l3 == 403, f"HTTP Code: {code_l3}")

# L4: Citizen tries to message another citizen directly -> 403 Forbidden
code_l4, data_l4 = api_request("/api/messages", method="POST", data={"receiverEmail": "citizen2@gmail.com", "message": "Direct message attempt"}, token=citizen_token)
report("TEST L4: Citizen Forbidden from Direct Citizen Messaging", code_l4 in (403, 400), f"HTTP Code: {code_l4}")

# L5: Citizen tries to broadcast alert via POST /api/alerts -> 403 Forbidden
code_l5, data_l5 = api_request("/api/alerts", method="POST", data={"title": "Unauthorized Alert", "message": "Spam", "region": "Karur"}, token=citizen_token)
report("TEST L5: Citizen Forbidden from Broadcasting Alerts", code_l5 == 403, f"HTTP Code: {code_l5}")

# L6: Unauthenticated request -> 401 Unauthorized
code_l6, data_l6 = api_request(f"/api/citizen-reports/{report_id}", method="GET", token=None)
report("TEST L6: Unauthenticated Access Rejected with 401", code_l6 == 401, f"HTTP Code: {code_l6}")

# L7: Role-Based Permitted Messaging: Citizen messaging Admin -> 200 OK
code_l7, data_l7 = api_request("/api/messages", method="POST", data={"receiverEmail": "admin@disaster.gov.in", "message": "Emergency dispatch status inquiry"}, token=citizen_token)
report("TEST L7: Permitted Communication: Citizen -> Admin", code_l7 in (200, 201), f"HTTP Code: {code_l7}")

# L8: Role-Based Permitted Messaging: Responder messaging assigned Citizen -> 200 OK
code_l8, data_l8 = api_request("/api/messages", method="POST", data={"receiverEmail": "citizen@gmail.com", "message": "Rescue team is arriving in 5 minutes."}, token=responder_token)
report("TEST L8: Permitted Communication: Assigned Responder -> Citizen", code_l8 in (200, 201), f"HTTP Code: {code_l8}")

# L9: Duplicate Alert Prevention: Admin creates alert, then tries creating identical active alert in same region -> 400 Bad Request
alert_title = f"Karur District Flash Flood Warning {ts}"
code_alert1, data_alert1 = api_request("/api/alerts", method="POST", data={
    "title": alert_title,
    "message": "Heavy rainfall alert and advisory notice.",
    "region": "Karur District",
    "severity": "Medium"
}, token=admin_token)
report("TEST L9a: Admin Successfully Publishes New Disaster Alert", code_alert1 in (200, 201), f"HTTP Code: {code_alert1}")

# Attempt identical active alert
code_alert2, data_alert2 = api_request("/api/alerts", method="POST", data={
    "title": alert_title,
    "message": "Duplicate submission attempt.",
    "region": "Karur District",
    "severity": "Medium"
}, token=admin_token)
is_dup_rejected = code_alert2 == 400 and "already exists" in str(data_alert2).lower()
report("TEST L9b: Duplicate Active Alert Blocked by Backend", is_dup_rejected, f"HTTP Code: {code_alert2}, Response: {data_alert2.get('message') if isinstance(data_alert2, dict) else data_alert2}")

print("\n======================================================================")
print(f"VERIFICATION COMPLETE: Passed={passed}, Failed={failed}, Total={passed + failed}")
print("======================================================================")

if failed > 0:
    sys.exit(1)
sys.exit(0)

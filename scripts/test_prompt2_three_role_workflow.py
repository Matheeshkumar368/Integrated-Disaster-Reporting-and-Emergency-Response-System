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
        print(f"  [PASS] {step_name} {('- ' + details) if details else ''}")
    else:
        failed += 1
        print(f"  [FAIL] {step_name} {('- ' + details) if details else ''}")

def login(email, password):
    r = requests.post(f"{BASE_URL}/api/auth/login", json={"email": email, "password": password})
    assert r.status_code == 200, f"Login failed for {email}: {r.text}"
    data = r.json()
    return data["token"], data

def run_e2e_test():
    global passed, failed
    print("=" * 70)
    print("PROMPT 2: COMPLETE THREE-ROLE BACKEND & DATABASE INTEGRATION TEST")
    print("=" * 70)

    # 1. Login all 3 roles
    print("\n[Step 1] Authenticating Three Distinct Roles...")
    admin_token, admin_info = login("admin@disaster.gov.in", "admin123")
    check("Admin Authentication", admin_token is not None, f"Role: {admin_info.get('role')}")

    citizen_token, citizen_info = login("citizen@gmail.com", "citizen123")
    check("Citizen Authentication", citizen_token is not None, f"Role: {citizen_info.get('role')}")

    responder_token, responder_info = login("responder@disaster.gov.in", "responder123")
    check("Responder Authentication", responder_token is not None, f"Role: {responder_info.get('role')}")

    responder2_token, responder2_info = login("responder2@disaster.gov.in", "responder123")
    check("Secondary Responder Authentication (for RBAC isolation)", responder2_token is not None)

    h_admin = {"Authorization": f"Bearer {admin_token}", "Content-Type": "application/json"}
    h_citizen = {"Authorization": f"Bearer {citizen_token}", "Content-Type": "application/json"}
    h_responder = {"Authorization": f"Bearer {responder_token}", "Content-Type": "application/json"}
    h_responder2 = {"Authorization": f"Bearer {responder2_token}", "Content-Type": "application/json"}

    # 2. Citizen submits disaster report
    print("\n[Step 2] Citizen Submits Emergency Disaster Report...")
    report_payload = {
        "title": f"Flash Flood Alert #{int(time.time())} - River Overflow",
        "message": "Water levels rising rapidly near Residential Colony Sector 4. Immediate evacuation assistance required.",
        "location": "Karur District, Sector 4",
        "latitude": 10.9601,
        "longitude": 78.0766,
        "incidentType": "Flood",
        "severity": "High"
    }
    r = requests.post(f"{BASE_URL}/api/citizen-reports", headers=h_citizen, json=report_payload)
    check("Citizen Report Submission (HTTP 200/201)", r.status_code in (200, 201), f"Status: {r.status_code}")
    report_data = r.json()
    report_id = report_data.get("reportId") or report_data.get("id")
    check("Report ID Generated", report_id is not None, f"Report ID: #{report_id}")
    check("Citizen Association Stored", report_data.get("citizenEmail") == "citizen@gmail.com")
    check("Initial Lifecycle Status: Pending", report_data.get("status") == "Pending")
    check("Initial Progress Stage: Submitted", report_data.get("progressStage") == "Submitted")

    # 3. Admin receives report & notification
    print("\n[Step 3] Admin Reviews Report & Receives Distress Notification...")
    r = requests.get(f"{BASE_URL}/api/citizen-reports/{report_id}", headers=h_admin)
    check("Admin Access to Report", r.status_code == 200)
    
    r_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_admin)
    check("Admin Notification Endpoint Functional", r_notifs.status_code == 200)
    admin_notifs = r_notifs.json()
    found_admin_notif = any(n.get("relatedIncidentId") == report_id or f"#{report_id}" in n.get("message", "") or "Flood" in n.get("title", "") for n in admin_notifs)
    check("Admin Received Report Notification", found_admin_notif, f"Total Admin Notifications: {len(admin_notifs)}")

    # 4. Admin verifies & assigns Responder to incident
    print("\n[Step 4] Admin Verifies & Assigns Responder to Incident...")
    assign_payload = {"responderEmail": "responder@disaster.gov.in"}
    r = requests.put(f"{BASE_URL}/api/citizen-reports/{report_id}/assign?responderEmail=responder@disaster.gov.in", headers=h_admin, json=assign_payload)
    check("Admin Assign Responder HTTP 200", r.status_code == 200)
    assigned_data = r.json()
    task_id = assigned_data.get("rescueTaskId") or (assigned_data.get("report") or {}).get("rescueTaskId")
    
    # If not in response body, query the report directly
    if not task_id:
        r_rep = requests.get(f"{BASE_URL}/api/citizen-reports/{report_id}", headers=h_admin).json()
        task_id = r_rep.get("rescueTaskId")
    
    check("Linked Rescue Task Generated", task_id is not None, f"Rescue Task ID: #{task_id}")
    check("Report Status Synchronized to 'Assigned'", assigned_data.get("status") == "Assigned" or (assigned_data.get("report") or {}).get("status") == "Assigned")

    # 5. Responder queries assigned tasks and receives assignment notification
    print("\n[Step 5] Responder Receives Assignment Notification & Queries Task...")
    r_resp_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_responder)
    check("Responder Notification Endpoint Functional", r_resp_notifs.status_code == 200)
    resp_notifs = r_resp_notifs.json()
    found_resp_notif = any(n.get("category") == "ASSIGNMENT" or "Assignment" in n.get("title", "") or n.get("relatedIncidentId") == task_id for n in resp_notifs)
    check("Responder Received Task Assignment Notification", found_resp_notif, f"Total Responder Notifications: {len(resp_notifs)}")

    r_my_tasks = requests.get(f"{BASE_URL}/api/tasks/my", headers=h_responder)
    check("Responder My-Tasks Endpoint HTTP 200", r_my_tasks.status_code == 200)
    tasks_list = r_my_tasks.json().get("tasks", [])
    found_task = any(t.get("taskId") == task_id for t in tasks_list)
    check("Assigned Task Present in Responder Task List", found_task, f"Total Assigned Tasks: {len(tasks_list)}")

    # 6. Responder accepts/acknowledges task
    print("\n[Step 6] Responder Acknowledges Task (Rescue Team Deployed)...")
    r = requests.put(f"{BASE_URL}/api/tasks/{task_id}/acknowledge", headers=h_responder)
    check("Responder Acknowledge Task HTTP 200", r.status_code == 200)
    ack_data = r.json().get("task", {})
    check("Task Status Changed to 'Acknowledged'", ack_data.get("status") == "Acknowledged")
    check("Task AcknowledgedAt Timestamp Persisted", ack_data.get("acknowledgedAt") is not None)

    # Verify Citizen received deployment notification
    r_cit_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=h_citizen)
    cit_notifs = r_cit_notifs.json()
    found_deploy_notif = any("Deployed" in n.get("title", "") or "accepted" in n.get("message", "") for n in cit_notifs)
    check("Citizen Received 'Rescue Team Deployed' Notification", found_deploy_notif)

    # 7. Responder updates status to 'En Route' (On the Way)
    print("\n[Step 7] Responder Updates Status to 'En Route'...")
    r = requests.put(f"{BASE_URL}/api/tasks/{task_id}/status", headers=h_responder, json={
        "status": "En Route",
        "notes": "Rescue vehicle dispatched with inflatable boats and medical kit"
    })
    check("Responder Status Update to 'En Route' HTTP 200", r.status_code == 200)
    enroute_data = r.json().get("task", {})
    check("Task Status: En Route", enroute_data.get("status") == "En Route")
    check("Task EnRouteAt Timestamp Persisted", enroute_data.get("enRouteAt") is not None)

    # 8. Responder updates status to 'Arrived' (On Scene)
    print("\n[Step 8] Responder Updates Status to 'Arrived' (On Scene)...")
    r = requests.put(f"{BASE_URL}/api/tasks/{task_id}/status", headers=h_responder, json={
        "status": "Arrived",
        "notes": "On scene at Riverbank Colony. Initiating evacuation of residents"
    })
    check("Responder Status Update to 'Arrived' HTTP 200", r.status_code == 200)
    arrived_data = r.json().get("task", {})
    check("Task Status: Arrived", arrived_data.get("status") == "Arrived")
    check("Task ArrivedAt Timestamp Persisted", arrived_data.get("arrivedAt") is not None)

    # 9. Responder updates status to 'In Progress'
    print("\n[Step 9] Responder Updates Status to 'In Progress'...")
    r = requests.put(f"{BASE_URL}/api/tasks/{task_id}/status", headers=h_responder, json={
        "status": "In Progress",
        "notes": "14 residents evacuated safely to relief shelter"
    })
    check("Responder Status Update to 'In Progress' HTTP 200", r.status_code == 200)
    check("Task Status: In Progress", r.json().get("task", {}).get("status") == "In Progress")

    # 10. Responder resolves task (Completed)
    print("\n[Step 10] Responder Marks Operation Completed...")
    r = requests.put(f"{BASE_URL}/api/tasks/{task_id}/resolve", headers=h_responder, json={
        "summary": "Evacuation complete. All 22 residents relocated. No casualties."
    })
    check("Responder Resolve Task HTTP 200", r.status_code == 200)
    comp_data = r.json().get("task", {})
    check("Task Status: Completed / Resolved", comp_data.get("status") in ("Completed", "Resolved"))
    check("Task CompletedAt Timestamp Persisted", comp_data.get("completedAt") is not None)

    # 11. Admin verifies and closes incident
    print("\n[Step 11] Admin Verifies & Closes Disaster Incident...")
    r = requests.put(f"{BASE_URL}/api/citizen-reports/{report_id}/resolve", headers=h_admin)
    check("Admin Resolve Report HTTP 200", r.status_code == 200)
    final_report = r.json()
    check("Final Incident Status: Resolved", final_report.get("status") == "Resolved")
    check("Final Incident Progress Stage: Resolved", final_report.get("progressStage") == "Resolved")

    # 12. Refresh Simulation: Verify Persistence in MySQL
    print("\n[Step 12] Refresh Simulation & Database Persistence Checks...")
    r_check_rep = requests.get(f"{BASE_URL}/api/citizen-reports/{report_id}", headers=h_citizen).json()
    check("Citizen Report Persisted in MySQL with Status 'Resolved'", r_check_rep.get("status") == "Resolved")

    r_check_task = requests.get(f"{BASE_URL}/api/tasks/{task_id}", headers=h_admin).json()
    task_obj = r_check_task.get("task", {})
    check("Rescue Task Persisted in MySQL with Status 'Completed'", task_obj.get("status") in ("Completed", "Resolved"))

    r_check_my = requests.get(f"{BASE_URL}/api/citizen-reports/my-reports", headers=h_citizen).json()
    check("Citizen 'My Reports' Endpoint Returns Persisted Record", any(r.get("reportId") == report_id for r in r_check_my))

    # 13. Security and Role-Based Authorization Checks (RBAC)
    print("\n[Step 13] Role-Based Access Control (RBAC) Security Verification...")
    # Citizen cannot assign responders
    r = requests.put(f"{BASE_URL}/api/citizen-reports/{report_id}/assign?responderEmail=responder@disaster.gov.in", headers=h_citizen)
    check("Citizen Forbidden from Assigning Responders (HTTP 403)", r.status_code == 403)

    # Citizen cannot modify rescue tasks
    r = requests.put(f"{BASE_URL}/api/tasks/{task_id}/status", headers=h_citizen, json={"status": "Pending"})
    check("Citizen Forbidden from Modifying Rescue Tasks (HTTP 403)", r.status_code == 403)

    # Unassigned responder cannot modify another unit's task
    r = requests.put(f"{BASE_URL}/api/tasks/{task_id}/status", headers=h_responder2, json={"status": "In Progress"})
    check("Unassigned Responder Forbidden from Modifying Task (HTTP 403)", r.status_code == 403)

    # Unauthenticated access rejected with 401
    r = requests.get(f"{BASE_URL}/api/citizen-reports/my-reports")
    check("Unauthenticated Access Rejected with 401 Unauthorized", r.status_code == 401)

    print("\n" + "=" * 70)
    print(f"VERIFICATION RESULTS: Passed={passed}, Failed={failed}, Total={passed + failed}")
    print("=" * 70)

    if failed > 0:
        sys.exit(1)
    else:
        sys.exit(0)

if __name__ == "__main__":
    run_e2e_test()

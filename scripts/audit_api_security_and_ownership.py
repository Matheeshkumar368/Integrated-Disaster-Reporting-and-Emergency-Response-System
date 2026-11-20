import requests
import json
import sys
import time

BASE_URL = "http://localhost:8080"
passed = 0
failed = 0
results = []

def check(test_name, condition, details=""):
    global passed, failed
    status = "PASS" if condition else "FAIL"
    if condition:
        passed += 1
    else:
        failed += 1
    results.append({"name": test_name, "status": status, "details": details})
    print(f"[{status}] {test_name}{' - ' + details if details else ''}")

def login(email, password):
    r = requests.post(f"{BASE_URL}/api/auth/login", json={"email": email, "password": password})
    if r.status_code == 200:
        return r.json().get("token"), r.json()
    return None, None

def run_security_and_ownership_audit():
    print("=" * 80)
    print("STARTING ADVANCED API SECURITY, DATABASE OWNERSHIP & DATA CONSISTENCY AUDIT")
    print("=" * 80)

    # 1. Authenticate All Test Personas
    print("\n--- 1. Authenticating Roles ---")
    admin_token, admin_info = login("admin@disaster.gov.in", "admin123")
    check("Admin authentication succeeds", admin_token is not None and admin_info.get("role") == "Admin")

    citizen_token, citizen_info = login("citizen@gmail.com", "citizen123")
    check("Citizen A authentication succeeds", citizen_token is not None and citizen_info.get("role") == "Citizen")

    # Let's ensure a secondary citizen exists for ownership testing
    r_reg = requests.post(f"{BASE_URL}/api/auth/register", json={
        "name": "Citizen Two",
        "email": "citizen2@gmail.com",
        "password": "citizen123",
        "role": "Citizen",
        "phone": "+919876543210"
    })
    citizen2_token, citizen2_info = login("citizen2@gmail.com", "citizen123")
    check("Citizen B (Secondary Citizen) authentication succeeds", citizen2_token is not None)

    responder_token, responder_info = login("responder@disaster.gov.in", "responder123")
    check("Responder A authentication succeeds", responder_token is not None and responder_info.get("role") == "Responder")

    responder2_token, responder2_info = login("responder2@disaster.gov.in", "responder123")
    check("Responder B authentication succeeds", responder2_token is not None and responder2_info.get("role") == "Responder")

    h_admin = {"Authorization": f"Bearer {admin_token}", "Content-Type": "application/json"}
    h_cit1 = {"Authorization": f"Bearer {citizen_token}", "Content-Type": "application/json"}
    h_cit2 = {"Authorization": f"Bearer {citizen2_token}", "Content-Type": "application/json"}
    h_resp1 = {"Authorization": f"Bearer {responder_token}", "Content-Type": "application/json"}
    h_resp2 = {"Authorization": f"Bearer {responder2_token}", "Content-Type": "application/json"}

    # =========================================================================
    # AREA 3: API SECURITY & ROLE-BASED ACCESS CONTROL (RBAC)
    # =========================================================================
    print("\n--- AREA 3: API Security & Role-Based Access Control (RBAC) ---")

    # 3.1 Unauthenticated requests to protected endpoints return 401 Unauthorized
    r_unauth1 = requests.get(f"{BASE_URL}/api/notifications")
    check("Unauthenticated GET /api/notifications returns 401 Unauthorized", r_unauth1.status_code == 401, f"Status: {r_unauth1.status_code}")

    r_unauth2 = requests.get(f"{BASE_URL}/api/tasks")
    check("Unauthenticated GET /api/tasks returns 401 Unauthorized", r_unauth2.status_code == 401, f"Status: {r_unauth2.status_code}")

    r_unauth3 = requests.post(f"{BASE_URL}/api/citizen-reports", json={"title": "Test", "message": "Test"})
    check("Unauthenticated POST /api/citizen-reports returns 401 Unauthorized", r_unauth3.status_code == 401, f"Status: {r_unauth3.status_code}")

    # 3.2 Invalid or malformed token returns 401 Unauthorized
    h_bad = {"Authorization": "Bearer InvalidTokenHeaderPayloadSignature.123"}
    r_bad_jwt = requests.get(f"{BASE_URL}/api/notifications", headers=h_bad)
    check("Malformed JWT returns 401 Unauthorized", r_bad_jwt.status_code == 401, f"Status: {r_bad_jwt.status_code}")

    # 3.3 Citizen role accessing Admin-only endpoints returns 403 Forbidden
    r_cit_admin1 = requests.get(f"{BASE_URL}/api/auth/users", headers=h_cit1)
    check("Citizen accessing GET /api/auth/users returns 403 Forbidden", r_cit_admin1.status_code == 403, f"Status: {r_cit_admin1.status_code}")

    r_cit_admin2 = requests.post(f"{BASE_URL}/api/tasks", headers=h_cit1, json={"title": "Unauthorized Task"})
    check("Citizen creating RescueTask returns 403 Forbidden", r_cit_admin2.status_code == 403, f"Status: {r_cit_admin2.status_code}")

    r_cit_admin3 = requests.delete(f"{BASE_URL}/api/alerts/999", headers=h_cit1)
    check("Citizen deleting Alert returns 403 Forbidden", r_cit_admin3.status_code == 403, f"Status: {r_cit_admin3.status_code}")

    # 3.4 Citizen role accessing Responder-only endpoints returns 403 Forbidden
    r_cit_resp = requests.put(f"{BASE_URL}/api/responder/location/status", headers=h_cit1, json={"status": "Available"})
    check("Citizen updating Responder Location returns 403 Forbidden", r_cit_resp.status_code == 403, f"Status: {r_cit_resp.status_code}")

    # 3.5 Responder role accessing Admin-only endpoints returns 403 Forbidden
    r_resp_admin = requests.get(f"{BASE_URL}/api/auth/users", headers=h_resp1)
    check("Responder accessing GET /api/auth/users returns 403 Forbidden", r_resp_admin.status_code == 403, f"Status: {r_resp_admin.status_code}")

    # =========================================================================
    # AREA 4: DATABASE OWNERSHIP & DATA PRIVACY ISOLATION
    # =========================================================================
    print("\n--- AREA 4: Database Ownership & Cross-User Privacy Isolation ---")

    # 4.1 Citizen A submits a private disaster report
    rep_payload = {
        "disasterType": "Flood",
        "title": "Flash flood near River Bridge",
        "message": "Critical flash flood near River Bridge. Need urgent evacuation.",
        "location": "River Bridge, Sector 4",
        "latitude": 13.0827,
        "longitude": 80.2707,
        "severity": "High"
    }
    r_rep1 = requests.post(f"{BASE_URL}/api/citizen-reports", headers=h_cit1, json=rep_payload)
    check("Citizen A submits disaster report", r_rep1.status_code in [200, 201], f"Status: {r_rep1.status_code}")
    rep1_id = r_rep1.json().get("reportId") or r_rep1.json().get("id")

    # 4.2 Citizen A can view their own report
    r_view_own = requests.get(f"{BASE_URL}/api/citizen-reports/{rep1_id}", headers=h_cit1)
    check("Citizen A can retrieve their own report", r_view_own.status_code == 200, f"ID: {rep1_id}")

    # 4.3 Citizen B CANNOT view Citizen A's report (Direct ID Access - Horizontal Privilege Escalation Test)
    r_cit2_tamper = requests.get(f"{BASE_URL}/api/citizen-reports/{rep1_id}", headers=h_cit2)
    check(
        "Citizen B is FORBIDDEN from viewing Citizen A's report",
        r_cit2_tamper.status_code == 403,
        f"Status: {r_cit2_tamper.status_code} (Expected 403)"
    )

    # 4.4 Citizen A and B /my-reports list isolation
    r_my1 = requests.get(f"{BASE_URL}/api/citizen-reports/my-reports", headers=h_cit1)
    r_my2 = requests.get(f"{BASE_URL}/api/citizen-reports/my-reports", headers=h_cit2)
    my1_reports = r_my1.json() if r_my1.status_code == 200 and isinstance(r_my1.json(), list) else []
    my2_reports = r_my2.json() if r_my2.status_code == 200 and isinstance(r_my2.json(), list) else []
    my1_ids = [r.get("reportId") or r.get("id") for r in my1_reports]
    my2_ids = [r.get("reportId") or r.get("id") for r in my2_reports]
    check("Citizen A's reports list contains Report 1", rep1_id in my1_ids)
    check("Citizen B's reports list DOES NOT contain Citizen A's Report 1", rep1_id not in my2_ids)

    # =========================================================================
    # AREA 7: DATA CONSISTENCY & NOTIFICATION AUDIT ACROSS ROLES
    # =========================================================================
    print("\n--- AREA 7: Data Consistency & Cross-Role Notification Lifecycle ---")

    # 7.1 Admin assigns Responder 1 to Citizen A's report
    assign_url = f"{BASE_URL}/api/citizen-reports/{rep1_id}/assign?responderEmail=responder@disaster.gov.in"
    r_assign = requests.put(assign_url, headers=h_admin, json={"responderEmail": "responder@disaster.gov.in"})
    check("Admin assigns Responder A to Citizen Report 1", r_assign.status_code == 200, f"Status: {r_assign.status_code}")

    # 7.2 Check that Citizen A receives an automated notification about assignment
    time.sleep(1)
    r_notifs_cit1 = requests.get(f"{BASE_URL}/api/notifications", headers=h_cit1)
    notifs_cit1 = r_notifs_cit1.json() if r_notifs_cit1.status_code == 200 and isinstance(r_notifs_cit1.json(), list) else []
    cit1_notif_ids = [n.get("id") for n in notifs_cit1]
    check("Citizen A receives notification of responder dispatch", len(notifs_cit1) > 0, f"Total notifications: {len(notifs_cit1)}")

    # 7.3 Notification Ownership Isolation: Citizen B CANNOT read or delete Citizen A's notification
    if cit1_notif_ids:
        target_notif_id = cit1_notif_ids[0]
        r_hack_notif = requests.put(f"{BASE_URL}/api/notifications/{target_notif_id}/read", headers=h_cit2)
        check(
            "Citizen B cannot mark Citizen A's notification as read (HTTP 403 Forbidden)",
            r_hack_notif.status_code == 403,
            f"Status: {r_hack_notif.status_code}"
        )

        r_del_notif = requests.delete(f"{BASE_URL}/api/notifications/{target_notif_id}", headers=h_cit2)
        check(
            "Citizen B cannot delete Citizen A's notification (HTTP 403 Forbidden)",
            r_del_notif.status_code == 403,
            f"Status: {r_del_notif.status_code}"
        )

    # 7.4 Responder A views pending/assigned tasks and finds Report 1
    r_resp_tasks = requests.get(f"{BASE_URL}/api/citizen-reports/pending", headers=h_resp1)
    check("Responder A can access assigned task/incident queue", r_resp_tasks.status_code == 200)

    # 7.5 Responder A acknowledges the assignment
    r_ack = requests.put(f"{BASE_URL}/api/citizen-reports/{rep1_id}/acknowledge", headers=h_resp1)
    check("Responder A acknowledges Citizen Report 1", r_ack.status_code == 200)

    # 7.6 Citizen A observes updated report status in real time
    r_cit_check = requests.get(f"{BASE_URL}/api/citizen-reports/{rep1_id}", headers=h_cit1)
    cur_status = r_cit_check.json().get("status") if r_cit_check.status_code == 200 else ""
    check("Citizen A observes updated report status in real time", r_cit_check.status_code == 200 and cur_status.upper() in ["ASSIGNED", "ACKNOWLEDGED", "IN_PROGRESS"], f"Current Status: {cur_status}")

    # 7.7 Responder A resolves the report
    r_res = requests.put(f"{BASE_URL}/api/citizen-reports/{rep1_id}/resolve", headers=h_resp1, json={"notes": "Victims safely evacuated to relief center."})
    check("Responder A resolves Citizen Report 1", r_res.status_code == 200)

    # 7.8 Verify final resolution state in database
    r_final = requests.get(f"{BASE_URL}/api/citizen-reports/{rep1_id}", headers=h_admin)
    final_status = r_final.json().get("status") if r_final.status_code == 200 else ""
    check("Database reflects final report status 'RESOLVED'", r_final.status_code == 200 and final_status.upper() == "RESOLVED", f"Final Status: {final_status}")

    # =========================================================================
    # AREA 6: ERROR HANDLING & RESILIENCE
    # =========================================================================
    print("\n--- AREA 6: Error Handling & API Resilience ---")

    # 6.1 Entity not found returns 404
    r_404 = requests.get(f"{BASE_URL}/api/citizen-reports/999999", headers=h_admin)
    check("Non-existent report ID returns 404 Not Found", r_404.status_code == 404, f"Status: {r_404.status_code}")

    # 6.2 Malformed request body handled gracefully
    r_bad_body = requests.post(f"{BASE_URL}/api/auth/login", data="this is not valid json", headers={"Content-Type": "application/json"})
    check("Malformed JSON request returns 400 Bad Request (not unhandled 500)", r_bad_body.status_code == 400, f"Status: {r_bad_body.status_code}")

    print("\n" + "=" * 80)
    print("API SECURITY & DATABASE OWNERSHIP AUDIT SUMMARY")
    print("=" * 80)
    print(f"Total Checks: {len(results)}")
    print(f"PASSED: {passed}")
    print(f"FAILED: {failed}")
    rate = round((passed / len(results)) * 100) if results else 0
    print(f"SUCCESS RATE: {rate}%")

    with open("scripts/api_security_audit_results.json", "w") as f:
        json.dump(results, f, indent=2)

    if failed > 0:
        sys.exit(1)

if __name__ == "__main__":
    run_security_and_ownership_audit()

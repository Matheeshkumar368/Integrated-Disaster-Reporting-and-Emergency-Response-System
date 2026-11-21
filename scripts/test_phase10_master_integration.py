#!/usr/bin/env python3
"""
Phase 10: Master Automated Integration Test Suite
Integrated Disaster Reporting and Emergency Response Coordination System

Validates the 14 mandatory cross-role integration tests:
1. Citizen submits report -> 200 OK, report in DB, Notification created for Admin.
2. Admin creates/broadcasts alert -> 200 OK, alert in DB, Notification created for Citizen and Responder.
3. Citizen attempts alert broadcast -> 403 Forbidden.
4. Responder attempts alert broadcast -> 403 Forbidden.
5. Admin assigns rescue task to Responder -> 200 OK, task status ASSIGNED, Notification created for assigned Responder.
6. Unselected Responder does NOT receive task assignment -> Verify unselected responder notifications DB count unchanged.
7. Responder acknowledges task -> 200 OK, task status ACKNOWLEDGED.
8. Responder updates mission status to EN_ROUTE -> 200 OK, task status EN_ROUTE, Notification created for Admin and Citizen.
9. Responder updates mission status to COMPLETED -> 200 OK, task status COMPLETED, Notification created for Admin and Citizen.
10. Notification isolation: Citizen reads own notifications -> 200 OK, contains only Citizen notifications, no Admin or Responder notifications.
11. Notification isolation: Citizen marks own notification read -> 200 OK. Citizen attempts to mark Admin notification read -> 403 Forbidden.
12. Notification isolation: Citizen attempts to delete Admin notification -> 403 Forbidden.
13. Invalid JWT token request -> 401 Unauthorized.
14. Expired JWT token request -> 401 Unauthorized.
"""

import urllib.request
import json
import time
import subprocess
import sys
import base64
import hmac
import hashlib

BASE = 'http://localhost:8080'
MYSQL_PATH = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
DB_PASS = "368fire368boy"
JWT_SECRET = "disaster-management-academic-super-secure-jwt-secret-key-2026-disaster-response-system-368"

def query_mysql(sql):
    try:
        cmd = [MYSQL_PATH, "-u", "root", f"-p{DB_PASS}", "disaster_management", "-e", sql]
        res = subprocess.run(cmd, capture_output=True, text=True, check=True)
        return res.stdout
    except Exception as e:
        return f"ERROR: {e}"

def login(email, password):
    data = json.dumps({'email': email, 'password': password}).encode()
    req = urllib.request.Request(
        f'{BASE}/api/auth/login', data=data,
        headers={'Content-Type': 'application/json'}
    )
    resp = urllib.request.urlopen(req)
    return json.loads(resp.read())

def api(method, path, token=None, body=None, extra_headers=None):
    data = json.dumps(body).encode() if body is not None else None
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = f'Bearer {token}'
    if extra_headers:
        headers.update(extra_headers)
    req = urllib.request.Request(
        f'{BASE}{path}', data=data,
        headers=headers,
        method=method
    )
    try:
        resp = urllib.request.urlopen(req)
        raw = resp.read()
        return (json.loads(raw) if raw else {}), resp.status
    except urllib.request.HTTPError as e:
        raw = e.read()
        try:
            return json.loads(raw), e.code
        except Exception:
            return {'raw': raw.decode('utf-8', errors='ignore')}, e.code
    except Exception as ex:
        return {'error': str(ex)}, 0

def make_jwt(payload, secret):
    header = {"alg": "HS256", "typ": "JWT"}
    b64_header = base64.urlsafe_b64encode(json.dumps(header).encode()).decode().rstrip('=')
    b64_payload = base64.urlsafe_b64encode(json.dumps(payload).encode()).decode().rstrip('=')
    msg = f"{b64_header}.{b64_payload}".encode()
    sig = hmac.new(secret.encode(), msg, hashlib.sha256).digest()
    b64_sig = base64.urlsafe_b64encode(sig).decode().rstrip('=')
    return f"{b64_header}.{b64_payload}.{b64_sig}"

passed = 0
failed = 0

def check(num, name, condition, detail=''):
    global passed, failed
    if condition:
        passed += 1
        print(f"  [PASS] Test {num:02d}: {name}" + (f" -> {detail}" if detail else ""))
    else:
        failed += 1
        print(f"  [FAIL] Test {num:02d}: {name}" + (f" -> {detail}" if detail else ""))

print("=" * 80)
print("PHASE 10: AUTOMATED INTEGRATION TEST SUITE (14 MANDATORY TESTS)")
print("=" * 80)

# 0. Authenticate all test roles
print("\n[Step 0] Logging in baseline test accounts...")
admin_auth = login('admin@disaster.gov.in', 'admin123')
citizen_auth = login('citizen@gmail.com', 'citizen123')
responder_auth = login('responder@disaster.gov.in', 'responder123')
responder2_auth = login('responder2@disaster.gov.in', 'responder123')

t_admin = admin_auth.get('token')
t_citizen = citizen_auth.get('token')
t_responder = responder_auth.get('token')
t_responder2 = responder2_auth.get('token')

print(f"  Admin token acquired: {bool(t_admin)}")
print(f"  Citizen token acquired: {bool(t_citizen)}")
print(f"  Responder 1 token acquired: {bool(t_responder)}")
print(f"  Responder 2 token acquired: {bool(t_responder2)}")

ts = int(time.time())

# -------------------------------------------------------------
# Test 1: Citizen submits report -> 200 OK, report in DB, Notification created for Admin.
# -------------------------------------------------------------
print("\n--- TEST 01: Citizen submits report ---")
report_payload = {
    'message': f'Distress SOS {ts}: Severe inundation in Gandhi Nagar Sector 4. Immediate evacuation requested.',
    'location': 'Gandhi Nagar Sector 4',
    'incidentType': 'Flood',
    'severity': 'High'
}
res_rep, status_rep = api('POST', '/api/citizen-reports', t_citizen, report_payload)
report_id = res_rep.get('reportId')

# Verify in DB
sql_rep = query_mysql(f"SELECT report_id, citizen_email, status, message FROM citizen_reports WHERE report_id = {report_id};") if report_id else ""
sql_admin_notif = query_mysql(f"SELECT id, recipient_email, title FROM notifications WHERE recipient_role = 'Admin' AND related_incident_id = {report_id} ORDER BY created_at DESC LIMIT 1;") if report_id else ""

check(1, "Citizen submits report", 
      status_rep == 200 and report_id is not None and "citizen@gmail.com" in sql_rep and "admin@disaster.gov.in" in sql_admin_notif,
      f"Report #{report_id}, MySQL status: Pending, Admin notified")

# -------------------------------------------------------------
# Test 2: Admin creates/broadcasts alert -> 200 OK, alert in DB, Notification created for Citizen and Responder.
# -------------------------------------------------------------
print("\n--- TEST 02: Admin creates/broadcasts alert ---")
# Get admin step-up token for High/Critical alert
pin_res, _ = api('POST', '/api/auth/admin/verify-pin', t_admin, {'pin': '123456'})
step_up_token = pin_res.get('stepUpToken')
headers_admin = {'X-Admin-Step-Up-Token': step_up_token} if step_up_token else {}

alert_payload = {
    'title': f'Emergency Warning {ts}: Tropical Storm Landfall',
    'message': 'Severe cyclonic storm approaching coast. Take shelter immediately.',
    'region': 'Tamil Nadu',
    'severity': 'Critical',
    'disasterType': 'Cyclone'
}
res_alert, status_alert = api('POST', '/api/alerts', t_admin, alert_payload, extra_headers=headers_admin)
alert_id = (res_alert.get('alert') or {}).get('alertId')

# Verify in DB
sql_alert = query_mysql(f"SELECT alert_id, title, status, broadcasted FROM alerts WHERE alert_id = {alert_id};") if alert_id else ""
sql_c_bc_notif = query_mysql(f"SELECT id, recipient_email FROM notifications WHERE recipient_email = 'citizen@gmail.com' AND related_incident_id = {alert_id} AND related_incident_type = 'ALERT';") if alert_id else ""
sql_r_bc_notif = query_mysql(f"SELECT id, recipient_email FROM notifications WHERE recipient_email = 'responder@disaster.gov.in' AND related_incident_id = {alert_id} AND related_incident_type = 'ALERT';") if alert_id else ""

check(2, "Admin creates and broadcasts alert",
      status_alert == 200 and alert_id is not None and "Active" in sql_alert and "citizen@gmail.com" in sql_c_bc_notif and "responder@disaster.gov.in" in sql_r_bc_notif,
      f"Alert #{alert_id}, Broadcasted in MySQL, Citizen & Responder notified")

# -------------------------------------------------------------
# Test 3: Citizen attempts alert broadcast -> 403 Forbidden.
# -------------------------------------------------------------
print("\n--- TEST 03: Citizen attempts alert broadcast ---")
res_c_bc, status_c_bc = api('POST', '/api/alerts', t_citizen, {'title': 'Citizen Fake Alert', 'message': 'Hacked', 'region': 'Tamil Nadu'})
check(3, "Citizen attempts alert broadcast",
      status_c_bc == 403,
      f"HTTP {status_c_bc} Forbidden")

# -------------------------------------------------------------
# Test 4: Responder attempts alert broadcast -> 403 Forbidden.
# -------------------------------------------------------------
print("\n--- TEST 04: Responder attempts alert broadcast ---")
res_r_bc, status_r_bc = api('POST', '/api/alerts', t_responder, {'title': 'Responder Fake Alert', 'message': 'Hacked', 'region': 'Tamil Nadu'})
check(4, "Responder attempts alert broadcast",
      status_r_bc == 403,
      f"HTTP {status_r_bc} Forbidden")

# -------------------------------------------------------------
# Test 5: Admin assigns rescue task to Responder -> 200 OK, task status ASSIGNED, Notification created for assigned Responder.
# -------------------------------------------------------------
print("\n--- TEST 05: Admin assigns rescue task to Responder ---")
# First, verify report so it is ready for assignment
api('PUT', f'/api/citizen-reports/{report_id}/verify', t_admin)

# Record unselected responder (responder2) notification count before assignment
count_r2_before = int(query_mysql("SELECT COUNT(*) FROM notifications WHERE recipient_email = 'responder2@disaster.gov.in';").strip().split()[-1])

# Assign responder 1
assign_res, status_assign = api('POST', f'/api/citizen-reports/{report_id}/assign', t_admin, {'responderEmail': 'responder@disaster.gov.in'})

# Fetch linked task_id from database
sql_task = query_mysql(f"SELECT task_id, status, assigned_to, citizen_report_id FROM rescue_tasks WHERE citizen_report_id = {report_id} ORDER BY task_id DESC LIMIT 1;")
task_id = None
try:
    task_id = int(sql_task.strip().split('\n')[1].split('\t')[0])
except Exception:
    pass

sql_r_assign_notif = query_mysql(f"SELECT id, recipient_email, title FROM notifications WHERE recipient_email = 'responder@disaster.gov.in' AND related_incident_id = {report_id};") if report_id else ""

check(5, "Admin assigns rescue task to Responder",
      status_assign == 200 and task_id is not None and "Assigned" in sql_task and "responder@disaster.gov.in" in sql_r_assign_notif,
      f"Task #{task_id}, status: Assigned, Notification created for responder@disaster.gov.in")

# -------------------------------------------------------------
# Test 6: Unselected Responder does NOT receive task assignment
# -------------------------------------------------------------
print("\n--- TEST 06: Unselected Responder does NOT receive task assignment ---")
count_r2_after = int(query_mysql("SELECT COUNT(*) FROM notifications WHERE recipient_email = 'responder2@disaster.gov.in';").strip().split()[-1])
check(6, "Unselected Responder does NOT receive task assignment",
      count_r2_before == count_r2_after,
      f"responder2@disaster.gov.in notification count unchanged: {count_r2_before} == {count_r2_after}")

# -------------------------------------------------------------
# Test 7: Responder acknowledges task -> 200 OK, task status ACKNOWLEDGED.
# -------------------------------------------------------------
print("\n--- TEST 07: Responder acknowledges task ---")
res_ack, status_ack = api('POST', f'/api/tasks/{task_id}/acknowledge', t_responder)
sql_ack = query_mysql(f"SELECT task_id, status, acknowledged_at FROM rescue_tasks WHERE task_id = {task_id};")
check(7, "Responder acknowledges task",
      status_ack == 200 and "Acknowledged" in sql_ack,
      f"HTTP {status_ack}, MySQL status: Acknowledged")

# -------------------------------------------------------------
# Test 8: Responder updates mission status to EN_ROUTE -> 200 OK, task status EN_ROUTE, Notification created for Admin and Citizen.
# -------------------------------------------------------------
print("\n--- TEST 08: Responder updates mission status to EN_ROUTE ---")
res_enroute, status_enroute = api('PUT', f'/api/tasks/{task_id}/status', t_responder, {'status': 'En Route', 'notes': 'Dispatched rescue boat to Gandhi Nagar'})
sql_enroute = query_mysql(f"SELECT task_id, status FROM rescue_tasks WHERE task_id = {task_id};")
sql_c_enroute_notif = query_mysql(f"SELECT id, recipient_email, title FROM notifications WHERE recipient_email = 'citizen@gmail.com' AND (related_incident_id = {task_id} OR related_incident_id = {report_id}) AND (title LIKE '%Deploy%' OR title LIKE '%Update%') ORDER BY created_at DESC LIMIT 1;")
sql_a_enroute_notif = query_mysql(f"SELECT id, recipient_email, title FROM notifications WHERE recipient_role = 'Admin' AND (related_incident_id = {task_id} OR related_incident_id = {report_id}) ORDER BY created_at DESC LIMIT 1;")

check(8, "Responder updates mission status to EN_ROUTE",
      status_enroute == 200 and "En Route" in sql_enroute and "citizen@gmail.com" in sql_c_enroute_notif and "admin@disaster.gov.in" in sql_a_enroute_notif,
      f"HTTP {status_enroute}, MySQL status: En Route, Admin and Citizen notified")

# -------------------------------------------------------------
# Test 9: Responder updates mission status to COMPLETED -> 200 OK, task status COMPLETED, Notification created for Admin and Citizen.
# -------------------------------------------------------------
print("\n--- TEST 09: Responder updates mission status to COMPLETED ---")
res_comp, status_comp = api('POST', f'/api/tasks/{task_id}/resolve', t_responder, {'summary': 'All civilians successfully rescued and relocated to relief camp.'})
sql_comp = query_mysql(f"SELECT task_id, status, completion_summary FROM rescue_tasks WHERE task_id = {task_id};")
sql_c_comp_notif = query_mysql(f"SELECT id, recipient_email, title FROM notifications WHERE recipient_email = 'citizen@gmail.com' AND (related_incident_id = {task_id} OR related_incident_id = {report_id}) AND title LIKE '%Completed%' ORDER BY created_at DESC LIMIT 1;")
sql_a_comp_notif = query_mysql(f"SELECT id, recipient_email, title FROM notifications WHERE recipient_role = 'Admin' AND (related_incident_id = {task_id} OR related_incident_id = {report_id}) AND title LIKE '%Completed%' ORDER BY created_at DESC LIMIT 1;")

check(9, "Responder updates mission status to COMPLETED",
      status_comp == 200 and "Completed" in sql_comp and "citizen@gmail.com" in sql_c_comp_notif and "admin@disaster.gov.in" in sql_a_comp_notif,
      f"HTTP {status_comp}, MySQL status: Completed, Admin and Citizen notified")

# -------------------------------------------------------------
# Test 10: Notification isolation: Citizen reads own notifications -> 200 OK, contains only Citizen notifications, no Admin or Responder notifications.
# -------------------------------------------------------------
print("\n--- TEST 10: Notification isolation: Citizen reads own notifications ---")
res_c_notifs, status_c_notifs = api('GET', '/api/notifications', t_citizen)
c_recipients = set(n.get('recipientEmail', '').lower() for n in res_c_notifs) if isinstance(res_c_notifs, list) else set()
check(10, "Citizen reads own notifications with strict role isolation",
      status_c_notifs == 200 and len(res_c_notifs) > 0 and c_recipients == {'citizen@gmail.com'},
      f"HTTP 200, {len(res_c_notifs)} notifications, strictly matching recipient 'citizen@gmail.com'")

# -------------------------------------------------------------
# Test 11: Notification isolation: Citizen marks own notification read -> 200 OK. Citizen attempts to mark Admin notification read -> 403 Forbidden.
# -------------------------------------------------------------
print("\n--- TEST 11: Notification isolation: Citizen marks own read (200), attempts to mark Admin notification read (403) ---")
# Pick one citizen notification
cit_notif_id = res_c_notifs[0].get('notificationId') or res_c_notifs[0].get('id')
res_c_mark, status_c_mark = api('PUT', f'/api/notifications/{cit_notif_id}/read', t_citizen)

# Pick an admin notification
admin_notifs, _ = api('GET', '/api/notifications', t_admin)
admin_notif_id = admin_notifs[0].get('notificationId') or admin_notifs[0].get('id')
res_cross_mark, status_cross_mark = api('PUT', f'/api/notifications/{admin_notif_id}/read', t_citizen)

check(11, "Citizen mark own read (200) vs Admin notification (403)",
      status_c_mark == 200 and status_cross_mark == 403,
      f"Own notification: HTTP {status_c_mark}, Cross-role Admin notification: HTTP {status_cross_mark} Forbidden")

# -------------------------------------------------------------
# Test 12: Notification isolation: Citizen attempts to delete Admin notification -> 403 Forbidden.
# -------------------------------------------------------------
print("\n--- TEST 12: Notification isolation: Citizen attempts to delete Admin notification ---")
res_cross_del, status_cross_del = api('DELETE', f'/api/notifications/{admin_notif_id}', t_citizen)
check(12, "Citizen attempts to delete Admin notification",
      status_cross_del == 403,
      f"HTTP {status_cross_del} Forbidden")

# -------------------------------------------------------------
# Test 13: Invalid JWT token request -> 401 Unauthorized.
# -------------------------------------------------------------
print("\n--- TEST 13: Invalid JWT token request ---")
res_inv, status_inv = api('GET', '/api/notifications', 'tampered.or.completely.invalid.jwt.token')
check(13, "Invalid JWT token rejected",
      status_inv == 401,
      f"HTTP {status_inv} Unauthorized")

# -------------------------------------------------------------
# Test 14: Expired JWT token request -> 401 Unauthorized.
# -------------------------------------------------------------
print("\n--- TEST 14: Expired JWT token request ---")
expired_payload = {
    'sub': 'citizen@gmail.com',
    'role': 'Citizen',
    'iat': 1000,
    'exp': 1000  # Expired in 1970
}
expired_token = make_jwt(expired_payload, JWT_SECRET)
res_exp, status_exp = api('GET', '/api/notifications', expired_token)
check(14, "Expired JWT token rejected",
      status_exp == 401,
      f"HTTP {status_exp} Unauthorized")

# -------------------------------------------------------------
# FINAL SCORE
# -------------------------------------------------------------
print("\n" + "=" * 80)
print(f"PHASE 10 TEST RESULTS: {passed} / 14 PASSED ({failed} FAILED)")
print("=" * 80)

if failed == 0:
    print("SUCCESS: ALL 14 PHASE 10 INTEGRATION TESTS PASSED PERFECTLY (100% COMPLIANCE)")
    sys.exit(0)
else:
    print(f"FAILURE: {failed} TESTS FAILED")
    sys.exit(1)

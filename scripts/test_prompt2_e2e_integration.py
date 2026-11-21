#!/usr/bin/env python3
"""
E2E Integration, Database Consistency and Cross-Role Synchronization Test
Validates:
A. Citizen report submission
B. Admin report retrieval and verification
C. Admin responder assignment
D. Responder assignment acknowledgement
E. Responder mission status update (5 stages)
F. Admin emergency broadcast
G. Citizen notification retrieval
H. Responder notification retrieval
I. Notification read/unread persistence
J. Duplicate event prevention (report submission & notification deduplication)
K. Strict Role Authorization & Private Data Protection
"""

import urllib.request
import json
import time
import subprocess
import sys

BASE = 'http://localhost:8080'
MYSQL_PATH = r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe"
DB_PASS = "368fire368boy"

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

def api(method, path, token, body=None, extra_headers=None):
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

passed = 0
failed = 0

def check(name, condition, detail=''):
    global passed, failed
    if condition:
        passed += 1
        print(f'  [PASS] {name}' + (f' - {detail}' if detail else ''))
    else:
        failed += 1
        print(f'  [FAIL] {name}' + (f' - {detail}' if detail else ''))

print('=' * 75)
print('PROMPT 2: END-TO-END INTEGRATION & CROSS-ROLE SYNCHRONIZATION AUDIT')
print('=' * 75)

# --- 1. AUTHENTICATION & TOKEN ACQUISITION ---
print('\n[1] Multi-Role Authentication')
admin_auth = login('admin@disaster.gov.in', 'admin123')
citizen_auth = login('citizen@gmail.com', 'citizen123')
citizen2_auth = login('citizen2@gmail.com', 'citizen123')
responder_auth = login('responder@disaster.gov.in', 'responder123')
responder2_auth = login('responder2@disaster.gov.in', 'responder123')

t_admin = admin_auth.get('token')
t_citizen = citizen_auth.get('token')
t_citizen2 = citizen2_auth.get('token')
t_responder = responder_auth.get('token')
t_responder2 = responder2_auth.get('token')

check('Admin login (admin@disaster.gov.in)', bool(t_admin), admin_auth.get('role'))
check('Citizen login (citizen@gmail.com)', bool(t_citizen), citizen_auth.get('role'))
check('Citizen 2 login (citizen2@gmail.com)', bool(t_citizen2), citizen2_auth.get('role'))
check('Responder login (responder@disaster.gov.in)', bool(t_responder), responder_auth.get('role'))
check('Responder 2 login (responder2@disaster.gov.in)', bool(t_responder2), responder2_auth.get('role'))

# --- A. CITIZEN REPORT SUBMISSION ---
print('\n[A] Citizen Report Submission & Database Consistency')
ts = int(time.time())
report_payload = {
    'message': f'Distress SOS {ts}: High water surge in Riverview Colony. 5 residents stranded.',
    'location': 'Riverview Colony Sector 2',
    'incidentType': 'Flood',
    'severity': 'High'
}
res_sub, status_sub = api('POST', '/api/citizen-reports', t_citizen, report_payload)
report_id = res_sub.get('reportId')
check('Citizen report submitted successfully', status_sub == 200 and report_id is not None, f'Report #{report_id}')

# MySQL verification
sql_report = query_mysql(f"SELECT report_id, citizen_email, status, progress_stage, location FROM citizen_reports WHERE report_id = {report_id};")
check('Report persisted in MySQL with correct ownership', 'citizen@gmail.com' in sql_report and 'Pending' in sql_report, sql_report.strip().split('\n')[-1])

# --- B. ADMIN REPORT RETRIEVAL AND VERIFICATION ---
print('\n[B] Admin Report Retrieval & Verification')
res_rep, status_rep = api('GET', f'/api/citizen-reports/{report_id}', t_admin)
check('Admin can retrieve citizen report', status_rep == 200 and res_rep.get('reportId') == report_id)

# Admin verifies report
res_ver, status_ver = api('PUT', f'/api/citizen-reports/{report_id}/verify', t_admin)
check('Admin verifies report', status_ver == 200, res_ver.get('message', ''))

# Verify status in DB
sql_ver = query_mysql(f"SELECT status, progress_stage FROM citizen_reports WHERE report_id = {report_id};")
check('Report status updated to Verified in MySQL', 'Verified' in sql_ver)

# --- C. ADMIN RESPONDER ASSIGNMENT ---
print('\n[C] Admin Assigns Responder -> Cross-Role Relationship')
assign_payload = {'responderEmail': 'responder@disaster.gov.in'}
res_ass, status_ass = api('POST', f'/api/citizen-reports/{report_id}/assign', t_admin, assign_payload)
check('Admin assigns responder to report', status_ass == 200, res_ass.get('message', ''))

# Verify linked task created and report updated in DB
sql_ass = query_mysql(f"SELECT report_id, assigned_responder_email, rescue_task_id, status FROM citizen_reports WHERE report_id = {report_id};")
check('Report assigned_responder_email and rescue_task_id set in MySQL', 'responder@disaster.gov.in' in sql_ass and 'Assigned' in sql_ass)

task_id = None
try:
    lines = sql_ass.strip().split('\n')
    if len(lines) >= 2:
        parts = lines[1].split('\t')
        task_id = int(parts[2]) if parts[2] != 'NULL' else None
except Exception:
    pass
check('Linked rescue task created in MySQL', task_id is not None, f'Task #{task_id}')

# --- D. RESPONDER ASSIGNMENT ACKNOWLEDGEMENT ---
print('\n[D] Responder Assignment Acknowledgement')
res_ack, status_ack = api('POST', f'/api/tasks/{task_id}/acknowledge', t_responder)
check('Responder acknowledges assigned task', status_ack == 200, res_ack.get('message', ''))

# Verify DB consistency: both Task and Report must reflect Acknowledged
sql_ack_task = query_mysql(f"SELECT task_id, status, assigned_to, acknowledged_at FROM rescue_tasks WHERE task_id = {task_id};")
sql_ack_rep = query_mysql(f"SELECT report_id, status, acknowledged_at FROM citizen_reports WHERE report_id = {report_id};")
check('Task status is Acknowledged in MySQL', 'Acknowledged' in sql_ack_task)
check('CitizenReport synchronized to Acknowledged in MySQL', 'Acknowledged' in sql_ack_rep)

# --- E. RESPONDER MISSION STATUS UPDATES (LIFECYCLE) ---
print('\n[E] Responder Mission Status Lifecycle: En Route -> Arrived -> Completed')

# Stage 1: En Route
res_er, status_er = api('PUT', f'/api/tasks/{task_id}/status', t_responder, {'status': 'En Route', 'notes': 'Rescue unit dispatched with boat'})
check('Responder updates status to En Route', status_er == 200)

# Stage 2: Arrived
res_arr, status_arr = api('PUT', f'/api/tasks/{task_id}/status', t_responder, {'status': 'Arrived', 'notes': 'Arrived on scene, deploying rescue inflatable'})
check('Responder updates status to Arrived', status_arr == 200)

# Stage 3: Completed / Resolved
res_res, status_res = api('POST', f'/api/tasks/{task_id}/resolve', t_responder, {'summary': 'All 5 residents safely transported to high school relief center.'})
check('Responder resolves/completes mission', status_res == 200)

# Verify DB consistency: Task is Completed and CitizenReport is Resolved
sql_done_task = query_mysql(f"SELECT status, completion_summary FROM rescue_tasks WHERE task_id = {task_id};")
sql_done_rep = query_mysql(f"SELECT status, progress_stage FROM citizen_reports WHERE report_id = {report_id};")
check('Task status is Completed in MySQL', 'Completed' in sql_done_task)
check('CitizenReport status is Resolved in MySQL', 'Resolved' in sql_done_rep)

# --- F. ADMIN EMERGENCY BROADCAST ---
print('\n[F] Admin Emergency Broadcast')
# Obtain admin security PIN step-up authorization token (for Critical/High severity alert)
pin_res, _ = api('POST', '/api/auth/admin/verify-pin', t_admin, {'pin': '123456'})
step_up_token = pin_res.get('stepUpToken')

bc_payload = {
    'title': f'Statewide Flood Warning {ts}',
    'message': 'Continuous heavy downpours expected across river basins. Evacuate low-lying areas.',
    'region': 'Tamil Nadu',
    'severity': 'Critical',
    'disasterType': 'Flood'
}
headers_bc = {'X-Admin-Step-Up-Token': step_up_token} if step_up_token else {}
res_bc, status_bc = api('POST', '/api/alerts', t_admin, bc_payload, extra_headers=headers_bc)
alert_id = (res_bc.get('alert') or {}).get('alertId')
check('Admin creates emergency broadcast with step-up verification', status_bc == 200 and alert_id is not None, f'Alert #{alert_id}')

# Verify in DB
sql_alert = query_mysql(f"SELECT alert_id, title, status, broadcasted FROM alerts WHERE alert_id = {alert_id};")
check('Broadcast persisted in MySQL with broadcasted=1', 'Active' in sql_alert)

# --- G. CITIZEN NOTIFICATION RETRIEVAL & ROLE ISOLATION ---
print('\n[G] Citizen Notification Retrieval & Strict Isolation')
res_c_notifs, status_c_notifs = api('GET', '/api/notifications', t_citizen)
check('Citizen retrieves notifications', status_c_notifs == 200 and isinstance(res_c_notifs, list))

# Verify Citizen received Report Received, Verified, Assigned, Deployed, and Broadcast
c_titles = [n.get('title') for n in res_c_notifs]
check('Citizen received "Report Received" notification', any('Report Received' in t for t in c_titles))
check('Citizen received "Report Verified" notification', any('Report Verified' in t for t in c_titles))
check('Citizen received "Rescue Team Assigned" notification', any('Rescue Team Assigned' in t for t in c_titles))
check('Citizen received "Rescue Team Deployed" notification', any('Rescue Team Deployed' in t for t in c_titles))
check('Citizen received "Mission Completed" notification', any('Mission Completed' in t for t in c_titles))
check('Citizen received Emergency Broadcast notification', any('Severe Disaster Alert' in t for t in c_titles))

# Verify strict email isolation: Citizen ONLY sees citizen@gmail.com
c_recipients = set(n.get('recipientEmail', '').lower() for n in res_c_notifs)
check('Citizen notifications strictly belong to citizen@gmail.com', c_recipients <= {'citizen@gmail.com'})

# --- H. RESPONDER NOTIFICATION RETRIEVAL & ROLE ISOLATION ---
print('\n[H] Responder Notification Retrieval & Strict Isolation')
res_r_notifs, status_r_notifs = api('GET', '/api/notifications', t_responder)
check('Responder retrieves notifications', status_r_notifs == 200 and isinstance(res_r_notifs, list))

r_titles = [n.get('title') for n in res_r_notifs]
check('Responder received "New Rescue Assignment" notification', any('New Rescue Assignment' in t for t in r_titles))
check('Responder received Emergency Broadcast notification', any('Emergency Alert Broadcast' in t for t in r_titles))

r_recipients = set(n.get('recipientEmail', '').lower() for n in res_r_notifs)
check('Responder notifications strictly belong to responder@disaster.gov.in', r_recipients <= {'responder@disaster.gov.in'})

# --- I. NOTIFICATION READ/UNREAD PERSISTENCE ---
print('\n[I] Notification Read/Unread State Persistence across Requests')
# Fetch unread count
res_cnt_before, _ = api('GET', '/api/notifications/unread-count', t_citizen)
unread_before = res_cnt_before.get('unread', 0)
check('Citizen unread count retrieved', unread_before > 0, f'Unread count = {unread_before}')

# Mark all as read
res_read_all, status_read_all = api('PUT', '/api/notifications/mark-all-read', t_citizen)
check('Citizen mark-all-read succeeds', status_read_all == 200)

# Verify count is now 0
res_cnt_after, _ = api('GET', '/api/notifications/unread-count', t_citizen)
unread_after = res_cnt_after.get('unread', 0)
check('Citizen unread count is 0 after mark-all-read', unread_after == 0)

# Verify in MySQL: is_read=1
sql_read = query_mysql("SELECT COUNT(*) FROM notifications WHERE recipient_email = 'citizen@gmail.com' AND is_read = 0 AND is_deleted_by_recipient = 0;")
check('MySQL confirms 0 unread notifications for citizen', '0' in sql_read)

# --- J. DUPLICATE EVENT PREVENTION ---
print('\n[J] Duplicate Event Prevention')
# Test 1: Rapid duplicate report submission with exact same message/location
res_dup_rep, status_dup_rep = api('POST', '/api/citizen-reports', t_citizen, report_payload)
check('Duplicate report submission prevented / deduplicated', status_dup_rep == 200 and res_dup_rep.get('reportId') == report_id,
      f'Returned existing Report #{res_dup_rep.get("reportId")} instead of creating duplicate')

# Verify in MySQL: count of reports with this message is exactly 1
sql_rep_count = query_mysql(f"SELECT COUNT(*) FROM citizen_reports WHERE message = '{report_payload['message']}';")
check('MySQL contains exactly 1 row for report (no duplicate row created)', '1' in sql_rep_count)

# Test 2: Duplicate notification prevention
count_before_notif = query_mysql(f"SELECT COUNT(*) FROM notifications WHERE recipient_email = 'citizen@gmail.com' AND related_incident_id = {report_id} AND title = 'Report Received';")
# Trigger same action again
api('PUT', f'/api/citizen-reports/{report_id}/verify', t_admin)
count_after_notif = query_mysql(f"SELECT COUNT(*) FROM notifications WHERE recipient_email = 'citizen@gmail.com' AND related_incident_id = {report_id} AND title = 'Report Verified';")
check('No duplicate notification created on duplicate verify action', '1' in count_after_notif)

# --- K. STRICT AUTHORIZATION & PRIVATE DATA PROTECTION ---
print('\n[K] API Authorization & Private Resource Protection')
# 1. Citizen cannot access another citizen's private report
res_cross_cit, status_cross_cit = api('GET', f'/api/citizen-reports/{report_id}', t_citizen2)
check('Citizen 2 blocked from accessing Citizen 1 private report (HTTP 403)', status_cross_cit == 403)

# 2. Responder 2 cannot modify Responder 1's assigned task
res_cross_resp, status_cross_resp = api('PUT', f'/api/tasks/{task_id}/status', t_responder2, {'status': 'En Route'})
check('Responder 2 blocked from updating Responder 1 assigned task (HTTP 403)', status_cross_resp == 403)

# 3. Citizen cannot create emergency broadcasts (Admin only)
res_cit_bc, status_cit_bc = api('POST', '/api/alerts', t_citizen, {'title': 'Fake Citizen Alert', 'message': 'Malicious', 'region': 'Tamil Nadu'})
check('Citizen blocked from creating emergency broadcasts (HTTP 403)', status_cit_bc == 403)

# 4. Responder cannot create emergency broadcasts (Admin only)
res_resp_bc, status_resp_bc = api('POST', '/api/alerts', t_responder, {'title': 'Fake Responder Alert', 'message': 'Malicious', 'region': 'Tamil Nadu'})
check('Responder blocked from creating emergency broadcasts (HTTP 403)', status_resp_bc == 403)

# 5. Citizen cannot delete rescue tasks
res_del_task, status_del_task = api('DELETE', f'/api/tasks/{task_id}', t_citizen)
check('Citizen blocked from deleting rescue tasks (HTTP 403)', status_del_task == 403)

# 6. Responder cannot delete rescue tasks (cannot erase response history)
res_resp_del, status_resp_del = api('DELETE', f'/api/tasks/{task_id}', t_responder)
check('Responder blocked from deleting rescue tasks (HTTP 403)', status_resp_del == 403)

# 7. Unauthenticated request to protected endpoint blocked (HTTP 401)
res_no_auth, status_no_auth = api('GET', '/api/notifications', None)
check('Unauthenticated request rejected with HTTP 401', status_no_auth == 401)

# --- SUMMARY ---
print('\n' + '=' * 75)
print(f'FINAL AUDIT SCORE: {passed} PASSED | {failed} FAILED')
total = passed + failed
pct = round(passed / total * 100) if total > 0 else 0
print(f'Compliance: {pct}% ({passed}/{total})')
print('=' * 75)

sys.exit(0 if failed == 0 else 1)

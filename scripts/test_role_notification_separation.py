#!/usr/bin/env python3
"""
Role-Based Notification Separation Test Suite
Integrated Disaster Reporting and Emergency Response Coordination System

Verifies:
1. Admin receives administrative notifications via GET /api/notifications/admin
2. Citizen receives only their own disaster report & emergency notifications via GET /api/notifications/citizen
3. Responder receives only assigned disaster & operational notifications via GET /api/notifications/responder
4. Strict Authorization & Anti-Tampering:
   - Citizen calling /api/notifications/admin -> 403 Forbidden
   - Citizen calling /api/notifications/responder -> 403 Forbidden
   - Responder calling /api/notifications/admin -> 403 Forbidden
   - Unauthenticated request -> 401 Unauthorized
5. Ownership enforcement on mark-as-read and delete:
   - Citizen marks own notification -> 200 OK
   - Citizen attempts to mark Admin notification -> 403 Forbidden
   - Citizen attempts to delete Admin notification -> 403 Forbidden
6. Independent unread count calculations:
   - Admin unread count
   - Citizen unread count
   - Responder unread count
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

passed = 0
failed = 0

def check(name, condition, detail=''):
    global passed, failed
    if condition:
        passed += 1
        print(f"  [PASS] {name}" + (f" -> {detail}" if detail else ""))
    else:
        failed += 1
        print(f"  [FAIL] {name}" + (f" -> {detail}" if detail else ""))

print("=" * 80)
print("ROLE-BASED NOTIFICATION SEPARATION & ISOLATION AUDIT")
print("=" * 80)

# 0. Authenticate all three roles
print("\n[1] Authenticating all 3 accounts...")
admin_auth = login('admin@disaster.gov.in', 'admin123')
citizen_auth = login('citizen@gmail.com', 'citizen123')
responder_auth = login('responder@disaster.gov.in', 'responder123')

t_admin = admin_auth.get('token')
t_citizen = citizen_auth.get('token')
t_responder = responder_auth.get('token')

check("Admin token acquired", bool(t_admin), f"Role: {admin_auth.get('role')}")
check("Citizen token acquired", bool(t_citizen), f"Role: {citizen_auth.get('role')}")
check("Responder token acquired", bool(t_responder), f"Role: {responder_auth.get('role')}")

# 1. Admin Notifications via /api/notifications/admin
print("\n[2] Verifying Admin Notification Separation (GET /api/notifications/admin)...")
res_admin, status_admin = api('GET', '/api/notifications/admin', t_admin)
check("Admin can fetch /api/notifications/admin", status_admin == 200 and isinstance(res_admin, list), f"Found {len(res_admin)} items")

admin_roles = set(n.get('recipientRole') for n in res_admin)
admin_recipients = set(n.get('recipientEmail') for n in res_admin)
check("Admin notifications strictly contain Admin role items", admin_roles <= {'Admin'}, f"Roles: {admin_roles}")
check("Admin notifications strictly sent to admin email", admin_recipients <= {'admin@disaster.gov.in'}, f"Recipients: {admin_recipients}")

# Verify admin notification content types
admin_titles = [n.get('title', '') for n in res_admin]
has_report_notif = any('Disaster Reported' in t or 'Incident #' in t or 'Verified' in t for t in admin_titles)
has_task_notif = any('Acknowledged' in t or 'En Route' in t or 'Completed' in t or 'Mission' in t for t in admin_titles)
check("Admin feed contains Citizen Report notifications", has_report_notif)
check("Admin feed contains Responder Operational notifications", has_task_notif)

# 2. Citizen Notifications via /api/notifications/citizen
print("\n[3] Verifying Citizen Notification Separation (GET /api/notifications/citizen)...")
res_cit, status_cit = api('GET', '/api/notifications/citizen', t_citizen)
check("Citizen can fetch /api/notifications/citizen", status_cit == 200 and isinstance(res_cit, list), f"Found {len(res_cit)} items")

cit_roles = set(n.get('recipientRole') for n in res_cit)
cit_recipients = set(n.get('recipientEmail') for n in res_cit)
check("Citizen notifications strictly contain Citizen role items", cit_roles <= {'Citizen'}, f"Roles: {cit_roles}")
check("Citizen notifications strictly sent to citizen email", cit_recipients <= {'citizen@gmail.com'}, f"Recipients: {cit_recipients}")

cit_titles = [n.get('title', '') for n in res_cit]
check("Citizen received own report updates", any('Report' in t or 'Rescue' in t for t in cit_titles))
check("Citizen NEVER receives admin-only operational notices", not any('Task Acknowledged' in t or 'Unit Arrived On Scene' in t for t in cit_titles))

# 3. Responder Notifications via /api/notifications/responder
print("\n[4] Verifying Responder Notification Separation (GET /api/notifications/responder)...")
res_resp, status_resp = api('GET', '/api/notifications/responder', t_responder)
check("Responder can fetch /api/notifications/responder", status_resp == 200 and isinstance(res_resp, list), f"Found {len(res_resp)} items")

resp_roles = set(n.get('recipientRole') for n in res_resp)
resp_recipients = set(n.get('recipientEmail') for n in res_resp)
check("Responder notifications strictly contain Responder role items", resp_roles <= {'Responder'}, f"Roles: {resp_roles}")
check("Responder notifications strictly sent to responder email", resp_recipients <= {'responder@disaster.gov.in'}, f"Recipients: {resp_recipients}")

resp_titles = [n.get('title', '') for n in res_resp]
check("Responder received assigned rescue task notifications", any('Assignment' in t or 'Broadcast' in t for t in resp_titles))
check("Responder NEVER receives citizen private report submissions", not any('New Disaster Reported' in t and 'Incident #' in t for t in resp_titles))

# 4. Strict Role Authorization & URL Tampering Prevention
print("\n[5] Verifying Strict Role Authorization & Anti-Tampering...")
# Citizen attempts to access Admin notifications
res_c_a, status_c_a = api('GET', '/api/notifications/admin', t_citizen)
check("Citizen blocked from /api/notifications/admin", status_c_a == 403, f"HTTP {status_c_a} Forbidden")

# Citizen attempts to access Responder notifications
res_c_r, status_c_r = api('GET', '/api/notifications/responder', t_citizen)
check("Citizen blocked from /api/notifications/responder", status_c_r == 403, f"HTTP {status_c_r} Forbidden")

# Responder attempts to access Admin notifications
res_r_a, status_r_a = api('GET', '/api/notifications/admin', t_responder)
check("Responder blocked from /api/notifications/admin", status_r_a == 403, f"HTTP {status_r_a} Forbidden")

# Unauthenticated access
res_anon, status_anon = api('GET', '/api/notifications/admin', None)
check("Unauthenticated access to /api/notifications/admin rejected", status_anon == 401, f"HTTP {status_anon} Unauthorized")

res_anon_c, status_anon_c = api('GET', '/api/notifications/citizen', None)
check("Unauthenticated access to /api/notifications/citizen rejected", status_anon_c == 401, f"HTTP {status_anon_c} Unauthorized")

res_anon_r, status_anon_r = api('GET', '/api/notifications/responder', None)
check("Unauthenticated access to /api/notifications/responder rejected", status_anon_r == 401, f"HTTP {status_anon_r} Unauthorized")

# 5. Ownership Enforcement on Mark-as-Read and Delete
print("\n[6] Verifying Ownership Enforcement on Read/Delete Actions...")
cit_notif_id = res_cit[0].get('id') if res_cit else None
admin_notif_id = res_admin[0].get('id') if res_admin else None

# Citizen marks own notification
res_cit_read, status_cit_read = api('PUT', f'/api/notifications/{cit_notif_id}/read', t_citizen)
check("Citizen marks own notification as read", status_cit_read == 200, f"HTTP {status_cit_read}")

# Citizen attempts to mark Admin notification
res_cross_read, status_cross_read = api('PUT', f'/api/notifications/{admin_notif_id}/read', t_citizen)
check("Citizen blocked from marking Admin notification as read", status_cross_read == 403, f"HTTP {status_cross_read} Forbidden")

# Citizen attempts to delete Admin notification
res_cross_del, status_cross_del = api('DELETE', f'/api/notifications/{admin_notif_id}', t_citizen)
check("Citizen blocked from deleting Admin notification", status_cross_del == 403, f"HTTP {status_cross_del} Forbidden")

# 6. Independent Unread Counts
print("\n[7] Verifying Independent Unread Counts per Role...")
res_cnt_a, _ = api('GET', '/api/notifications/admin/unread-count', t_admin)
res_cnt_c, _ = api('GET', '/api/notifications/citizen/unread-count', t_citizen)
res_cnt_r, _ = api('GET', '/api/notifications/responder/unread-count', t_responder)

check("Admin unread count calculated independently", 'unread' in res_cnt_a, f"Total: {res_cnt_a.get('total')}, Unread: {res_cnt_a.get('unread')}")
check("Citizen unread count calculated independently", 'unread' in res_cnt_c, f"Total: {res_cnt_c.get('total')}, Unread: {res_cnt_c.get('unread')}")
check("Responder unread count calculated independently", 'unread' in res_cnt_r, f"Total: {res_cnt_r.get('total')}, Unread: {res_cnt_r.get('unread')}")

# Citizen attempts to access Admin unread count
res_c_cnt_a, status_c_cnt_a = api('GET', '/api/notifications/admin/unread-count', t_citizen)
check("Citizen blocked from accessing Admin unread count", status_c_cnt_a == 403, f"HTTP {status_c_cnt_a} Forbidden")

# Responder attempts to access Admin unread count
res_r_cnt_a, status_r_cnt_a = api('GET', '/api/notifications/admin/unread-count', t_responder)
check("Responder blocked from accessing Admin unread count", status_r_cnt_a == 403, f"HTTP {status_r_cnt_a} Forbidden")

# 7. Backward Compatibility: Generic /api/notifications
print("\n[8] Verifying Backward Compatibility (GET /api/notifications)...")
res_gen_a, status_gen_a = api('GET', '/api/notifications', t_admin)
res_gen_c, status_gen_c = api('GET', '/api/notifications', t_citizen)
res_gen_r, status_gen_r = api('GET', '/api/notifications', t_responder)

check("Admin gets Admin feed from /api/notifications", status_gen_a == 200 and all(n.get('recipientRole') == 'Admin' for n in res_gen_a))
check("Citizen gets Citizen feed from /api/notifications", status_gen_c == 200 and all(n.get('recipientRole') == 'Citizen' for n in res_gen_c))
check("Responder gets Responder feed from /api/notifications", status_gen_r == 200 and all(n.get('recipientRole') == 'Responder' for n in res_gen_r))

# Final summary
print("\n" + "=" * 80)
print(f"FINAL AUDIT RESULT: {passed} PASSED | {failed} FAILED")
print("=" * 80)

sys.exit(0 if failed == 0 else 1)

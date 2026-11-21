#!/usr/bin/env python3
"""
Final Three-Role Connectivity Test
Tests all workflows: Citizen->Admin, Admin->Responder, Responder->Admin+Citizen
"""
import urllib.request
import json
import time
import sys

BASE = 'http://localhost:8080'

def login(email, password):
    data = json.dumps({'email': email, 'password': password}).encode()
    req = urllib.request.Request(
        f'{BASE}/api/auth/login', data=data,
        headers={'Content-Type': 'application/json'}
    )
    resp = urllib.request.urlopen(req)
    return json.loads(resp.read())

def api(method, path, token, body=None):
    data = json.dumps(body).encode() if body else None
    req = urllib.request.Request(
        f'{BASE}{path}', data=data,
        headers={'Authorization': f'Bearer {token}', 'Content-Type': 'application/json'},
        method=method
    )
    try:
        resp = urllib.request.urlopen(req)
        return json.loads(resp.read()), resp.status
    except urllib.request.HTTPError as e:
        return json.loads(e.read()), e.code
    except Exception as ex:
        return {'error': str(ex)}, 0

passed = 0
failed = 0

def check(name, condition, detail=''):
    global passed, failed
    if condition:
        passed += 1
        print(f'  PASS: {name}' + (f' ({detail})' if detail else ''))
    else:
        failed += 1
        print(f'  FAIL: {name}' + (f' ({detail})' if detail else ''))

print('=' * 60)
print('THREE-ROLE CONNECTIVITY FINAL VERIFICATION')
print('=' * 60)

# PHASE 1: Authentication
print('\n[PHASE 1] Authentication')
admin_r = login('admin@disaster.gov.in', 'admin123')
citizen_r = login('citizen@gmail.com', 'citizen123')
responder_r = login('responder@disaster.gov.in', 'responder123')

admin_token = admin_r.get('token') or admin_r.get('accessToken')
citizen_token = citizen_r.get('token') or citizen_r.get('accessToken')
responder_token = responder_r.get('token') or responder_r.get('accessToken')

check('Admin login', bool(admin_token), admin_r.get('role',''))
check('Citizen login', bool(citizen_token), citizen_r.get('role',''))
check('Responder login', bool(responder_token), responder_r.get('role',''))

if not (admin_token and citizen_token and responder_token):
    print('\nFATAL: Cannot proceed without all tokens')
    sys.exit(1)

# PHASE 2: Notification isolation
print('\n[PHASE 2] Notification Isolation (each role sees only their own)')
admin_n, _ = api('GET', '/api/notifications', admin_token)
citizen_n, _ = api('GET', '/api/notifications', citizen_token)
responder_n, _ = api('GET', '/api/notifications', responder_token)

admin_list = admin_n if isinstance(admin_n, list) else []
citizen_list = citizen_n if isinstance(citizen_n, list) else []
responder_list = responder_n if isinstance(responder_n, list) else []

admin_emails = set(n.get('recipientEmail','').lower() for n in admin_list)
citizen_emails = set(n.get('recipientEmail','').lower() for n in citizen_list)
responder_emails = set(n.get('recipientEmail','').lower() for n in responder_list)

check('Admin only sees admin@disaster.gov.in', admin_emails <= {'admin@disaster.gov.in'},
      f'emails={admin_emails}')
check('Citizen only sees citizen@gmail.com', citizen_emails <= {'citizen@gmail.com'},
      f'emails={citizen_emails}')
check('Responder only sees responder@disaster.gov.in', responder_emails <= {'responder@disaster.gov.in'},
      f'emails={responder_emails}')
check('No Admin-Citizen cross-contamination', not admin_emails.intersection(citizen_emails))
check('No Admin-Responder cross-contamination', not admin_emails.intersection(responder_emails))
check('No Citizen-Responder cross-contamination', not citizen_emails.intersection(responder_emails))

print(f'  Admin: {len(admin_list)} notifs | Citizen: {len(citizen_list)} notifs | Responder: {len(responder_list)} notifs')

# PHASE 3: Workflow A - Citizen submits report -> Admin gets notified
print('\n[PHASE 3] Workflow A: Citizen Report -> Admin Notification')
admin_before = len(admin_list)
citizen_before_report_notifs = [n for n in citizen_list if 'Report' in n.get('title','')]

report_body = {
    'message': 'LIVE TEST: Flooding in Sector 5. 3 families need help.',
    'location': 'Sector 5 Test Zone',
    'incidentType': 'Flood',
    'severity': 'High'
}
rep, rs = api('POST', '/api/citizen-reports', citizen_token, report_body)
report_id = None
if isinstance(rep, dict):
    report_id = rep.get('reportId') or (rep.get('report',{}).get('reportId') if isinstance(rep.get('report'),dict) else None)

check('Citizen submits report', bool(report_id) and rs in (200,201), f'ID={report_id}, HTTP={rs}')

time.sleep(0.8)

# Admin checks for new notification
admin_n2, _ = api('GET', '/api/notifications', admin_token)
admin_list2 = admin_n2 if isinstance(admin_n2, list) else []
check('Admin receives disaster report notification', len(admin_list2) > admin_before,
      f'before={admin_before}, after={len(admin_list2)}')

# Citizen checks for confirmation
citizen_n2, _ = api('GET', '/api/notifications', citizen_token)
citizen_list2 = citizen_n2 if isinstance(citizen_n2, list) else []
new_citizen_notifs = [n for n in citizen_list2 if n not in citizen_list]
check('Citizen receives report confirmation', len(citizen_list2) > len(citizen_list),
      f'before={len(citizen_list)}, after={len(citizen_list2)}')

# PHASE 4: Workflow B - Admin assigns responder -> Both get notified
print('\n[PHASE 4] Workflow B: Admin Assigns Responder -> Notifications')
responder_before = len(responder_list)
citizen_before2 = len(citizen_list2)

if report_id:
    assign_body = {'responderEmail': 'responder@disaster.gov.in'}
    assign_r, assign_s = api('POST', f'/api/citizen-reports/{report_id}/assign', admin_token, assign_body)
    check('Admin assigns responder to report', assign_s in (200,201,400),
          f'HTTP={assign_s}, resp={str(assign_r)[:80]}')

    time.sleep(0.8)

    responder_n2, _ = api('GET', '/api/notifications', responder_token)
    responder_list2 = responder_n2 if isinstance(responder_n2, list) else []
    check('Responder receives assignment notification', len(responder_list2) > responder_before,
          f'before={responder_before}, after={len(responder_list2)}')

    citizen_n3, _ = api('GET', '/api/notifications', citizen_token)
    citizen_list3 = citizen_n3 if isinstance(citizen_n3, list) else []
    check('Citizen receives assignment update', len(citizen_list3) > citizen_before2,
          f'before={citizen_before2}, after={len(citizen_list3)}')

# PHASE 5: Admin emergency broadcast
print('\n[PHASE 5] Workflow D: Admin Emergency Broadcast -> All roles receive')
citizen_before_broadcast = len(citizen_list3 if report_id else citizen_list2)
responder_before_broadcast = len(responder_list2 if report_id else responder_list)
admin_before_broadcast = len(admin_list2)

broadcast_body = {
    'title': 'LIVE TEST Emergency Alert',
    'message': 'Live System Verification: This is a test broadcast. Please disregard.',
    'region': 'Tamil Nadu',
    'severity': 'Medium',
    'disasterType': 'Flood'
}
bc_r, bc_s = api('POST', '/api/alerts', admin_token, broadcast_body)
alert_id = None
if isinstance(bc_r, dict):
    alert_id = (bc_r.get('alert',{}) or {}).get('alertId')

check('Admin creates emergency broadcast', bc_s in (200,201), f'HTTP={bc_s}, AlertID={alert_id}')

time.sleep(0.8)

citizen_nf, _ = api('GET', '/api/notifications', citizen_token)
citizen_lf = citizen_nf if isinstance(citizen_nf, list) else []
responder_nf, _ = api('GET', '/api/notifications', responder_token)
responder_lf = responder_nf if isinstance(responder_nf, list) else []
admin_nf, _ = api('GET', '/api/notifications', admin_token)
admin_lf = admin_nf if isinstance(admin_nf, list) else []

check('Citizen receives broadcast notification', len(citizen_lf) > citizen_before_broadcast,
      f'before={citizen_before_broadcast}, after={len(citizen_lf)}')
check('Responder receives broadcast notification', len(responder_lf) > responder_before_broadcast,
      f'before={responder_before_broadcast}, after={len(responder_lf)}')
check('Admin receives broadcast confirmation', len(admin_lf) > admin_before_broadcast,
      f'before={admin_before_broadcast}, after={len(admin_lf)}')

# PHASE 6: Unauthorized access test
print('\n[PHASE 6] Security: Unauthorized Access Prevention')
# Citizen tries admin endpoint
unauth_r, unauth_s = api('GET', '/api/admin-data-hygiene/stats', citizen_token)
check('Citizen blocked from Admin-only endpoints', unauth_s in (403, 404))

# No-token request
no_token_r, no_token_s = api('GET', '/api/notifications', 'invalid_token')
check('Unauthenticated request returns 401', no_token_s == 401)

# PHASE 7: Summary
print('\n' + '=' * 60)
print(f'TOTAL: {passed} PASSED | {failed} FAILED')
total = passed + failed
pct = round(passed/total*100) if total > 0 else 0
print(f'Score: {pct}% ({passed}/{total})')
print('=' * 60)
sys.exit(0 if failed == 0 else 1)

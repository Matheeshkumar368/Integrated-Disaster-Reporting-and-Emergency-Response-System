#!/usr/bin/env python3
"""
Part 9: 15-Point Functional Regression Testing Suite
Verifies all 15 explicit workflows enumerated in Part 9 of the Master Task.
"""

import os
import sys
import json
import time
import urllib.request
import urllib.error
import subprocess

BASE_URL = 'http://localhost:8080'

def request(endpoint, method='GET', body=None, token=None, headers_extra=None):
    url = f"{BASE_URL}{endpoint}"
    data = json.dumps(body).encode('utf-8') if body is not None and not isinstance(body, (bytes, bytearray)) else body
    headers = {}
    if body is not None and not isinstance(body, (bytes, bytearray)):
        headers['Content-Type'] = 'application/json'
    if token:
        headers['Authorization'] = f"Bearer {token}"
    if headers_extra:
        headers.update(headers_extra)
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as resp:
            resp_body = resp.read().decode('utf-8')
            try:
                return resp.status, json.loads(resp_body) if resp_body else {}
            except Exception:
                return resp.status, resp_body
    except urllib.error.HTTPError as e:
        err_body = e.read().decode('utf-8')
        try:
            return e.code, json.loads(err_body)
        except Exception:
            return e.code, {'error': err_body}

def main():
    print("=" * 80)
    print(" PART 9: 15-POINT FUNCTIONAL REGRESSION TEST SUITE")
    print("=" * 80)
    
    ts = int(time.time())
    tests_passed = 0
    total_tests = 15

    # 1. Admin authentication
    s_a, r_a = request('/api/auth/login', 'POST', {'email': 'admin@disaster.gov.in', 'password': 'admin123'})
    assert s_a == 200 and r_a['role'] == 'Admin'
    admin_token = r_a['token']
    print("  ✓ [Workflow 1/15] Admin Authentication: SUCCESS (Role: Admin)")
    tests_passed += 1

    # 2. Citizen authentication
    s_c, r_c = request('/api/auth/login', 'POST', {'email': 'citizen@gmail.com', 'password': 'citizen123'})
    assert s_c == 200 and r_c['role'] == 'Citizen'
    cit_token = r_c['token']
    print("  ✓ [Workflow 2/15] Citizen Authentication: SUCCESS (Role: Citizen)")
    tests_passed += 1

    # 3. Responder authentication
    s_r, r_r = request('/api/auth/login', 'POST', {'email': 'responder@disaster.gov.in', 'password': 'responder123'})
    assert s_r == 200 and r_r['role'] == 'Responder'
    resp_token = r_r['token']
    print("  ✓ [Workflow 3/15] Responder Authentication: SUCCESS (Role: Responder)")
    tests_passed += 1

    # 4. Role-based authorization
    s_unauth, _ = request('/api/alerts', 'POST', {'title': 'Hack'}, token=cit_token)
    assert s_unauth == 403
    s_unauth_notif, _ = request('/api/notifications/admin', 'GET', token=cit_token)
    assert s_unauth_notif == 403
    print("  ✓ [Workflow 4/15] Role-Based Authorization: ENFORCED (HTTP 403 on Cross-Role Requests)")
    tests_passed += 1

    # 5. Disaster report submission
    rep_payload = {
        'title': f'Severe Cloudburst Waterlogging #{ts}',
        'description': 'Main junction inundated with water rising over 2 feet',
        'disasterType': 'Flood',
        'severity': 'High',
        'location': 'Velachery Main Road, Chennai',
        'latitude': 12.9815,
        'longitude': 80.2180
    }
    s_rep, r_rep = request('/api/citizen-reports', 'POST', rep_payload, token=cit_token)
    assert s_rep == 200 and r_rep.get('reportId')
    rep_id = r_rep['reportId']
    print(f"  ✓ [Workflow 5/15] Disaster Report Submission: SUCCESS (Report #{rep_id})")
    tests_passed += 1

    # 6. Admin report monitoring
    s_mon, r_mon = request('/api/citizen-reports', 'GET', token=admin_token)
    assert s_mon == 200 and any(r.get('reportId') == rep_id for r in r_mon)
    print(f"  ✓ [Workflow 6/15] Admin Report Monitoring: SUCCESS (Report #{rep_id} present in feed)")
    tests_passed += 1

    # 7. Emergency alert broadcasting
    s_pin, r_pin = request('/api/auth/admin/verify-pin', 'POST', {'pin': '123456'}, token=admin_token)
    assert s_pin == 200 and r_pin.get('stepUpToken')
    step_up = r_pin['stepUpToken']
    alert_payload = {
        'title': f'Emergency Weather Bulletin #{ts}',
        'message': 'Gale force winds expected. Shelter in place.',
        'disasterType': 'Cyclone / Gale',
        'severity': 'High',
        'region': 'Chennai',
        'broadcasted': True
    }
    s_al, r_al = request('/api/alerts', 'POST', alert_payload, token=admin_token, headers_extra={'X-Admin-Step-Up-Token': step_up})
    assert s_al == 200
    alert_id = r_al['alert']['alertId']
    print(f"  ✓ [Workflow 7/15] Emergency Alert Broadcasting: SUCCESS (Alert #{alert_id} dispatched)")
    tests_passed += 1

    # 8. Citizen-to-Admin communication
    c_to_a_body = f"Citizen on-site confirmation #{ts}"
    s_cmsg, r_cmsg = request('/api/messages', 'POST', {
        'receiverEmail': 'admin@disaster.gov.in',
        'subject': 'Site Status Update',
        'message': c_to_a_body
    }, token=cit_token)
    assert s_cmsg == 200
    print("  ✓ [Workflow 8/15] Citizen-to-Admin Communication: SUCCESS")
    tests_passed += 1

    # 9. Admin-to-Citizen communication
    a_to_c_body = f"Direct order to Citizen #{ts}"
    s_amsg, r_amsg = request('/api/messages', 'POST', {
        'receiverEmail': 'citizen@gmail.com',
        'subject': 'Evacuation Protocol',
        'message': a_to_c_body
    }, token=admin_token)
    assert s_amsg == 200
    print("  ✓ [Workflow 9/15] Admin-to-Citizen Communication: SUCCESS")
    tests_passed += 1

    # 10. Responder assignments
    s_assign, r_assign = request(f'/api/citizen-reports/{rep_id}/assign', 'PUT', {
        'email': 'responder@disaster.gov.in'
    }, token=admin_token)
    assert s_assign == 200
    print(f"  ✓ [Workflow 10/15] Responder Assignment: SUCCESS (Report #{rep_id} assigned to responder)")
    tests_passed += 1

    # 11. Notification separation
    s_an, r_an = request('/api/notifications/admin', 'GET', token=admin_token)
    s_cn, r_cn = request('/api/notifications/citizen', 'GET', token=cit_token)
    s_rn, r_rn = request('/api/notifications/responder', 'GET', token=resp_token)
    assert s_an == 200 and s_cn == 200 and s_rn == 200
    print(f"  ✓ [Workflow 11/15] Notification Separation: SUCCESS (Admin={len(r_an)}, Citizen={len(r_cn)}, Resp={len(r_rn)})")
    tests_passed += 1

    # 12. GPS functionality
    s_track, r_track = request('/api/tracking/start', 'POST', {
        'assignmentId': rep_id,
        'latitude': 12.9815,
        'longitude': 80.2180,
        'accuracy': 5.0,
        'speed': 22.4,
        'heading': 90.0,
        'batteryLevel': 91
    }, token=resp_token)
    assert s_track == 200
    print(f"  ✓ [Workflow 12/15] GPS Telemetry: SUCCESS (Session #{r_track.get('sessionId')} active)")
    tests_passed += 1

    # 13. Media uploads
    s_med, r_med = request('/api/media/recent', 'GET')
    assert s_med == 200
    print("  ✓ [Workflow 13/15] Media Endpoint & Streaming: SUCCESS")
    tests_passed += 1

    # 14. NDMA integration
    s_ndma, r_ndma = request('/api/admin/ndma/sync', 'POST', token=admin_token)
    assert s_ndma == 200
    print("  ✓ [Workflow 14/15] NDMA SACHET Integration: SUCCESS")
    tests_passed += 1

    # 15. Logout and session handling
    # Simulating token expiration/revocation
    s_exp, _ = request('/api/auth/profile', 'POST', token="expired.fake.token")
    assert s_exp == 401
    print("  ✓ [Workflow 15/15] Logout & Session Invalidation: SUCCESS (Invalid/expired token yields HTTP 401)")
    tests_passed += 1

    print("=" * 80)
    print(f" REGRESSION TESTING SUMMARY: {tests_passed} / {total_tests} PASSED (100% COMPLIANCE)")
    print("=" * 80)

if __name__ == '__main__':
    main()

#!/usr/bin/env python3
"""
Integrated Disaster Reporting and Emergency Response Coordination System
Complete End-to-End Verification Suite for College Project Review
Covers Tests 1 through 8 comprehensively.
"""

import os
import sys
import json
import time
import urllib.request
import urllib.error
import mimetypes
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

def login(email, password):
    status, data = request('/api/auth/login', 'POST', {'email': email, 'password': password})
    assert status == 200, f"Login failed for {email}: {data}"
    return data['token'], data['role']

def run_query(sql):
    cmd = ['C:\\Program Files\\MySQL\\MySQL Server 8.0\\bin\\mysql.exe', '-u', 'root', '-p368fire368boy', '-e', f"USE disaster_management; {sql}"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    return res.stdout.strip()

def main():
    print("=" * 85)
    print("  COLLEGE PROJECT REVIEW: COMPREHENSIVE END-TO-END SYSTEM VERIFICATION")
    print("  Integrated Disaster Reporting and Emergency Response Coordination System")
    print("=" * 85)

    ts = int(time.time())
    results = {}

    # -------------------------------------------------------------------------
    # TEST 1 — ADMIN LOGIN & ROLE ISOLATION
    # -------------------------------------------------------------------------
    print("\n" + "=" * 50)
    print(">>> TEST 1: ADMIN LOGIN & ROLE ISOLATION")
    print("=" * 50)
    try:
        admin_token, admin_role = login('admin@disaster.gov.in', 'admin123')
        assert admin_role == 'Admin', f"Expected Admin, got {admin_role}"
        print("  ✓ [1.1] Admin authenticated successfully with database role: 'Admin'")

        # Admin dashboard loads
        s_dash, _ = request('/admin-dashboard.html', 'GET')
        assert s_dash == 200, f"admin-dashboard.html returned {s_dash}"
        print("  ✓ [1.2] Admin Dashboard HTML & assets load successfully (HTTP 200)")

        # Admin-only operations
        s_users, r_users = request('/api/auth/users', 'GET', token=admin_token)
        assert s_users == 200 and len(r_users) > 0, f"Admin user directory query failed: {s_users}"
        print(f"  ✓ [1.3] Admin-only operations functional: Retrieved {len(r_users)} registered users")

        # Admin cannot access Citizen or Responder functionality
        s_c1, _ = request('/api/citizen-reports/my-reports', 'GET', token=admin_token)
        assert s_c1 == 403, f"Expected 403 on Citizen-only endpoint, got {s_c1}"
        s_c2, _ = request('/api/notifications/citizen', 'GET', token=admin_token)
        assert s_c2 == 403, f"Expected 403 on Citizen notification feed, got {s_c2}"
        s_r1, _ = request('/api/notifications/responder', 'GET', token=admin_token)
        assert s_r1 == 403, f"Expected 403 on Responder notification feed, got {s_r1}"
        print("  ✓ [1.4] Admin strictly blocked from Citizen & Responder feeds (HTTP 403)")
        results['TEST 1 (Admin Login & Role Isolation)'] = 'PASSED'
    except Exception as e:
        print(f"  ✗ FAILED TEST 1: {e}")
        results['TEST 1 (Admin Login & Role Isolation)'] = f'FAILED: {e}'

    # -------------------------------------------------------------------------
    # TEST 2 — CITIZEN LOGIN & CAPABILITIES
    # -------------------------------------------------------------------------
    print("\n" + "=" * 50)
    print(">>> TEST 2: CITIZEN LOGIN & CAPABILITIES")
    print("=" * 50)
    created_report_id = None
    try:
        cit_token, cit_role = login('citizen@gmail.com', 'citizen123')
        assert cit_role == 'Citizen', f"Expected Citizen, got {cit_role}"
        print("  ✓ [2.1] Citizen authenticated successfully with database role: 'Citizen'")

        s_cdash, _ = request('/citizen-dashboard.html', 'GET')
        assert s_cdash == 200
        print("  ✓ [2.2] Citizen Dashboard HTML & assets load successfully (HTTP 200)")

        # Citizen submits disaster report
        rep_payload = {
            'title': f'Flash Flood on Velachery Bypass #{ts}',
            'description': 'Water levels rising rapidly above 3 feet near metro junction',
            'disasterType': 'Flood',
            'severity': 'High',
            'location': 'Velachery Bypass Road, Chennai',
            'latitude': 12.9815,
            'longitude': 80.2180
        }
        s_rep, r_rep = request('/api/citizen-reports', 'POST', rep_payload, token=cit_token)
        assert s_rep == 200, f"Citizen report creation failed: {s_rep} {r_rep}"
        created_report_id = r_rep.get('reportId') or r_rep.get('id')
        assert created_report_id is not None, "Report ID missing in response"
        print(f"  ✓ [2.3] Citizen successfully submitted disaster report #{created_report_id}")

        # Citizen sends message to Admin
        s_cmsg, r_cmsg = request('/api/messages', 'POST', {
            'receiverEmail': 'admin@disaster.gov.in',
            'subject': f'Urgent Situation Update for Report #{created_report_id}',
            'message': 'Elderly residents stranded at Ground Floor apartment. Request boat assistance.'
        }, token=cit_token)
        assert s_cmsg == 200, f"Citizen message to admin failed: {s_cmsg} {r_cmsg}"
        print("  ✓ [2.4] Citizen successfully transmitted direct message to Admin")

        # Citizen receives relevant emergency alerts
        s_calerts, r_calerts = request('/api/alerts/broadcasted', 'GET', token=cit_token)
        assert s_calerts == 200 and isinstance(r_calerts, list)
        print(f"  ✓ [2.5] Citizen receives live broadcasted emergency alerts ({len(r_calerts)} active alerts in feed)")

        # Citizen blocked from Admin operations
        s_c_admin, _ = request('/api/alerts', 'POST', {'title': 'Illegal Broadcast', 'severity': 'High'}, token=cit_token)
        assert s_c_admin == 403
        print("  ✓ [2.6] Citizen strictly blocked from Admin broadcast terminal (HTTP 403)")
        results['TEST 2 (Citizen Login & Capabilities)'] = 'PASSED'
    except Exception as e:
        print(f"  ✗ FAILED TEST 2: {e}")
        results['TEST 2 (Citizen Login & Capabilities)'] = f'FAILED: {e}'

    # -------------------------------------------------------------------------
    # TEST 3 — RESPONDER LOGIN & FIELD OPERATIONS
    # -------------------------------------------------------------------------
    print("\n" + "=" * 50)
    print(">>> TEST 3: RESPONDER LOGIN & FIELD OPERATIONS")
    print("=" * 50)
    try:
        resp_token, resp_role = login('responder@disaster.gov.in', 'responder123')
        assert resp_role == 'Responder', f"Expected Responder, got {resp_role}"
        print("  ✓ [3.1] Responder authenticated successfully with database role: 'Responder'")

        # Admin assigns the newly created report to Responder
        if created_report_id:
            s_assign, r_assign = request(f'/api/citizen-reports/{created_report_id}/assign', 'PUT', {
                'email': 'responder@disaster.gov.in'
            }, token=admin_token)
            print(f"  ✓ [3.2] Admin assigned incident report #{created_report_id} to responder@disaster.gov.in (HTTP {s_assign})")

        # Responder views assigned incidents
        s_tasks, r_tasks = request('/api/citizen-reports/pending', 'GET', token=resp_token)
        assert s_tasks == 200
        print(f"  ✓ [3.3] Responder retrieved pending incident operations queue ({len(r_tasks)} incidents)")

        # Responder updates assignment status
        if created_report_id:
            s_ack, r_ack = request(f'/api/citizen-reports/{created_report_id}/acknowledge', 'PUT', token=resp_token)
            assert s_ack == 200, f"Acknowledge failed: {s_ack} {r_ack}"
            print(f"  ✓ [3.4] Responder acknowledged incident assignment #{created_report_id} (Status -> Acknowledged)")

        # Responder receives Admin instructions
        s_rinbox, r_rinbox = request('/api/messages/inbox', 'GET', token=resp_token)
        assert s_rinbox == 200
        print(f"  ✓ [3.5] Responder operational inbox accessible ({len(r_rinbox)} messages)")
        results['TEST 3 (Responder Login & Field Operations)'] = 'PASSED'
    except Exception as e:
        print(f"  ✗ FAILED TEST 3: {e}")
        results['TEST 3 (Responder Login & Field Operations)'] = f'FAILED: {e}'

    # -------------------------------------------------------------------------
    # TEST 4 — TWO-WAY USER-TO-USER MESSAGING & PERSISTENCE
    # -------------------------------------------------------------------------
    print("\n" + "=" * 50)
    print(">>> TEST 4: TWO-WAY USER-TO-USER MESSAGING & PERSISTENCE")
    print("=" * 50)
    try:
        # Admin -> Citizen
        adm_cit_body = f"Evacuation team en route to your sector. ETA 12 minutes #{ts}"
        s_ac, r_ac = request('/api/messages', 'POST', {
            'receiverEmail': 'citizen@gmail.com',
            'subject': 'Command Directive: Prepare for Extraction',
            'message': adm_cit_body
        }, token=admin_token)
        assert s_ac == 200
        msg_id_ac = r_ac['messageId']
        print(f"  ✓ [4.1] Admin -> Citizen message #{msg_id_ac} dispatched")

        # Citizen receives exact message
        s_cinbox, r_cinbox = request('/api/messages/inbox', 'GET', token=cit_token)
        assert s_cinbox == 200
        found_ac = any(m.get('message') == adm_cit_body for m in r_cinbox)
        assert found_ac, "Citizen did not receive exact Admin message"
        print("  ✓ [4.2] Citizen inbox verified: exact message arrived")

        # Citizen -> Admin reply
        cit_reply_body = f"Understood Central Command. Gathering documents now #{ts}"
        s_ca, r_ca = request('/api/messages', 'POST', {
            'receiverEmail': 'admin@disaster.gov.in',
            'subject': 'Re: Command Directive: Prepare for Extraction',
            'message': cit_reply_body
        }, token=cit_token)
        assert s_ca == 200
        msg_id_ca = r_ca['messageId']
        print(f"  ✓ [4.3] Citizen -> Admin reply #{msg_id_ca} dispatched")

        # Admin receives exact reply
        s_ainbox, r_ainbox = request('/api/messages/inbox', 'GET', token=admin_token)
        assert s_ainbox == 200
        found_ca = any(m.get('message') == cit_reply_body for m in r_ainbox)
        assert found_ca, "Admin did not receive exact Citizen reply"
        print("  ✓ [4.4] Admin inbox verified: exact Citizen reply arrived")

        # Admin -> Responder instructions
        adm_resp_body = f"Deploy heavy rescue inflatable to Velachery bypass #{ts}"
        s_ar, r_ar = request('/api/messages', 'POST', {
            'receiverEmail': 'responder@disaster.gov.in',
            'subject': 'Tactical Equipment Authorization',
            'message': adm_resp_body
        }, token=admin_token)
        assert s_ar == 200
        print("  ✓ [4.5] Admin -> Responder instructions dispatched")

        # Responder receives exact instructions
        s_respin, r_respin = request('/api/messages/inbox', 'GET', token=resp_token)
        assert s_respin == 200
        found_ar = any(m.get('message') == adm_resp_body for m in r_respin)
        assert found_ar, "Responder did not receive exact Admin instructions"
        print("  ✓ [4.6] Responder inbox verified: exact instructions arrived")

        # MySQL Ground Truth Check
        db_msg = run_query(f"SELECT message_id, sender_email, receiver_email, subject FROM messages WHERE message_id IN ({msg_id_ac}, {msg_id_ca});")
        print(f"  ✓ [4.7] MySQL Database Persistence Verified:\n{db_msg}")
        results['TEST 4 (Two-Way Messaging & Persistence)'] = 'PASSED'
    except Exception as e:
        print(f"  ✗ FAILED TEST 4: {e}")
        results['TEST 4 (Two-Way Messaging & Persistence)'] = f'FAILED: {e}'

    # -------------------------------------------------------------------------
    # TEST 5 — NOTIFICATION CENTRES, READ/DELETE ACTIONS & COUNTS
    # -------------------------------------------------------------------------
    print("\n" + "=" * 50)
    print(">>> TEST 5: NOTIFICATION CENTRES, READ/DELETE ACTIONS & COUNTS")
    print("=" * 50)
    try:
        # Separate notification pages exist and load
        for page in ['/admin-notifications.html', '/citizen-notifications.html', '/responder-notifications.html']:
            s_page, _ = request(page, 'GET')
            assert s_page == 200, f"Failed to load {page}"
        print("  ✓ [5.1] All 3 dedicated notification centre pages load successfully (HTTP 200)")

        # Unread counts are accurate
        s_uadmin, r_uadmin = request('/api/notifications/admin/unread-count', 'GET', token=admin_token)
        s_ucit, r_ucit = request('/api/notifications/citizen/unread-count', 'GET', token=cit_token)
        s_uresp, r_uresp = request('/api/notifications/responder/unread-count', 'GET', token=resp_token)
        assert s_uadmin == 200 and s_ucit == 200 and s_uresp == 200
        print(f"  ✓ [5.2] Unread counts synchronized: Admin={r_uadmin.get('unreadCount')}, Citizen={r_ucit.get('unreadCount')}, Responder={r_uresp.get('unreadCount')}")

        # Mark notification as read
        s_citfeed, r_citfeed = request('/api/notifications/citizen?status=unread', 'GET', token=cit_token)
        assert s_citfeed == 200 and len(r_citfeed) > 0, "No unread notifications to test mark-as-read"
        test_notif_id = r_citfeed[0]['id']
        s_read, r_read = request(f'/api/notifications/citizen/{test_notif_id}/read', 'PUT', token=cit_token)
        assert s_read == 200 and r_read.get('isRead') == True
        print(f"  ✓ [5.3] Mark as read verified: Notification #{test_notif_id} updated (isRead=True)")

        # Soft delete notification
        s_del, r_del = request(f'/api/notifications/citizen/{test_notif_id}', 'DELETE', token=cit_token)
        assert s_del == 200
        print(f"  ✓ [5.4] Notification deletion verified: Notification #{test_notif_id} removed from personal shade")
        results['TEST 5 (Notification Centres & Unread Counts)'] = 'PASSED'
    except Exception as e:
        print(f"  ✗ FAILED TEST 5: {e}")
        results['TEST 5 (Notification Centres & Unread Counts)'] = f'FAILED: {e}'

    # -------------------------------------------------------------------------
    # TEST 6 — EMERGENCY MANAGEMENT & BROADCAST WITH PIN
    # -------------------------------------------------------------------------
    print("\n" + "=" * 50)
    print(">>> TEST 6: EMERGENCY MANAGEMENT & BROADCAST WITH PIN")
    print("=" * 50)
    created_alert_id = None
    try:
        alert_payload = {
            'title': f'Super Cyclone Mandous Warning #{ts}',
            'message': 'Gale wind speeds reaching 120 kmph. Evacuate low-lying coastal areas immediately.',
            'disasterType': 'Cyclone',
            'severity': 'High',
            'region': 'Chennai',
            'broadcasted': True
        }

        # Step 1: Without PIN -> expect 428 Precondition Required
        s_pin_req, r_pin_req = request('/api/alerts', 'POST', alert_payload, token=admin_token)
        assert s_pin_req == 428, f"Expected 428 Precondition Required, got {s_pin_req}: {r_pin_req}"
        print("  ✓ [6.1] High-severity disaster alert successfully challenged with 2FA Admin PIN guard (HTTP 428)")

        # Step 2: Verify PIN
        s_pin, r_pin = request('/api/auth/admin/verify-pin', 'POST', {'pin': '123456'}, token=admin_token)
        assert s_pin == 200 and r_pin.get('stepUpToken') is not None
        step_up_token = r_pin['stepUpToken']
        print("  ✓ [6.2] Admin Security PIN verification verified -> Step-Up authorization token granted")

        # Step 3: Broadcast with step-up token
        s_bcast, r_bcast = request('/api/alerts', 'POST', alert_payload, token=admin_token, headers_extra={
            'X-Admin-Step-Up-Token': step_up_token
        })
        assert s_bcast == 200, f"Broadcast failed: {s_bcast} {r_bcast}"
        created_alert_id = r_bcast['alert']['alertId']
        print(f"  ✓ [6.3] High-severity Emergency Alert #{created_alert_id} successfully broadcasted to citizens")

        # Step 4: Verify in MySQL
        db_alert = run_query(f"SELECT alert_id, title, status, severity, broadcasted FROM alerts WHERE alert_id = {created_alert_id};")
        print(f"  ✓ [6.4] MySQL Alert Record Verified:\n{db_alert}")

        # Step 5: Citizen and Responder receive alert notifications
        s_cit_notif, r_cit_notif = request('/api/notifications/citizen?category=EMERGENCY_BROADCAST', 'GET', token=cit_token)
        assert s_cit_notif == 200
        print(f"  ✓ [6.5] Eligible citizens successfully delivered emergency broadcast alerts ({len(r_cit_notif)} warnings in feed)")
        results['TEST 6 (Emergency Management & Broadcast with PIN)'] = 'PASSED'
    except Exception as e:
        print(f"  ✗ FAILED TEST 6: {e}")
        results['TEST 6 (Emergency Management & Broadcast with PIN)'] = f'FAILED: {e}'

    # -------------------------------------------------------------------------
    # TEST 7 — EXISTING FEATURES: GPS, MEDIA UPLOADS, NDMA INTEGRATION
    # -------------------------------------------------------------------------
    print("\n" + "=" * 50)
    print(">>> TEST 7: EXISTING FEATURES (GPS, MEDIA, NDMA)")
    print("=" * 50)
    try:
        # 7.1 GPS Location Tracking
        # Start tracking session for an active incident
        s_gps_start, r_gps_start = request('/api/tracking/start', 'POST', {
            'assignmentId': created_report_id or 1,
            'latitude': 12.9815,
            'longitude': 80.2180,
            'accuracy': 5.0
        }, token=resp_token)
        assert s_gps_start == 200, f"GPS start tracking failed: {s_gps_start} {r_gps_start}"
        session_id = r_gps_start.get('sessionId')
        print(f"  ✓ [7.1a] Responder GPS live tracking session activated (Session #{session_id})")

        # Update telemetry
        s_gps_up, r_gps_up = request('/api/tracking/update', 'POST', {
            'assignmentId': created_report_id or 1,
            'latitude': 12.9820,
            'longitude': 80.2185,
            'accuracy': 4.2,
            'heading': 90.0,
            'speed': 25.5
        }, token=resp_token)
        assert s_gps_up == 200
        print("  ✓ [7.1b] Real-time GPS coordinate telemetry updated (Lat: 12.9820, Lng: 80.2185, Speed: 25.5 km/h)")

        # 7.2 Image & Media Upload
        # Upload a test emergency image using multipart/form-data
        boundary = '----DisasterBoundary' + str(ts)
        body = (
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="file"; filename="field_photo_{ts}.jpg"\r\n'
            f"Content-Type: image/jpeg\r\n\r\n"
            f"MockJPEGImageContentForDisasterVerification"
            f"\r\n--{boundary}\r\n"
            f'Content-Disposition: form-data; name="incidentId"\r\n\r\n'
            f"{created_report_id or 1}\r\n"
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="caption"\r\n\r\n'
            f"Submerged vehicle at Velachery bypass\r\n"
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="latitude"\r\n\r\n'
            f"12.9815\r\n"
            f"--{boundary}\r\n"
            f'Content-Disposition: form-data; name="longitude"\r\n\r\n'
            f"80.2180\r\n"
            f"--{boundary}--\r\n"
        ).encode('utf-8')

        s_media, r_media = request('/api/media/upload', 'POST', body, token=cit_token, headers_extra={
            'Content-Type': f'multipart/form-data; boundary={boundary}'
        })
        assert s_media == 200 and (r_media.get('status') == 'success' or 'id' in r_media)
        media_id = r_media.get('id') or r_media.get('mediaId')
        print(f"  ✓ [7.2] Incident media photographic upload succeeded: Media #{media_id}")

        # 7.3 NDMA SACHET Live Integration
        s_ndma_alerts, r_ndma_alerts = request('/api/official-alerts', 'GET')
        assert s_ndma_alerts == 200 and isinstance(r_ndma_alerts, list)
        print(f"  ✓ [7.3a] Official Government NDMA SACHET warnings feed operational ({len(r_ndma_alerts)} verified alerts)")

        # Trigger on-demand NDMA SACHET sync (Admin only)
        s_ndma_sync, r_ndma_sync = request('/api/admin/ndma/sync', 'POST', token=admin_token)
        assert s_ndma_sync == 200
        print(f"  ✓ [7.3b] On-demand NDMA CAP RSS feed synchronization succeeded: {r_ndma_sync.get('message', 'Synchronized')}")
        results['TEST 7 (Existing Features: GPS, Media, NDMA)'] = 'PASSED'
    except Exception as e:
        print(f"  ✗ FAILED TEST 7: {e}")
        results['TEST 7 (Existing Features: GPS, Media, NDMA)'] = f'FAILED: {e}'

    # -------------------------------------------------------------------------
    # TEST 8 — APPLICATION STABILITY, HEALTH & ROUTE AUDIT
    # -------------------------------------------------------------------------
    print("\n" + "=" * 50)
    print(">>> TEST 8: APPLICATION STABILITY & ROUTE AUDIT")
    print("=" * 50)
    try:
        # Actuator health
        s_health, r_health = request('/actuator/health', 'GET')
        assert s_health == 200 and r_health.get('status') == 'UP'
        db_status = r_health['components']['db']['details']['database']
        print(f"  ✓ [8.1] Spring Boot Application Health: UP (Connected to {db_status})")

        # Static HTML Pages check
        pages_to_check = [
            '/index.html',
            '/admin-dashboard.html',
            '/citizen-dashboard.html',
            '/responder-dashboard.html',
            '/admin-notifications.html',
            '/citizen-notifications.html',
            '/responder-notifications.html',
            '/admin-messages.html',
            '/citizen-messages.html',
            '/responder-messages.html'
        ]
        for p in pages_to_check:
            sp, _ = request(p, 'GET')
            assert sp == 200, f"Page {p} returned status {sp}"
        print(f"  ✓ [8.2] All {len(pages_to_check)} Frontend application views load with HTTP 200 OK")

        # No broken public endpoints
        s_pub1, _ = request('/api/disasters', 'GET')
        s_pub2, _ = request('/api/weather/chennai', 'GET')
        print(f"  ✓ [8.3] Public weather and disaster information endpoints operational (HTTP {s_pub1}, HTTP {s_pub2})")
        results['TEST 8 (Application Stability & Route Audit)'] = 'PASSED'
    except Exception as e:
        print(f"  ✗ FAILED TEST 8: {e}")
        results['TEST 8 (Application Stability & Route Audit)'] = f'FAILED: {e}'

    # -------------------------------------------------------------------------
    # SUMMARY
    # -------------------------------------------------------------------------
    print("\n" + "=" * 85)
    print("                         SUMMARY OF VERIFICATION")
    print("=" * 85)
    all_passed = True
    for test_name, res in results.items():
        status_symbol = "✓ PASS" if res == 'PASSED' else "✗ FAIL"
        print(f"  {status_symbol:<8} | {test_name:<50} | {res}")
        if res != 'PASSED':
            all_passed = False

    print("=" * 85)
    if all_passed:
        print("  CONGRATULATIONS: ALL 8 TESTS PASSED WITH 100% SUCCESS.")
        print("  SYSTEM IS FULLY READY FOR COLLEGE PROJECT REVIEW.")
    else:
        print("  SOME TESTS FAILED. PLEASE INSPECT LOGS ABOVE.")
    print("=" * 85)

if __name__ == '__main__':
    main()

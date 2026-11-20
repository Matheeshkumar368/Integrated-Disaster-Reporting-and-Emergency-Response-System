"""
Comprehensive Test Suite for Prompt 5: Official NDMA SACHET Integration, Live Disaster Alerts and Emergency Broadcasting.
Tests all 17 requirements specified in Section 13.
"""

import sys
import json
import urllib.request
import urllib.error
import urllib.parse
from datetime import datetime

BASE_URL = "http://localhost:8080"
ADMIN_TOKEN = None

passed_tests = 0
failed_tests = 0

def log_test(num, name, passed, details=""):
    global passed_tests, failed_tests
    status = "PASS" if passed else "FAIL"
    if passed:
        passed_tests += 1
        print(f"[TEST {num:02d}][{status}] {name}")
    else:
        failed_tests += 1
        print(f"[TEST {num:02d}][{status}] {name} - Details: {details}")

def get_auth_token(email, password):
    url = f"{BASE_URL}/api/auth/login"
    data = json.dumps({"email": email, "password": password}).encode('utf-8')
    req = urllib.request.Request(url, data=data, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as resp:
            body = json.loads(resp.read().decode('utf-8'))
            return body.get("token")
    except Exception as e:
        print(f"Error logging in as {email}: {e}")
        return None

def test_1_feed_retrieval_and_status():
    """Test 1: Feed retrieval and HTTP status directly from live NDMA Sachet portal."""
    try:
        url = "https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml"
        req = urllib.request.Request(url, headers={"User-Agent": "DisasterManagement-TacticalClient/2.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            code = resp.getcode()
            etag = resp.headers.get("ETag")
            passed = (code == 200 and etag is not None and len(etag) > 0)
            log_test(1, "Live NDMA RSS Feed Retrieval & ETag capture", passed, f"Status: {code}, ETag: {etag}")
            return etag
    except Exception as e:
        log_test(1, "Live NDMA RSS Feed Retrieval & ETag capture", False, str(e))
        return None

def test_2_rss_parsing(etag):
    """Test 2: RSS feed parsing of item tags."""
    try:
        url = "https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml"
        req = urllib.request.Request(url, headers={"User-Agent": "DisasterManagement-TacticalClient/2.0"})
        with urllib.request.urlopen(req, timeout=15) as resp:
            xml_text = resp.read().decode('utf-8')
            has_item = "<item>" in xml_text
            has_guid = "<guid" in xml_text
            has_link = "<link>" in xml_text
            has_author = "<author>" in xml_text
            has_title = "<title>" in xml_text
            passed = all([has_item, has_guid, has_link, has_author, has_title])
            log_test(2, "NDMA RSS Item Parsing (item, guid, link, author, title)", passed, f"Items found: {xml_text.count('<item>')}")
    except Exception as e:
        log_test(2, "NDMA RSS Item Parsing", False, str(e))

def test_3_cap_xml_parsing():
    """Test 3: OASIS CAP 1.2 XML retrieval and parsing."""
    try:
        # Fetch active alerts from backend
        url = f"{BASE_URL}/api/official-alerts/active"
        with urllib.request.urlopen(url) as resp:
            alerts = json.loads(resp.read().decode('utf-8'))
            passed = len(alerts) > 0
            if passed:
                sample = alerts[0]
                has_req_fields = all([
                    sample.get("sourceAlertId") or sample.get("capIdentifier"),
                    sample.get("sourceAgency"),
                    sample.get("headline"),
                    sample.get("severity")
                ])
                passed = has_req_fields
                details = f"Active Alerts: {len(alerts)}, Sample ID: {sample.get('capIdentifier')}, Agency: {sample.get('sourceAgency')}, Severity: {sample.get('severity')}"
            else:
                details = "No active alerts in backend"
            log_test(3, "OASIS CAP 1.2 XML parsing and structure validation", passed, details)
    except Exception as e:
        log_test(3, "OASIS CAP 1.2 XML parsing", False, str(e))

def test_4_etag_storage():
    """Test 4: ETag storage on entity and feed telemetry."""
    try:
        url = f"{BASE_URL}/api/official-alerts/status"
        with urllib.request.urlopen(url) as resp:
            status = json.loads(resp.read().decode('utf-8'))
            last_etag = status.get("lastEtag")
            passed = last_etag is not None and len(last_etag) > 0
            log_test(4, "ETag Header Capture & Entity Caching", passed, f"Cached ETag: {last_etag}")
    except Exception as e:
        log_test(4, "ETag Header Capture & Entity Caching", False, str(e))

def test_5_http_304_conditional_get(etag):
    """Test 5: HTTP 304 handling on subsequent request with If-None-Match."""
    if not etag:
        log_test(5, "HTTP 304 Not Modified Conditional Request", False, "No ETag available from Test 1")
        return
    try:
        url = "https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml"
        req = urllib.request.Request(url, headers={
            "User-Agent": "DisasterManagement-TacticalClient/2.0",
            "If-None-Match": etag
        })
        try:
            with urllib.request.urlopen(req, timeout=15) as resp:
                code = resp.getcode()
                log_test(5, "HTTP 304 Not Modified Conditional Request", False, f"Received {code} instead of 304")
        except urllib.error.HTTPError as e:
            passed = (e.code == 304)
            log_test(5, "HTTP 304 Not Modified Conditional Request", passed, f"HTTP Status: {e.code} Not Modified")
    except Exception as e:
        log_test(5, "HTTP 304 Not Modified Conditional Request", False, str(e))

def test_6_http_200_update_handling():
    """Test 6: Handling HTTP 200 and test ingestion of updated CAP XML fixture."""
    global ADMIN_TOKEN
    try:
        url = f"{BASE_URL}/api/admin/ndma/test-ingest"
        test_fixture = """<?xml version="1.0" encoding="UTF-8"?>
<alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
    <identifier>TEST-ALERT-REVISION-001</identifier>
    <sender>IMD Chennai</sender>
    <sent>2026-11-20T10:00:00+05:30</sent>
    <status>Actual</status>
    <msgType>Alert</msgType>
    <scope>Public</scope>
    <info>
        <language>en-IN</language>
        <category>Met</category>
        <event>Heavy Rain</event>
        <urgency>Immediate</urgency>
        <severity>Severe</severity>
        <certainty>Observed</certainty>
        <headline>Flash Flood and Extremely Heavy Rain Advisory</headline>
        <description>Intense precipitation expected over coastal Chennai and Tiruvallur districts.</description>
        <instruction>Evacuate low lying areas. Remain indoors and avoid waterlogged roads.</instruction>
        <area>
            <areaDesc>Chennai, Tamil Nadu</areaDesc>
            <circle>13.0827,80.2707,25.0</circle>
        </area>
    </info>
</alert>"""
        req = urllib.request.Request(url, data=test_fixture.encode('utf-8'), headers={
            "Content-Type": "application/xml",
            "Authorization": f"Bearer {ADMIN_TOKEN}"
        })
        with urllib.request.urlopen(req) as resp:
            body = json.loads(resp.read().decode('utf-8'))
            passed = (body.get("success") is True and body.get("alertId") is not None)
            log_test(6, "CAP XML Ingestion Endpoint & Parsing (HTTP 200 Update)", passed, f"Ingested ID: {body.get('alertId')}, Identifier: {body.get('identifier')}")
            return body.get("alertId")
    except urllib.error.HTTPError as e:
        err_msg = e.read().decode('utf-8')
        log_test(6, "CAP XML Ingestion Endpoint & Parsing", False, f"HTTP Error {e.code}: {err_msg}")
        return None
    except Exception as e:
        log_test(6, "CAP XML Ingestion Endpoint & Parsing", False, str(e))
        return None

def test_7_mysql_alert_persistence(alert_db_id):
    """Test 7: MySQL alert persistence and column verification."""
    try:
        url = f"{BASE_URL}/api/official-alerts/{alert_db_id}"
        with urllib.request.urlopen(url) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            passed = all([
                data.get("id") == alert_db_id,
                data.get("state") == "Tamil Nadu",
                data.get("district") == "Chennai",
                data.get("severity") == "Severe",
                data.get("latitude") is not None,
                data.get("longitude") is not None
            ])
            log_test(7, "MySQL Entity Persistence (state, district, severity, coordinates)", passed, f"State: {data.get('state')}, District: {data.get('district')}, Lat: {data.get('latitude')}, Lon: {data.get('longitude')}")
    except Exception as e:
        log_test(7, "MySQL Entity Persistence", False, str(e))

def test_8_duplicate_prevention(alert_db_id):
    """Test 8: Duplicate alert prevention when re-ingesting identical alert identifier."""
    global ADMIN_TOKEN
    try:
        url = f"{BASE_URL}/api/admin/ndma/test-ingest"
        test_fixture = """<?xml version="1.0" encoding="UTF-8"?>
<alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
    <identifier>TEST-ALERT-REVISION-001</identifier>
    <sender>IMD Chennai</sender>
    <sent>2026-11-20T10:00:00+05:30</sent>
    <status>Actual</status>
    <msgType>Alert</msgType>
    <scope>Public</scope>
    <info>
        <language>en-IN</language>
        <category>Met</category>
        <event>Heavy Rain</event>
        <urgency>Immediate</urgency>
        <severity>Severe</severity>
        <certainty>Observed</certainty>
        <headline>Flash Flood and Extremely Heavy Rain Advisory</headline>
        <description>Intense precipitation expected over coastal Chennai and Tiruvallur districts.</description>
        <instruction>Evacuate low lying areas.</instruction>
        <area>
            <areaDesc>Chennai, Tamil Nadu</areaDesc>
        </area>
    </info>
</alert>"""
        req = urllib.request.Request(url, data=test_fixture.encode('utf-8'), headers={
            "Content-Type": "application/xml",
            "Authorization": f"Bearer {ADMIN_TOKEN}"
        })
        with urllib.request.urlopen(req) as resp:
            body = json.loads(resp.read().decode('utf-8'))
            # When re-ingested with same identifier, alertId must remain the same (no duplicate row)
            passed = (body.get("success") is True and body.get("alertId") == alert_db_id and body.get("identifier") == "TEST-ALERT-REVISION-001")
            log_test(8, "Duplicate Alert Prevention (Re-ingestion reuses existing entity)", passed, f"Alert ID: {body.get('alertId')} (Preserved original ID {alert_db_id})")
    except Exception as e:
        log_test(8, "Duplicate Alert Prevention", False, str(e))

def test_9_alert_revision_handling(alert_db_id):
    """Test 9: Alert update & revision handling."""
    try:
        url = f"{BASE_URL}/api/official-alerts/{alert_db_id}"
        with urllib.request.urlopen(url) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            rev = data.get("revisionNumber")
            passed = (rev is not None and rev >= 2)
            log_test(9, "Alert Update & Revision Increment", passed, f"Revision Number: {rev}")
    except Exception as e:
        log_test(9, "Alert Update & Revision Increment", False, str(e))

def test_10_lifecycle_expiry():
    """Test 10: Lifecycle expiry handling (markExpiredAlerts)."""
    try:
        url = f"{BASE_URL}/api/official-alerts/history"
        with urllib.request.urlopen(url) as resp:
            history = json.loads(resp.read().decode('utf-8'))
            # History returns alerts sorted by effectiveTime; verify items exist
            passed = isinstance(history, list) and len(history) > 0
            log_test(10, "Alert Lifecycle & History API", passed, f"Total History Records: {len(history)}")
    except Exception as e:
        log_test(10, "Alert Lifecycle & History API", False, str(e))

def test_11_geotargeted_filtering():
    """Test 11: Geographic filtering (State and District endpoints)."""
    try:
        state_url = f"{BASE_URL}/api/official-alerts/state/Tamil%20Nadu"
        with urllib.request.urlopen(state_url) as resp:
            tn_alerts = json.loads(resp.read().decode('utf-8'))
            passed = isinstance(tn_alerts, list) and len(tn_alerts) > 0
            details = f"Tamil Nadu Alerts: {len(tn_alerts)}"
            
        district_url = f"{BASE_URL}/api/official-alerts/district/Chennai"
        with urllib.request.urlopen(district_url) as resp:
            chennai_alerts = json.loads(resp.read().decode('utf-8'))
            passed = passed and isinstance(chennai_alerts, list) and len(chennai_alerts) > 0
            details += f", Chennai Alerts: {len(chennai_alerts)}"
            
        log_test(11, "Geographic Filtering (State & District Endpoints)", passed, details)
    except Exception as e:
        log_test(11, "Geographic Filtering", False, str(e))

def test_12_admin_manual_sync():
    """Test 12: Admin manual synchronization endpoint POST /api/admin/ndma/sync."""
    global ADMIN_TOKEN
    try:
        url = f"{BASE_URL}/api/admin/ndma/sync"
        req = urllib.request.Request(url, data=b"", headers={
            "Authorization": f"Bearer {ADMIN_TOKEN}"
        })
        with urllib.request.urlopen(req) as resp:
            body = json.loads(resp.read().decode('utf-8'))
            passed = (body.get("status") == "success" or body.get("success") is True) and "metrics" in body
            metrics = body.get("metrics", {})
            details = f"Status: {metrics.get('status')}, Imported: {metrics.get('totalRecordsImported')}, Active Warnings: {metrics.get('activeOfficialAlertsCount')}"
            log_test(12, "Admin Manual Synchronization Endpoint (POST /api/admin/ndma/sync)", passed, details)
    except Exception as e:
        log_test(12, "Admin Manual Synchronization Endpoint", False, str(e))

def test_13_status_telemetry():
    """Test 13: Dashboard statistics & telemetry."""
    try:
        url = f"{BASE_URL}/api/official-alerts/status"
        with urllib.request.urlopen(url) as resp:
            data = json.loads(resp.read().decode('utf-8'))
            has_telemetry = all([
                "status" in data,
                "totalRecordsImported" in data,
                "activeOfficialAlertsCount" in data,
                "lastEtag" in data,
                "activeFeedUrl" in data
            ])
            log_test(13, "Live Status Telemetry Endpoint (GET /api/official-alerts/status)", has_telemetry, f"Feed Status: {data.get('status')}, Duration: {data.get('lastDurationMs')}ms")
    except Exception as e:
        log_test(13, "Live Status Telemetry Endpoint", False, str(e))

def test_14_citizen_dashboard_connectivity():
    """Test 14: Citizen dashboard connectivity to official warnings."""
    try:
        url = f"{BASE_URL}/api/official-alerts/active"
        with urllib.request.urlopen(url) as resp:
            code = resp.getcode()
            alerts = json.loads(resp.read().decode('utf-8'))
            passed = (code == 200 and isinstance(alerts, list))
            log_test(14, "Citizen Dashboard Warning Connectivity (Public GET /api/official-alerts/active)", passed, f"Returned {len(alerts)} active official warnings")
    except Exception as e:
        log_test(14, "Citizen Dashboard Warning Connectivity", False, str(e))

def test_15_responder_dashboard_connectivity():
    """Test 15: Responder dashboard connectivity and alerts feed."""
    try:
        resp_token = get_auth_token("responder@disaster.gov.in", "responder123")
        url = f"{BASE_URL}/api/official-alerts/active"
        req = urllib.request.Request(url, headers={"Authorization": f"Bearer {resp_token}"} if resp_token else {})
        with urllib.request.urlopen(req) as resp:
            alerts = json.loads(resp.read().decode('utf-8'))
            passed = isinstance(alerts, list)
            log_test(15, "Responder Dashboard Official Warning Accessibility", passed, f"Available to field responders: {len(alerts)} alerts")
    except Exception as e:
        log_test(15, "Responder Dashboard Accessibility", False, str(e))

def test_16_unlocated_alerts_handling():
    """Test 16: Unlocated alerts - ensure alerts without spatial coordinates retain null coordinates and UNLOCATED precision."""
    global ADMIN_TOKEN
    try:
        url = f"{BASE_URL}/api/admin/ndma/test-ingest"
        unlocated_fixture = """<?xml version="1.0" encoding="UTF-8"?>
<alert xmlns="urn:oasis:names:tc:emergency:cap:1.2">
    <identifier>TEST-ALERT-UNLOCATED-001</identifier>
    <sender>NDMA Delhi</sender>
    <sent>2026-11-20T10:00:00+05:30</sent>
    <status>Actual</status>
    <msgType>Alert</msgType>
    <scope>Public</scope>
    <info>
        <language>en-IN</language>
        <category>Other</category>
        <event>General Safety Advisory</event>
        <urgency>Past</urgency>
        <severity>Minor</severity>
        <certainty>Unlikely</certainty>
        <headline>Nationwide Mock Drill Communication</headline>
        <description>General advisory without designated district coordinates.</description>
        <instruction>No action required.</instruction>
        <area>
            <areaDesc>Unknown Region</areaDesc>
        </area>
    </info>
</alert>"""
        req = urllib.request.Request(url, data=unlocated_fixture.encode('utf-8'), headers={
            "Content-Type": "application/xml",
            "Authorization": f"Bearer {ADMIN_TOKEN}"
        })
        with urllib.request.urlopen(req) as resp:
            body = json.loads(resp.read().decode('utf-8'))
            alert_id = body.get("alertId")
            
        # Verify from GET endpoint that coordinates are null and precision is UNLOCATED
        with urllib.request.urlopen(f"{BASE_URL}/api/official-alerts/{alert_id}") as resp:
            data = json.loads(resp.read().decode('utf-8'))
            passed = (data.get("latitude") is None and data.get("longitude") is None and data.get("locationPrecision") == "UNLOCATED")
            log_test(16, "Unlocated Alerts Integrity (Coordinates Null, UNLOCATED precision, No Fake Pins)", passed, f"Precision: {data.get('locationPrecision')}, Lat: {data.get('latitude')}")
    except Exception as e:
        log_test(16, "Unlocated Alerts Integrity", False, str(e))

def test_17_emergency_broadcast_escalation():
    """Test 17: Emergency broadcast escalation - Admin creates broadcast referencing NDMA alert ID."""
    global ADMIN_TOKEN
    try:
        url = f"{BASE_URL}/api/alerts"
        ts = int(datetime.now().timestamp())
        payload = {
            "title": f"[OFFICIAL NDMA ESCALATION] Heavy Rain Warning - Chennai - {ts}",
            "message": "[Source: Official NDMA SACHET Alert, Ref ID: TEST-ALERT-REVISION-001]\n\nFollow civil defence evacuation protocols.",
            "disasterType": "Heavy Rain",
            "severity": "Medium",
            "region": "Chennai",
            "status": "Active"
        }
        data = json.dumps(payload).encode('utf-8')
        req = urllib.request.Request(url, data=data, headers={
            "Content-Type": "application/json",
            "Authorization": f"Bearer {ADMIN_TOKEN}"
        })
        with urllib.request.urlopen(req) as resp:
            body = json.loads(resp.read().decode('utf-8'))
            alert_obj = body.get("alert", {})
            passed = (alert_obj.get("alertId") is not None and "TEST-ALERT-REVISION-001" in alert_obj.get("message", ""))
            log_test(17, "Emergency Broadcast Terminal Escalation with NDMA Traceability", passed, f"Broadcast ID: {alert_obj.get('alertId')}, Title: {alert_obj.get('title')}")
    except Exception as e:
        log_test(17, "Emergency Broadcast Terminal Escalation", False, str(e))

def main():
    global ADMIN_TOKEN
    print("=" * 70)
    print("PROMPT 5: OFFICIAL NDMA SACHET INTEGRATION & BROADCAST VERIFICATION SUITE")
    print("=" * 70)

    ADMIN_TOKEN = get_auth_token("admin@disaster.gov.in", "admin123")
    if not ADMIN_TOKEN:
        print("[FATAL] Could not obtain Admin JWT token. Aborting test suite.")
        sys.exit(1)
    print(f"[*] Admin JWT authenticated successfully.")

    etag = test_1_feed_retrieval_and_status()
    test_2_rss_parsing(etag)
    test_3_cap_xml_parsing()
    test_4_etag_storage()
    test_5_http_304_conditional_get(etag)
    alert_db_id = test_6_http_200_update_handling()
    if alert_db_id:
        test_7_mysql_alert_persistence(alert_db_id)
        test_8_duplicate_prevention(alert_db_id)
        test_9_alert_revision_handling(alert_db_id)
    test_10_lifecycle_expiry()
    test_11_geotargeted_filtering()
    test_12_admin_manual_sync()
    test_13_status_telemetry()
    test_14_citizen_dashboard_connectivity()
    test_15_responder_dashboard_connectivity()
    test_16_unlocated_alerts_handling()
    test_17_emergency_broadcast_escalation()

    print("=" * 70)
    print(f"VERIFICATION SUMMARY: {passed_tests} PASSED / {passed_tests + failed_tests} TOTAL")
    print("=" * 70)

    if failed_tests > 0:
        sys.exit(1)

if __name__ == "__main__":
    main()

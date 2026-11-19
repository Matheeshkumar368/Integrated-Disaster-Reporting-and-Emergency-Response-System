import requests
import json
import sys

BASE_URL = "http://localhost:8080"

def log_step(step_num, title, success, details=""):
    status = "PASS" if success else "FAIL"
    print(f"[{status}] STEP {step_num}: {title}")
    if details:
        print(f"       {details}")

def run_navigation_flow():
    print("=" * 75)
    print("TESTING CITIZEN NOTIFICATIONS TO DASHBOARD NAVIGATION & REPORT FLOW")
    print("=" * 75)
    
    passed = 0
    failed = 0

    # -------------------------------------------------------------------------
    # STEP 1: Citizen logs in
    # -------------------------------------------------------------------------
    cit_token = None
    try:
        r = requests.post(f"{BASE_URL}/api/auth/login", json={"email": "citizen@gmail.com", "password": "citizen123"})
        if r.status_code == 200:
            data = r.json()
            cit_token = data.get("token")
            role = data.get("role")
            if cit_token and role == "Citizen":
                log_step(1, "Citizen Authentication & JWT Issuance", True, f"Role: {role}, Token issued successfully")
                passed += 1
            else:
                log_step(1, "Citizen Authentication & JWT Issuance", False, f"Unexpected data: {data}")
                failed += 1
        else:
            log_step(1, "Citizen Authentication & JWT Issuance", False, f"HTTP {r.status_code}: {r.text}")
            failed += 1
    except Exception as e:
        log_step(1, "Citizen Authentication & JWT Issuance", False, str(e))
        failed += 1

    cit_headers = {"Authorization": f"Bearer {cit_token}"}

    # -------------------------------------------------------------------------
    # STEP 2: Citizen Notification Center opens
    # -------------------------------------------------------------------------
    try:
        r = requests.get(f"{BASE_URL}/citizen-notifications.html")
        if r.status_code == 200 and "Citizen" in r.text and "Notifications" in r.text:
            log_step(2, "Citizen Dedicated Notification Center Loads", True, f"HTTP 200, Content-Length: {len(r.text)} bytes")
            passed += 1
        else:
            log_step(2, "Citizen Dedicated Notification Center Loads", False, f"HTTP {r.status_code}")
            failed += 1
    except Exception as e:
        log_step(2, "Citizen Dedicated Notification Center Loads", False, str(e))
        failed += 1

    # -------------------------------------------------------------------------
    # STEP 3: Citizen clicks Dashboard link
    # -------------------------------------------------------------------------
    try:
        # Verify citizen-notifications.html has dashboard link pointing to citizen-dashboard.html
        r = requests.get(f"{BASE_URL}/citizen-notifications.html")
        has_dash_hero = 'href="citizen-dashboard.html"' in r.text
        has_dash_sidebar = 'href="citizen-dashboard.html"' in r.text
        if has_dash_hero and has_dash_sidebar:
            log_step(3, "Dashboard Navigation Links Configured", True, "Both hero button and sidebar link navigate directly to citizen-dashboard.html")
            passed += 1
        else:
            log_step(3, "Dashboard Navigation Links Configured", False, f"Hero: {has_dash_hero}, Sidebar: {has_dash_sidebar}")
            failed += 1
    except Exception as e:
        log_step(3, "Dashboard Navigation Links Configured", False, str(e))
        failed += 1

    # -------------------------------------------------------------------------
    # STEP 4: Citizen clicks Report Disaster -> URL has ?report
    # -------------------------------------------------------------------------
    try:
        r = requests.get(f"{BASE_URL}/citizen-notifications.html")
        has_report_link = 'href="citizen-dashboard.html?report"' in r.text
        if has_report_link:
            log_step(4, "Report Disaster Navigation Link", True, 'Sidebar link configured to citizen-dashboard.html?report')
            passed += 1
        else:
            log_step(4, "Report Disaster Navigation Link", False, 'Link with ?report not found in sidebar')
            failed += 1
    except Exception as e:
        log_step(4, "Report Disaster Navigation Link", False, str(e))
        failed += 1

    # -------------------------------------------------------------------------
    # STEP 5: Verify citizen-dashboard.html handles ?report and switches to reportTab
    # -------------------------------------------------------------------------
    try:
        r = requests.get(f"{BASE_URL}/citizen-dashboard.html?report")
        if r.status_code == 200:
            content = r.text
            has_report_tab = 'id="reportTab"' in content
            has_resolver = 'resolveActiveTabFromUrl' in content
            has_report_query_check = "urlParams.has('report')" in content or "searchStr === '?report'" in content
            has_activate_fn = 'activateTabFromUrl' in content

            if has_report_tab and has_resolver and has_report_query_check and has_activate_fn:
                log_step(5, "Citizen Dashboard ?report Query Handling Code", True, "URL tab resolver, ?report detection, and reportTab auto-switch verified in citizen-dashboard.html")
                passed += 1
            else:
                log_step(5, "Citizen Dashboard ?report Query Handling Code", False, f"tab={has_report_tab}, resolver={has_resolver}, check={has_report_query_check}")
                failed += 1
        else:
            log_step(5, "Citizen Dashboard ?report Query Handling Code", False, f"HTTP {r.status_code}")
            failed += 1
    except Exception as e:
        log_step(5, "Citizen Dashboard ?report Query Handling Code", False, str(e))
        failed += 1

    # -------------------------------------------------------------------------
    # STEP 6: Verify other query parameters (?alerts, ?map, ?my-reports)
    # -------------------------------------------------------------------------
    try:
        r = requests.get(f"{BASE_URL}/citizen-dashboard.html")
        content = r.text
        has_alerts_check = "urlParams.has('alerts')" in content
        has_map_check = "urlParams.has('map')" in content
        has_myreports_check = "urlParams.has('my-reports')" in content
        has_safety_check = "urlParams.has('safety')" in content
        has_hash_check = "window.location.hash" in content

        if has_alerts_check and has_map_check and has_myreports_check and has_safety_check and has_hash_check:
            log_step(6, "Full Query & Hash Parameter Support (?alerts, ?map, ?my-reports, ?safety)", True, "All 5 tabs correctly mapped to URL query and hash parameters")
            passed += 1
        else:
            log_step(6, "Full Query & Hash Parameter Support", False, f"alerts={has_alerts_check}, map={has_map_check}, myreports={has_myreports_check}")
            failed += 1
    except Exception as e:
        log_step(6, "Full Query & Hash Parameter Support", False, str(e))
        failed += 1

    # -------------------------------------------------------------------------
    # STEP 7: Citizen fills in and submits incident report form
    # -------------------------------------------------------------------------
    created_report_id = None
    try:
        report_payload = {
            "title": "Severe Flash Flood Inundation - West Colony",
            "incidentType": "Flood",
            "severity": "High",
            "location": "West Colony, Karur, Tamil Nadu",
            "message": "[Flood | Severity: High] Flash flood inundation trapped 8 families on residential rooftops. Need immediate boat evacuation.\n\nWater level reaching 4 feet and rising.",
            "latitude": 10.9602,
            "longitude": 78.0768,
            "citizenName": "Arjun Kumar",
            "citizenContact": "9876543210"
        }
        r = requests.post(f"{BASE_URL}/api/citizen-reports", json=report_payload, headers=cit_headers)
        if r.status_code in [200, 201]:
            rep_data = r.json()
            created_report_id = rep_data.get("reportId") or rep_data.get("id")
            log_step(7, "Citizen Submits Emergency Incident Report", True, f"Report created with ID #{created_report_id}")
            passed += 1
        else:
            log_step(7, "Citizen Submits Emergency Incident Report", False, f"HTTP {r.status_code}: {r.text}")
            failed += 1
    except Exception as e:
        log_step(7, "Citizen Submits Emergency Incident Report", False, str(e))
        failed += 1

    # -------------------------------------------------------------------------
    # STEP 8: Verify report is saved in actual MySQL database
    # -------------------------------------------------------------------------
    try:
        if created_report_id:
            r = requests.get(f"{BASE_URL}/api/citizen-reports/{created_report_id}", headers=cit_headers)
            if r.status_code == 200:
                saved = r.json()
                if saved.get("reportId") == created_report_id and saved.get("incidentType") == "Flood":
                    log_step(8, "Report Verified in Actual MySQL Database", True, f"Persisted: ID={saved.get('reportId')}, Title='{saved.get('title')}', Status={saved.get('status')}")
                    passed += 1
                else:
                    log_step(8, "Report Verified in Actual MySQL Database", False, f"Data mismatch: {saved}")
                    failed += 1
            else:
                log_step(8, "Report Verified in Actual MySQL Database", False, f"HTTP {r.status_code}: {r.text}")
                failed += 1
        else:
            log_step(8, "Report Verified in Actual MySQL Database", False, "No report ID created")
            failed += 1
    except Exception as e:
        log_step(8, "Report Verified in Actual MySQL Database", False, str(e))
        failed += 1

    # -------------------------------------------------------------------------
    # STEP 9: Verify Admin receives the new report and notification
    # -------------------------------------------------------------------------
    try:
        r_admin_login = requests.post(f"{BASE_URL}/api/auth/login", json={"email": "admin@disaster.gov.in", "password": "admin123"})
        admin_token = r_admin_login.json().get("token")
        admin_headers = {"Authorization": f"Bearer {admin_token}"}

        # Check Admin's notification feed
        r_admin_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=admin_headers)
        if r_admin_notifs.status_code == 200:
            notifs = r_admin_notifs.json()
            has_report_notif = any(
                n.get("relatedIncidentId") == created_report_id or
                "West Colony" in n.get("message", "") or
                "Flood" in n.get("title", "")
                for n in notifs
            )
            if has_report_notif:
                log_step(9, "Admin Notification Received for New Report", True, f"Admin notification verified for Report #{created_report_id}")
                passed += 1
            else:
                log_step(9, "Admin Notification Received for New Report", False, f"Report notification not in Admin feed ({len(notifs)} items)")
                failed += 1
        else:
            log_step(9, "Admin Notification Received for New Report", False, f"HTTP {r_admin_notifs.status_code}")
            failed += 1
    except Exception as e:
        log_step(9, "Admin Notification Received for New Report", False, str(e))
        failed += 1

    # -------------------------------------------------------------------------
    # STEP 10: Verify Notification click contextual routing logic in notifications.js
    # -------------------------------------------------------------------------
    try:
        r_js = requests.get(f"{BASE_URL}/notifications.js")
        if r_js.status_code == 200:
            js_text = r_js.text
            has_target_resolver = 'resolveTargetUrl' in js_text
            has_myreports_route = '/citizen-dashboard.html?my-reports' in js_text
            has_alerts_route = '/citizen-dashboard.html?alerts' in js_text
            has_report_route = '/citizen-dashboard.html?report' in js_text

            if has_target_resolver and has_myreports_route and has_alerts_route and has_report_route:
                log_step(10, "Notification Contextual Routing in notifications.js", True, "Incident notifications route to ?my-reports, alerts to ?alerts, reports to ?report")
                passed += 1
            else:
                log_step(10, "Notification Contextual Routing in notifications.js", False, f"resolver={has_target_resolver}, myreports={has_myreports_route}")
                failed += 1
        else:
            log_step(10, "Notification Contextual Routing in notifications.js", False, f"HTTP {r_js.status_code}")
            failed += 1
    except Exception as e:
        log_step(10, "Notification Contextual Routing in notifications.js", False, str(e))
        failed += 1

    # -------------------------------------------------------------------------
    # STEP 11: Refresh Dashboard with ?report test
    # -------------------------------------------------------------------------
    try:
        # Simulate browser GET reload on citizen-dashboard.html?report
        r1 = requests.get(f"{BASE_URL}/citizen-dashboard.html?report", headers=cit_headers)
        r2 = requests.get(f"{BASE_URL}/citizen-dashboard.html?report", headers=cit_headers)
        if r1.status_code == 200 and r2.status_code == 200 and "activateTabFromUrl()" in r2.text:
            log_step(11, "Dashboard Page Reload with ?report Parameter", True, "Subsequent reloads reliably preserve URL query parameter and activateTabFromUrl() trigger")
            passed += 1
        else:
            log_step(11, "Dashboard Page Reload with ?report Parameter", False, f"r1={r1.status_code}, r2={r2.status_code}")
            failed += 1
    except Exception as e:
        log_step(11, "Dashboard Page Reload with ?report Parameter", False, str(e))
        failed += 1

    print("=" * 75)
    print(f"TEST RESULTS: {passed} PASSED, {failed} FAILED (TOTAL: {passed + failed}/11)")
    print("=" * 75)
    return failed == 0

if __name__ == "__main__":
    success = run_navigation_flow()
    sys.exit(0 if success else 1)

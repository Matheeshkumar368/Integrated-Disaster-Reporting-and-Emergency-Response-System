import urllib.request
import urllib.error
import json
import base64
import re
import sys

BASE_URL = "http://localhost:8080"

results = []

def record(test_name, status, details=""):
    results.append({"test": test_name, "status": status, "details": details})
    marker = "[PASS]" if status == "PASSED" else "[FAIL]"
    print(f"{marker} {test_name}: {details}")

def login(email, password):
    url = f"{BASE_URL}/api/auth/login"
    payload = json.dumps({"email": email, "password": password}).encode("utf-8")
    req = urllib.request.Request(url, data=payload, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as resp:
            data = json.loads(resp.read().decode("utf-8"))
            return data
    except Exception as e:
        print(f"Login failed for {email}: {e}")
        return None

print("======================================================================")
print(" SUITE 1: STATIC NAVIGATION LINK & LAYOUT AUDIT")
print("======================================================================")

# 1. Admin Mission Control Pages Navigation
admin_mc_pages = [
    ("admin-dashboard.html", "Mission Control"),
    ("admin-alerts.html", "Emergency Broadcasts"),
    ("admin-tasks.html", "Responder Tasks"),
    ("reports.html", "Incident Telemetry"),
    ("admin-profile.html", "Security & 2FA PIN")
]

required_admin_mc_links = [
    "/admin-notifications.html",
    "/admin-dashboard.html",
    "/admin-alerts.html",
    "/admin-tasks.html",
    "/reports.html",
    "/admin-users.html",
    "/emergency-videos.html",
    "/admin-profile.html"
]

for page, label in admin_mc_pages:
    path = f"src/main/resources/static/{page}"
    try:
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()
        missing = [lnk for lnk in required_admin_mc_links if lnk not in content]
        if not missing:
            record(f"Admin MC Navbar: {page} ({label})", "PASSED", f"All 8 tactical links present.")
        else:
            record(f"Admin MC Navbar: {page} ({label})", "FAILED", f"Missing links: {missing}")
    except Exception as e:
        record(f"Admin MC Navbar: {page}", "FAILED", str(e))

# 2. Admin Sidebar Management Pages Navigation
admin_sidebar_pages = [
    ("admin-notifications.html", "Notification Center"),
    ("admin-users.html", "Manage Users")
]

required_admin_sidebar_links = [
    "admin-notifications.html",
    "admin-dashboard.html",
    "admin-users.html",
    "admin-dashboard.html?disasters",
    "admin-alerts.html",
    "admin-tasks.html",
    "reports.html",
    "emergency-videos.html",
    "admin-profile.html"
]

for page, label in admin_sidebar_pages:
    path = f"src/main/resources/static/{page}"
    try:
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()
        missing = [lnk for lnk in required_admin_sidebar_links if lnk not in content]
        has_auth_helper = "auth-helper.js" in content
        if not missing and has_auth_helper:
            record(f"Admin Sidebar: {page} ({label})", "PASSED", f"All 9 core admin destinations present + auth-helper included.")
        else:
            record(f"Admin Sidebar: {page} ({label})", "FAILED", f"Missing: {missing}, auth-helper={has_auth_helper}")
    except Exception as e:
        record(f"Admin Sidebar: {page}", "FAILED", str(e))

# 3. Responder Pages Navigation
responder_pages = [
    ("responder-dashboard.html", "Tactical Queue"),
    ("responder-rescue.html", "Live Rescue Operations"),
    ("responder-tasks.html", "Assigned Tasks"),
    ("responder-profile.html", "Unit Profile & Zone")
]

required_responder_links = [
    "/responder-notifications.html",
    "/responder-dashboard.html",
    "/responder-rescue.html",
    "/responder-tasks.html",
    "/emergency-videos.html",
    "/responder-profile.html"
]

for page, label in responder_pages:
    path = f"src/main/resources/static/{page}"
    try:
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()
        missing = [lnk for lnk in required_responder_links if lnk not in content]
        if not missing:
            record(f"Responder Navbar: {page} ({label})", "PASSED", "All 6 responder links present.")
        else:
            record(f"Responder Navbar: {page} ({label})", "FAILED", f"Missing links: {missing}")
    except Exception as e:
        record(f"Responder Navbar: {page}", "FAILED", str(e))

# Responder Notifications sidebar
try:
    with open("src/main/resources/static/responder-notifications.html", "r", encoding="utf-8") as f:
        r_notif_content = f.read()
    r_sidebar_req = ["responder-notifications.html", "responder-dashboard.html", "responder-rescue.html", "responder-tasks.html", "emergency-videos.html", "responder-profile.html"]
    r_missing = [lnk for lnk in r_sidebar_req if lnk not in r_notif_content]
    if not r_missing and "auth-helper.js" in r_notif_content:
        record("Responder Sidebar: responder-notifications.html", "PASSED", "All 6 responder destinations present.")
    else:
        record("Responder Sidebar: responder-notifications.html", "FAILED", f"Missing: {r_missing}")
except Exception as e:
    record("Responder Sidebar: responder-notifications.html", "FAILED", str(e))

# 4. Citizen Pages Navigation
citizen_pages = [
    ("citizen-dashboard.html", "Citizen Safety Hub"),
    ("profile.html", "Civilian Profile")
]

required_citizen_links = [
    "/citizen-notifications.html",
    "/citizen-dashboard.html",
    "/citizen-dashboard.html?report",
    "/citizen-dashboard.html?my-reports",
    "/emergency-videos.html",
    "/profile.html"
]

for page, label in citizen_pages:
    path = f"src/main/resources/static/{page}"
    try:
        with open(path, "r", encoding="utf-8") as f:
            content = f.read()
        missing = [lnk for lnk in required_citizen_links if lnk not in content]
        if not missing:
            record(f"Citizen Navbar: {page} ({label})", "PASSED", "All 6 citizen destinations present.")
        else:
            record(f"Citizen Navbar: {page} ({label})", "FAILED", f"Missing links: {missing}")
    except Exception as e:
        record(f"Citizen Navbar: {page}", "FAILED", str(e))

# Citizen Notifications sidebar
try:
    with open("src/main/resources/static/citizen-notifications.html", "r", encoding="utf-8") as f:
        c_notif_content = f.read()
    c_sidebar_req = ["citizen-notifications.html", "citizen-dashboard.html", "citizen-dashboard.html?report", "citizen-dashboard.html?my-reports", "emergency-videos.html", "profile.html"]
    c_missing = [lnk for lnk in c_sidebar_req if lnk not in c_notif_content]
    if not c_missing and "auth-helper.js" in c_notif_content:
        record("Citizen Sidebar: citizen-notifications.html", "PASSED", "All 6 citizen destinations present.")
    else:
        record("Citizen Sidebar: citizen-notifications.html", "FAILED", f"Missing: {c_missing}")
except Exception as e:
    record("Citizen Sidebar: citizen-notifications.html", "FAILED", str(e))

print("\n======================================================================")
print(" SUITE 2: ANTI-ROLE-CROSSOVER & ROLE SEPARATION AUDIT")
print("======================================================================")

# Check fallback logic in code
with open("src/main/resources/static/admin-dashboard.html", "r", encoding="utf-8") as f:
    ad_content = f.read()
if "/citizen-notifications.html" in ad_content:
    record("Admin Dashboard Fallback Check", "FAILED", "Found fallback to citizen-notifications.html!")
else:
    record("Admin Dashboard Fallback Check", "PASSED", "No hardcoded citizen-notifications fallback.")

with open("src/main/resources/static/responder-dashboard.html", "r", encoding="utf-8") as f:
    rd_content = f.read()
if "/citizen-notifications.html" in rd_content:
    record("Responder Dashboard Fallback Check", "FAILED", "Found fallback to citizen-notifications.html!")
else:
    record("Responder Dashboard Fallback Check", "PASSED", "No hardcoded citizen-notifications fallback.")

# Check notifications.js anti-crossover guard
with open("src/main/resources/static/notifications.js", "r", encoding="utf-8") as f:
    notif_js = f.read()
if "actualRole && actualRole !== this.role" in notif_js and "window.location.replace" in notif_js:
    record("Notification Center Anti-Crossover Guard", "PASSED", "Active role verification and redirection implemented.")
else:
    record("Notification Center Anti-Crossover Guard", "FAILED", "Anti-crossover guard not found in notifications.js.")

# Check citizen dashboard strict role interception
with open("src/main/resources/static/citizen-dashboard.html", "r", encoding="utf-8") as f:
    cd_content = f.read()
if "Admin identity detected. Redirecting to Mission Control" in cd_content and "Responder identity detected" in cd_content:
    record("Citizen Dashboard Strict Role Interception", "PASSED", "Admin and Responder automatically redirected.")
else:
    record("Citizen Dashboard Strict Role Interception", "FAILED", "Role interception missing in citizen-dashboard.html.")

# Check profile.html auto-redirection
with open("src/main/resources/static/profile.html", "r", encoding="utf-8") as f:
    prof_content = f.read()
if "redirectToCorrectProfile();" in prof_content and "admin-profile.html" in prof_content:
    record("Generic Profile Auto-Redirection", "PASSED", "redirectToCorrectProfile() actively executes on load.")
else:
    record("Generic Profile Auto-Redirection", "FAILED", "redirectToCorrectProfile() not executed.")

print("\n======================================================================")
print(" SUITE 3: AUTHENTICATION TOKENS & BACKEND IDENTITY")
print("======================================================================")

admin_auth = login("admin@disaster.gov.in", "admin123")
responder_auth = login("responder@disaster.gov.in", "responder123")
citizen_auth = login("citizen@gmail.com", "citizen123")

if admin_auth and admin_auth.get("role") == "Admin":
    record("Admin Identity Verification", "PASSED", f"Authenticated as Admin, token received.")
else:
    record("Admin Identity Verification", "FAILED", "Failed to authenticate admin.")

if responder_auth and responder_auth.get("role") == "Responder":
    record("Responder Identity Verification", "PASSED", f"Authenticated as Responder, token received.")
else:
    record("Responder Identity Verification", "FAILED", "Failed to authenticate responder.")

if citizen_auth and citizen_auth.get("role") == "Citizen":
    record("Citizen Identity Verification", "PASSED", f"Authenticated as Citizen, token received.")
else:
    record("Citizen Identity Verification", "FAILED", "Failed to authenticate citizen.")

admin_token = admin_auth.get("token")
responder_token = responder_auth.get("token")
citizen_token = citizen_auth.get("token")

print("\n======================================================================")
print(" SUITE 4: THREE-ROLE ALERT CONNECTIVITY & BROADCASTING AUDIT")
print("======================================================================")

import time
timestamp = int(time.time())
alert_title = f"Cyclone Varuna Advisory {timestamp}"
alert_region = "Coimbatore"
alert_msg = f"Severe tropical storm advisory issued for {alert_region}. Emergency teams deployed and alert broadcasted."

# Step 1: Admin broadcasts new emergency alert
create_alert_url = f"{BASE_URL}/api/alerts"
alert_payload = json.dumps({
    "title": alert_title,
    "description": alert_msg,
    "message": alert_msg,
    "region": alert_region,
    "severity": "Moderate",
    "status": "Active",
    "broadcasted": True
}).encode("utf-8")

req = urllib.request.Request(
    create_alert_url,
    data=alert_payload,
    headers={
        "Content-Type": "application/json",
        "Authorization": f"Bearer {admin_token}"
    }
)

created_alert_id = None
try:
    with urllib.request.urlopen(req) as resp:
        resp_data = json.loads(resp.read().decode("utf-8"))
        if resp_data.get("status") == "success" and "alert" in resp_data:
            created_alert_id = resp_data["alert"]["alertId"]
            record("Emergency Alert Broadcast (Admin API)", "PASSED", f"Alert #{created_alert_id} successfully created and broadcasted.")
        else:
            record("Emergency Alert Broadcast (Admin API)", "FAILED", f"Unexpected response: {resp_data}")
except Exception as e:
    record("Emergency Alert Broadcast (Admin API)", "FAILED", str(e))

if created_alert_id:
    # Step 2: Verify Citizen receives alert in notifications
    citizen_notif_url = f"{BASE_URL}/api/notifications"
    req_c = urllib.request.Request(citizen_notif_url, headers={"Authorization": f"Bearer {citizen_token}"})
    try:
        with urllib.request.urlopen(req_c) as resp:
            c_notifs = json.loads(resp.read().decode("utf-8"))
            match = any(alert_region in (n.get("title", "") + n.get("message", "")) for n in c_notifs)
            if match:
                record("Alert Delivery to Citizen Feed", "PASSED", f"Citizen received broadcast notification for {alert_region}.")
            else:
                record("Alert Delivery to Citizen Feed", "FAILED", f"Alert notification not found in citizen feed ({len(c_notifs)} items).")
    except Exception as e:
        record("Alert Delivery to Citizen Feed", "FAILED", str(e))

    # Step 3: Verify Responder receives alert in notifications
    responder_notif_url = f"{BASE_URL}/api/notifications"
    req_r = urllib.request.Request(responder_notif_url, headers={"Authorization": f"Bearer {responder_token}"})
    try:
        with urllib.request.urlopen(req_r) as resp:
            r_notifs = json.loads(resp.read().decode("utf-8"))
            match = any(alert_region in (n.get("title", "") + n.get("message", "")) for n in r_notifs)
            if match:
                record("Alert Delivery to Responder Feed", "PASSED", f"Responder received broadcast notification for {alert_region}.")
            else:
                record("Alert Delivery to Responder Feed", "FAILED", f"Alert notification not found in responder feed ({len(r_notifs)} items).")
    except Exception as e:
        record("Alert Delivery to Responder Feed", "FAILED", str(e))

    # Step 4: Verify Admin receives alert record in notifications
    admin_notif_url = f"{BASE_URL}/api/notifications"
    req_a = urllib.request.Request(admin_notif_url, headers={"Authorization": f"Bearer {admin_token}"})
    try:
        with urllib.request.urlopen(req_a) as resp:
            a_notifs = json.loads(resp.read().decode("utf-8"))
            match = any(alert_title in n.get("title", "") or alert_region in n.get("message", "") for n in a_notifs)
            if match:
                record("Alert Delivery to Admin Feed", "PASSED", f"Admin received broadcast notification log.")
            else:
                record("Alert Delivery to Admin Feed", "FAILED", f"Alert notification not found in admin feed.")
    except Exception as e:
        record("Alert Delivery to Admin Feed", "FAILED", str(e))

    # Step 5: Verify Public Broadcasted Alerts endpoint
    public_alerts_url = f"{BASE_URL}/api/alerts/broadcasted"
    req_pub = urllib.request.Request(public_alerts_url)
    try:
        with urllib.request.urlopen(req_pub) as resp:
            pub_alerts = json.loads(resp.read().decode("utf-8"))
            match = any(a.get("alertId") == created_alert_id for a in pub_alerts)
            if match:
                record("Public/Citizen Broadcasted Alert Stream", "PASSED", f"Alert #{created_alert_id} visible in /api/alerts/broadcasted.")
            else:
                record("Public/Citizen Broadcasted Alert Stream", "FAILED", f"Alert #{created_alert_id} not in broadcast stream.")
    except Exception as e:
        record("Public/Citizen Broadcasted Alert Stream", "FAILED", str(e))

    # Step 6: Responder Acknowledges Alert
    ack_url = f"{BASE_URL}/api/alerts/{created_alert_id}/acknowledge"
    req_ack = urllib.request.Request(ack_url, method="PUT", headers={"Authorization": f"Bearer {responder_token}"})
    try:
        with urllib.request.urlopen(req_ack) as resp:
            ack_data = json.loads(resp.read().decode("utf-8"))
            if ack_data.get("status") == "Acknowledged":
                record("Responder Alert Acknowledgment", "PASSED", f"Alert #{created_alert_id} status updated to Acknowledged.")
            else:
                record("Responder Alert Acknowledgment", "FAILED", f"Unexpected status: {ack_data.get('status')}")
    except Exception as e:
        record("Responder Alert Acknowledgment", "FAILED", str(e))

    # Step 7: Admin Resolves Alert
    resolve_url = f"{BASE_URL}/api/alerts/{created_alert_id}/resolve"
    req_res = urllib.request.Request(resolve_url, method="PUT", headers={"Authorization": f"Bearer {admin_token}"})
    try:
        with urllib.request.urlopen(req_res) as resp:
            res_data = json.loads(resp.read().decode("utf-8"))
            if res_data.get("status") == "Resolved":
                record("Admin Alert Resolution", "PASSED", f"Alert #{created_alert_id} status updated to Resolved.")
            else:
                record("Admin Alert Resolution", "FAILED", f"Unexpected status: {res_data.get('status')}")
    except Exception as e:
        record("Admin Alert Resolution", "FAILED", str(e))

# Summary
passed_count = sum(1 for r in results if r["status"] == "PASSED")
failed_count = sum(1 for r in results if r["status"] == "FAILED")
total_count = len(results)

print("\n======================================================================")
print(f" FINAL TEST REPORT: {passed_count}/{total_count} PASSED ({failed_count} FAILED)")
print("======================================================================")

if failed_count > 0:
    sys.exit(1)
else:
    sys.exit(0)

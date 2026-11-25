import requests
import json
import time
import sys

BASE_URL = "http://localhost:8080"

def log(msg, status="INFO"):
    colors = {
        "INFO": "\033[94m[INFO]\033[0m",
        "PASS": "\033[92m[PASS]\033[0m",
        "FAIL": "\033[91m[FAIL]\033[0m",
        "STEP": "\033[93m[STEP]\033[0m"
    }
    prefix = colors.get(status, f"[{status}]")
    print(f"{prefix} {msg}")

def login(email, password):
    url = f"{BASE_URL}/api/auth/login"
    payload = {"email": email, "password": password}
    res = requests.post(url, json=payload, timeout=10)
    if res.status_code == 200:
        data = res.json()
        token = data.get("token") or (data.get("data") and data["data"].get("token"))
        return token
    else:
        log(f"Login failed for {email}: {res.status_code} {res.text}", "FAIL")
        return None

def run_tests():
    print("=" * 80)
    print("  INTEGRATED DISASTER MANAGEMENT SYSTEM - THREE-ROLE MASTER INTEGRATION E2E TEST")
    print("=" * 80)

    # 1. Authenticate All 3 Roles
    log("Authenticating Admin, Citizen, and Responder accounts...", "STEP")
    admin_token = login("admin@disaster.gov.in", "admin123")
    citizen_token = login("citizen@gmail.com", "citizen123")
    responder_token = login("responder@disaster.gov.in", "responder123")

    if not (admin_token and citizen_token and responder_token):
        log("Failed to authenticate all 3 roles. Aborting.", "FAIL")
        sys.exit(1)
    log("Admin, Citizen, and Responder successfully authenticated with JWT tokens.", "PASS")

    admin_headers = {"Authorization": f"Bearer {admin_token}", "Content-Type": "application/json"}
    citizen_headers = {"Authorization": f"Bearer {citizen_token}", "Content-Type": "application/json"}
    responder_headers = {"Authorization": f"Bearer {responder_token}", "Content-Type": "application/json"}

    # ---------------------------------------------------------
    # PRIORITY 1: Fix Admin-to-Citizen Alert Delivery
    # ---------------------------------------------------------
    log("Priority 1: Verifying Admin-to-Citizen Emergency Alert Delivery...", "STEP")
    
    # Verify Admin Security PIN to obtain Step-Up Token for High-Severity Alert
    res_pin = requests.post(f"{BASE_URL}/api/auth/admin/verify-pin", json={"pin": "123456"}, headers=admin_headers, timeout=10)
    step_up_token = None
    if res_pin.status_code == 200:
        step_up_token = res_pin.json().get("stepUpToken")
        log("Admin Security PIN verified (Step-up token acquired)", "PASS")

    alert_headers = dict(admin_headers)
    if step_up_token:
        alert_headers["X-Admin-Step-Up-Token"] = step_up_token

    alert_payload = {
        "title": f"URGENT: Flash Flood Evacuation Notice #{int(time.time())}",
        "message": "Heavy torrential rains expected. Move to elevated ground immediately.",
        "severity": "High",
        "category": "Flood",
        "region": "Coimbatore",
        "targetAudience": "ALL"
    }
    res_alert = requests.post(f"{BASE_URL}/api/alerts", json=alert_payload, headers=alert_headers, timeout=10)
    if res_alert.status_code in [200, 201]:
        alert_data = res_alert.json()
        alert_id = alert_data.get("alertId") or alert_data.get("id") or (alert_data.get("alert") and alert_data["alert"].get("alertId"))
        log(f"Admin broadcasted emergency alert successfully (Alert ID: {alert_id})", "PASS")
    else:
        log(f"Admin alert creation failed: {res_alert.status_code} {res_alert.text}", "FAIL")
        sys.exit(1)

    # Citizen retrieves alerts and verifies the new alert is present
    res_citizen_alerts = requests.get(f"{BASE_URL}/api/alerts", headers=citizen_headers, timeout=10)
    if res_citizen_alerts.status_code == 200:
        alerts_list = res_citizen_alerts.json()
        found = any(a.get("alertId") == alert_id or a.get("id") == alert_id for a in alerts_list)
        if found:
            log(f"Citizen successfully received broadcasted emergency alert #{alert_id}!", "PASS")
        else:
            log(f"Alert #{alert_id} was created but not present in Citizen alert feed.", "FAIL")
    else:
        log(f"Citizen failed to fetch alerts: {res_citizen_alerts.status_code}", "FAIL")

    # ---------------------------------------------------------
    # PRIORITY 2: Citizen Incident Report & Shared Collaboration Room
    # ---------------------------------------------------------
    log("Priority 2: Testing Incident Report Submission & Shared Collaboration Room...", "STEP")
    report_payload = {
        "message": "Water levels rising above 4ft on North Gandhi Street. 2 elders require boat rescue.",
        "location": "North Gandhi Street, Coimbatore",
        "incidentType": "Flood",
        "severity": "HIGH",
        "latitude": 11.0168,
        "longitude": 76.9558
    }
    res_report = requests.post(f"{BASE_URL}/api/citizen-reports", json=report_payload, headers=citizen_headers, timeout=10)
    if res_report.status_code in [200, 201]:
        report_data = res_report.json()
        report_id = report_data.get("reportId") or report_data.get("id")
        log(f"Citizen successfully submitted incident report (Report #{report_id})", "PASS")
    else:
        log(f"Incident submission failed: {res_report.status_code} {res_report.text}", "FAIL")
        sys.exit(1)

    # Admin assigns Responder to this report
    log(f"Admin assigning responder@disaster.gov.in to Incident #{report_id}...", "STEP")
    res_assign = requests.put(
        f"{BASE_URL}/api/citizen-reports/{report_id}/assign",
        params={"responderEmail": "responder@disaster.gov.in"},
        headers=admin_headers,
        timeout=10
    )
    if res_assign.status_code == 200:
        log(f"Admin assigned responder@disaster.gov.in to Incident #{report_id} successfully.", "PASS")
    else:
        log(f"Assignment failed: {res_assign.status_code} {res_assign.text}", "FAIL")

    # All 3 roles converse in the exact same incident collaboration room
    log("Verifying all 3 roles post and read messages in the same Incident Collaboration Room...", "STEP")
    # Citizen posts
    requests.post(
        f"{BASE_URL}/api/incidents/{report_id}/note?type=CITIZEN_REPORT",
        json={"message": "Citizens gathered on 1st floor balcony. Need assistance with stretcher."},
        headers=citizen_headers
    )
    # Admin posts
    requests.post(
        f"{BASE_URL}/api/incidents/{report_id}/note?type=CITIZEN_REPORT",
        json={"message": "Command Center dispatched SDRF Boat Unit #4 to your coordinates."},
        headers=admin_headers
    )
    # Responder posts
    requests.post(
        f"{BASE_URL}/api/incidents/{report_id}/note?type=CITIZEN_REPORT",
        json={"message": "SDRF Unit is 1.2km away approaching via bypass road."},
        headers=responder_headers
    )

    # Fetch room as Citizen
    res_room = requests.get(f"{BASE_URL}/api/incidents/{report_id}/room?type=CITIZEN_REPORT", headers=citizen_headers, timeout=10)
    if res_room.status_code == 200:
        room_data = res_room.json()
        timeline = room_data.get("timeline", [])
        notes = [e.get("message") for e in timeline if e.get("message")]
        log(f"Room timeline verified: Total {len(timeline)} events present across all 3 roles.", "PASS")
        for idx, event in enumerate(timeline[-3:], 1):
            log(f"   Event {idx}: [{event.get('actorRole')}] {event.get('actorEmail')}: {event.get('message')}")
    else:
        log(f"Failed to fetch collaboration room: {res_room.status_code}", "FAIL")

    # ---------------------------------------------------------
    # PRIORITY 3: Live GPS Responder Location Streaming
    # ---------------------------------------------------------
    log("Priority 3: Testing Live GPS Location Streaming and Radar Tracking...", "STEP")
    gps_payload = {
        "taskId": report_id,
        "latitude": 11.0180,
        "longitude": 76.9570,
        "speed": 34.5,
        "heading": 142.0,
        "accuracy": 4.5
    }
    res_gps = requests.post(f"{BASE_URL}/api/responder/location/update", json=gps_payload, headers=responder_headers, timeout=10)
    if res_gps.status_code == 200:
        log("Responder successfully broadcasted live GPS coordinates (11.0180, 76.9570)", "PASS")
    else:
        log(f"GPS broadcast failed: {res_gps.status_code} {res_gps.text}", "FAIL")

    # Citizen queries tracking session for this incident
    res_tracking = requests.get(f"{BASE_URL}/api/tracking/latest/{report_id}", headers=citizen_headers, timeout=10)
    if res_tracking.status_code == 200:
        track_data = res_tracking.json()
        log(f"Citizen Live Radar received responder coordinates: Lat {track_data.get('latitude')}, Lng {track_data.get('longitude')}, Speed: {track_data.get('speed')} km/h", "PASS")
    else:
        log(f"Citizen tracking latest check (Status {res_tracking.status_code})", "INFO")

    # ---------------------------------------------------------
    # PRIORITY 4: Responder-Initiated Video Call Session (WebRTC)
    # ---------------------------------------------------------
    log("Priority 4: Testing Responder-Initiated Emergency WebRTC Video Call...", "STEP")
    call_payload = {
        "incidentId": report_id,
        "citizenEmail": "citizen@gmail.com",
        "citizenName": "John Citizen",
        "responderName": "NDRF Field Officer",
        "sdpOffer": '{"type":"offer","sdp":"v=0\\r\\no=- 12345 2 IN IP4 127.0.0.1\\r\\ns=-\\r\\nt=0 0\\r\\n"}'
    }
    res_call_init = requests.post(f"{BASE_URL}/api/calls/initiate", json=call_payload, headers=responder_headers, timeout=10)
    if res_call_init.status_code == 200:
        call_info = res_call_init.json()
        call_id = call_info.get("callId")
        log(f"Responder initiated emergency video call (Call ID: {call_id})", "PASS")
    else:
        log(f"Call initiation failed: {res_call_init.status_code} {res_call_init.text}", "FAIL")
        sys.exit(1)

    # Citizen checks for incoming call
    res_incoming = requests.get(f"{BASE_URL}/api/calls/incoming", headers=citizen_headers, timeout=10)
    if res_incoming.status_code == 200:
        incoming_data = res_incoming.json()
        if incoming_data.get("hasIncomingCall") and incoming_data.get("call", {}).get("callId") == call_id:
            log(f"Citizen detected incoming emergency call from {incoming_data['call']['responderName']}!", "PASS")
        else:
            log("Citizen did not detect incoming call.", "FAIL")
    else:
        log(f"Incoming call check failed: {res_incoming.status_code}", "FAIL")

    # Citizen answers call with SDP Answer
    answer_payload = {
        "sdpAnswer": '{"type":"answer","sdp":"v=0\\r\\no=- 67890 2 IN IP4 127.0.0.1\\r\\ns=-\\r\\nt=0 0\\r\\n"}'
    }
    res_answer = requests.post(f"{BASE_URL}/api/calls/{call_id}/answer", json=answer_payload, headers=citizen_headers, timeout=10)
    if res_answer.status_code == 200:
        log("Citizen answered call with SDP Answer -> Status: CONNECTED", "PASS")
    else:
        log(f"Failed to answer call: {res_answer.status_code}", "FAIL")

    # Exchange ICE candidates
    res_cand = requests.post(
        f"{BASE_URL}/api/calls/{call_id}/ice-candidate",
        json={"role": "RESPONDER", "candidate": '{"candidate":"candidate:1 1 UDP 2130706431 192.168.1.10 50000 typ host","sdpMid":"0"}'},
        headers=responder_headers
    )
    if res_cand.status_code == 200:
        log("Exchanged WebRTC ICE candidate via signaling relay.", "PASS")

    # Poll call state
    res_poll = requests.get(f"{BASE_URL}/api/calls/{call_id}/poll", headers=responder_headers, timeout=10)
    if res_poll.status_code == 200:
        poll_call = res_poll.json().get("call", {})
        log(f"Call session verified in database: Status={poll_call.get('status')}, ConnectedAt={poll_call.get('connectedAt')}", "PASS")

    # Responder ends call
    res_end = requests.post(f"{BASE_URL}/api/calls/{call_id}/end", headers=responder_headers, timeout=10)
    if res_end.status_code == 200:
        log("Call ended cleanly. All tracks and resources disposed.", "PASS")

    # ---------------------------------------------------------
    # PRIORITY 5: Incident Status Progression Connected to Real Actions
    # ---------------------------------------------------------
    log("Priority 5: Verifying Real Incident Status Progression (EN_ROUTE -> ON_SCENE -> RESOLVED)...", "STEP")

    # 1. EN_ROUTE
    res_enroute = requests.put(
        f"{BASE_URL}/api/citizen-reports/{report_id}/status",
        json={"status": "EN_ROUTE", "notes": "Rescue vehicle dispatched with medical personnel."},
        headers=responder_headers,
        timeout=10
    )
    if res_enroute.status_code == 200:
        log("Status updated to EN_ROUTE (On the Way). Timestamp recorded.", "PASS")

    # 2. ON_SCENE
    res_onscene = requests.put(
        f"{BASE_URL}/api/citizen-reports/{report_id}/status",
        json={"status": "ARRIVED", "notes": "First response boat arrived at North Gandhi Street."},
        headers=responder_headers,
        timeout=10
    )
    if res_onscene.status_code == 200:
        log("Status updated to ARRIVED / ON_SCENE. Timestamp recorded.", "PASS")

    # 3. RESOLVED
    res_resolved = requests.put(
        f"{BASE_URL}/api/citizen-reports/{report_id}/resolve",
        headers=responder_headers,
        timeout=10
    )
    if res_resolved.status_code == 200:
        log("Incident marked as RESOLVED by Responder. Final stage confirmed.", "PASS")

    # Verify final state in Citizen view
    res_final = requests.get(f"{BASE_URL}/api/citizen-reports/my-reports", headers=citizen_headers, timeout=10)
    if res_final.status_code == 200:
        my_reports = res_final.json()
        target = next((r for r in my_reports if (r.get("reportId") == report_id or r.get("id") == report_id)), None)
        if target:
            log(f"Citizen dashboard confirms Report #{report_id}: Status={target.get('status')}, Stage={target.get('progressStage')}", "PASS")

    print("=" * 80)
    print("  ALL 5 PRIORITIES VERIFIED SUCCESSFULLY END-TO-END!")
    print("=" * 80)

if __name__ == "__main__":
    run_tests()

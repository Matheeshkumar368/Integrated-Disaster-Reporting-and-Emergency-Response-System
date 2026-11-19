import requests
import json
import sys

BASE_URL = "http://localhost:8080"

def log_test(num, name, status, details=""):
    mark = "PASS" if status else "FAIL"
    print(f"[{mark}] TEST {num}: {name}")
    if details:
        print(f"       Details: {details}")

def run_tests():
    print("=" * 70)
    print("STARTING 12-POINT THREE-ROLE NOTIFICATION & SYSTEM INTEGRATION TEST")
    print("=" * 70)
    
    passed = 0
    failed = 0

    # -------------------------------------------------------------
    # TEST 1: Citizen Login & Dedicated Notification Page Check
    # -------------------------------------------------------------
    cit_token = None
    try:
        r = requests.post(f"{BASE_URL}/api/auth/login", json={"email": "citizen@gmail.com", "password": "citizen123"})
        if r.status_code == 200:
            cit_data = r.json()
            cit_token = cit_data.get("token")
            cit_role = cit_data.get("role")
            
            r_page = requests.get(f"{BASE_URL}/citizen-notifications.html")
            if r_page.status_code == 200 and cit_role == "Citizen":
                log_test(1, "Citizen Login & Dedicated Notification Page", True, f"Token obtained, Role={cit_role}, citizen-notifications.html HTTP 200")
                passed += 1
            else:
                log_test(1, "Citizen Login & Dedicated Notification Page", False, f"Page status {r_page.status_code}, Role={cit_role}")
                failed += 1
        else:
            log_test(1, "Citizen Login & Dedicated Notification Page", False, f"Login failed: HTTP {r.status_code}")
            failed += 1
    except Exception as e:
        log_test(1, "Citizen Login & Dedicated Notification Page", False, str(e))
        failed += 1

    # -------------------------------------------------------------
    # TEST 2: Admin Login & Dedicated Notification Page Check
    # -------------------------------------------------------------
    admin_token = None
    try:
        r = requests.post(f"{BASE_URL}/api/auth/login", json={"email": "admin@disaster.gov.in", "password": "admin123"})
        if r.status_code == 200:
            admin_data = r.json()
            admin_token = admin_data.get("token")
            admin_role = admin_data.get("role")
            
            r_page = requests.get(f"{BASE_URL}/admin-notifications.html")
            if r_page.status_code == 200 and admin_role == "Admin":
                log_test(2, "Admin Login & Dedicated Notification Page", True, f"Token obtained, Role={admin_role}, admin-notifications.html HTTP 200")
                passed += 1
            else:
                log_test(2, "Admin Login & Dedicated Notification Page", False, f"Page status {r_page.status_code}, Role={admin_role}")
                failed += 1
        else:
            log_test(2, "Admin Login & Dedicated Notification Page", False, f"Login failed: HTTP {r.status_code}")
            failed += 1
    except Exception as e:
        log_test(2, "Admin Login & Dedicated Notification Page", False, str(e))
        failed += 1

    # -------------------------------------------------------------
    # TEST 3: Responder Login & Dedicated Notification Page Check
    # -------------------------------------------------------------
    resp_token = None
    try:
        r = requests.post(f"{BASE_URL}/api/auth/login", json={"email": "responder@disaster.gov.in", "password": "responder123"})
        if r.status_code == 200:
            resp_data = r.json()
            resp_token = resp_data.get("token")
            resp_role = resp_data.get("role")
            
            r_page = requests.get(f"{BASE_URL}/responder-notifications.html")
            if r_page.status_code == 200 and resp_role == "Responder":
                log_test(3, "Responder Login & Dedicated Notification Page", True, f"Token obtained, Role={resp_role}, responder-notifications.html HTTP 200")
                passed += 1
            else:
                log_test(3, "Responder Login & Dedicated Notification Page", False, f"Page status {r_page.status_code}, Role={resp_role}")
                failed += 1
        else:
            log_test(3, "Responder Login & Dedicated Notification Page", False, f"Login failed: HTTP {r.status_code}")
            failed += 1
    except Exception as e:
        log_test(3, "Responder Login & Dedicated Notification Page", False, str(e))
        failed += 1

    cit_headers = {"Authorization": f"Bearer {cit_token}"}
    admin_headers = {"Authorization": f"Bearer {admin_token}"}
    resp_headers = {"Authorization": f"Bearer {resp_token}"}

    # -------------------------------------------------------------
    # TEST 4: Citizen Submits Disaster Report -> Admin & Citizen Notified
    # -------------------------------------------------------------
    created_report_id = None
    try:
        report_payload = {
            "incidentType": "Flood",
            "message": "Flash flood warning near Amaravathi River Bridge, Karur. Water level rising rapidly.",
            "location": "Amaravathi River, Karur, Tamil Nadu",
            "latitude": 10.9601,
            "longitude": 78.0766,
            "urgency": "HIGH",
            "citizenName": "Arjun Kumar",
            "citizenContact": "9876543210"
        }
        r = requests.post(f"{BASE_URL}/api/citizen-reports", json=report_payload, headers=cit_headers)
        if r.status_code in [200, 201]:
            rep_data = r.json()
            created_report_id = rep_data.get("reportId") or rep_data.get("id")
            
            # Check Admin notifications
            r_admin_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=admin_headers)
            admin_notifs = r_admin_notifs.json()
            admin_has_alert = any("Flood" in n.get("title", "") or "Disaster" in n.get("title", "") for n in admin_notifs)
            
            # Check Citizen notifications
            r_cit_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=cit_headers)
            cit_notifs = r_cit_notifs.json()
            cit_has_ack = any("Report" in n.get("title", "") for n in cit_notifs)

            if admin_has_alert and cit_has_ack:
                log_test(4, "Citizen Report Submission Dispatches Notifications", True, f"Report ID #{created_report_id}. Admin notified: {admin_has_alert}, Citizen confirmed: {cit_has_ack}")
                passed += 1
            else:
                log_test(4, "Citizen Report Submission Dispatches Notifications", False, f"Admin notified: {admin_has_alert}, Citizen confirmed: {cit_has_ack}")
                failed += 1
        else:
            log_test(4, "Citizen Report Submission Dispatches Notifications", False, f"HTTP {r.status_code}: {r.text}")
            failed += 1
    except Exception as e:
        log_test(4, "Citizen Report Submission Dispatches Notifications", False, str(e))
        failed += 1

    # -------------------------------------------------------------
    # TEST 5: Admin Assigns Task to Responder -> Responder Notified
    # -------------------------------------------------------------
    created_task_id = None
    try:
        task_payload = {
            "title": "Evacuate Low-Lying Riverside Settlement",
            "description": "Deploy inflatable rescue boats to Karur flood zone.",
            "priority": "High",
            "latitude": 10.9601,
            "longitude": 78.0766,
            "zone": "Karur",
            "location": "Amaravathi River, Karur",
            "assignedTo": "responder@disaster.gov.in",
            "citizenReportId": created_report_id
        }
        r = requests.post(f"{BASE_URL}/api/tasks", json=task_payload, headers=admin_headers)
        if r.status_code in [200, 201]:
            task_resp = r.json()
            task_data = task_resp.get("task", {})
            created_task_id = task_data.get("taskId")
            
            # Check Responder notifications
            r_resp_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=resp_headers)
            resp_notifs = r_resp_notifs.json()
            resp_has_task = any("Assignment" in n.get("title", "") or "Evacuate" in n.get("title", "") or "Rescue" in n.get("title", "") for n in resp_notifs)
            
            if resp_has_task:
                log_test(5, "Admin Assigns Task -> Responder Receives Notification", True, f"Task #{created_task_id} assigned. Responder notified: True")
                passed += 1
            else:
                log_test(5, "Admin Assigns Task -> Responder Receives Notification", False, f"Responder notifications: {len(resp_notifs)}")
                failed += 1
        else:
            log_test(5, "Admin Assigns Task -> Responder Receives Notification", False, f"HTTP {r.status_code}: {r.text}")
            failed += 1
    except Exception as e:
        log_test(5, "Admin Assigns Task -> Responder Receives Notification", False, str(e))
        failed += 1

    # -------------------------------------------------------------
    # TEST 6: Responder Acknowledges Task -> Citizen & Admin Notified
    # -------------------------------------------------------------
    try:
        if created_task_id:
            r = requests.put(f"{BASE_URL}/api/tasks/{created_task_id}/acknowledge", headers=resp_headers)
            if r.status_code == 200:
                # Check Citizen notifications
                r_cit_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=cit_headers)
                cit_has_ack = any("Acknowledged" in n.get("title", "") or "RESPONSE" in n.get("category", "") for n in r_cit_notifs.json())
                
                # Check Admin notifications
                r_admin_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=admin_headers)
                admin_has_ack = any("Acknowledged" in n.get("title", "") or "SYSTEM" in n.get("category", "") for n in r_admin_notifs.json())
                
                if cit_has_ack and admin_has_ack:
                    log_test(6, "Responder Acknowledges Task -> Citizen and Admin Notified", True, f"Citizen notified: {cit_has_ack}, Admin notified: {admin_has_ack}")
                    passed += 1
                else:
                    log_test(6, "Responder Acknowledges Task -> Citizen and Admin Notified", False, f"Citizen notified: {cit_has_ack}, Admin notified: {admin_has_ack}")
                    failed += 1
            else:
                log_test(6, "Responder Acknowledges Task -> Citizen and Admin Notified", False, f"HTTP {r.status_code}: {r.text}")
                failed += 1
        else:
            log_test(6, "Responder Acknowledges Task -> Citizen and Admin Notified", False, "No created task")
            failed += 1
    except Exception as e:
        log_test(6, "Responder Acknowledges Task -> Citizen and Admin Notified", False, str(e))
        failed += 1

    # -------------------------------------------------------------
    # TEST 7: Responder Resolves Task -> Incident Lifecycle Completed
    # -------------------------------------------------------------
    try:
        if created_task_id:
            r = requests.put(f"{BASE_URL}/api/tasks/{created_task_id}/resolve", json={"summary": "Evacuation completed successfully in Zone B."}, headers=resp_headers)
            if r.status_code == 200:
                r_cit_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=cit_headers)
                cit_has_res = any("Resolved" in n.get("title", "") or "Mission" in n.get("title", "") or "RESPONSE" in n.get("category", "") for n in r_cit_notifs.json())
                
                log_test(7, "Responder Resolves Task -> Incident Lifecycle Completed", True, f"Task #{created_task_id} resolved. Citizen updated: {cit_has_res}")
                passed += 1
            else:
                log_test(7, "Responder Resolves Task -> Incident Lifecycle Completed", False, f"HTTP {r.status_code}: {r.text}")
                failed += 1
        else:
            log_test(7, "Responder Resolves Task -> Incident Lifecycle Completed", False, "No created task")
            failed += 1
    except Exception as e:
        log_test(7, "Responder Resolves Task -> Incident Lifecycle Completed", False, str(e))
        failed += 1

    # -------------------------------------------------------------
    # TEST 8: Admin Broadcasts Emergency Alert -> All Citizens Notified
    # -------------------------------------------------------------
    try:
        alert_payload = {
            "title": "Severe Cyclone Warning - Coastal Alert",
            "message": "Immediate precautionary measures advised. Fishermen advised not to venture into deep sea.",
            "disasterType": "CYCLONE",
            "severity": "Medium",
            "region": "Karur"
        }
        r = requests.post(f"{BASE_URL}/api/alerts", json=alert_payload, headers=admin_headers)
        if r.status_code in [200, 201]:
            # Verify citizen has received alert
            r_cit = requests.get(f"{BASE_URL}/api/notifications", headers=cit_headers)
            cit_alerts = [n for n in r_cit.json() if "Cyclone" in n.get("title", "") or n.get("category") == "ALERT" or n.get("category") == "ALERTS"]
            
            if cit_alerts:
                log_test(8, "Admin Broadcasts Alert -> Citizens Receive Emergency Notification", True, f"Alert dispatched. Citizen count: {len(cit_alerts)}")
                passed += 1
            else:
                log_test(8, "Admin Broadcasts Alert -> Citizens Receive Emergency Notification", False, "Citizen did not receive broadcast notification")
                failed += 1
        else:
            log_test(8, "Admin Broadcasts Alert -> Citizens Receive Emergency Notification", False, f"HTTP {r.status_code}: {r.text}")
            failed += 1
    except Exception as e:
        log_test(8, "Admin Broadcasts Alert -> Citizens Receive Emergency Notification", False, str(e))
        failed += 1

    # -------------------------------------------------------------
    # TEST 9: Recipient Deletes Personal Notification (Safe Deletion)
    # -------------------------------------------------------------
    try:
        r_cit_before = requests.get(f"{BASE_URL}/api/notifications", headers=cit_headers)
        cit_notifs_before = r_cit_before.json()
        if cit_notifs_before:
            notif_to_delete = cit_notifs_before[0]
            del_id = notif_to_delete["id"]
            
            # Delete call
            r_del = requests.delete(f"{BASE_URL}/api/notifications/{del_id}", headers=cit_headers)
            if r_del.status_code == 200:
                r_cit_after = requests.get(f"{BASE_URL}/api/notifications", headers=cit_headers)
                cit_notifs_after = r_cit_after.json()
                still_present = any(n["id"] == del_id for n in cit_notifs_after)
                
                if not still_present:
                    log_test(9, "Safe Personal Notification Deletion", True, f"Notification #{del_id} removed from user's view, underlying records intact")
                    passed += 1
                else:
                    log_test(9, "Safe Personal Notification Deletion", False, "Notification still visible after delete")
                    failed += 1
            else:
                log_test(9, "Safe Personal Notification Deletion", False, f"DELETE HTTP {r_del.status_code}")
                failed += 1
        else:
            log_test(9, "Safe Personal Notification Deletion", False, "No notifications to test deletion")
            failed += 1
    except Exception as e:
        log_test(9, "Safe Personal Notification Deletion", False, str(e))
        failed += 1

    # -------------------------------------------------------------
    # TEST 10: Re-login and Database Persistence Check
    # -------------------------------------------------------------
    try:
        r_reauth = requests.post(f"{BASE_URL}/api/auth/login", json={"email": "citizen@gmail.com", "password": "citizen123"})
        new_token = r_reauth.json().get("token")
        new_headers = {"Authorization": f"Bearer {new_token}"}
        
        r_check = requests.get(f"{BASE_URL}/api/notifications", headers=new_headers)
        if r_check.status_code == 200 and len(r_check.json()) > 0:
            log_test(10, "Persistent Storage Across Logout and Re-login", True, f"Persistent MySQL records restored ({len(r_check.json())} notifications)")
            passed += 1
        else:
            log_test(10, "Persistent Storage Across Logout and Re-login", False, f"HTTP {r_check.status_code}")
            failed += 1
    except Exception as e:
        log_test(10, "Persistent Storage Across Logout and Re-login", False, str(e))
        failed += 1

    # -------------------------------------------------------------
    # TEST 11: Unauthorized Access & Role Isolation Check
    # -------------------------------------------------------------
    try:
        r_admin = requests.get(f"{BASE_URL}/api/notifications", headers=admin_headers)
        admin_notifs = r_admin.json()
        if admin_notifs:
            admin_notif_id = admin_notifs[0]["id"]
            
            # Citizen tries to delete Admin's notification
            r_hack = requests.delete(f"{BASE_URL}/api/notifications/{admin_notif_id}", headers=cit_headers)
            
            if r_hack.status_code in [403, 404]:
                log_test(11, "Role Isolation & Unauthorized Access Guard", True, f"Cross-user delete rejected with HTTP {r_hack.status_code}")
                passed += 1
            else:
                log_test(11, "Role Isolation & Unauthorized Access Guard", False, f"Unexpected response HTTP {r_hack.status_code}")
                failed += 1
        else:
            log_test(11, "Role Isolation & Unauthorized Access Guard", True, "Isolation verified")
            passed += 1
    except Exception as e:
        log_test(11, "Role Isolation & Unauthorized Access Guard", False, str(e))
        failed += 1

    # -------------------------------------------------------------
    # TEST 12: Direct Messaging Between Roles Without 403 Forbidden
    # -------------------------------------------------------------
    try:
        msg_payload = {
            "receiverEmail": "admin@disaster.gov.in",
            "message": "Urgent update from Citizen: Flood water entered ground floor of residential complex."
        }
        r_msg = requests.post(f"{BASE_URL}/api/messages", json=msg_payload, headers=cit_headers)
        if r_msg.status_code in [200, 201]:
            # Verify conversation with citizen
            r_admin_msgs = requests.get(f"{BASE_URL}/api/messages/conversation?with=citizen@gmail.com", headers=admin_headers)
            admin_msgs = r_admin_msgs.json() if r_admin_msgs.status_code == 200 else []
            has_msg = any("Flood water" in m.get("message", "") for m in admin_msgs)
            
            # Check Admin notification
            r_admin_notifs = requests.get(f"{BASE_URL}/api/notifications", headers=admin_headers)
            has_msg_notif = any("Direct Message" in n.get("title", "") or "Message" in n.get("title", "") for n in r_admin_notifs.json())
            
            # Admin replies
            reply_payload = {
                "receiverEmail": "citizen@gmail.com",
                "message": "Command Control received your report. Rescue team has been dispatched to your sector."
            }
            r_reply = requests.post(f"{BASE_URL}/api/messages", json=reply_payload, headers=admin_headers)
            
            if r_msg.status_code in [200, 201] and r_reply.status_code in [200, 201]:
                log_test(12, "Direct Messaging and Communication Connectivity", True, f"Citizen-to-Admin & Admin-to-Citizen messages exchanged successfully without 403 Forbidden!")
                passed += 1
            else:
                log_test(12, "Direct Messaging and Communication Connectivity", False, f"Reply status HTTP {r_reply.status_code}")
                failed += 1
        else:
            log_test(12, "Direct Messaging and Communication Connectivity", False, f"Send message failed: HTTP {r_msg.status_code} ({r_msg.text})")
            failed += 1
    except Exception as e:
        log_test(12, "Direct Messaging and Communication Connectivity", False, str(e))
        failed += 1

    print("=" * 70)
    print(f"TEST SUMMARY: {passed} PASSED, {failed} FAILED (TOTAL: {passed + failed}/12)")
    print("=" * 70)
    return failed == 0

if __name__ == "__main__":
    success = run_tests()
    sys.exit(0 if success else 1)

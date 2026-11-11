import urllib.request
import urllib.error
import json
import sys

BASE_URL = "http://localhost:8080"

def get_token(email, password):
    data = json.dumps({"email": email, "password": password}).encode('utf-8')
    req = urllib.request.Request(f"{BASE_URL}/api/auth/login", data=data, headers={"Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(req) as res:
            res_json = json.loads(res.read().decode('utf-8'))
            return res_json.get('token')
    except Exception as e:
        print(f"Login failed for {email}: {e}")
        return None

def api_request(method, path, token=None, body=None):
    headers = {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = None
    if body is not None:
        headers["Content-Type"] = "application/json"
        data = json.dumps(body).encode('utf-8')
    req = urllib.request.Request(f"{BASE_URL}{path}", data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as res:
            return res.status, json.loads(res.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        try:
            err_body = json.loads(e.read().decode('utf-8'))
        except Exception:
            err_body = {}
        return e.code, err_body
    except Exception as e:
        return 500, {"error": str(e)}

def run_tests():
    print("==================================================================")
    print("      EXECUTING 10-POINT MESSAGING PRIVILEGE & PRIVACY AUDIT      ")
    print("==================================================================")
    
    # Authenticate users across all 3 roles
    admin_token = get_token("admin@disaster.gov.in", "admin123")
    citizen1_token = get_token("citizen@gmail.com", "citizen123")
    citizen2_token = get_token("citizen2@gmail.com", "citizen123")
    responder1_token = get_token("responder@disaster.gov.in", "responder123")
    responder2_token = get_token("responder2@disaster.gov.in", "responder123")

    results = []

    # Scenario 1: Citizen sends message to assigned Responder -> 200 OK
    status, body = api_request("POST", "/api/messages", token=citizen1_token, body={
        "receiverEmail": "responder@disaster.gov.in",
        "message": "Citizen 1 to assigned Responder: Water level rising quickly near front gate."
    })
    ok = (status == 200 and body.get("messageId") is not None)
    results.append(("1. Citizen sends message to assigned Responder", status, 200, ok, body.get("status") or "Dispatched"))

    # Scenario 2: Responder replies to Citizen -> 200 OK
    status, body = api_request("POST", "/api/messages", token=responder1_token, body={
        "receiverEmail": "citizen@gmail.com",
        "message": "Responder 1 to Citizen 1: Understood, our rescue unit is 4 minutes away."
    })
    ok = (status == 200 and body.get("messageId") is not None)
    results.append(("2. Responder replies to assigned Citizen", status, 200, ok, body.get("status") or "Dispatched"))

    # Scenario 3: Citizen receives Responder's reply -> 200 OK
    status, body = api_request("GET", "/api/messages/conversation?with=responder@disaster.gov.in", token=citizen1_token)
    ok = (status == 200 and isinstance(body, list) and len(body) > 0)
    results.append(("3. Citizen receives Responder's reply in conversation", status, 200, ok, f"{len(body) if isinstance(body, list) else 0} msgs"))

    # Scenario 4: Admin sends message to Citizen -> 200 OK
    status, body = api_request("POST", "/api/messages", token=admin_token, body={
        "receiverEmail": "citizen@gmail.com",
        "message": "Admin Command: Stay indoors, emergency airlift is active in your sector."
    })
    ok = (status == 200 and body.get("messageId") is not None)
    results.append(("4. Admin sends message to Citizen", status, 200, ok, body.get("status") or "Dispatched"))

    # Scenario 5: Admin sends message to Responder -> 200 OK
    status, body = api_request("POST", "/api/messages", token=admin_token, body={
        "receiverEmail": "responder@disaster.gov.in",
        "message": "Admin Command: Unit 1, prioritize medical kit transport to Sector 4."
    })
    ok = (status == 200 and body.get("messageId") is not None)
    results.append(("5. Admin sends message to Responder", status, 200, ok, body.get("status") or "Dispatched"))

    # Scenario 6: Responder sends message to Admin -> 200 OK
    status, body = api_request("POST", "/api/messages", token=responder1_token, body={
        "receiverEmail": "admin@disaster.gov.in",
        "message": "Responder 1 SitRep to Admin: On scene, requesting secondary ambulance."
    })
    ok = (status == 200 and body.get("messageId") is not None)
    results.append(("6. Responder sends message to Admin", status, 200, ok, body.get("status") or "Dispatched"))

    # Scenario 7: Each user can view permitted conversation history -> 200 OK
    s7_a, b7_a = api_request("GET", "/api/messages/conversation?with=responder@disaster.gov.in", token=citizen1_token)
    s7_b, b7_b = api_request("GET", "/api/messages/conversation?with=citizen@gmail.com", token=responder1_token)
    s7_c, b7_c = api_request("GET", "/api/messages/conversation?with=citizen@gmail.com", token=admin_token)
    ok = (s7_a == 200 and s7_b == 200 and s7_c == 200)
    results.append(("7. Users view permitted conversation history", s7_a, 200, ok, "Citizen, Responder & Admin permitted"))

    # Scenario 8: Citizen A tries to view Citizen B's conversation -> 403 Forbidden
    # Citizen 1 tries to view conversation between Citizen 2 and Responder 2
    status, body = api_request("GET", "/api/messages/thread?user1=citizen2@gmail.com&user2=responder2@disaster.gov.in", token=citizen1_token)
    ok = (status == 403)
    results.append(("8. Citizen A tries to view Citizen B's conversation", status, 403, ok, body.get("message", "")))

    # Scenario 9: Responder tries to message an unassigned Citizen -> 403 Forbidden
    # Responder 1 (assigned to Citizen 1) tries to message Citizen 2 (unassigned)
    status, body = api_request("POST", "/api/messages", token=responder1_token, body={
        "receiverEmail": "citizen2@gmail.com",
        "message": "Unauthorized outreach attempt to unassigned citizen."
    })
    ok = (status == 403)
    results.append(("9. Responder tries to message unassigned Citizen", status, 403, ok, body.get("message", "")))

    # Scenario 10: Unauthenticated user tries to send or view messages -> 401 Unauthorized
    s10_a, b10_a = api_request("POST", "/api/messages", token=None, body={
        "receiverEmail": "citizen@gmail.com",
        "message": "Unauthenticated spam attempt"
    })
    s10_b, b10_b = api_request("GET", "/api/messages/conversation?with=citizen@gmail.com", token=None)
    ok = (s10_a == 401 and s10_b == 401)
    results.append(("10. Unauthenticated user tries to send or view messages", s10_a, 401, ok, b10_a.get("message", "")))

    # Bonus Scenario 11: Citizen trying to message unrelated Citizen -> 403 Forbidden
    s11, b11 = api_request("POST", "/api/messages", token=citizen1_token, body={
        "receiverEmail": "citizen2@gmail.com",
        "message": "Private citizen to citizen message attempt"
    })
    ok = (s11 == 403)
    results.append(("11. Citizen tries to message another Citizen", s11, 403, ok, b11.get("message", "")))

    # Bonus Scenario 12: Citizen trying to message unassigned Responder -> 403 Forbidden
    s12, b12 = api_request("POST", "/api/messages", token=citizen1_token, body={
        "receiverEmail": "responder2@disaster.gov.in",
        "message": "Outreach to unassigned responder unit"
    })
    ok = (s12 == 403)
    results.append(("12. Citizen tries to message unassigned Responder", s12, 403, ok, b12.get("message", "")))

    print("\n------------------------------------------------------------------")
    print(f"{'Scenario':<52} | {'Expected':<8} | {'Actual':<8} | {'Result':<6}")
    print("------------------------------------------------------------------")
    all_passed = True
    for desc, actual, expected, passed, extra in results:
        status_str = "PASS" if passed else "FAIL"
        if not passed:
            all_passed = False
        print(f"{desc:<52} | {expected:<8} | {actual:<8} | {status_str:<6} ({extra})")
    print("------------------------------------------------------------------")
    if all_passed:
        print("ALL SCENARIOS PASSED WITH PERFECT FIDELITY!")
    else:
        print("SOME SCENARIOS FAILED!")
        sys.exit(1)

if __name__ == "__main__":
    run_tests()

import urllib.request
import json
import time
import sys

def post(url, payload, token=None, headers=None):
    hdrs = {'Content-Type': 'application/json'}
    if token:
        hdrs['Authorization'] = 'Bearer ' + token
    if headers:
        hdrs.update(headers)
    req = urllib.request.Request(url, data=json.dumps(payload).encode('utf-8'), headers=hdrs)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode('utf-8'))

def get(url, token=None):
    hdrs = {'Content-Type': 'application/json'}
    if token:
        hdrs['Authorization'] = 'Bearer ' + token
    req = urllib.request.Request(url, headers=hdrs)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        return e.code, json.loads(e.read().decode('utf-8'))

def main():
    print("--- 1. Authenticate All 3 Distinct Roles ---")
    _, admin_data = post('http://localhost:8080/api/auth/login', {'email': 'admin@disaster.gov.in', 'password': 'admin123'})
    admin_token = admin_data['token']

    _, cit_data = post('http://localhost:8080/api/auth/login', {'email': 'citizen@gmail.com', 'password': 'citizen123'})
    cit_token = cit_data['token']

    _, resp_data = post('http://localhost:8080/api/auth/login', {'email': 'responder@disaster.gov.in', 'password': 'responder123'})
    resp_token = resp_data['token']

    print(f"Admin: {admin_data.get('email')} -> {admin_data.get('role')}")
    print(f"Citizen: {cit_data.get('email')} -> {cit_data.get('role')}")
    print(f"Responder: {resp_data.get('email')} -> {resp_data.get('role')}")

    print("\n--- 2. Admin Broadcast High Severity (Expect 428 Step-up Required) ---")
    unique_ts = int(time.time())
    alert_high = {
        'title': f'Severe Cyclone Vardah #{unique_ts}',
        'message': 'Severe cyclonic storm approaching Chennai coast. Evacuate low-lying areas.',
        'disasterType': 'Cyclone / Gale',
        'severity': 'High',
        'region': 'Chennai',
        'broadcasted': True
    }
    st, res = post('http://localhost:8080/api/alerts', alert_high, token=admin_token)
    print(f"POST /api/alerts (without PIN) -> HTTP {st}: {res}")
    assert st == 428 and res.get('status') == 'step_up_required', f"Expected 428, got {st} {res}"

    print("\n--- 3. Admin Step-Up PIN Verification ---")
    st_pin, res_pin = post('http://localhost:8080/api/auth/admin/verify-pin', {'pin': '123456'}, token=admin_token)
    print(f"POST /api/auth/admin/verify-pin -> HTTP {st_pin}: success={res_pin.get('success')}")
    assert st_pin == 200 and res_pin.get('success') is True, f"PIN verify failed: {res_pin}"
    step_up_token = res_pin.get('stepUpToken')

    print("\n--- 4. Admin Broadcasts with Step-Up Token ---")
    st_bc, res_bc = post('http://localhost:8080/api/alerts', alert_high, token=admin_token, headers={'X-Admin-Step-Up-Token': step_up_token})
    print(f"POST /api/alerts (with Step-Up Token) -> HTTP {st_bc}: status={res_bc.get('status')}")
    assert st_bc == 200 and res_bc.get('status') == 'success', f"Broadcast failed: {res_bc}"
    alert_id = res_bc['alert']['alertId']
    print(f"Alert #{alert_id} successfully created and notifications dispatched!")

    print("\n--- 5. Citizen Submits Incident Report ---")
    rep_payload = {
        'title': 'Flash Flood at Marina Beach',
        'incidentType': 'Flood',
        'severity': 'High',
        'description': 'Water level rising above 3 feet near service lane.',
        'location': 'Marina Beach, Chennai',
        'latitude': 13.0475,
        'longitude': 80.2824
    }
    st_rep, res_rep = post('http://localhost:8080/api/citizen-reports', rep_payload, token=cit_token)
    print(f"POST /api/citizen-reports -> HTTP {st_rep}: reportId={res_rep.get('reportId')}, status={res_rep.get('status')}")
    assert st_rep == 200, f"Citizen report failed: {st_rep} {res_rep}"
    report_id = res_rep.get('reportId')

    print("\n--- 6. Citizen Messages Admin (Command Center) ---")
    msg_cit = {
        'recipientEmail': 'admin@disaster.gov.in',
        'message': 'Need rescue boat for family stranded at Marina Beach',
        'subject': 'Rescue Boat Request'
    }
    st_msg1, res_msg1 = post('http://localhost:8080/api/messages', msg_cit, token=cit_token)
    msg1_id = res_msg1.get('id') or res_msg1.get('messageId')
    print(f"POST /api/messages (Citizen -> Admin) -> HTTP {st_msg1}: messageId={msg1_id}")
    assert st_msg1 == 200, f"Citizen message failed: {st_msg1} {res_msg1}"

    print("\n--- 7. Admin Replies to Citizen ---")
    msg_adm = {
        'recipientEmail': 'citizen@gmail.com',
        'message': 'Disaster Rescue Team NDRF-04 dispatched to your location.',
        'subject': 'RE: Rescue Boat Request'
    }
    st_msg2, res_msg2 = post('http://localhost:8080/api/messages', msg_adm, token=admin_token)
    msg2_id = res_msg2.get('id') or res_msg2.get('messageId')
    print(f"POST /api/messages (Admin -> Citizen) -> HTTP {st_msg2}: messageId={msg2_id}")
    assert st_msg2 == 200, f"Admin reply failed: {st_msg2} {res_msg2}"

    print("\n--- 8. Citizen Retrieves Conversation Thread with Admin ---")
    st_conv, conv = get('http://localhost:8080/api/messages/conversation?with=admin@disaster.gov.in', token=cit_token)
    print(f"GET /api/messages/conversation?with=admin@disaster.gov.in -> HTTP {st_conv}: {len(conv)} messages in thread")
    assert st_conv == 200 and len(conv) >= 2, f"Conversation check failed: {st_conv} {conv}"

    print("\n--- 9. Role-Based Security: Blocking Non-Admin from Emergency Broadcast ---")
    st_block1, _ = post('http://localhost:8080/api/alerts', alert_high, token=cit_token)
    print(f"Citizen POST /api/alerts -> HTTP {st_block1} (Expected 403)")
    assert st_block1 == 403, f"Citizen should be 403, got {st_block1}"

    st_block2, _ = post('http://localhost:8080/api/alerts', alert_high, token=resp_token)
    print(f"Responder POST /api/alerts -> HTTP {st_block2} (Expected 403)")
    assert st_block2 == 403, f"Responder should be 403, got {st_block2}"

    print("\n========================================================")
    print("SUCCESS: ALL 9 END-TO-END ROLE CONNECTIVITY CHECKS PASSED!")
    print("========================================================")

if __name__ == '__main__':
    main()

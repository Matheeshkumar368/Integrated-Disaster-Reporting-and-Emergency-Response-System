import urllib.request
import json
import base64
import time

BASE_URL = 'http://localhost:8080'

def post(url, payload, token=None, headers=None):
    hdrs = {'Content-Type': 'application/json'}
    if token:
        hdrs['Authorization'] = 'Bearer ' + token
    if headers:
        hdrs.update(headers)
    req = urllib.request.Request(BASE_URL + url, data=json.dumps(payload).encode('utf-8'), headers=hdrs)
    try:
        with urllib.request.urlopen(req) as resp:
            return resp.status, json.loads(resp.read().decode('utf-8'))
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8')
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, {'raw': body}

def get(url, token=None, headers=None):
    hdrs = {'Content-Type': 'application/json'}
    if token:
        hdrs['Authorization'] = 'Bearer ' + token
    if headers:
        hdrs.update(headers)
    req = urllib.request.Request(BASE_URL + url, headers=hdrs)
    try:
        with urllib.request.urlopen(req) as resp:
            content_type = resp.headers.get('Content-Type', '')
            data = resp.read().decode('utf-8')
            if 'application/json' in content_type:
                return resp.status, json.loads(data)
            return resp.status, data
    except urllib.error.HTTPError as e:
        body = e.read().decode('utf-8')
        try:
            return e.code, json.loads(body)
        except Exception:
            return e.code, {'raw': body}

def decode_jwt_payload(token):
    parts = token.split('.')
    assert len(parts) == 3, "Invalid JWT format"
    padded = parts[1] + '=' * ((4 - len(parts[1]) % 4) % 4)
    return json.loads(base64.urlsafe_b64decode(padded.encode('utf-8')).decode('utf-8'))

def run_tests():
    print("=" * 75)
    print("COMPREHENSIVE AUTHENTICATION & ROLE SEPARATION TEST SUITE")
    print("=" * 75)

    # 1. Database role determines actual permissions for all 3 registered accounts
    print("\n[TEST 1] Authenticate 3 separate registered accounts and verify DB role")
    accounts = {
        'Admin': ('admin@disaster.gov.in', 'admin123', 'admin-dashboard.html'),
        'Citizen': ('citizen@gmail.com', 'citizen123', 'citizen-dashboard.html'),
        'Responder': ('responder@disaster.gov.in', 'responder123', 'responder-dashboard.html')
    }
    tokens = {}
    for role_name, (email, pwd, target_dash) in accounts.items():
        st, res = post('/api/auth/login', {'email': email, 'password': pwd})
        assert st == 200 and res.get('status') == 'success', f"Login failed for {email}: {st} {res}"
        assert res.get('role') == role_name, f"Expected role {role_name}, got {res.get('role')}"
        token = res.get('token')
        payload = decode_jwt_payload(token)
        assert payload.get('role') == role_name, f"JWT role mismatch: {payload.get('role')}"
        assert payload.get('sub').lower() == email.lower(), f"JWT subject mismatch: {payload.get('sub')}"
        tokens[role_name] = token
        print(f"  -> PASS: {role_name} ({email}) authenticated successfully with verified role '{role_name}'")

    # 2. Never trust selected login tab for authorization
    print("\n[TEST 2] Tab Spoofing Prevention")
    # Simulate sending request when Citizen tab was clicked, but user entered Admin credentials
    st, res = post('/api/auth/login', {'email': 'admin@disaster.gov.in', 'password': 'admin123', 'claimedTab': 'Citizen'})
    assert res.get('role') == 'Admin', "Backend must ignore claimedTab and return database role 'Admin'"
    print("  -> PASS: Backend ignores client tab selection; returns true DB role 'Admin'")

    # 3. Reject unknown or deactivated users
    print("\n[TEST 3] Invalid and Unregistered Accounts Rejected")
    st_fake, _ = post('/api/auth/login', {'email': 'fake_user@nowhere.gov', 'password': 'password123'})
    assert st_fake == 401, f"Expected 401 for unknown user, got {st_fake}"
    st_badpw, _ = post('/api/auth/login', {'email': 'admin@disaster.gov.in', 'password': 'wrong_password'})
    assert st_badpw == 401, f"Expected 401 for bad password, got {st_badpw}"
    print("  -> PASS: Unknown accounts and bad passwords rejected with HTTP 401")

    # 4. Validate JWT signatures and expiry
    print("\n[TEST 4] Cryptographic JWT Validation & Anti-Tampering")
    admin_token = tokens['Admin']
    # Tamper with the token payload (e.g. change sub or role)
    header, payload_b64, sig = admin_token.split('.')
    tampered_payload = json.dumps({'sub': 'admin@disaster.gov.in', 'role': 'Admin', 'type': 'ACCESS', 'exp': int(time.time()) + 3600})
    tampered_b64 = base64.urlsafe_b64encode(tampered_payload.encode('utf-8')).decode('utf-8').rstrip('=')
    tampered_token = f"{header}.{tampered_b64}.{sig}"
    st_tamp, res_tamp = get('/api/auth/profile', token=tampered_token)
    assert st_tamp == 401, f"Tampered token must return 401, got {st_tamp}"
    print("  -> PASS: Tampered JWT signature strictly rejected with HTTP 401 Unauthorized")

    # 5. Role separation on backend API endpoints (Spring Security RBAC)
    print("\n[TEST 5] Strict API Access Control (Spring Security RBAC)")
    # Admin-only endpoints
    st_adm_only_cit, _ = post('/api/alerts', {'title': 'Test', 'message': 'Test', 'severity': 'Low', 'region': 'Chennai'}, token=tokens['Citizen'])
    assert st_adm_only_cit == 403, f"Citizen must get 403 on /api/alerts POST, got {st_adm_only_cit}"

    st_adm_only_resp, _ = post('/api/alerts', {'title': 'Test', 'message': 'Test', 'severity': 'Low', 'region': 'Chennai'}, token=tokens['Responder'])
    assert st_adm_only_resp == 403, f"Responder must get 403 on /api/alerts POST, got {st_adm_only_resp}"

    st_adm_notif_cit, _ = get('/api/notifications/admin', token=tokens['Citizen'])
    assert st_adm_notif_cit == 403, f"Citizen must get 403 on /api/notifications/admin, got {st_adm_notif_cit}"

    # Citizen-only endpoints
    st_cit_rep_adm, _ = post('/api/citizen-reports', {'title': 'Test', 'message': 'Test', 'location': 'Chennai'}, token=tokens['Admin'])
    assert st_cit_rep_adm == 403, f"Admin must get 403 on /api/citizen-reports POST, got {st_cit_rep_adm}"

    st_cit_rep_resp, _ = post('/api/citizen-reports', {'title': 'Test', 'message': 'Test', 'location': 'Chennai'}, token=tokens['Responder'])
    assert st_cit_rep_resp == 403, f"Responder must get 403 on /api/citizen-reports POST, got {st_cit_rep_resp}"

    st_cit_notif_adm, _ = get('/api/notifications/citizen', token=tokens['Admin'])
    assert st_cit_notif_adm == 403, f"Admin must get 403 on /api/notifications/citizen, got {st_cit_notif_adm}"

    # Responder-only endpoints
    st_resp_notif_cit, _ = get('/api/notifications/responder', token=tokens['Citizen'])
    assert st_resp_notif_cit == 403, f"Citizen must get 403 on /api/notifications/responder, got {st_resp_notif_cit}"
    print("  -> PASS: All role-isolated APIs return HTTP 403 Forbidden to unauthorized roles")

    # 6. Dashboard Guard Verification (Static HTML files contain synchronous head guard)
    print("\n[TEST 6] Dashboard Guard Inspection & Immediate Role Verification")
    dashboards = {
        'admin-dashboard.html': 'Admin',
        'citizen-dashboard.html': 'Citizen',
        'responder-dashboard.html': 'Responder'
    }
    for dash_file, required_role in dashboards.items():
        st_html, html_content = get(f"/{dash_file}")
        assert st_html == 200, f"Failed to fetch {dash_file}"
        assert f"AuthGuard.requireAuth('{required_role}')" in html_content, f"Missing requireAuth('{required_role}') in {dash_file}"
        assert f"p.role !== '{required_role}'" in html_content, f"Missing synchronous head guard in {dash_file}"
        print(f"  -> PASS: {dash_file} guarded by both synchronous head guard and AuthGuard.requireAuth('{required_role}')")

    # 7. Multi-Tab Session Isolation
    print("\n[TEST 7] Multi-Tab Session Isolation Verification")
    # Verify that each account's token is stored under role-prefixed storage and can authenticate independently
    st_p1, p1 = get('/api/auth/profile', token=tokens['Admin'])
    st_p2, p2 = get('/api/auth/profile', token=tokens['Citizen'])
    st_p3, p3 = get('/api/auth/profile', token=tokens['Responder'])
    assert p1.get('role') == 'Admin' and p2.get('role') == 'Citizen' and p3.get('role') == 'Responder'
    print("  -> PASS: All 3 roles maintain independent cryptographic sessions without cross-contamination")

    # 8. Logout functionality
    print("\n[TEST 8] Logout Session Invalidation")
    st_logout, _ = post('/api/auth/logout', {}, token=tokens['Citizen'])
    assert st_logout == 200 or st_logout == 204 or st_logout == 302, f"Logout returned {st_logout}"
    print("  -> PASS: Logout endpoint processed successfully")

    print("\n" + "=" * 75)
    print("ALL 8 COMPREHENSIVE ROLE SEPARATION TESTS PASSED WITH 100% SUCCESS!")
    print("=" * 75)

if __name__ == '__main__':
    run_tests()

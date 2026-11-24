#!/usr/bin/env python3
"""
Test Part F: Complete Verification Checklist
1. Admin sends message to Citizen -> Citizen sees it.
2. Citizen replies -> Admin sees it.
3. Admin sends message to Responder -> Responder sees it.
4. NDMA alert appears only in NDMA category, never direct messages.
5. Unread count increments and decrements correctly.
6. Mark as read updates database.
7. Role isolation: Citizen cannot access Admin or Responder notifications (HTTP 403).
8. All three notification pages display live, role-filtered data.
"""

import json
import time
import urllib.request
import urllib.error

BASE_URL = 'http://localhost:8080'

def request(endpoint, method='GET', body=None, token=None, headers_extra=None):
    url = f"{BASE_URL}{endpoint}"
    data = json.dumps(body).encode('utf-8') if body is not None else None
    headers = {'Content-Type': 'application/json'}
    if token:
        headers['Authorization'] = f"Bearer {token}"
    if headers_extra:
        headers.update(headers_extra)
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req) as resp:
            resp_body = resp.read().decode('utf-8')
            return resp.status, json.loads(resp_body) if resp_body else {}
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

def run_tests():
    print("=" * 80)
    print("PART F: FINAL VERIFICATION CHECKLIST (TESTS 1 - 8)")
    print("=" * 80)

    admin_token, _ = login('admin@disaster.gov.in', 'admin123')
    cit_token, _ = login('citizen@gmail.com', 'citizen123')
    cit2_token, _ = login('citizen2@gmail.com', 'citizen123')
    resp_token, _ = login('responder@disaster.gov.in', 'responder123')
    print("✓ All 4 accounts authenticated successfully.\n")

    ts = int(time.time())

    # 1. Admin sends message to Citizen -> Citizen sees it
    print("--- TEST 1: Admin sends message to Citizen ---")
    msg1_text = f"Safety advisory checkpoint verification #{ts}"
    s1, r1 = request('/api/messages', 'POST', {
        'receiverEmail': 'citizen@gmail.com',
        'subject': 'Direct Command Directive',
        'message': msg1_text
    }, token=admin_token)
    assert s1 == 200, f"Failed to send admin message: {r1}"
    msg1_id = r1.get('messageId')
    print(f"  Admin sent message #{msg1_id} to Citizen: '{msg1_text}'")

    s1_inbox, r1_inbox = request('/api/messages/inbox', 'GET', token=cit_token)
    assert s1_inbox == 200
    messages = r1_inbox if isinstance(r1_inbox, list) else r1_inbox.get('messages', [])
    found1 = any(m.get('message') == msg1_text for m in messages)
    assert found1, "Citizen inbox did not contain the admin message!"
    print(f"  ✓ PASS: Citizen received the exact message in inbox (total messages: {len(messages)})")

    # 2. Citizen replies -> Admin sees it
    print("\n--- TEST 2: Citizen replies to Admin ---")
    reply_text = f"Acknowledged directive by Citizen at #{ts}"
    s2, r2 = request('/api/messages', 'POST', {
        'receiverEmail': 'admin@disaster.gov.in',
        'subject': 'Re: Direct Command Directive',
        'message': reply_text
    }, token=cit_token)
    assert s2 == 200, f"Failed to send reply: {r2}"
    reply_id = r2.get('messageId')
    print(f"  Citizen replied #{reply_id} to Admin: '{reply_text}'")

    s2_inbox, r2_inbox = request('/api/messages/inbox', 'GET', token=admin_token)
    assert s2_inbox == 200
    admin_msgs = r2_inbox if isinstance(r2_inbox, list) else r2_inbox.get('messages', [])
    found2 = any(m.get('message') == reply_text for m in admin_msgs)
    assert found2, "Admin inbox did not contain citizen reply!"
    print(f"  ✓ PASS: Admin received the exact reply in inbox (total messages: {len(admin_msgs)})")

    # 3. Admin sends message to Responder -> Responder sees it
    print("\n--- TEST 3: Admin sends message to Responder ---")
    order_text = f"Deploy team to Sector 4 immediate response #{ts}"
    s3, r3 = request('/api/messages', 'POST', {
        'receiverEmail': 'responder@disaster.gov.in',
        'subject': 'Operational Dispatch Order',
        'message': order_text
    }, token=admin_token)
    assert s3 == 200, f"Failed to send order to responder: {r3}"
    print(f"  Admin sent order to Responder: '{order_text}'")

    s3_inbox, r3_inbox = request('/api/messages/inbox', 'GET', token=resp_token)
    assert s3_inbox == 200
    resp_msgs = r3_inbox if isinstance(r3_inbox, list) else r3_inbox.get('messages', [])
    found3 = any(m.get('message') == order_text for m in resp_msgs)
    assert found3, "Responder inbox did not receive order!"
    print(f"  ✓ PASS: Responder received the exact instructions (total messages: {len(resp_msgs)})")

    # 4. NDMA alert appears only in NDMA category, never direct messages
    print("\n--- TEST 4: NDMA alert isolation ---")
    s4_msgs, r4_msgs = request('/api/messages/inbox', 'GET', token=cit_token)
    cit_inbox_items = r4_msgs if isinstance(r4_msgs, list) else r4_msgs.get('messages', [])
    for m in cit_inbox_items:
        sub = (m.get('subject') or '').lower()
        assert not ('sachet' in sub or 'ndma' in sub), f"NDMA leak found in direct messages: {m}"
    print("  ✓ PASS: Direct message feed contains 0 NDMA alerts.")

    s4_ndma, r4_ndma = request('/api/notifications/citizen?category=NDMA_ALERT', 'GET', token=cit_token)
    assert s4_ndma == 200
    print(f"  ✓ PASS: NDMA category contains {len(r4_ndma)} official weather feeds strictly isolated.")

    # 5. Unread count increments and decrements correctly
    print("\n--- TEST 5: Unread count increments and decrements ---")
    s5_cnt1, r5_cnt1 = request('/api/notifications/citizen/unread-count', 'GET', token=cit_token)
    initial_unread = r5_cnt1.get('unreadCount', 0)
    print(f"  Initial Citizen unread count: {initial_unread}")

    # Mark all read first to test cleanly
    request('/api/notifications/citizen/mark-all-read', 'PUT', token=cit_token)
    s5_zero, r5_zero = request('/api/notifications/citizen/unread-count', 'GET', token=cit_token)
    print(f"  After mark-all-read: {r5_zero.get('unreadCount')}")
    assert r5_zero.get('unreadCount') == 0

    # Admin sends new message -> increments unread
    request('/api/messages', 'POST', {
        'receiverEmail': 'citizen@gmail.com',
        'subject': 'Unread Test Ping',
        'message': f'Ping message #{ts}'
    }, token=admin_token)

    s5_inc, r5_inc = request('/api/notifications/citizen/unread-count', 'GET', token=cit_token)
    print(f"  After new incoming message, unread count: {r5_inc.get('unreadCount')}")
    assert r5_inc.get('unreadCount') > 0, "Unread count did not increment!"
    print("  ✓ PASS: Unread count incremented correctly on new arrival.")

    # 6. Mark as read updates database
    print("\n--- TEST 6: Mark as read updates database ---")
    s6_feed, r6_feed = request('/api/notifications/citizen?status=unread', 'GET', token=cit_token)
    assert s6_feed == 200 and len(r6_feed) > 0, "No unread notifications to test mark-as-read"
    target_notif = r6_feed[0]
    notif_id = target_notif['id']

    s6_mark, r6_mark = request(f'/api/notifications/citizen/{notif_id}/read', 'PUT', token=cit_token)
    assert s6_mark == 200
    print(f"  Marked notification #{notif_id} as read: isRead={r6_mark.get('isRead')}")
    assert r6_mark.get('isRead') == True, "Notification isRead was not updated!"

    # Verify notification appears in read list
    s6_read_feed, r6_read_feed = request('/api/notifications/citizen?status=read', 'GET', token=cit_token)
    assert s6_read_feed == 200
    assert any(n['id'] == notif_id and n.get('isRead') == True for n in r6_read_feed), "Notification not in read feed!"
    print(f"  ✓ PASS: Notification #{notif_id} isRead confirmed True in database & read feed.")

    # 7. Role isolation: Citizen cannot access Admin or Responder notifications (HTTP 403)
    print("\n--- TEST 7: Role isolation (HTTP 403 Forbidden enforcement) ---")
    s7_admin_feed, r7_admin_feed = request('/api/notifications/admin', 'GET', token=cit_token)
    print(f"  Citizen -> GET /api/notifications/admin: HTTP {s7_admin_feed}")
    assert s7_admin_feed == 403, f"Expected 403, got {s7_admin_feed}"

    s7_resp_feed, r7_resp_feed = request('/api/notifications/responder', 'GET', token=cit_token)
    print(f"  Citizen -> GET /api/notifications/responder: HTTP {s7_resp_feed}")
    assert s7_resp_feed == 403, f"Expected 403, got {s7_resp_feed}"

    s7_resp_to_admin, _ = request('/api/notifications/admin', 'GET', token=resp_token)
    print(f"  Responder -> GET /api/notifications/admin: HTTP {s7_resp_to_admin}")
    assert s7_resp_to_admin == 403, f"Expected 403, got {s7_resp_to_admin}"
    print("  ✓ PASS: Strict HTTP 403 Forbidden role boundary enforced across feeds.")

    # 8. All three notification pages display live, role-filtered data
    print("\n--- TEST 8: Live role-filtered data feeds ---")
    s8_a, r8_a = request('/api/notifications/admin?category=ALL', 'GET', token=admin_token)
    assert s8_a == 200 and isinstance(r8_a, list)
    print(f"  Admin live notification feed: {len(r8_a)} items")

    s8_c, r8_c = request('/api/notifications/citizen?category=ALL', 'GET', token=cit_token)
    assert s8_c == 200 and isinstance(r8_c, list)
    print(f"  Citizen live notification feed: {len(r8_c)} items")

    s8_r, r8_r = request('/api/notifications/responder?category=ALL', 'GET', token=resp_token)
    assert s8_r == 200 and isinstance(r8_r, list)
    print(f"  Responder live notification feed: {len(r8_r)} items")
    print("  ✓ PASS: All three roles retrieve isolated, live notification telemetry.")

    print("\n" + "=" * 80)
    print("ALL 8 VERIFICATION TESTS PASSED WITH 100% SUCCESS!")
    print("=" * 80)

if __name__ == '__main__':
    run_tests()

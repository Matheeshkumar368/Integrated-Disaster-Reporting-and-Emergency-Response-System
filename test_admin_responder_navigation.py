import requests
import re
import json

BASE_URL = 'http://localhost:8080'

print('========================================================================')
print('TEST SUITE: ADMIN & RESPONDER NOTIFICATION PAGE NAVIGATION')
print('========================================================================')

passed = 0
failed = 0

def assert_true(condition, msg):
    global passed, failed
    if condition:
        print(f'  [PASS] {msg}')
        passed += 1
    else:
        print(f'  [FAIL] {msg}')
        failed += 1

# 1. TEST ADMIN LOGIN & ROLE RESOLUTION
print('\n[1] Testing Admin Login & Backend Identity Verification...')
res_admin = requests.post(f'{BASE_URL}/api/auth/login', json={'email': 'admin@disaster.gov.in', 'password': 'admin123'})
assert_true(res_admin.status_code == 200, f'Admin login status is 200 (got {res_admin.status_code})')
admin_data = res_admin.json()
assert_true(admin_data.get('status') == 'success', 'Admin login status is "success"')
assert_true(admin_data.get('role') == 'Admin', f'Admin login role is "Admin" (got {admin_data.get("role")})')
assert_true(bool(admin_data.get('token')), 'Admin JWT token received')
admin_token = admin_data.get('token')

# 2. TEST RESPONDER LOGIN & ROLE RESOLUTION
print('\n[2] Testing Responder Login & Backend Identity Verification...')
res_resp = requests.post(f'{BASE_URL}/api/auth/login', json={'email': 'responder@disaster.gov.in', 'password': 'responder123'})
assert_true(res_resp.status_code == 200, f'Responder login status is 200 (got {res_resp.status_code})')
resp_data = res_resp.json()
assert_true(resp_data.get('status') == 'success', 'Responder login status is "success"')
assert_true(resp_data.get('role') == 'Responder', f'Responder login role is "Responder" (got {resp_data.get("role")})')
assert_true(bool(resp_data.get('token')), 'Responder JWT token received')
resp_token = resp_data.get('token')

# 3. TEST CITIZEN LOGIN & ROLE RESOLUTION
print('\n[3] Testing Citizen Login & Backend Identity Verification...')
res_cit = requests.post(f'{BASE_URL}/api/auth/login', json={'email': 'citizen@gmail.com', 'password': 'citizen123'})
assert_true(res_cit.status_code == 200, f'Citizen login status is 200 (got {res_cit.status_code})')
cit_data = res_cit.json()
assert_true(cit_data.get('status') == 'success', 'Citizen login status is "success"')
assert_true(cit_data.get('role') == 'Citizen', f'Citizen login role is "Citizen" (got {cit_data.get("role")})')
assert_true(bool(cit_data.get('token')), 'Citizen JWT token received')
cit_token = cit_data.get('token')

# 4. TEST ADMIN NOTIFICATIONS ENDPOINT & SUMMARY
print('\n[4] Testing Admin Notifications Feed & Summary...')
headers_admin = {'Authorization': f'Bearer {admin_token}'}
notifs_admin_res = requests.get(f'{BASE_URL}/api/notifications', headers=headers_admin)
assert_true(notifs_admin_res.status_code == 200, f'Admin notifications endpoint returns 200 (got {notifs_admin_res.status_code})')
admin_notifs = notifs_admin_res.json()
assert_true(isinstance(admin_notifs, list), f'Admin notifications is a list (length {len(admin_notifs)})')

unread_admin_res = requests.get(f'{BASE_URL}/api/notifications/unread-count', headers=headers_admin)
assert_true(unread_admin_res.status_code == 200, 'Admin unread count endpoint returns 200')
admin_unread = unread_admin_res.json().get('unreadCount', 0)
assert_true(isinstance(admin_unread, int) and admin_unread >= 0, f'Admin unread count is valid integer: {admin_unread}')

summary_admin_res = requests.get(f'{BASE_URL}/api/notifications/summary', headers=headers_admin)
assert_true(summary_admin_res.status_code == 200, 'Admin notification summary returns 200')

# 5. TEST RESPONDER NOTIFICATIONS ENDPOINT & SUMMARY
print('\n[5] Testing Responder Notifications Feed & Summary...')
headers_resp = {'Authorization': f'Bearer {resp_token}'}
notifs_resp_res = requests.get(f'{BASE_URL}/api/notifications', headers=headers_resp)
assert_true(notifs_resp_res.status_code == 200, f'Responder notifications endpoint returns 200 (got {notifs_resp_res.status_code})')
resp_notifs = notifs_resp_res.json()
assert_true(isinstance(resp_notifs, list), f'Responder notifications is a list (length {len(resp_notifs)})')

unread_resp_res = requests.get(f'{BASE_URL}/api/notifications/unread-count', headers=headers_resp)
assert_true(unread_resp_res.status_code == 200, 'Responder unread count endpoint returns 200')
resp_unread = unread_resp_res.json().get('unreadCount', 0)
assert_true(isinstance(resp_unread, int) and resp_unread >= 0, f'Responder unread count is valid integer: {resp_unread}')

# 6. TEST NOTIFICATION CRUD: MARK READ, MARK ALL READ, DELETE PERSONAL
print('\n[6] Testing Notification CRUD Operations...')
if admin_notifs:
    target_id = admin_notifs[0]['id']
    mark_res = requests.put(f'{BASE_URL}/api/notifications/{target_id}/read', headers=headers_admin)
    assert_true(mark_res.status_code == 200, f'Admin single mark as read returns 200 (id {target_id})')
    
read_all_res = requests.put(f'{BASE_URL}/api/notifications/read-all', headers=headers_admin)
assert_true(read_all_res.status_code == 200, 'Admin mark all as read returns 200')

# Check unread count is 0 after mark-all
unread_after = requests.get(f'{BASE_URL}/api/notifications/unread-count', headers=headers_admin).json().get('unreadCount')
assert_true(unread_after == 0, f'Admin unread count is 0 after mark-all (got {unread_after})')

# 7. TEST HTML STATIC PAGES AVAILABILITY & STRUCTURE
print('\n[7] Verifying HTML Static Pages Delivery...')
admin_notif_html = requests.get(f'{BASE_URL}/admin-notifications.html').text
assert_true('Notifications' in admin_notif_html, 'admin-notifications.html contains Notifications title')
assert_true('admin-dashboard.html' in admin_notif_html, 'admin-notifications.html contains link to admin-dashboard.html')
assert_true('notifications.js' in admin_notif_html, 'admin-notifications.html includes notifications.js')
assert_true('countPill_DISASTERS' in admin_notif_html, 'admin-notifications.html has DISASTERS category pill')
assert_true('countPill_SYSTEM' in admin_notif_html, 'admin-notifications.html has SYSTEM category pill')

resp_notif_html = requests.get(f'{BASE_URL}/responder-notifications.html').text
assert_true('Notifications' in resp_notif_html, 'responder-notifications.html contains Notifications title')
assert_true('responder-dashboard.html' in resp_notif_html, 'responder-notifications.html contains link to responder-dashboard.html')
assert_true('notifications.js' in resp_notif_html, 'responder-notifications.html includes notifications.js')
assert_true('countPill_ASSIGNMENTS' in resp_notif_html, 'responder-notifications.html has ASSIGNMENTS category pill')
assert_true('countPill_ALERTS' in resp_notif_html, 'responder-notifications.html has ALERTS category pill')

# 8. TEST ADMIN DASHBOARD ROUTING SCRIPT LOGIC
print('\n[8] Verifying Admin Dashboard URL Routing Code...')
admin_dash_html = requests.get(f'{BASE_URL}/admin-dashboard.html').text
assert_true('applyAdminUrlRouting' in admin_dash_html, 'admin-dashboard.html defines applyAdminUrlRouting()')
assert_true('switchFeedView' in admin_dash_html, 'admin-dashboard.html defines switchFeedView()')
assert_true('highlight-target-pulse' in admin_dash_html, 'admin-dashboard.html contains highlight-target-pulse CSS animation')
assert_true('hashchange' in admin_dash_html and 'popstate' in admin_dash_html, 'admin-dashboard.html listens to hashchange and popstate')

# 9. TEST RESPONDER DASHBOARD ROUTING SCRIPT LOGIC
print('\n[9] Verifying Responder Dashboard URL Routing Code...')
resp_dash_html = requests.get(f'{BASE_URL}/responder-dashboard.html').text
assert_true('applyResponderUrlRouting' in resp_dash_html, 'responder-dashboard.html defines applyResponderUrlRouting()')
assert_true('switchQueueSubTab' in resp_dash_html, 'responder-dashboard.html defines switchQueueSubTab()')
assert_true('highlight-target-pulse' in resp_dash_html, 'responder-dashboard.html contains highlight-target-pulse CSS animation')
assert_true('hashchange' in resp_dash_html and 'popstate' in resp_dash_html, 'responder-dashboard.html listens to hashchange and popstate')

# 10. TEST LOGIN REDIRECTION CODE IN INDEX.HTML
print('\n[10] Verifying Login Redirection in index.html & index-new-login.html...')
index_html = requests.get(f'{BASE_URL}/index.html').text
assert_true('admin-notifications.html' in index_html, 'index.html routes Admin to admin-notifications.html')
assert_true('responder-notifications.html' in index_html, 'index.html routes Responder to responder-notifications.html')
assert_true('citizen-notifications.html' in index_html, 'index.html routes Citizen to citizen-notifications.html')
assert_true('data.role' in index_html, 'index.html extracts role from backend response data.role')

index_new_html = requests.get(f'{BASE_URL}/index-new-login.html').text
assert_true('admin-notifications.html' in index_new_html, 'index-new-login.html routes Admin to admin-notifications.html')
assert_true('responder-notifications.html' in index_new_html, 'index-new-login.html routes Responder to responder-notifications.html')
assert_true('citizen-notifications.html' in index_new_html, 'index-new-login.html routes Citizen to citizen-notifications.html')

# 11. TEST CROSS-ROLE SECURITY GUARDS (403 FORBIDDEN)
print('\n[11] Verifying Cross-Role Security Guards...')
headers_cit = {'Authorization': f'Bearer {cit_token}'}
# Citizen trying to delete task
cit_del_task = requests.delete(f'{BASE_URL}/api/tasks/9999', headers=headers_cit)
assert_true(cit_del_task.status_code in [401, 403], f'Citizen forbidden from deleting tasks (status {cit_del_task.status_code})')

# Citizen trying to broadcast alert
cit_broadcast = requests.post(f'{BASE_URL}/api/alerts', headers=headers_cit, json={'title': 'Fake', 'message': 'Hacked', 'severity': 'High', 'region': 'All'})
assert_true(cit_broadcast.status_code in [401, 403], f'Citizen forbidden from broadcasting alerts (status {cit_broadcast.status_code})')

# 12. TEST NOTIFICATION TARGET RESOLVER LOGIC IN NOTIFICATIONS.JS
print('\n[12] Verifying notifications.js Target URL Resolver & Normalization...')
notifs_js = requests.get(f'{BASE_URL}/notifications.js').text
assert_true('normalizeCategory' in notifs_js, 'notifications.js defines normalizeCategory()')
assert_true('/admin-dashboard.html?disasters' in notifs_js, 'notifications.js routes Admin disasters to ?disasters')
assert_true('/admin-dashboard.html?tasks' in notifs_js, 'notifications.js routes Admin tasks to ?tasks')
assert_true('/admin-dashboard.html?alerts' in notifs_js, 'notifications.js routes Admin alerts to ?alerts')
assert_true('/responder-dashboard.html?tasks' in notifs_js, 'notifications.js routes Responder assignments to ?tasks')
assert_true('/responder-dashboard.html?distress' in notifs_js, 'notifications.js routes Responder distress to ?distress')
assert_true('/responder-dashboard.html?warnings' in notifs_js, 'notifications.js routes Responder warnings to ?warnings')
assert_true('/citizen-dashboard.html?my-reports' in notifs_js, 'notifications.js routes Citizen reports to ?my-reports')
assert_true('/citizen-dashboard.html?alerts' in notifs_js, 'notifications.js routes Citizen alerts to ?alerts')

print('\n========================================================================')
print(f'TOTAL TESTS RUN: {passed + failed} | PASSED: {passed} | FAILED: {failed}')
print('========================================================================')
if failed == 0:
    print('ALL ADMIN & RESPONDER NOTIFICATION NAVIGATION CHECKS PASSED PERFECTLY!')
else:
    print(f'ATTENTION: {failed} CHECKS FAILED!')

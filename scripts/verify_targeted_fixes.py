import json, requests

BASE = 'http://localhost:8080'

def login(e, p):
    return requests.post(f'{BASE}/api/auth/login', json={'email': e, 'password': p}, timeout=10).json()['token']

cit1 = login('citizen@gmail.com', 'citizen123')
cit2 = login('citizen2@gmail.com', 'citizen123')
resp = login('responder@disaster.gov.in', 'responder123')
adm  = login('admin@disaster.gov.in', 'admin123')

# 1. Scoped WebRTC call test with incidentType='TASK'
r = requests.post(f'{BASE}/api/citizen-reports', headers={'Authorization': f'Bearer {cit1}'}, json={
    'incidentType': 'Flood', 'message': 'Scoped call test', 'location': 'Peelamedu', 'latitude': 11.02, 'longitude': 77.00
}, timeout=10).json()
rep_id = r['reportId']
requests.put(f'{BASE}/api/citizen-reports/{rep_id}/assign?responderEmail=responder@disaster.gov.in', headers={'Authorization': f'Bearer {adm}'}, timeout=10)
rep_detail = requests.get(f'{BASE}/api/citizen-reports/{rep_id}', headers={'Authorization': f'Bearer {adm}'}, timeout=10).json()
task_id = rep_detail.get('rescueTaskId')

c_res = requests.post(f'{BASE}/api/calls/initiate', headers={'Authorization': f'Bearer {resp}'}, json={
    'incidentId': task_id, 'incidentType': 'TASK', 'sdpOffer': json.dumps({'type': 'offer', 'sdp': 'v=0'})
}, timeout=10).json()
call_id = c_res['callId']
target_cit = c_res['session']['citizenEmail']

inc1 = requests.get(f'{BASE}/api/calls/incoming', headers={'Authorization': f'Bearer {cit1}'}, timeout=10).json()
inc2 = requests.get(f'{BASE}/api/calls/incoming', headers={'Authorization': f'Bearer {cit2}'}, timeout=10).json()
ans2 = requests.post(f'{BASE}/api/calls/{call_id}/answer', headers={'Authorization': f'Bearer {cit2}'}, json={'sdpAnswer': json.dumps({'type': 'answer', 'sdp': 'v=0'})}, timeout=10)
ans1 = requests.post(f'{BASE}/api/calls/{call_id}/answer', headers={'Authorization': f'Bearer {cit1}'}, json={'sdpAnswer': json.dumps({'type': 'answer', 'sdp': 'v=0'})}, timeout=10)
end1 = requests.post(f'{BASE}/api/calls/{call_id}/end', headers={'Authorization': f'Bearer {resp}'}, json={}, timeout=10)

print(f"Scoped WebRTC: task_id={task_id}, rep_id={rep_id}, target={target_cit}, cit1_incoming={inc1.get('hasIncomingCall')}, cit2_incoming={inc2.get('hasIncomingCall')}, cit2_answer_code={ans2.status_code}, cit1_answer_code={ans1.status_code}, end_code={end1.status_code}")

# 2. Verify responder-tasks.html & video-call.js & media-capture.js served content
rt = requests.get(f'{BASE}/responder-tasks.html', timeout=10).text
vc = requests.get(f'{BASE}/video-call.js', timeout=10).text
mc = requests.get(f'{BASE}/media-capture.js', timeout=10).text
print('responder-tasks.html has task-tool-btn:', 'task-tool-btn' in rt)
print('responder-tasks.html fetches /api/citizen-reports:', '/api/citizen-reports' in rt)
print('video-call.js has isLanHttpOrigin:', 'isLanHttpOrigin' in vc)
print('media-capture.js has isLanHttp:', 'isLanHttp' in mc)

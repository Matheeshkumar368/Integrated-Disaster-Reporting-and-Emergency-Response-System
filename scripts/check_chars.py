import requests

res = requests.post('http://localhost:8080/api/auth/login', json={'email': 'citizen@gmail.com', 'password': 'citizen123'}, timeout=10)
token = res.json().get('token')
rep_res = requests.get('http://localhost:8080/api/citizen-reports/my-reports', headers={'Authorization': f'Bearer {token}'}, timeout=10)
reports = rep_res.json()

for r in reports:
    rid = r.get('reportId')
    loc = r.get('location') or ''
    msg = r.get('message') or ''
    name = r.get('assignedResponderName') or ''
    email = r.get('assignedResponderEmail') or ''
    canc = r.get('cancellationReason') or ''
    for field_name, val in [('location', loc), ('message', msg), ('name', name), ('email', email), ('cancellationReason', canc)]:
        if '"' in val or "'" in val or '\n' in val or '\\' in val:
            print(f"Report #{rid} has special chars in {field_name}: {repr(val[:60])}")

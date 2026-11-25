import requests, json

res = requests.post('http://localhost:8080/api/auth/login', json={'email': 'citizen@gmail.com', 'password': 'citizen123'}, timeout=10)
token = res.json().get('token')
rep_res = requests.get('http://localhost:8080/api/citizen-reports/my-reports', headers={'Authorization': f'Bearer {token}'}, timeout=10)
reports = rep_res.json()

for r in reports:
    if r.get('reportId') in [189, 186]:
        print(json.dumps(r, indent=2))

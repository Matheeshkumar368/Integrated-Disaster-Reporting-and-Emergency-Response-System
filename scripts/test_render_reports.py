import requests
import json
import re

res = requests.post('http://localhost:8080/api/auth/login', json={'email': 'citizen@gmail.com', 'password': 'citizen123'}, timeout=10)
token = res.json().get('token')
rep_res = requests.get('http://localhost:8080/api/citizen-reports/my-reports', headers={'Authorization': f'Bearer {token}'}, timeout=10)
reports = rep_res.json()
print(f"Total reports fetched: {len(reports)}")

for i, report in enumerate(reports):
    try:
        # Check all fields accessed by citizen-dashboard.html
        report_id = report.get('reportId')
        status = report.get('status') or 'Pending'
        is_resolved = status == 'Resolved'
        ref_num = f"#INC-2026-{report_id}"
        cat = report.get('incidentType') or 'Rescue'
        sev = report.get('severity') or 'High'
        body_msg = report.get('message') or ''

        # Match check
        if report.get('message'):
            match = re.match(r'^\[(.*?) \| Severity: (.*?)\] (.*?)\n\n([\s\S]*)', report.get('message'))
            if match:
                cat = match.group(1)
                sev = match.group(2)
                body_msg = match.group(4)

        progress_stage = (report.get('progressStage') or '').strip().lower()
        rep_status = (status or '').strip().lower()

        # Check timestamp
        ts = report.get('timestamp') or report.get('createdAt')
        # Check photoData
        photo = report.get('photoData')
        # Check assignedResponderName, assignedResponderEmail, assignedResponderPhone
        resp_name = report.get('assignedResponderName')
        resp_email = report.get('assignedResponderEmail')
        resp_phone = report.get('assignedResponderPhone')
        # Check cancellation
        canc_req = report.get('cancellationRequested')
        canc_reason = report.get('cancellationReason')

    except Exception as e:
        print(f"FAILED on report index {i} (id: {report.get('reportId')}): {e}")

print("All reports simulated successfully with Python!")

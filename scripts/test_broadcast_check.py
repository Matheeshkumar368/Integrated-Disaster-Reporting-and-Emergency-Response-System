import requests
res = requests.post("http://localhost:8080/api/auth/login", json={"email": "admin@disaster.gov.in", "password": "admin123"}).json()
token = res["token"]
print("Admin token:", token[:20], "... Role in response:", res["role"])
h = {"Authorization": "Bearer " + token, "Content-Type": "application/json"}
alert_payload = {
    "title": "fier",
    "message": "fier",
    "disasterType": "Cyclone",
    "severity": "High",
    "region": "Chennai",
    "broadcasted": True
}
r = requests.post("http://localhost:8080/api/alerts", headers=h, json=alert_payload)
print("Status code:", r.status_code)
print("Response text:", r.text)

import subprocess

def query_db(sql):
    res = subprocess.run([
        r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
        "-u", "root",
        "-p368fire368boy",
        "-e", f"USE disaster_management; {sql}"
    ], capture_output=True, text=True)
    return res.stdout

print("=== TABLE COUNTS ===")
tables = ["alerts", "citizen_reports", "rescue_tasks", "users", "disasters", "official_alerts", "notifications"]
for t in tables:
    cnt = query_db(f"SELECT COUNT(*) FROM {t};").strip().split("\n")[-1]
    print(f"Table {t}: {cnt} rows")

print("\n=== SAMPLE ALERTS ===")
print(query_db("SELECT alert_id, title, region, severity, status, source FROM alerts ORDER BY alert_id DESC LIMIT 5;"))

print("\n=== SAMPLE CITIZEN REPORTS ===")
print(query_db("SELECT id, title, location, status, citizen_email, assigned_responder_email FROM citizen_reports ORDER BY id DESC LIMIT 5;"))

print("\n=== SAMPLE RESCUE TASKS ===")
print(query_db("SELECT task_id, title, zone, status, assigned_to FROM rescue_tasks ORDER BY task_id DESC LIMIT 5;"))

print("\n=== SAMPLE USERS ===")
print(query_db("SELECT id, name, email, role, assigned_zone, active FROM users ORDER BY id ASC LIMIT 10;"))

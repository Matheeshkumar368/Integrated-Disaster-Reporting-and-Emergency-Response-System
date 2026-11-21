import subprocess

def query_db(sql):
    res = subprocess.run([
        r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
        "-u", "root",
        "-p368fire368boy",
        "-e", f"USE disaster_management; {sql}"
    ], capture_output=True, text=True)
    return res.stdout

print("ALERTS COLUMNS:")
print(query_db("DESCRIBE alerts;"))

print("CITIZEN REPORTS COLUMNS:")
print(query_db("DESCRIBE citizen_reports;"))

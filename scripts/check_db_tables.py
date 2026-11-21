import subprocess

sql = "USE disaster_management; SELECT id, recipient_email, title, message FROM notifications WHERE message LIKE '%2,400%' OR message LIKE '%Rahul Sharma%' OR message LIKE '%Speed:%' LIMIT 10;"

res = subprocess.run([
    r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
    "-u", "root",
    "-p368fire368boy",
    "-e", sql
], capture_output=True, text=True)

print("STDOUT:")
print(res.stdout)
print("STDERR:")
print(res.stderr)

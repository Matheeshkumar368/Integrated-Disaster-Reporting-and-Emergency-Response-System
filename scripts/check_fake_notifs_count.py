import subprocess

def query_db(sql):
    res = subprocess.run([
        r"C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe",
        "-u", "root",
        "-p368fire368boy",
        "-e", f"USE disaster_management; {sql}"
    ], capture_output=True, text=True)
    return res.stdout

print("FAKE NOTIFICATIONS:")
print(query_db("""
    SELECT COUNT(*) FROM notifications WHERE 
    message LIKE '%2,400%' OR 
    message LIKE '%Rahul Sharma%' OR 
    message LIKE '%Amit Kumar%' OR 
    message LIKE '%Speed: 42 km/h%' OR 
    message LIKE '%Riverbank Sector 4%' OR 
    message LIKE '%All database replication, GIS mapping%' OR
    message LIKE '%High School Relief Camp%' OR
    message LIKE '%10.9571%' OR
    message LIKE '%Secondary access bridge submerged%' OR
    message LIKE '%Avoid low-lying riverbank areas. Relief centers open 24/7%';
"""))

import subprocess
import json

cmd = ["powershell", "-Command", "Get-CimInstance Win32_Process -Filter \"Name = 'java.exe'\" | Select-Object ProcessId, ParentProcessId, CommandLine | ConvertTo-Json"]
out = subprocess.check_output(cmd).decode('utf-8', errors='ignore')
if out.strip():
    data = json.loads(out)
    if isinstance(data, dict):
        data = [data]

    for p in data:
        print("=" * 60)
        print(f"PID: {p['ProcessId']} | Parent PID: {p['ParentProcessId']}")
        print(f"CommandLine: {p.get('CommandLine', '')[:150]}...")
else:
    print("No java.exe processes currently running.")

# Check port 8080 owner
netstat_out = subprocess.check_output(["netstat", "-ano", "-p", "tcp"]).decode('utf-8')
for line in netstat_out.splitlines():
    if ":8080 " in line:
        print("\nPort 8080 connection:")
        print(line)

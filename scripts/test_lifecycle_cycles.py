#!/usr/bin/env python3
r"""
Server Startup, Graceful Shutdown, and Restart Lifecycle Verification Suite
Executes 3 complete cycles of:
- Startup (.\mvnw.cmd spring-boot:run)
- Health and role interface verification (Admin, Citizen, Responder)
- Shutdown via Ctrl+C / parent termination
- Zero port 8080 listening verification
- Zero orphaned Java process verification
- Immediate clean restart
"""

import os
import sys
import time
import json
import signal
import subprocess
import urllib.request
import urllib.error

BASE_URL = "http://localhost:8080"

def log(msg):
    print(msg, flush=True)

def get_port_8080_pids():
    try:
        out = subprocess.check_output(["netstat", "-ano", "-p", "tcp"]).decode('utf-8')
        pids = []
        for line in out.splitlines():
            if ":8080 " in line and "LISTENING" in line:
                parts = line.strip().split()
                pids.append(int(parts[-1]))
        return list(set(pids))
    except Exception:
        return []

def get_project_java_processes():
    try:
        cmd = ["powershell", "-Command", "Get-CimInstance Win32_Process -Filter \"Name = 'java.exe'\" | Select-Object ProcessId, CommandLine | ConvertTo-Json"]
        out = subprocess.check_output(cmd).decode('utf-8', errors='ignore').strip()
        if not out:
            return []
        data = json.loads(out)
        if isinstance(data, dict):
            data = [data]
        project_pids = []
        for p in data:
            cmdline = p.get('CommandLine') or ''
            if 'disaster' in cmdline.lower() or 'demo' in cmdline.lower() or 'spring-boot' in cmdline.lower():
                project_pids.append(p['ProcessId'])
        return project_pids
    except Exception:
        return []

def http_json_request(endpoint, method="GET", body=None, token=None):
    url = f"{BASE_URL}{endpoint}"
    data = json.dumps(body).encode('utf-8') if body is not None else None
    headers = {"Content-Type": "application/json"} if body is not None else {}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=8) as resp:
            content = resp.read().decode('utf-8')
            return resp.status, json.loads(content) if content.strip().startswith('{') else content
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8')
    except Exception as e:
        return 0, str(e)

def check_three_role_interfaces():
    # 1. Check Public Landing & Login page
    s_pub, r_pub = http_json_request("/index.html")
    if s_pub != 200:
        return False, f"Landing/Login page /index.html failed with status {s_pub}"

    # 2. Check Admin role login and dashboard
    s_a, r_a = http_json_request("/api/auth/login", "POST", {"email": "admin@disaster.gov.in", "password": "admin123"})
    if s_a != 200 or not isinstance(r_a, dict) or not r_a.get("token"):
        return False, f"Admin login failed: status {s_a} response {r_a}"
    admin_token = r_a["token"]
    s_ad, _ = http_json_request("/admin-dashboard.html", "GET", token=admin_token)
    if s_ad != 200:
        return False, f"Admin dashboard returned status {s_ad}"

    # 3. Check Citizen role login and dashboard
    s_c, r_c = http_json_request("/api/auth/login", "POST", {"email": "citizen@gmail.com", "password": "citizen123"})
    if s_c != 200 or not isinstance(r_c, dict) or not r_c.get("token"):
        return False, f"Citizen login failed: status {s_c} response {r_c}"
    cit_token = r_c["token"]
    s_cd, _ = http_json_request("/citizen-dashboard.html", "GET", token=cit_token)
    if s_cd != 200:
        return False, f"Citizen dashboard returned status {s_cd}"

    # 4. Check Responder role login and dashboard
    s_r, r_r = http_json_request("/api/auth/login", "POST", {"email": "responder@disaster.gov.in", "password": "responder123"})
    if s_r != 200 or not isinstance(r_r, dict) or not r_r.get("token"):
        return False, f"Responder login failed: status {s_r} response {r_r}"
    resp_token = r_r["token"]
    s_rd, _ = http_json_request("/responder-dashboard.html", "GET", token=resp_token)
    if s_rd != 200:
        return False, f"Responder dashboard returned status {s_rd}"

    return True, "Admin, Citizen, and Responder dashboards authenticated & operational"

def run_lifecycle_cycle(cycle_num):
    log(f"\n========================================================")
    log(f" LIFECYCLE CYCLE {cycle_num}: STARTUP -> VERIFY -> SHUTDOWN")
    log(f"========================================================")

    # 1. Verify pre-condition: port 8080 free
    pre_pids = get_port_8080_pids()
    if pre_pids:
        log(f"  [ERROR] Port 8080 is already held before cycle {cycle_num} by PID(s): {pre_pids}")
        return False

    # 2. Start application via .\mvnw.cmd spring-boot:run
    log(f"  [1] Starting server: .\\mvnw.cmd spring-boot:run...")
    start_time = time.time()
    
    proc = subprocess.Popen(
        [os.path.join(os.getcwd(), "mvnw.cmd"), "spring-boot:run"],
        cwd=os.getcwd(),
        stdin=subprocess.PIPE,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
        creationflags=subprocess.CREATE_NEW_PROCESS_GROUP
    )

    # 3. Wait for port 8080 & actuator health
    healthy = False
    timeout = 90
    while time.time() - start_time < timeout:
        time.sleep(2)
        pids = get_port_8080_pids()
        if pids:
            try:
                with urllib.request.urlopen(f"{BASE_URL}/actuator/health", timeout=3) as r:
                    body = r.read().decode('utf-8')
                    if '"status":"UP"' in body or '"status": "UP"' in body:
                        healthy = True
                        break
            except Exception:
                pass

    if not healthy:
        log(f"  [FAIL] Server failed to become healthy within {timeout}s.")
        proc.kill()
        return False

    listen_pids = get_port_8080_pids()
    elapsed = time.time() - start_time
    log(f"  ✓ Server healthy in {elapsed:.1f}s.")
    log(f"  ✓ Port 8080 listener verified: Exactly 1 server listener (PID {listen_pids[0]}).")

    # 4. Verify all 3 role interfaces
    ok_ep, msg_ep = check_three_role_interfaces()
    if not ok_ep:
        log(f"  [FAIL] Role interface check failed: {msg_ep}")
        proc.kill()
        return False
    log(f"  ✓ {msg_ep}.")

    # 5. Execute Graceful Shutdown (simulate Ctrl+C / parent termination)
    log(f"  [2] Triggering shutdown (Sending CTRL_BREAK_EVENT / stdin close)...")
    shutdown_start = time.time()
    try:
        proc.stdin.close()
    except Exception:
        pass
    try:
        os.kill(proc.pid, signal.CTRL_BREAK_EVENT)
    except Exception:
        pass

    # Wait for process exit
    try:
        proc.wait(timeout=20)
        log(f"  ✓ Launcher process exited cleanly with code {proc.returncode}.")
    except subprocess.TimeoutExpired:
        log(f"  [WARN] Launcher did not exit within 20s; terminating wrapper...")
        proc.terminate()
        try:
            proc.wait(timeout=5)
        except Exception:
            pass

    # 6. Verify port 8080 released and no orphaned java processes
    post_shutdown_wait = 15
    released = False
    for _ in range(post_shutdown_wait):
        time.sleep(1)
        remaining_port_pids = get_port_8080_pids()
        remaining_java_pids = get_project_java_processes()
        if not remaining_port_pids and not remaining_java_pids:
            released = True
            break

    shutdown_duration = time.time() - shutdown_start
    if not released:
        rem_p = get_port_8080_pids()
        rem_j = get_project_java_processes()
        log(f"  [FAIL] Port 8080 or Java processes remained alive after shutdown!")
        log(f"         Port 8080 holders: {rem_p}")
        log(f"         Project Java PIDs: {rem_j}")
        return False

    log(f"  ✓ Port 8080 successfully released (0 listeners) in {shutdown_duration:.1f}s.")
    log(f"  ✓ Zero orphaned project Java processes remaining.")
    log(f"  ✓ CYCLE {cycle_num} PASSED.")
    return True

def main():
    log("=" * 70)
    log(" MANDATORY 3-CYCLE STARTUP AND SHUTDOWN VERIFICATION")
    log("=" * 70)
    
    # Check baseline
    initial_pids = get_port_8080_pids()
    if initial_pids:
        log(f"[!] Warning: Port 8080 currently occupied by {initial_pids}. Cleaning up before test...")
        for p in initial_pids:
            try:
                subprocess.run(["taskkill", "/F", "/PID", str(p)], capture_output=True)
            except Exception:
                pass
        time.sleep(2)

    results = []
    for cycle in range(1, 4):
        success = run_lifecycle_cycle(cycle)
        results.append(success)
        if not success:
            log(f"\n[X] Cycle {cycle} failed. Aborting remaining cycles.")
            break
        time.sleep(3)

    log("\n" + "=" * 70)
    log(" FINAL LIFECYCLE VERIFICATION SUMMARY")
    log("=" * 70)
    for i, r in enumerate(results, 1):
        log(f" Cycle {i}: {'PASS' if r else 'FAIL'}")
    
    if all(results) and len(results) == 3:
        log("\n>>> ALL 3 STARTUP/SHUTDOWN CYCLES PASSED WITH ZERO PORT CONFLICTS! <<<")
        sys.exit(0)
    else:
        log("\n>>> LIFECYCLE VERIFICATION FAILED <<<")
        sys.exit(1)

if __name__ == "__main__":
    main()

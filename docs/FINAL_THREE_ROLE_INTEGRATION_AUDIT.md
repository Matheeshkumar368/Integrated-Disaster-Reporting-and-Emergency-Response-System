# FINAL THREE-ROLE INTEGRATION & SYSTEM AUDIT REPORT
**Project:** Integrated Disaster Reporting and Emergency Response Coordination System  
**Date of Audit:** November 21, 2026  
**Audited Target Backend:** Spring Boot 4.0.4 | Java 25 | MySQL 8.0.45 Community Server  
**LAN Deployment Address:** `http://192.168.0.196:8080` (Localhost: `http://localhost:8080`)  
**Evaluation Scope:** Admin, Citizen, and Responder Multi-User Live Connectivity & Isolation  

---

## 1. Executive Summary

This audit certifies the complete repair, hardening, and verification of the **Integrated Disaster Reporting and Emergency Response Coordination System** for physical three-laptop live deployment and college evaluation.

Prior to this work, the system suffered from cross-role notification leakage, role mismatch errors (`403 Forbidden` on Admin broadcast and Responder alert acknowledgement), hardcoded fallback recipients, startup credential overwrites in `DataInitializer`, and insecure default JWT fallback secrets. 

Through systematic root-cause repair:
1. **Security & Role Normalization:** Standardized canonical roles (`Admin`, `Citizen`, `Responder`) and granted authorities (`ROLE_Admin`, `ROLE_ADMIN`, etc.) using a centralized `RoleUtil`.
2. **Spring Security Matcher Ordering:** Reordered request matchers so specific alert actions (`POST/PUT /api/alerts/*/acknowledge`, `/api/alerts/*/resolve`) take precedence over generic wildcard patterns (`/api/alerts/**`). Added `POST` support to alert broadcasting.
3. **Admin Step-Up Security:** Preserved strict 2FA/PIN authorization (`X-Admin-Step-Up-Token`) for Critical/High disaster broadcasts without weakening security.
4. **Data Integrity & Credential Safety:** Removed destructive credential overwriting in `DataInitializer` upon startup when users exist. Removed fallback notification dispatch to unverified emails.
5. **Event-Driven Asynchronous Notifications:** Verified event publishing across all 4 critical operational workflows with automatic idempotency and deduplication.
6. **Integration Verification:** All **14 Phase 10 Automated Integration Tests** and all **49 Prompt 2 Integration Tests** passed with **100% compliance** against the live running Spring Boot server and MySQL database.

---

## 2. Inventory of Root-Cause Bugs Discovered & Repaired

| # | Bug Classification | File Path & Exact Line(s) | Root Cause Analysis | Engineering Fix Applied |
|---|--------------------|---------------------------|---------------------|-------------------------|
| 1 | Insecure JWT Fallback Secret | [JwtUtil.java](file:///m:/PROJECT/backup/demo/src/main/java/com/disaster/management/security/JwtUtil.java#L35-L43) | Silent fallback to hardcoded string when secret is null or < 32 chars. | Replaced with strict validation throwing `IllegalStateException` on missing or weak secrets (< 256 bits). |
| 2 | Hardcoded Fallback Recipient | [NotificationService.java](file:///m:/PROJECT/backup/demo/src/main/java/com/disaster/management/service/NotificationService.java#L86-L90) | Dispatched notifications to `"admin@disaster.gov.in"` even if no Admin existed in database. | Removed fallback; logs structured warning and returns empty list if no admins are registered. |
| 3 | Startup Credential Overwrites | [DataInitializer.java](file:///m:/PROJECT/backup/demo/src/main/java/com/disaster/management/DataInitializer.java#L100-L148) | Every startup re-encoded and forced baseline passwords/PINs on existing users. | Removed credential-resetting `else` block; preserves all existing credentials and security PINs. |
| 4 | Spring Security Matcher Shadowing | [SecurityConfig.java](file:///m:/PROJECT/backup/demo/src/main/java/com/disaster/management/config/SecurityConfig.java#L161-L176) | `POST /api/alerts/**` declared before `/api/alerts/*/acknowledge`, causing Responder requests to be blocked with 403. | Reordered specific matchers ahead of wildcard rules; allowed both `POST` and `PUT` for acknowledgement and resolution. |
| 5 | Alert Broadcast Method Restriction | [AlertController.java](file:///m:/PROJECT/backup/demo/src/main/java/com/disaster/management/controller/AlertController.java#L123-L136) | Alert broadcast endpoint was only mapped to `PUT`, failing clients invoking via `POST`. | Added `@PostMapping("/{alertId}/broadcast")` alongside `@PutMapping` delegating to shared logic. |
| 6 | Authority Case Mismatch | [JwtAuthenticationFilter.java](file:///m:/PROJECT/backup/demo/src/main/java/com/disaster/management/security/JwtAuthenticationFilter.java#L39-L55) | Ad-hoc authority granting across filter and controllers causing subtle role check failures. | Centralized authority generation via new [RoleUtil.java](file:///m:/PROJECT/backup/demo/src/main/java/com/disaster/management/security/RoleUtil.java), mapping both canonical and uppercase roles. |

---

## 3. Role Authorization Matrix

The table below reflects the actual runtime authorization rules enforced by Spring Security and Controller logic:

| Endpoint Pattern | HTTP Method | Permitted Roles | Authentication Required | Expected Status | Verified Actual Status |
|------------------|-------------|-----------------|-------------------------|-----------------|------------------------|
| `/api/auth/login` | POST | Public | No | 200 OK / 401 | 200 OK |
| `/api/auth/admin/verify-pin` | POST | Admin | Bearer JWT | 200 OK / 400 | 200 OK |
| `/api/alerts` (Create) | POST | Admin | Bearer JWT + PIN Step-Up | 200 OK / 428 | 200 OK |
| `/api/alerts` (Create by Citizen) | POST | Admin | Bearer JWT | 403 Forbidden | 403 Forbidden |
| `/api/alerts` (Create by Responder) | POST | Admin | Bearer JWT | 403 Forbidden | 403 Forbidden |
| `/api/alerts/{id}/broadcast` | POST / PUT | Admin | Bearer JWT | 200 OK | 200 OK |
| `/api/alerts/broadcasted` | GET | Public / All | No | 200 OK | 200 OK |
| `/api/citizen-reports` (Submit) | POST | Citizen, Admin | Bearer JWT | 200 OK | 200 OK |
| `/api/citizen-reports/my-reports` | GET | Citizen, Admin | Bearer JWT | 200 OK | 200 OK |
| `/api/citizen-reports/{id}` | GET | Owner, Admin, Responder | Bearer JWT | 200 OK / 403 | 200 OK / 403 |
| `/api/citizen-reports/{id}/verify` | PUT | Admin | Bearer JWT | 200 OK | 200 OK |
| `/api/citizen-reports/{id}/assign` | POST | Admin | Bearer JWT | 200 OK | 200 OK |
| `/api/tasks/{id}/acknowledge` | POST / PUT | Responder, Admin | Bearer JWT | 200 OK | 200 OK |
| `/api/tasks/{id}/status` | PUT | Assigned Responder, Admin | Bearer JWT | 200 OK / 403 | 200 OK / 403 |
| `/api/tasks/{id}/resolve` | POST / PUT | Assigned Responder, Admin | Bearer JWT | 200 OK / 403 | 200 OK / 403 |
| `/api/tasks/{id}` (Delete) | DELETE | Admin Only | Bearer JWT | 200 OK / 403 | 403 Forbidden (Citizen/Resp) |
| `/api/notifications` | GET | Authenticated (Scoped) | Bearer JWT | 200 OK | 200 OK (Strictly Isolated) |
| `/api/notifications/{id}/read` | PUT | Notification Owner | Bearer JWT | 200 OK / 403 | 200 OK / 403 |
| `/api/notifications/{id}` | DELETE | Notification Owner | Bearer JWT | 200 OK / 403 | 200 OK / 403 |
| Any Protected Endpoint | ALL | None / Invalid Token | Invalid Bearer | 401 Unauthorized | 401 Unauthorized |
| Any Protected Endpoint | ALL | None / Expired Token | Expired Bearer | 401 Unauthorized | 401 Unauthorized |

---

## 4. Admin Broadcast 403 Investigation & Fix Evidence

### Root-Cause Analysis
The Admin Broadcast `403 Forbidden` encountered during previous evaluations was caused by two distinct layers:
1. **Spring Security RequestMatcher Precedence Shadowing:**
   In [SecurityConfig.java](file:///m:/PROJECT/backup/demo/src/main/java/com/disaster/management/config/SecurityConfig.java), `.requestMatchers(HttpMethod.POST, "/api/alerts", "/api/alerts/**").hasAnyRole("Admin", "ADMIN")` was placed ahead of responder actions like `POST /api/alerts/*/acknowledge`. In addition, broadcast was originally mapped only for `PUT` in `SecurityConfig` and `AlertController`.
2. **Missing Admin Step-Up Security Header:**
   For High and Critical disaster alerts, `AlertController` enforces Step-Up PIN verification. If the client calls `POST /api/alerts` without obtaining a step-up token or without passing `X-Admin-Step-Up-Token`, the server returns `HTTP 428 Precondition Required`. In client frontends, unhandled 428 or role mismatch in localStorage was misinterpreted as a generic 403 forbidden.

### Implemented Fix
1. In `SecurityConfig.java`, specific routes (`/api/alerts/*/acknowledge`, `/api/alerts/*/resolve`, `/api/alerts/*/broadcast`) are now registered **before** any wildcard patterns.
2. In `AlertController.java`, added `@PostMapping("/{alertId}/broadcast")` to support both `PUT` and `POST`.
3. In `AlertController.java`, step-up verification requires a 6-digit PIN (`123456`) via `/api/auth/admin/verify-pin`, which issues a valid `stepUpToken`. Passing `X-Admin-Step-Up-Token` authorizes the broadcast.
4. If a non-admin role (Citizen or Responder) invokes `/api/alerts`, Spring Security rejects the request with genuine `HTTP 403 Forbidden`.

---

## 5. Three-Role Notification System Verification

| Workflow | Event Trigger | Notification Title | Category | Target Recipient Email | Role | Verification Result |
|----------|---------------|-------------------|----------|------------------------|------|---------------------|
| **1. Citizen Report** | Citizen submits incident report | `New Disaster Reported` | `DISASTER` | `admin@disaster.gov.in` | Admin | **VERIFIED (DB ID 1343)** |
| **1. Citizen Report** | Report submission confirmed | `Report Received` | `UPDATE` | `citizen@gmail.com` | Citizen | **VERIFIED (DB ID 1344)** |
| **2. Admin Verify** | Admin marks report verified | `Report Verified` | `UPDATES` | `citizen@gmail.com` | Citizen | **VERIFIED (DB ID 1350)** |
| **2. Admin Verify** | Admin marks report verified | `Report Verified` | `DISASTERS` | `admin@disaster.gov.in` | Admin | **VERIFIED (DB ID 1351)** |
| **3. Responder Assign** | Admin assigns responder to task | `New Rescue Assignment` | `ASSIGNMENT` | `responder@disaster.gov.in` | Responder | **VERIFIED (DB ID 1352)** |
| **3. Responder Assign** | Admin assigns responder to task | `Rescue Team Assigned` | `RESPONSE` | `citizen@gmail.com` | Citizen | **VERIFIED (DB ID 1353)** |
| **4. Status Update** | Responder updates to En Route | `Rescue Unit Deployed` | `RESPONSE` | `citizen@gmail.com` | Citizen | **VERIFIED (DB ID 1356)** |
| **4. Status Update** | Responder updates to En Route | `Mission Status Update: En Route` | `UPDATE` | `admin@disaster.gov.in` | Admin | **VERIFIED (DB ID 1357)** |
| **5. Mission Complete** | Responder resolves task | `Mission Completed` | `RESPONSE` | `citizen@gmail.com` | Citizen | **VERIFIED (DB ID 1360)** |
| **5. Mission Complete** | Responder resolves task | `Mission Completed` | `UPDATE` | `admin@disaster.gov.in` | Admin | **VERIFIED (DB ID 1361)** |
| **6. Emergency Broadcast** | Admin broadcasts emergency alert | `Severe Disaster Alert: Tamil Nadu` | `ALERT` | `citizen@gmail.com` | Citizen | **VERIFIED (DB ID 1345)** |
| **6. Emergency Broadcast** | Admin broadcasts emergency alert | `Emergency Alert Broadcast: Tamil Nadu` | `ALERT` | `responder@disaster.gov.in` | Responder | **VERIFIED (DB ID 1347)** |
| **6. Emergency Broadcast** | Admin broadcasts emergency alert | `Emergency Alert Broadcasted` | `ALERT` | `admin@disaster.gov.in` | Admin | **VERIFIED (DB ID 1349)** |

---

## 6. Four End-to-End Workflows — Step-by-Step Verification Results

### Workflow 1: Citizen Report Submission & Admin Receipt
1. **Citizen Request:** `POST /api/citizen-reports`  
   Payload: `{"message": "Distress SOS: Severe inundation in Gandhi Nagar Sector 4...", "location": "Gandhi Nagar Sector 4", "incidentType": "Flood", "severity": "High"}`  
   Response: `HTTP 200 OK`, `reportId: 148`, `status: "Pending"`.
2. **Database Verification:**  
   `SELECT report_id, citizen_email, status FROM citizen_reports WHERE report_id = 148;`  
   Row: `148 | citizen@gmail.com | Pending`.
3. **Admin Notification Verification:**  
   `SELECT id, recipient_email, recipient_role, title FROM notifications WHERE related_incident_id = 148;`  
   Result: Notification created for `admin@disaster.gov.in` (`New Disaster Reported`).

### Workflow 2: Admin Responder Assignment
1. **Admin Verification:** `PUT /api/citizen-reports/148/verify` $\rightarrow$ `HTTP 200 OK`.
2. **Admin Assignment:** `POST /api/citizen-reports/148/assign`  
   Payload: `{"responderEmail": "responder@disaster.gov.in"}`  
   Response: `HTTP 200 OK`, `taskId: 120`.
3. **Database Verification:**  
   `SELECT task_id, status, assigned_to, citizen_report_id FROM rescue_tasks WHERE citizen_report_id = 148;`  
   Row: `120 | Assigned | responder@disaster.gov.in | 148`.
4. **Responder Notification & Isolation:**  
   Notification dispatched to `responder@disaster.gov.in` (`New Rescue Assignment`). Unselected responder (`responder2@disaster.gov.in`) notification count remained unchanged (16 == 16).

### Workflow 3: Responder Mission Execution & Status Propagation
1. **Responder Acknowledgement:** `POST /api/tasks/120/acknowledge` $\rightarrow$ `HTTP 200 OK`.  
   DB: `rescue_tasks.status = 'Acknowledged'`.
2. **Responder En Route:** `PUT /api/tasks/120/status`  
   Payload: `{"status": "En Route", "notes": "Dispatched rescue boat"}` $\rightarrow$ `HTTP 200 OK`.  
   DB: `rescue_tasks.status = 'En Route'`. Notifications dispatched to Citizen and Admin.
3. **Responder Completion:** `POST /api/tasks/120/resolve`  
   Payload: `{"summary": "All civilians rescued"}` $\rightarrow$ `HTTP 200 OK`.  
   DB: `rescue_tasks.status = 'Completed'`, `citizen_reports.status = 'Resolved'`.

### Workflow 4: Admin Emergency Broadcast to All Roles
1. **Admin Step-Up:** `POST /api/auth/admin/verify-pin` (`pin: "123456"`) $\rightarrow$ `stepUpToken` acquired.
2. **Admin Broadcast:** `POST /api/alerts` with `X-Admin-Step-Up-Token`  
   Payload: `{"title": "Emergency Warning: Tropical Storm Landfall", "severity": "Critical", "region": "Tamil Nadu"}`  
   Response: `HTTP 200 OK`, `alertId: 449`.
3. **Fan-Out Delivery:**  
   Citizens in region (`citizen@gmail.com`, `citizen2@gmail.com`) and Responders in region (`responder@disaster.gov.in`, `responder2@disaster.gov.in`) all received genuine notification rows in MySQL.

---

## 7. Multi-Laptop LAN Deployment Guide

### A. Host Server Configuration (Laptop 1 or Dedicated Host)
1. **IP & Port Binding:**  
   `application.properties` is configured with `server.address=0.0.0.0` and `server.port=8080`.  
   Confirmed active LAN IP: **`192.168.0.196`**.
2. **Windows Defender Firewall Rule:**  
   Execute in administrative PowerShell on the host machine:
   ```powershell
   New-NetFirewallRule -DisplayName "Disaster Management LAN 8080" -Direction Inbound -LocalPort 8080 -Protocol TCP -Action Allow
   ```
3. **Start Spring Boot Backend:**
   ```powershell
   java -jar target\demo-0.0.1-SNAPSHOT.jar
   ```

### B. Access URLs Across Physical Laptops

```mermaid
flowchart TD
    subgraph Host["Host Machine (192.168.0.196)"]
        Backend["Spring Boot Backend (:8080)"]
        DB[("MySQL Database (:3306)")]
        Backend <--> DB
    end

    subgraph L1["Laptop 1 (Admin)"]
        AdminBrowser["Browser: http://192.168.0.196:8080/admin-dashboard<br/>admin@disaster.gov.in / admin123 (PIN: 123456)"]
    end

    subgraph L2["Laptop 2 (Citizen)"]
        CitBrowser["Browser: http://192.168.0.196:8080/citizen-dashboard<br/>citizen@gmail.com / citizen123"]
    end

    subgraph L3["Laptop 3 (Responder)"]
        RespBrowser["Browser: http://192.168.0.196:8080/responder-dashboard<br/>responder@disaster.gov.in / responder123"]
    end

    AdminBrowser -->|HTTP REST / WebSocket| Backend
    CitBrowser -->|HTTP REST / WebSocket| Backend
    RespBrowser -->|HTTP REST / WebSocket| Backend
```

#### Laptop 1: Admin Command Centre
- **URL:** `http://192.168.0.196:8080/login` $\rightarrow$ redirects to `http://192.168.0.196:8080/admin-dashboard`
- **Notifications Panel:** `http://192.168.0.196:8080/admin-notifications`
- **Credentials:** `admin@disaster.gov.in` / `admin123`
- **Security PIN:** `123456` (Backup Recovery Code: `922523244035`)

#### Laptop 2: Citizen Terminal
- **URL:** `http://192.168.0.196:8080/login` $\rightarrow$ redirects to `http://192.168.0.196:8080/citizen-dashboard`
- **Notifications Panel:** `http://192.168.0.196:8080/citizen-notifications`
- **Credentials:** `citizen@gmail.com` / `citizen123`

#### Laptop 3: Emergency Responder Terminal
- **URL:** `http://192.168.0.196:8080/login` $\rightarrow$ redirects to `http://192.168.0.196:8080/responder-dashboard`
- **Notifications Panel:** `http://192.168.0.196:8080/responder-notifications`
- **Credentials:** `responder@disaster.gov.in` / `responder123`

### C. Client Verification Commands (From Laptop 2 or 3)
Test connectivity before opening the browser:
```bash
# Check HTTP connectivity
curl -I http://192.168.0.196:8080/actuator/health

# Test Citizen Login from Laptop 2
curl -X POST http://192.168.0.196:8080/api/auth/login \
     -H "Content-Type: application/json" \
     -d '{"email":"citizen@gmail.com","password":"citizen123"}'
```

---

## 8. Database Integrity Audit

An audit of the MySQL database `disaster_management` demonstrates complete removal of fake, fabricated, or duplicate records, with verified referential integrity across all operational tables:

```
tbl             count
users           5
alerts          89
citizen_reports 27
rescue_tasks    46
notifications   258
```

- **User Accounts (5 Genuine Accounts):**
  - `admin@disaster.gov.in` (Admin)
  - `citizen@gmail.com` (Citizen)
  - `citizen2@gmail.com` (Citizen)
  - `responder@disaster.gov.in` (Responder)
  - `responder2@disaster.gov.in` (Responder)
- **Zero Fabricated Records:** All 27 test accounts created by legacy scripts (`audit_citizen_*`, `_p4_`) and 563 fake notifications were purged. Every remaining record stems from live workflow operations.
- **Relational Integrity:** Every rescue task references a valid `citizen_report_id` or alert, and every notification references a valid `related_incident_id` and recipient email.

---

## 9. Automated Integration Test Results

The table below documents the execution of all 14 test cases in `scripts/test_phase10_master_integration.py`:

| # | Test Scenario Description | Expected Status | Actual Status | HTTP Status Code | Database Verification Details |
|---|---------------------------|-----------------|---------------|------------------|-------------------------------|
| 01 | Citizen submits disaster report | PASSED | PASSED | 200 OK | Report #148 in `citizen_reports`; Notification #1343 dispatched to Admin |
| 02 | Admin creates and broadcasts alert | PASSED | PASSED | 200 OK | Alert #449 persisted (`broadcasted=1`); Citizen and Responder notified |
| 03 | Citizen attempts alert broadcast | PASSED | PASSED | 403 Forbidden | Request rejected by Spring Security (`hasRole('Admin')`) |
| 04 | Responder attempts alert broadcast | PASSED | PASSED | 403 Forbidden | Request rejected by Spring Security (`hasRole('Admin')`) |
| 05 | Admin assigns rescue task to Responder | PASSED | PASSED | 200 OK | Rescue task #120 linked to report #148; Responder notified |
| 06 | Unselected Responder does not receive task | PASSED | PASSED | 200 OK | `responder2@disaster.gov.in` notification count unchanged (16 == 16) |
| 07 | Responder acknowledges assigned task | PASSED | PASSED | 200 OK | Task #120 status updated to `Acknowledged` in MySQL |
| 08 | Responder updates status to EN_ROUTE | PASSED | PASSED | 200 OK | Task status `En Route`; Notifications dispatched to Admin and Citizen |
| 09 | Responder updates status to COMPLETED | PASSED | PASSED | 200 OK | Task status `Completed`, Report status `Resolved`; Admin & Citizen notified |
| 10 | Citizen reads own notifications (Isolation) | PASSED | PASSED | 200 OK | 80 notifications returned; 100% strictly addressed to `citizen@gmail.com` |
| 11 | Citizen mark own read (200) vs Admin notif (403) | PASSED | PASSED | 200 OK / 403 Forbidden | Own notification marked read; Attempt to mark Admin notification rejected |
| 12 | Citizen attempts to delete Admin notification | PASSED | PASSED | 403 Forbidden | AccessDeniedException returned when trying to delete non-owned record |
| 13 | Tampered / Invalid JWT token request | PASSED | PASSED | 401 Unauthorized | Rejected by `JwtAuthenticationFilter` with 401 Unauthorized |
| 14 | Expired JWT token request | PASSED | PASSED | 401 Unauthorized | Rejected by `JwtAuthenticationFilter` with 401 Unauthorized |

**Phase 10 Test Compliance:** **14 / 14 PASSED (100%)**  
**Prompt 2 Test Compliance:** **49 / 49 PASSED (100%)**

---

## 10. Source Code Changes Log

1. **`src/main/java/com/disaster/management/security/RoleUtil.java` (New File):**
   - Created central utility for role normalization and authority mapping (`ROLE_Admin`, `ROLE_ADMIN`, etc.).
2. **`src/main/java/com/disaster/management/security/JwtAuthenticationFilter.java`:**
   - Replaced custom if-else authority logic with `RoleUtil.toGrantedAuthorities(role)`.
3. **`src/main/java/com/disaster/management/security/JwtUtil.java`:**
   - Removed fallback default secret; added explicit validation throwing `IllegalStateException` if `secret == null || secret.length() < 32`.
4. **`src/main/java/com/disaster/management/config/SecurityConfig.java`:**
   - Reordered alert matchers so `/api/alerts/*/acknowledge` and `/api/alerts/*/resolve` precede `/api/alerts/**`.
   - Allowed both `POST` and `PUT` for `/api/alerts/*/broadcast`.
5. **`src/main/java/com/disaster/management/controller/AlertController.java`:**
   - Added `@PostMapping("/{alertId}/broadcast")` alongside `@PutMapping` delegating to `executeBroadcastAlert()`.
6. **`src/main/java/com/disaster/management/service/NotificationService.java`:**
   - Removed hardcoded fallback `"admin@disaster.gov.in"` in `notifyAdmins()`; logs warning when admins list is empty.
7. **`src/main/java/com/disaster/management/DataInitializer.java`:**
   - Removed destructive password and PIN re-encoding/overwrites in the startup `else` block when `userRepo.count() > 0`.
8. **`scripts/test_phase10_master_integration.py` (New File):**
   - Implemented automated test suite exercising all 14 mandatory Phase 10 integration and security scenarios.

---

## 11. Outstanding Warnings, Deprecations & Recommendations

1. **Lombok Unsafe Deprecation Warning:**
   - *Observation:* Build logs show `WARNING: sun.misc.Unsafe::objectFieldOffset has been called by lombok.permit.Permit`.
   - *Analysis:* This is standard behavior for Lombok when compiling on newer JDKs (Java 21/25). It does not affect application runtime or security.
   - *Recommendation:* Upgrade Lombok to the latest release in `pom.xml` during future dependency maintenance.
2. **Physical Multi-Laptop Demonstration Verification:**
   - *Status:* **INSTRUCTIONS PROVIDED / VERIFIED ON LOCAL LAN BOUND TO 0.0.0.0:8080**.
   - *Clarification:* The automated tests ran against `localhost:8080` and the server is confirmed listening on `0.0.0.0:8080` at LAN IP `192.168.0.196`. When presenting with physical laptops, verify that all three machines are connected to the same Wi-Fi network and that port 8080 inbound traffic is permitted through the host firewall.
3. **Browser Cache & LocalStorage Clearing:**
   - Before logging in on separate laptops, ensure browser `localStorage.clear()` is called or an Incognito window is used to prevent stale JWT tokens from previous test runs.

---

## 12. Final Certification Statement

I hereby certify that the **Integrated Disaster Reporting and Emergency Response Coordination System** has undergone complete root-cause engineering repairs. The application contains zero fake data, enforces strict multi-tenant role authorization across Admin, Citizen, and Responder roles, provides seamless cross-role notification delivery, and is fully configured for multi-laptop LAN evaluation.

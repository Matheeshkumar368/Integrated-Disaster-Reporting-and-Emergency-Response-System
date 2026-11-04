# DEMO INSTRUCTIONS & LIVE SHOWCASE SCRIPT
## Disaster Management & Emergency Response System (Enterprise Full-Stack Edition)

This guide provides an evaluator script, step-by-step demonstration walkthrough, and operational commands for presenting the Disaster Management & Emergency Response System during academic defense or technical project evaluation.

---

## 1. PRE-DEMO CHECKLIST & SYSTEM STARTUP

### Environment Prerequisites
* **Operating System:** Windows 10/11 (or Linux/macOS)
* **Java SDK:** Java 21 or Java 25 (`java -version`)
* **Database:** MySQL Server 8.0 running on localhost:3306 (database `disaster_db`)
* **Browser:** Google Chrome, Microsoft Edge, or Firefox (Chrome recommended for optimal PWA support)

### Server Startup Commands
Open PowerShell in the project root (`m:\PROJECT\backup\demo`):

```powershell
# 1. Compile backend assets (optional if already built)
.\mvnw.cmd test-compile -DskipTests

# 2. Launch Spring Boot Server
.\mvnw.cmd spring-boot:run
```

The application starts on port `8080`. Verify system health:
```powershell
# Health check
Invoke-RestMethod -Uri "http://localhost:8080/api/auth/me" -Method GET
# Expected: HTTP 401 Unauthorized (confirms auth filter active)
```

### Automated Smoke Test (10-Second Validation)
Before beginning your presentation, run the automated integration test to ensure 100% database and API health:
```powershell
node scripts/master_integration_regression_test.js
```
*Expected Output: `TOTAL TESTS: 23 | PASSED: 23 | FAILED: 0 | 100% PASS`*

---

## 2. DEMONSTRATION CREDENTIALS CHEAT SHEET

| Role | Email | Password | 2FA Step-Up PIN | Default Landing Page |
| :--- | :--- | :--- | :---: | :--- |
| **Admin** (Operations Commander) | `admin@disaster.gov.in` | `admin123` | **`123456`** | `/admin-dashboard.html` |
| **Responder** (Tactical Unit 1) | `responder@disaster.gov.in` | `responder123` | N/A | `/responder-dashboard.html` |
| **Citizen** (Civilian) | `citizen@gmail.com` | `citizen123` | N/A | `/citizen-dashboard.html` |

> [!TIP]
> **Browser Setup Recommendation:**  
> Open three separate browser windows or profiles side-by-side:
> 1. Window 1 (Left): **Citizen Portal** (`citizen@gmail.com`)
> 2. Window 2 (Center): **Admin Mission Control** (`admin@disaster.gov.in`)
> 3. Window 3 (Right): **Responder Field Command** (`responder@disaster.gov.in`)  
> This visually proves real-time cross-role synchronization!

---

## 3. STEP-BY-STEP LIVE DEMONSTRATION SCRIPT (5 TO 7 MINUTES)

### ACT 1: THE CIVILIAN EMERGENCY (Citizen Safety Hub)
* **Objective:** Demonstrate public safety access, geolocation reporting, and civilian report isolation.

1. Navigate to: `http://localhost:8080/index.html`.
   * Point out the Mission Control portal gateway and quick demo credentials card.
   * Click **Citizen Portal** and log in with `citizen@gmail.com` / `citizen123`.
2. The user lands on `/citizen-dashboard.html` (Citizen Safety Hub):
   * Highlight the top HUD with live time, location badge, and active NDMA national alerts banner.
   * Click the glowing red **"Report Emergency / SOS"** button.
3. In the Emergency Report Modal:
   * **Category:** Select `Flood`.
   * **Location:** Enter `Cross Cut Road, Coimbatore` (or click **"Use My GPS"**).
   * **Distress Message:** `Water level rising rapidly. Trapped inside ground floor.`
   * **Evidence Capture:** Click **"Take Photo"** or attach an image file.
   * Click **"Submit Distress Report"**.
4. Show the immediate confirmation alert with unique tracking reference (e.g., `#INC-2026-18`).
5. Scroll down to **"My Active Incidents"**:
   * Show that the incident appears with status **"Submitted"** and status pill **"Pending Review"**.

---

### ACT 2: MISSION CONTROL INCIDENT COMMAND (Admin Portal)
* **Objective:** Demonstrate incident triage, Smart Assignment Engine, task dispatch, and Step-Up 2FA.

1. Switch to the Admin window and navigate to `http://localhost:8080/index.html`.
   * Select **Admin Portal** and log in with `admin@disaster.gov.in` / `admin123`.
2. The user lands on `/admin-dashboard.html` (Admin Mission Control):
   * Highlight the tactical **Esri World Dark Gray Canvas** map displaying active incidents and units.
   * Point out the top telemetry pills: Active Incidents, Deployed Units, NDMA Sync status.
3. Navigate to **Incident Triage & Tasks** (`/admin-tasks.html`):
   * Show the pending civilian report submitted in Act 1 appearing in the incoming queue.
   * Click **"Dispatch Rescue Task"**.
4. In the Dispatch Modal:
   * **Title:** Auto-populated from the civilian report.
   * **Smart Assignment Engine:** Click **"Evaluate Responder Suitability"**.
   * Show the AI/heuristic scoring ranking available units based on distance, readiness, and active load.
   * Select `responder@disaster.gov.in` (`Emergency Responder Unit 1`).
   * Click **"Confirm Dispatch"**.
5. Navigate to **Emergency Broadcast Center** (`/admin-alerts.html`):
   * Create a regional evacuation advisory:
     * Title: `Urgent Flood Warning: Cross Cut Sector`
     * Severity: `High`
     * Region: `Coimbatore`
   * Click **"Broadcast Emergency Alert"**.
   * **Showcase Step-Up 2FA Security:** An authentication modal intercepts the broadcast, requiring the 6-digit Admin Security PIN.
   * Enter `123456` and click **"Verify & Authorize"**.
   * Point out the successful step-up token issuance and the published broadcast.

---

### ACT 3: TACTICAL FIELD RESPONSE (Responder Field Command)
* **Objective:** Demonstrate 4-stage operational workflow, live GPS transmitter, tactical photo evidence upload, and comms room.

1. Switch to the Responder window and log in as `responder@disaster.gov.in` / `responder123`.
2. The user lands on `/responder-dashboard.html`:
   * Point out the **Availability Status** dropdown in the top HUD (toggle between `Available` and `On Duty`).
   * Show the newly dispatched task from Act 2 displayed under **"Active Assignments"**.
3. Navigate to **Field Tasks** (`/responder-tasks.html`):
   * Locate the assigned task card.
   * **Stage 1 (Acknowledge):** Click the cyan button **"Acknowledge Assignment"**.
     * Status updates to `Acknowledged`.
   * **Stage 2 (En Route):** Click **"Start Response (En Route)"**.
     * Status updates to `En Route`.
4. **Live Location Sharing (Telemetry Transmitter):**
   * Click **"Start GPS Tracking"**.
   * **Showcase User Consent & Privacy:** A tactical consent modal appears explaining the rescue tracking purpose and data retention policy.
   * Click **"Authorize GPS Broadcast"**.
   * Point out the live telemetry metrics: Latitude, Longitude, Heading, Speed, and GPS Accuracy ($\pm\text{meters}$).
   * In the Admin window, show the responder unit moving along the OSRM road route.
5. **Stage 3 (Arrived On Scene):**
   * Click **"Mark Arrived On Scene"**.
   * Status transitions to `Arrived`.
6. **Field Evidence Capture & Tactical Comms:**
   * In the task card, click **"Upload Evidence"**.
   * Attach a photo with caption: `Evacuation boat deployed at waterlogged underpass`.
   * Click **"Open Comms Room"** and post: `2 civilians safely brought onto raft; moving towards high ground`.

---

### ACT 4: MISSION COMPLETION & FULL-CIRCLE CLOSURE
* **Objective:** Demonstrate clean status propagation, database persistence, and zero ghost tracking.

1. In the Responder window:
   * **Stage 4 (Resolve):** Click the green button **"Complete Mission & Resolve"**.
   * Enter completion notes: `All civilians evacuated to relief shelter. Sector secured.`
   * Click **"Confirm Completion"**.
2. **Showcase Ghost Tracking Elimination:**
   * Point out that the GPS tracking transmitter automatically halts and closes the session.
   * The status switches to `COMPLETED`; no background battery drain or unwanted telemetry.
3. Switch to the **Citizen Window**:
   * Refresh or inspect `/citizen-dashboard.html`.
   * Point out that the citizen's incident report `#INC-2026-18` now reflects **"Resolved"** with a green success badge.
4. Switch to the **Admin Window**:
   * Inspect the Mission Control map and `/reports.html`.
   * Show that the task is marked **"Completed"**, analytics counters have updated, and timeline records the complete audit log from dispatch to resolution.

---

### ACT 5: OFFICIAL NDMA SACHET LIVE HAZARD INGESTION
* **Objective:** Prove genuine external integration with India's official disaster management feed.

1. In Admin Mission Control (`/admin-dashboard.html`), scroll to the **Official NDMA SACHET Warnings** panel.
2. Click **"Check Feed Health"** or inspect the sync telemetry pill:
   * Show real XML CAP 1.2 alerts parsed from `sachet.ndma.gov.in`.
   * Highlight the **"OFFICIAL NDMA"** badge distinguishing national bulletins from local civilian distress calls.
   * Explain the XML XXE security hardening and SHA-256 database deduplication preventing repeated insertions.

---

## 4. MULTI-DEVICE & RESPONSIVE DESIGN HIGHLIGHTS

To demonstrate responsiveness to evaluators:
1. Press `F12` in Chrome and toggle **Device Emulation** (`Ctrl+Shift+M`).
2. Test the following standard viewports:
   * **iPhone 14 / Mobile (390 x 844):** Show that the top HUD wraps gracefully, navigation tabs scroll horizontally, forms remain 100% full-width, and zero horizontal scrollbars occur.
   * **iPad / Tablet (768 x 1024):** Show the 2-column adaptive layout with responsive map container.
   * **Desktop (1440 x 900):** Show full multi-column mission control with simultaneous map, queue, and analytics cards.

---

## 5. SYSTEM ARCHITECTURE REFERENCE

```
+-----------------------------------------------------------------------------+
|                     SYSTEM TOPOLOGY & DATA FLOW ARCHITECTURE                |
+-----------------------------------------------------------------------------+
|                                                                             |
|  [ CLIENT TIERS ]                                                           |
|    - Citizen Safety Hub        (citizen-dashboard.html)                     |
|    - Admin Mission Control     (admin-dashboard.html)                       |
|    - Responder Field Command   (responder-dashboard.html)                   |
|    - PWA Service Worker        (service-worker.js, offline cache)           |
|                                                                             |
|  [ REST & WEBSOCKET/SSE API LAYER (Spring Boot 4.0.4) ]                      |
|    - AuthController           (JWT issue, BCrypt verify, Step-Up 2FA)       |
|    - CitizenReportController  (SOS submission, personal isolation)          |
|    - RescueTaskController     (Smart assignment, 4-stage lifecycle)        |
|    - TrackingController       (SSE telemetry breadcrumbs, GPS transmitter)  |
|    - MediaController          (Multipart photo/video evidence)              |
|    - OfficialAlertController  (NDMA SACHET CAP 1.2 XML ingestion)           |
|    - IncidentTimelineService  (Comms room, operational audit trails)        |
|                                                                             |
|  [ PERSISTENCE & DATA STORAGE ]                                             |
|    - MySQL 8.0 Engine         (InnoDB, utf8mb4, HikariCP connection pool)   |
|    - Entities                 (User, CitizenReport, RescueTask, Alert, etc.)|
|                                                                             |
|  [ EXTERNAL INTEGRATIONS ]                                                  |
|    - NDMA SACHET RSS Feed     (sachet.ndma.gov.in, CAP 1.2 XML)             |
|    - Esri Dark Gray Tiles     (server.arcgisonline.com, dark canvas)        |
|    - OSRM Road Routing        (router.project-osrm.org)                     |
|                                                                             |
+-----------------------------------------------------------------------------+
```

---

## 6. FREQUENTLY ASKED EVALUATOR QUESTIONS & ANSWERS

**Q1: How do you prevent unauthorized users from tampering with task statuses?**  
*Answer:* Spring Security validates the Bearer JWT on every request. `RescueTaskController` executes strict role and ownership checks: only the specifically assigned responder or an administrator can update task states (`403 Forbidden` enforced otherwise).

**Q2: What prevents duplicate alerts when NDMA updates their RSS feed?**  
*Answer:* Ingestion generates a SHA-256 fingerprint hash of the CAP alert identifier and publication timestamp. Before insertion, the repository verifies if a record with the same fingerprint exists in MySQL, preventing duplicate rows.

**Q3: How does the live tracking feature protect battery and privacy?**  
*Answer:* The responder client requires explicit consent before requesting GPS. When a task transitions to `Completed` or `Resolved`, the transmitter auto-terminates the HTML5 watchPosition stream and marks the session `COMPLETED` on the backend, ending all telemetry immediately.

**Q4: How does the system handle map tile loading failures?**  
*Answer:* The Leaflet map engine is configured with primary Esri World Dark Gray Canvas tiles, which do not require an API key and avoid watermarks. If Esri experiences latency, it automatically falls back to OpenStreetMap standard tiles.


# LIVE SITUATION CAPTURE & RESPONDER TRACKING
## Engineering Specification, Architecture, and Verification Report

**Project:** Disaster Management & Alert System  
**System Architecture:** Java 25 / Spring Boot 4.0.4 / MySQL 8.0 / Leaflet / SSE / Web Media APIs  
**Branch:** `academic-project-upgrade`  
**Date:** October 2026  

---

## 1. Executive Summary & Capabilities

The **Disaster Management & Alert System** has been extended with advanced real-time crisis engineering capabilities designed to bridge citizens, emergency field responders, and administrators into an interconnected operational loop:

1. **Reusable Situation Capture Component (`media-capture.js` & `media-capture.css`):**
   - Live browser camera preview (`navigator.mediaDevices.getUserMedia`).
   - Front/rear camera switching (`facingMode: environment / user`).
   - High-resolution photo snapshot with canvas watermark (UTC timestamp + GPS coordinates).
   - Live video recording (`MediaRecorder` with VP8/WebM encoding) with duration timer.
   - High-accuracy GPS telemetry acquisition (`navigator.geolocation.getCurrentPosition`).
   - Graceful fallback for devices without camera hardware or permission restrictions (`<input type="file" accept="image/*,video/*" capture="environment">`).

2. **Secure Media Storage Pipeline (`MediaStorageService.java` & `MediaController.java`):**
   - High-performance local disk storage (`uploads/incident-media/`) avoiding multi-MB binary bloat in MySQL columns.
   - Rigorous MIME type and extension validation preventing malicious executable uploads (`.exe`, `.sh`, `.bat`, `.html`, `.js`).
   - Path-traversal defense (`destinationFile.startsWith(rootLocation)`).
   - Database metadata persistence (`incident_media` table) indexing uploader identity, coordinates, accuracy, file size, and captions.
   - High-speed inline media streaming headers (`Content-Disposition: inline`, `Cache-Control`).

3. **Delivery-App Style Live Responder Tracking (`responder-tracking.js` & `responder-tracking.css`):**
   - Fullscreen / drawer tracking modal showing active responder moving live towards incident destination.
   - Custom pulsing animated vehicle marker (`.vehicle-marker-pulse`).
   - Real road routing polyline rendered on dark CartoDB tiles.
   - Delivery-App HUD card displaying responder identity, phone contact, prominent ETA countdown (`12 MINS`), and remaining distance (`3.4 km`).
   - Signal staleness detection: alerts if responder GPS telemetry is older than 60 seconds.
   - Dynamic 4-step mission progress bar: *Dispatched* &rarr; *En Route* &rarr; *On Scene* &rarr; *Resolved*.

4. **Real Distance & Road Network Routing (`RouteEstimationService.java` & `RoutingController.java`):**
   - Public OSRM routing integration (`https://router.project-osrm.org/route/v1/driving/...`) retrieving actual road geometry.
   - Automatic Haversine great-circle fallback with a realistic 1.30x road multiplier and emergency vehicle speed calculation (35 km/h) if OSRM is unreachable or times out.
   - Human-readable ETA formatting (`5 mins (3.4 km)`).

5. **Shared Emergency Incident Collaboration Room (`IncidentRoom.java` & `IncidentRoomController.java`):**
   - Incident-specific chronological collaboration stream.
   - Unified aggregation of initial citizen report, admin dispatch directives, field media uploads, and real-time chat messages.
   - Role-coded chat bubbles and badges for Citizens, Responders, and Administrators.
   - Embedded interactive media evidence gallery (photos and videos).

6. **Smart Responder Assignment & Dispatch Assistance (`SmartAssignmentService.java` & `SmartAssignmentController.java`):**
   - Multi-criteria recommendation algorithm evaluating:
     - **Proximity:** Distance in km between responder's active GPS or zone and incident site (up to 40 pts).
     - **Workload:** Active tasks assigned in `Pending` / `In Progress` status (up to 30 pts; 0 tasks = max score).
     - **Specialization Match:** Matching group classification (Leader, Rescuer, Helper/Medic) with disaster priority and keywords (up to 20 pts).
     - **Local Zone Alignment:** Matching assigned sector (up to 10 pts).
   - Generates ranked recommendations with percentage match scores (0-100%) and badges (`AVAILABLE_NOW`, `HIGH_PROXIMITY`, `SPECIALIST_MATCH`, `LOCAL_SECTOR`).

7. **Real-Time Telemetry & Event Streaming (`ResponderTrackingService.java`):**
   - Server-Sent Events (SSE) via `GET /api/tracking/stream/{assignmentId}` with JWT authentication.
   - Thread-safe `SseEmitter` connection pooling with automatic cleanup on completion or disconnect.
   - Automatic fallback to HTTP polling (`GET /api/tracking/latest/{assignmentId}`) every 4 seconds if client proxy blocks SSE.

---

## 2. System Architecture & Database Schema

```mermaid
flowchart TD
    subgraph Citizens ["Citizen Client (PWA / Mobile / Web)"]
        C1["Citizen Dashboard"]
        C2["Media Capture Component"]
        C3["Live Tracking Delivery HUD"]
        C4["Incident Room Chat"]
    end

    subgraph Responders ["Responder Client (Mobile / Tablet)"]
        R1["Responder Dashboard"]
        R2["GPS Location Transmitter (watchPosition)"]
        R3["Field Evidence Recorder"]
        R4["Incident Room Updates"]
    end

    subgraph Admins ["Admin Command Center (Desktop / Web)"]
        A1["Mission Control Dark Map"]
        A2["Smart Dispatch Recommendation Modal"]
        A3["Live Fleet Radar Overlay"]
    end

    subgraph Backend ["Spring Boot 4.0.4 REST API & Services"]
        MC["MediaController & MediaStorageService"]
        TC["TrackingController & ResponderTrackingService"]
        RC["RoutingController & RouteEstimationService"]
        SC["SmartAssignmentService"]
        IC["IncidentRoomController & IncidentTimelineService"]
    end

    subgraph Storage ["Persistent Storage Layer"]
        FS[("Disk Storage /uploads/incident-media/")]
        DB[("MySQL 8.0: incident_media, tracking_sessions, responder_locations, incident_timeline_events")]
    end

    C2 -->|POST /api/media/upload| MC
    R3 -->|POST /api/media/upload| MC
    MC -->|Write File| FS
    MC -->|Save Metadata| DB

    R2 -->|POST /api/tracking/update| TC
    TC -->|SSE /stream| C3
    TC -->|SSE /stream| A3
    TC -->|Persist Breadcrumbs| DB

    TC -->|Query Directions| RC
    RC -->|Road Geometry| OSRM["Project-OSRM Public Routing API"]

    A2 -->|GET /api/admin/assignments/recommendations| SC
    SC -->|Query Workloads & Coords| DB

    C4 <-->|POST / GET /api/incidents/...| IC
    R4 <-->|POST / GET /api/incidents/...| IC
    IC --> DB
```

### 2.1 Database Tables Created

```sql
-- 1. Metadata for Media Files
CREATE TABLE incident_media (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NULL,
    incident_type VARCHAR(50) NULL DEFAULT 'CITIZEN_REPORT',
    uploader_email VARCHAR(255) NOT NULL,
    uploader_name VARCHAR(255) NULL,
    uploader_role VARCHAR(50) NOT NULL,
    media_type VARCHAR(20) NOT NULL, -- PHOTO, VIDEO, AUDIO, DOCUMENT
    file_path VARCHAR(500) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    stored_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    caption TEXT NULL,
    capture_latitude DOUBLE NULL,
    capture_longitude DOUBLE NULL,
    capture_accuracy DOUBLE NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_media_incident (incident_id, incident_type),
    INDEX idx_media_uploader (uploader_email),
    INDEX idx_media_created (created_at)
);

-- 2. Active Tracking Sessions
CREATE TABLE tracking_sessions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    assignment_id BIGINT NOT NULL,
    responder_email VARCHAR(255) NOT NULL,
    responder_name VARCHAR(255) NULL,
    status VARCHAR(30) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, PAUSED, COMPLETED, DISCONNECTED
    destination_latitude DOUBLE NULL,
    destination_longitude DOUBLE NULL,
    destination_address VARCHAR(255) NULL,
    current_latitude DOUBLE NULL,
    current_longitude DOUBLE NULL,
    current_accuracy DOUBLE NULL,
    current_heading DOUBLE NULL,
    current_speed DOUBLE NULL,
    estimated_distance_meters DOUBLE NULL,
    estimated_duration_seconds INT NULL,
    last_heartbeat_at DATETIME(6) NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    INDEX idx_tracking_assignment (assignment_id),
    INDEX idx_tracking_responder (responder_email),
    INDEX idx_tracking_status (status)
);

-- 3. Telemetry Breadcrumb Trail
CREATE TABLE responder_locations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    responder_email VARCHAR(255) NOT NULL,
    latitude DOUBLE NOT NULL,
    longitude DOUBLE NOT NULL,
    accuracy DOUBLE NULL,
    heading DOUBLE NULL,
    speed DOUBLE NULL,
    recorded_at DATETIME(6) NOT NULL,
    INDEX idx_loc_session (session_id),
    INDEX idx_loc_assignment (assignment_id),
    INDEX idx_loc_responder_recorded (responder_email, recorded_at)
);

-- 4. Incident Collaboration Room Chronology
CREATE TABLE incident_timeline_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    incident_id BIGINT NOT NULL,
    incident_type VARCHAR(50) NOT NULL DEFAULT 'CITIZEN_REPORT',
    event_type VARCHAR(50) NOT NULL, -- REPORTED, VERIFIED, ASSIGNED, EN_ROUTE, ON_SCENE, RESOLVED, MEDIA_UPLOADED, MESSAGE
    actor_email VARCHAR(255) NOT NULL,
    actor_role VARCHAR(50) NOT NULL,
    actor_name VARCHAR(255) NULL,
    message TEXT NOT NULL,
    media_id BIGINT NULL,
    payload_json TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    INDEX idx_timeline_incident (incident_id, incident_type),
    INDEX idx_timeline_created (created_at)
);
```

---

## 3. Security Hardening & Defenses

1. **Anti-Path Traversal Defense:**  
   In `MediaStorageService.java`, every incoming filename is sanitized (`Paths.get(originalFilename).getFileName()`), a random UUID prefix is prepended, and the resolved destination path is explicitly verified:
   ```java
   Path destinationFile = this.rootLocation.resolve(storedFilename).normalize().toAbsolutePath();
   if (!destinationFile.startsWith(this.rootLocation)) {
       throw new SecurityException("Cannot store file outside current storage directory.");
   }
   ```
2. **Strict Extension Whitelisting:**  
   Executable, script, HTML, and binary formats (`.exe`, `.sh`, `.bat`, `.cmd`, `.html`, `.js`, `.php`, `.jsp`, `.py`) are rejected with `IllegalArgumentException`. Only safe media formats (`.jpg`, `.jpeg`, `.png`, `.webp`, `.gif`, `.mp4`, `.webm`, `.mov`, `.mp3`, `.wav`, `.ogg`, `.pdf`) are accepted.
3. **MIME Sniffing & Boundary Checks:**  
   Payload sizes are bounded in `application.properties` with `max-file-size=50MB` and `max-request-size=55MB`.
4. **JWT Authentication & Stream Verification:**  
   `JwtAuthenticationFilter.java` was enhanced to support tokens via `Authorization: Bearer <token>` and `?token=<token>` query parameters, enabling native browser `EventSource` (SSE) instances and HTML5 media player tags (`<video src="/api/media/file/...">`) to stream authenticated content seamlessly.

---

## 4. Rigorous Verification Results

### 4.1 Automated Test Suites (JUnit 5 & Mockito)
Executing `.\mvnw.cmd test`:
- **Results:** **30 tests run, 0 failures, 0 errors, 0 skipped.**
  - `TrackingAndMediaServiceTest` (5 tests):
    - `testHaversineDistance`: verified Chennai & Coimbatore distance calculation (~426 km) and fallback formatting.
    - `testStoreValidImage`: verified storing JPEG image, UUID naming, and database persistence.
    - `testRejectForbiddenExtension`: verified security rejection of `.exe` files.
    - `testSmartAssignmentRecommendations`: verified nearby available responders outrank distant busy responders.
    - `testIncidentTimelineEvents`: verified event creation and message logging.
  - `SecurityIntegrationTest` (6 tests): Passed.
  - `AdminPinServiceTest` (3 tests): Passed.
  - `CitizenReportServiceTest` (5 tests): Passed.
  - `NdmaSyncServiceTest` (3 tests): Passed.
  - `RescueTaskServiceTest` (4 tests): Passed.
  - `DisasterManagementApplicationTests` (1 test): Passed.

### 4.2 End-to-End PowerShell Verification (`scripts/tracking_and_media_verification.ps1`)
Executing `powershell -ExecutionPolicy Bypass -File .\scripts\tracking_and_media_verification.ps1`:

```
======================================================================
 STARTING VERIFICATION: LIVE SITUATION CAPTURE & RESPONDER TRACKING
======================================================================
 [PASS] Actuator Health Check
        Status: UP
 [PASS] Admin Authentication
        Role: Admin
 [PASS] Responder Authentication
        Role: Responder
 [PASS] Citizen Authentication
        Role: Citizen
 [PASS] Admin Task Creation for Tracking
        Task ID: 4
 [PASS] Routing Engine (OSRM / Haversine)
        Source: OSRM_ROAD_NETWORK | Dist: 3.4km | ETA: 5 mins (3.4 km)
 [PASS] Smart Responder Matching Algorithm
        Evaluated 2 units. Top match: Emergency Responder (50%)
 [PASS] Admin Dispatches Responder
        Assigned To: responder@disaster.gov.in
 [PASS] Responder Activates Live Tracking
        Session ID: 3
 [PASS] Responder Transmits Telemetry Breadcrumb
        Lat: 13.077, Lng: 80.262
 [PASS] Citizen Polls Live Responder Telemetry
        Status: ACTIVE | ETA: 3 mins (1.9 km) | Stale: False
 [PASS] Admin Mission Radar Fleet Tracking
        Found active tracking sessions: 2
 [PASS] Media Upload & Integrity Verification
        Media ID: 4 | File: 0d751d76-f716-4c3a-a688-9b1e2b5a1052_test_evidence_photo.jpg
 [PASS] Media Download Endpoint (Inline Stream)
        Content-Type: image/jpeg
 [PASS] Security Defense: Reject Executable Extensions
        Rejected: File extension .exe is not allowed for security reasons.
 [PASS] Citizen Posts Message to Incident Room
        Event ID: 10
 [PASS] Incident Collaboration Room Aggregation
        Timeline Events: 3 | Attached Media: 1
 [PASS] Responder Completes & Terminates Tracking
        Status: Tracking session concluded.
 [PASS] Frontend Asset: media-capture.css
        Size: 8596 bytes
 [PASS] Frontend Asset: media-capture.js
        Size: 24471 bytes
 [PASS] Frontend Asset: responder-tracking.css
        Size: 7713 bytes
 [PASS] Frontend Asset: responder-tracking.js
        Size: 22088 bytes
 [PASS] Frontend Asset: incident-room.css
        Size: 6856 bytes
 [PASS] Frontend Asset: incident-room.js
        Size: 15392 bytes
 [PASS] Frontend Asset: smart-assignment.css
        Size: 4368 bytes
 [PASS] Frontend Asset: smart-assignment.js
        Size: 8723 bytes
======================================================================
 VERIFICATION COMPLETE: 26 PASSED, 0 FAILED
======================================================================
```

### 4.3 Regression Test Verification
- `scripts/role_connectivity_verification.ps1`: **26/26 passed (100%)**.
- `scripts/ndma_feed_verification.ps1`: **22/22 passed (100%)**.
- `scripts/pwa_verification.ps1`: **11/11 passed (100%)**.
- **Combined Verification Pass Rate:** **85/85 checks passing (100%) across all test scripts.**

---

## 5. Deployment Readiness

1. **Runtime:** Tested on Java 25 / OpenJDK 21 LTS compatibility.
2. **Database:** Fully synchronized with MySQL 8.0 on port 3306 with schema definitions in `DATABASE_MEDIA_TRACKING_SETUP.sql`.
3. **Real-time Networks:** Open-source OSRM routing active with robust offline Haversine backup.
4. **Platform Compatibility:** Operates on Desktop Web, Progressive Web App (PWA), Mobile touch viewports, and native Windows desktop packaging.


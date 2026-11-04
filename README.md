# Integrated Disaster Reporting and Emergency Response Coordination System

[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)]()
[![Java](https://img.shields.io/badge/Java-21%20LTS-orange.svg)]()
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.0%2F3.4.1-brightgreen.svg)]()
[![Database](https://img.shields.io/badge/MySQL-8.0-blue.svg)]()
[![Security](https://img.shields.io/badge/Security-BCrypt%20%2B%20JWT%20%2B%202FA%20PIN-red.svg)]()
[![NDMA Ingestion](https://img.shields.io/badge/NDMA%20SACHET-Live%20CAP%20Feed-cyan.svg)]()

---

## 1. Project Title
**Integrated Disaster Reporting and Emergency Response Coordination System**  
*An Academic Capstone & Research Platform for Multi-Role Emergency Management, Real-Time Telemetry Tracking, and Public Safety Advisory.*

---

## 2. Project Overview
The **Integrated Disaster Reporting and Emergency Response Coordination System** is a unified full-stack web and Progressive Web Application (PWA) designed to bridge critical communication gaps during catastrophic natural disasters and civilian emergencies. Built on Java 21, Spring Boot, MySQL 8.0, and modern GIS mapping libraries, the platform interconnects civilians in distress, municipal emergency command authorities, and active field response units into a synchronized operational network.

---

## 3. Problem Statement
During severe disaster events (cyclones, urban flash flooding, earthquakes, structural collapses, and fire outbreaks), traditional emergency communication channels experience heavy degradation:
- **Civilian Vulnerability:** Trapped citizens struggle to convey precise distress locations, photographic evidence, and severity metrics to authorities.
- **Fragmented Dispatch:** Municipal control centers lack real-time visibility into responder availability, proximity, and mission status, leading to uncoordinated deployments.
- **Stale Operational Telemetry:** Dispatched units often operate with static coordinates, lacking continuous routing, victim proximity updates, and mutual situational awareness.
- **Delayed Warning Broadcasts:** Official national meteorological warnings from agencies such as the National Disaster Management Authority (NDMA) are frequently siloed and do not reach affected populations in actionable formats.

---

## 4. Proposed Solution
This system provides an end-to-end, multi-tier disaster coordination ecosystem:
- **Instant Distress Signaling:** Dual-action Emergency SOS with local audible acoustic warble alarms and hardware GPS beacon transmissions.
- **Smart Dispatch & Assignment:** Algorithmic recommendation engine considering responder distance, zone specialization, and current active workload.
- **Live Hardware GPS Telemetry:** Continuous responder telemetry via `navigator.geolocation.watchPosition()`, streamed to MySQL and visualized on Leaflet tactical radars with OSRM driving routes.
- **Automated Warning Ingestion:** Live background synchronization with official NDMA SACHET CAP 1.2 RSS feeds, cataloging actionable alerts across Indian states.
- **Emergency Survival Library:** Built-in protocol video guides (NDMA, NDRF, WHO, NFPA) providing immediate offline-accessible survival instructions.

---

## 5. Three User Roles

### 1. Citizen (`ROLE_Citizen`)
- **Distress Reporting:** Submit geotagged incident reports with disaster classification, description, and photo/video evidence.
- **Emergency SOS:** Toggle local device audible warble siren or transmit instant high-priority SOS distress signals with locked GPS coordinates.
- **Safety Radar Map:** View designated relief shelters, active local hazards, and track assigned responders live as they approach.
- **Disaster Survival Library:** Browse curated NDMA/NDRF instructional videos on evacuation, flood survival, and fire escape.

### 2. Emergency Responder (`ROLE_Responder`)
- **Field Operations Console:** View assigned rescue missions filtered strictly to the responder's authorized operational zone.
- **Tactical GPS Telemetry:** Broadcast continuous hardware GPS coordinates, speed, heading, and battery telemetry to command centers.
- **Mission Lifecycle Management:** Advance operations through a strict 5-stage lifecycle (`Assigned` ➔ `Acknowledged` ➔ `En Route` ➔ `Arrived` ➔ `Completed`).
- **Duty Availability:** Switch between `Available`, `On Duty`, and `Unavailable` duty modes with workload safeguards.

### 3. Disaster Command Administrator (`ROLE_Admin`)
- **Central Live Operations Map:** Monitor all active incidents, deployed rescue personnel, and real-time movement vectors across regional sectors.
- **Incident Dispatch & Smart Assignment:** Review civilian distress submissions and assign optimal responders using AI-assisted proximity heuristics.
- **Official Broadcasts & Ingestion:** Ingest and publish NDMA SACHET alerts and issue municipal evacuation advisories.
- **Security & Data Hygiene:** Step-Up 2FA Security PIN verification for high-impact actions, administrative audit trails, and automatic deduplication of redundant incident reports.

---

## 6. Major Features
- **Dual-Action Emergency SOS:** Local Web Audio API 600Hz–1200Hz synthesized siren + hardware GPS backend transmission.
- **Hardware GPS Tracking:** Real coordinates from device GPS, stored in `responder_locations` and synchronized across Admin Radar and Citizen HUD.
- **Strict Role-Based Isolation:** Citizens can only view their own reports; Responders are strictly isolated to their assigned tasks; Admin operations require administrative authority.
- **Step-Up 2FA Security PIN:** Admin actions (broadcasting alerts, assigning zones, system maintenance) require a 6-digit cryptographic PIN with lockout defenses.
- **NDMA SACHET Integration:** Periodic RSS ingestion from `https://sachet.ndma.gov.in`, automatically converting XML Common Alerting Protocol items into relational records.
- **Emergency Survival Video Library:** 14 verified multi-hazard video guides categorized into 10 disaster categories with multilingual support.
- **Public Evidence Media Streaming:** Fast streaming of incident evidence images and video clips without 401 token blocking on browser `<img>` elements.
- **Automated Data Hygiene:** Spatial clustering identifies and deduplicates duplicate emergency submissions within 150 meters.

---

## 7. Technology Stack

| Layer | Component | Technologies Used |
| :--- | :--- | :--- |
| **Backend** | Framework | Java 21 LTS, Spring Boot 4.0 / 3.4.1, Spring MVC, Spring Data JPA |
| **Security** | Auth & AuthZ | Spring Security, JWT (HMAC SHA-256), BCrypt Password Hashing, 2FA PIN |
| **Database** | Relational DB | MySQL 8.0 (Production & Local Development), H2 Database (Test Suite) |
| **Connection Pool**| Pooling | HikariCP with prepared statement caching |
| **Frontend** | UI / PWA | Semantic HTML5, CSS3 (Dark Tactical Theme), Vanilla JavaScript (ES6+) |
| **Mapping & GIS** | Navigation | Leaflet.js 1.9.4, OpenStreetMap CartoDB Tiles, OSRM Routing Engine |
| **Audio Engine** | Siren Synthesizer| Web Audio API (Dual-frequency oscillator warble) |
| **Integrations** | External Data | NDMA SACHET RSS (Rome XML Tools), Open-Meteo & IMD Weather APIs |
| **Build & Deploy** | DevOps | Apache Maven 3.9+, Docker, Docker Compose |

---

## 8. System Architecture

```
                      +------------------------------------------+
                      |   Civilian / Responder / Admin Clients   |
                      |   (Web Browsers, Mobile PWA, Desktop)    |
                      +--------------------+---------------------+
                                           |
                                   HTTPS / REST / JWT
                                           |
                                           v
                      +------------------------------------------+
                      |       Spring Security Filter Chain       |
                      |        JwtAuthenticationFilter           |
                      +--------------------+---------------------+
                                           |
              +----------------------------+----------------------------+
              |                            |                            |
              v                            v                            v
    [ Citizen Controller ]       [ Responder Controller ]     [ Admin Controllers ]
    - POST /api/citizen-reports  - /api/responder/location/*  - /api/tasks/*
    - GET /my-reports            - /api/tasks/my-tasks        - /api/admin/data/*
    - GET /api/tracking/latest   - PUT /tasks/{id}/status     - /api/admin/assignments
              |                            |                            |
              +----------------------------+----------------------------+
                                           |
                                           v
                      +------------------------------------------+
                      |            Service Layer                 |
                      | - ResponderTrackingService & OSRM Router |
                      | - CitizenReportService & TimelineEngine  |
                      | - SmartAssignmentService & Proximity     |
                      | - NdmaSyncService (Scheduled Cron)       |
                      | - AdminPinService (2FA Step-Up Engine)   |
                      +--------------------+---------------------+
                                           |
                                   Spring Data JPA
                                           |
                                           v
                      +------------------------------------------+
                      |            MySQL 8.0 Database            |
                      |   (users, citizen_reports, rescue_tasks, |
                      |    responder_locations, tracking_sessions|
                      |    official_alerts, emergency_videos)    |
                      +------------------------------------------+
```

---

## 9. Project Folder Structure

```text
├── src/
│   ├── main/
│   │   ├── java/com/disaster/management/
│   │   │   ├── config/              # SecurityConfig, WebMvcConfig
│   │   │   ├── controller/          # REST Controllers (Auth, Alerts, Tasks, Location, Videos, etc.)
│   │   │   ├── entity/              # JPA Entities (User, CitizenReport, RescueTask, etc.)
│   │   │   ├── repository/          # Spring Data JPA Repositories
│   │   │   ├── security/            # JwtUtil, JwtAuthenticationFilter
│   │   │   ├── service/             # Business Logic & External Integrations
│   │   │   ├── DataInitializer.java # Database Seeding & Startup Validation
│   │   │   └── DisasterManagementApplication.java
│   │   └── resources/
│   │       ├── static/              # Frontend HTML, CSS, JS, PWA Assets, Icons
│   │       │   ├── admin-dashboard.html
│   │       │   ├── citizen-dashboard.html
│   │       │   ├── responder-dashboard.html
│   │       │   ├── emergency-videos.html
│   │       │   ├── js/emergency-alert.js
│   │       │   └── ...
│   │       ├── application.properties
│   │       ├── application.properties.example
│   │       └── DATABASE_MEDIA_TRACKING_SETUP.sql
│   └── test/
│       ├── java/com/disaster/management/ # JUnit 5 & MockMvc Automated Tests
│       └── resources/                    # Test-specific isolated H2 configuration
├── docs/                                # Technical Specifications, Architecture, Security Docs
├── loading-screen/                      # Custom Role Preloader Component
├── login-designs/                       # Alternative UI Theme Variations
├── scripts/                             # Node.js & PowerShell End-to-End Verification Suites
├── DATABASE_SETUP.sql                   # MySQL Baseline Schema & Initial Data
├── Dockerfile                           # Production Container Build
├── docker-compose.yml                   # Multi-Container Compose Configuration
├── pom.xml                              # Maven Project Dependencies
└── README.md                            # Main Project Documentation
```

---

## 10. Database Setup Instructions

1. Ensure MySQL Server 8.0 is running locally on port `3306`.
2. Connect to MySQL via MySQL Workbench or command line:
   ```bash
   mysql -u root -p
   ```
3. Execute the setup script:
   ```sql
   SOURCE DATABASE_SETUP.sql;
   ```
4. Verify the database:
   ```sql
   USE disaster_management;
   SHOW TABLES;
   ```

---

## 11. Backend Configuration

Configuration templates are provided in `.env.example` and `src/main/resources/application.properties.example`.

Create a local configuration or set environment variables:
```bash
# Database Credentials
export DB_URL="jdbc:mysql://localhost:3306/disaster_management?createDatabaseIfNotExist=true&useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
export DB_USERNAME="root"
export DB_PASSWORD="your_mysql_password"

# JWT Security
export JWT_SECRET="your_secure_256_bit_jwt_secret_key_minimum_32_characters"
export JWT_EXPIRATION_MS="86400000"

# Server Port (Defaults to 8080)
export PORT="8080"
```

---

## 12. How to Run the Spring Boot Application

### Using Maven Wrapper (Recommended)

**On Windows:**
```powershell
.\mvnw.cmd clean spring-boot:run
```

**On Linux / macOS:**
```bash
./mvnw clean spring-boot:run
```

### Using Docker Compose
```bash
docker compose up --build -d
```

Application will start and listen on **`http://localhost:8080`**.

---

## 13. How to Access the Three Dashboards

Navigate to **`http://localhost:8080`** in any modern web browser.

### Pre-Configured Demonstration Accounts

| Role | Email Address | Password | Admin Security PIN | Assigned Zone / Coverage |
| :--- | :--- | :--- | :--- | :--- |
| **Admin** | `admin@disaster.gov.in` | `admin123` | **`123456`** | State Command HQ (Chennai) |
| **Responder 1** | `responder@disaster.gov.in` | `responder123` | N/A | Coimbatore Zone |
| **Responder 2** | `responder2@disaster.gov.in` | `responder123` | N/A | Tiruchirappalli Zone |
| **Citizen 1** | `citizen@gmail.com` | `citizen123` | N/A | Coimbatore Resident |
| **Citizen 2** | `citizen2@gmail.com` | `citizen123` | N/A | Chennai Resident |

- **Citizen Dashboard:** `/citizen-dashboard.html`
- **Responder Dashboard:** `/responder-dashboard.html`
- **Admin Command Center:** `/admin-dashboard.html`
- **Emergency Survival Videos:** `/emergency-videos.html`

---

## 14. NDMA SACHET Integration Details

The system includes an automated background integration service ([`NdmaSyncService.java`](file:///m:/PROJECT/backup/demo/src/main/java/com/disaster/management/service/NdmaSyncService.java)):
- **Source:** Official NDMA SACHET National RSS Feed (`https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml`).
- **Standard:** Complies with OASIS Common Alerting Protocol (CAP v1.2).
- **Execution:** Runs automatically every 5 minutes (`@Scheduled(fixedRate = 300000)`).
- **Processing:** Extracts headline, severity (`Extreme`, `Severe`, `Moderate`), affected state/district polygons, and safety instructions, inserting new alerts into `official_alerts` while discarding duplicates.
- **Monitoring:** Live feed health and sync status can be queried at `GET /api/official-alerts/status`.

---

## 15. GPS and Location Functionality

### Implemented & Fully Operational
1. **Device GPS Acquisition:** Citizens and responders obtain precise latitude and longitude via browser `navigator.geolocation` APIs with high-accuracy modes.
2. **Continuous Location Telemetry:** Responders stream live location updates via `POST /api/responder/location/update`, recorded in MySQL table `responder_locations`.
3. **Driving Route & ETA Estimation:** Real routing calculation via OSRM engine (`RouteEstimationService.java`), yielding actual driving distances and travel durations.
4. **Live Assigned Vehicle Tracking:** Citizen Safety Map polls live responder telemetry and displays moving marker, route polyline, and dynamic ETA countdown.
5. **Administrative Sector Radar:** Admin Live Operations Map queries `/api/tracking/active` to visualize all concurrent active responder missions.

### Scope for Future Refinements
- **Offline Dead Reckoning:** Accelerometer/gyroscope based position extrapolation during total cellular/GPS blackout.
- **WebSocket Streaming:** Native STOMP/WebSocket pub-sub for sub-second telemetry delivery to replace rapid HTTP polling under massive scale.

---

## 16. Testing Instructions

### Backend JUnit Test Suite
The project includes unit and integration tests executing against an isolated H2 in-memory database:
```powershell
.\mvnw.cmd test
```

### End-to-End Master Functionality Suite
A complete 39-step verification suite verifies authentication, SOS dispatch, GPS telemetry, role isolation, and API routes across all three roles:
```bash
node scripts/verify_fullstack_audit_suite.js
```
*Expected Result:* `FULL-STACK AUDIT COMPLETE: 39 OF 39 CHECKS PASSED (100%)`.

---

## 17. Future Enhancements
- **LoRaWAN / Satellite Mesh Integration:** Emergency offline peer-to-peer packet relays when cellular base stations are flooded or de-energized.
- **AI Damage Assessment:** Computer vision analysis of citizen-uploaded incident photographs to automatically categorize structural damage severity.
- **Multilingual Voice Synthesis:** Text-to-speech audio broadcast of NDMA warnings in regional Indian languages (Tamil, Hindi, Telugu, Malayalam).

---

## 18. Academic Project Information
- **Course / Capstone:** B.E. / B.Tech Computer Science & Engineering Final Year Project
- **Domain:** Disaster Management, Geographic Information Systems (GIS), Distributed Systems, Cloud Architecture
- **Author / Lead Developer:** Matheesh Kumar (`Matheeshkumar368`)
- **Repository:** [https://github.com/Matheeshkumar368/Integrated-Disaster-Reporting-and-Emergency-Response-System.git](https://github.com/Matheeshkumar368/Integrated-Disaster-Reporting-and-Emergency-Response-System.git)
- **Status:** Complete, Functionally Verified, and Ready for Evaluation.

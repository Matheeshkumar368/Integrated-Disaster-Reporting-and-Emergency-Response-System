# Disaster Management & Alert System - Architecture Documentation

## 1. System Overview

The **Disaster Management & Alert System** is a mission-critical web application designed for emergency crisis coordination, multi-hazard disaster warning broadcasts, citizen incident reporting, and responder task dispatch.

```
+-----------------------------------------------------------------------------------+
|                                 CLIENT TIER                                       |
|  [Citizen Dashboard]      [Responder Rescue View]       [Admin Command Center]    |
|   (Geolocation & Media)    (Task Dispatch & Status)      (Step-Up 2FA & Overview) |
+------------------------------------------+----------------------------------------+
                                           | HTTPS / REST / JWT
                                           v
+-----------------------------------------------------------------------------------+
|                             SPRING BOOT APPLICATION TIER                          |
|                                                                                   |
|  [Spring Security Filter Chain]                                                   |
|    |-- CORS & Stateless Session Configuration                                     |
|    +-- JwtAuthenticationFilter (Token Parsing & SecurityContext Population)       |
|                                                                                   |
|  [REST Controllers]                                                               |
|    |-- AuthController (Login, Register, Admin PIN 2FA, Profile)                   |
|    |-- AlertController (Disaster Alerts, Broadcast Overrides)                     |
|    |-- CitizenReportController (Citizen Submissions & Status Tracking)            |
|    |-- RescueTaskController (Task Assignment, Status Lifecycle)                  |
|    |-- IncidentReportController (Field Reports, CSV Safety Export)                |
|    |-- DisasterController (Disaster Feeds, Open-Meteo Weather with Fallbacks)     |
|    +-- MessageController (In-App Coordination Notifications)                      |
|                                                                                   |
|  [Service & Business Logic Tier]                                                  |
|    |-- AdminPinService (BCrypt PIN Hashing, Rate Limiting, Audit Trail)           |
|    |-- AlertService (Multi-hazard Alert Propagation & Automatic Task Generation)  |
|    |-- CitizenReportService (Incident Lifecycle Management)                       |
|    |-- RescueTaskService (Zone-based Responder Task Coordination)                 |
|    +-- DisasterService (Async NDMA RSS Ingestion & Deduplication)                 |
+------------------------------------------+----------------------------------------+
                                           | JDBC / Spring Data JPA
                                           v
+-----------------------------------------------------------------------------------+
|                                  PERSISTENCE TIER                                 |
|  [Production]: MySQL 8.0 Engine (InnoDB, Foreign Keys, Compound Indexes)          |
|  [Testing]:    H2 In-Memory Database (Isolated Test Execution, Zero Contamination)|
+-----------------------------------------------------------------------------------+
```

---

## 2. Component Design & Roles

### 2.1 Citizen Tier
- **Authentication:** Self-registration and login with BCrypt-hashed passwords.
- **Reporting:** Submit real-time emergency reports with text description, location/zone, and Base64-encoded camera photos directly to `/api/citizen-reports`.
- **Transparency:** Follow status progression (`Pending` -> `Acknowledged` -> `Resolved`) on personal submissions via `GET /api/citizen-reports/my-reports`.
- **Alerts:** Real-time awareness of active government disaster broadcasts.
- **Emergency Sound Siren:** Web Audio synthesizer emitting loud acoustic alarm for individuals unable to verbally call for help.

### 2.2 Responder Tier
- **Registration & Profiles:** Public registration for certified emergency personnel with specialized classifications (`Leader`, `Rescuer`, `Medical`).
- **Rescue Operations:** Real-time visibility into pending citizen rescue requests and disaster photos (`GET /api/citizen-reports/pending`).
- **Actioning:** Acknowledge incidents (`PUT /api/citizen-reports/{id}/acknowledge`), which triggers citizen notifications and dispatches response teams.
- **Task Management:** Execute zone-assigned rescue missions (`/api/tasks/my-tasks`) with status tracking (`Pending` -> `In Progress` -> `Completed`).

### 2.3 Administrator Tier
- **Two-Factor Protection:** High-impact operations protected by secondary 6-digit **Admin Security PIN** step-up authorization with 15-minute validity tokens.
- **Broadcast Control:** Issue regional disaster alerts with automatic generation and dispatch of rescue tasks to responders stationed in affected zones.
- **Responder Zone Management:** Allocate responders to zones (`Chennai`, `Coimbatore`, `Madurai`, `Tiruchirappalli`, `Salem`, `Ooty`).
- **Security Audit Trail:** Immutable logging of all administrative actions with actor email, action code, timestamp, IP address, and outcome.
- **Data Export:** Export structured incident history to CSV for academic and governmental review.

---

## 3. External Integrations & Fault Tolerance

1. **National Disaster Management Authority (NDMA) RSS Feed:**
   - Ingested asynchronously upon startup and periodically without blocking Tomcat readiness.
   - Guarded by 5-second socket connection and read timeouts.
   - In-memory $O(1)$ key set prevents $N+1$ query degradation during ingestion.
2. **Open-Meteo Weather API:**
   - Provides live meteorological indicators (temperature, apparent temperature, precipitation) for Tamil Nadu disaster zones.
   - Enforces 4-second connection timeouts with structured fallback estimates if the external weather service is unavailable or rate-limited.


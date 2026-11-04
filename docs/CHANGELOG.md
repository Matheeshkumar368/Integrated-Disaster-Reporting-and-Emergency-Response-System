# Academic Upgrade & Technical Audit Changelog

## [2.0.0] - Academic Milestone Upgrade & Security Hardening (October 2026)

### Added
- **Admin Step-Up Security (PIN 2FA):**
  - Added BCrypt-hashed administrative security PIN mechanism (`users.admin_pin`, `users.admin_pin_enrolled`).
  - Added enrollment endpoint (`POST /api/auth/admin/pin/enroll`) requiring primary password re-authentication.
  - Added verification endpoint (`POST /api/auth/admin/pin/verify`) issuing short-lived, scoped step-up JWT tokens (15-min expiry).
  - Added strict rate limiting and automated 15-minute account lockout upon 3 consecutive invalid PIN attempts.
  - Created `admin_audit_logs` entity, repository, and UI modal viewer for full accountability.
  - Added Step-Up PIN modal on `admin-dashboard.html` for high-severity broadcasts and zone assignments.
- **Automated Test Architecture:**
  - Configured in-memory H2 database test profile (`src/test/resources/application.properties`) preventing test contamination of the live MySQL database.
  - Implemented 22 comprehensive unit and integration tests across services, controllers, Spring Security, and cryptographic tokens.
- **Reporting & Data Export:**
  - Added CSV export endpoint (`GET /api/reports/export/csv`) for verified disaster incident logs.
  - Connected citizen dashboard report submission directly to Spring Boot backend database.
  - Added citizen personal report retrieval endpoint (`GET /api/citizen-reports/my-reports`).
- **Production Deployment Readiness:**
  - Added multi-stage `Dockerfile` based on Eclipse Temurin OpenJDK 21 with non-root runtime security.
  - Added `docker-compose.yml` for unified app and MySQL 8.0 orchestration with volume persistence and health checks.
  - Added `.env.example` environment template.
  - Integrated Spring Boot Actuator (`/actuator/health`, `/actuator/info`).

### Fixed
- **Critical Data Loss:** Removed destructive `deleteAll()` calls in `DataInitializer.java`. Replaced with idempotent checks (`count() == 0`) so restarts never wipe out existing users, alerts, or disasters.
- **Severe Authorization Bypass:** Fixed `SecurityConfig.java` which previously permitted all requests (`.anyRequest().permitAll()`). Added `JwtAuthenticationFilter` extending `OncePerRequestFilter` to populate `SecurityContextHolder`.
- **Token Parsing Exceptions:** Eliminated naked `authHeader.substring(7)` crashes across all controllers by delegating identity resolution to Spring Security context.
- **User Enumeration Vulnerability:** Unified login failure responses to `"Invalid email or password"`.
- **NDMA RSS Startup Hang:** Converted synchronous `@PostConstruct` NDMA fetch into an asynchronous worker (`fetchNDMAAlertsAsync()`) with 5s connection and read timeouts.
- **NDMA Query Performance:** Fixed $O(N \times M)$ query loop by loading existing keys once into memory before deduplication.
- **Weather API Thread Hang:** Added 4-second connect/read timeouts and structured JSON fallback to `DisasterController.getWeather`.
- **Disconnected Frontend Features:**
  - Rewired `citizen-dashboard.html` to transmit emergency reports to `/api/citizen-reports` rather than browser `localStorage`.
  - Rewired `responder-rescue.html` to render real citizen evidence photos and rescue requests from the database rather than `localStorage`.
  - Optimized `role-loading.js` timing from 3000ms down to a responsive 600ms transition.
- **Database Schema Completeness:** Updated `DATABASE_SETUP.sql` with tables for `citizen_reports`, `incident_reports`, `rescue_tasks`, `messages`, and `admin_audit_logs`.


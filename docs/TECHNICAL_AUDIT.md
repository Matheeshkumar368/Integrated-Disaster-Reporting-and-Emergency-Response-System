# Technical Audit: Disaster Management & Alert System
**Academic Project Upgrade, Security Hardening & Deployment Readiness Audit**

- **Date:** October 2026
- **Auditor:** Senior Full-Stack, Security & QA Engineering Team
- **Baseline Git Branch:** `academic-project-upgrade`
- **Application Stack:** Java 21/25, Spring Boot 4.0.4/3.x, Spring Data JPA, Spring Security, MySQL 8.0, Vanilla JS / HTML5 / CSS3

---

## 1. Executive Summary

This technical audit was conducted on the **Disaster Management & Alert System** repository. The system is designed to provide real-time disaster alerts, citizen emergency incident reporting, responder rescue task dispatch, and administrative coordination for public safety agencies (specifically tailored for regional disaster preparedness such as Tamil Nadu / NDMA feeds).

While the application demonstrates solid domain modeling and modern frontend styling, the audit uncovered multiple **Critical** and **High** vulnerabilities, including:
1. **Destructive Data Initialization**: `DataInitializer.java` wipes out the entire database on every application startup (`deleteAll()`).
2. **Broken Server-Side Authorization**: `SecurityConfig.java` has `.anyRequest().permitAll()`, leaving every API endpoint unprotected by the Spring Security filter chain.
3. **Missing Spring Security JWT Filter**: No `OncePerRequestFilter` populates the `SecurityContext`.
4. **Disconnected Frontend Features**: The citizen reporting form saves emergency reports to browser `localStorage` instead of calling backend REST endpoints, and the responder rescue page reads photos and rescue counts from `localStorage` rather than the database.
5. **Vulnerable External Integrations**: The NDMA RSS feed is fetched synchronously at `@PostConstruct` on application boot with an $O(N \times M)$ query loop, and external weather API requests lack HTTP timeouts.
6. **Hardcoded Database Credentials & Fallback Secrets**: Live database credentials exist in `application.properties`.
7. **Missing Administrative Step-Up Security**: No Admin Security PIN or second-factor authentication mechanism exists for sensitive operations.
8. **Lack of Isolated Test Configuration**: The test suite ran directly against the live MySQL database.

---

## 2. Findings Matrix

| ID | Category | Severity | Description | Status |
|---|---|---|---|---|
| **SEC-01** | Security / Auth | **CRITICAL** | Server-side Spring Security `.anyRequest().permitAll()` disables authentication enforcement | Verified |
| **SEC-02** | Security / Auth | **CRITICAL** | No Spring Security JWT filter exists; `SecurityContextHolder` is never populated | Verified |
| **DAT-01** | Data Integrity | **CRITICAL** | `DataInitializer` executes `deleteAll()` on all tables upon every restart | Verified |
| **FND-01** | Frontend / API | **CRITICAL** | Citizen emergency reporting form persists reports to browser `localStorage` instead of API | Verified |
| **FND-02** | Frontend / API | **CRITICAL** | Responder rescue view retrieves photos and requests from `localStorage` rather than database | Verified |
| **EXT-01** | Reliability | **CRITICAL** | `@PostConstruct` synchronous NDMA RSS fetch blocks application startup and can cause crash | Verified |
| **EXT-02** | Reliability | **CRITICAL** | External Open-Meteo weather HTTP connection lacks connection/read timeouts | Verified |
| **CFG-01** | Security / Config | **CRITICAL** | Database credentials hardcoded in `application.properties`; weak fallback JWT secret | Verified |
| **SEC-03** | Security / Admin | **HIGH** | Missing Admin Security PIN / second-factor step-up authentication for high-impact actions | Verified |
| **SEC-04** | Security / Auth | **HIGH** | Sensitive endpoints perform fragile token parsing (`substring(7)`) leading to 500 crashes | Verified |
| **SEC-05** | Security / Auth | **HIGH** | User enumeration possible via differentiated login failure error messages | Verified |
| **SEC-06** | Security / Auth | **HIGH** | No login rate limiting or failed attempt lockout protection | Verified |
| **SEC-07** | Authorization | **HIGH** | `GET /api/citizen-reports` and `/api/citizen-reports/pending` lack role-based authorization | Verified |
| **DAT-02** | Database / Schema | **HIGH** | `DATABASE_SETUP.sql` contains plaintext passwords and lacks 4 core entity tables | Verified |
| **DAT-03** | Database / ORM | **HIGH** | N+1 query pattern inside `DisasterService.fetchNDMAAlerts()` (`findAll()` in loop) | Verified |
| **DAT-04** | Transactions | **HIGH** | Missing `@Transactional` on alert resolution, task creation, and report status changes | Verified |
| **API-01** | API Design | **HIGH** | Missing Citizen personal reports endpoint (`GET /api/citizen-reports/my-reports`) | Verified |
| **TST-01** | Testing / QA | **HIGH** | Only 1 test exists; runs against production/live database without test isolation | Verified |
| **DEP-01** | Deployment | **HIGH** | Missing Docker containerization, health check actuators, and `.env.example` template | Verified |
| **UX-01** | UI / UX | **MEDIUM** | Artificial 3-second login delay and loading container race conditions freeze navigation | Verified |
| **UX-02** | UI / UX | **MEDIUM** | Inconsistent navigation routes across Citizen, Responder, and Admin dashboards | Verified |
| **API-02** | API / Error | **MEDIUM** | Raw exception messages (`e.getMessage()`) leaked to clients; missing `@RestControllerAdvice` | Verified |
| **API-03** | API / Data | **MEDIUM** | Unbounded database queries without pagination across alerts, tasks, and reports | Verified |
| **ACC-01** | Accessibility | **MEDIUM** | Form inputs lack `<label for>` attributes; modals lack keyboard traps | Verified |
| **LOG-01** | Maintainability | **LOW** | Raw `System.out.println` used instead of SLF4J logger; potential PII in logs | Verified |
| **DEP-02** | Deployment | **LOW** | Missing CI/CD pipeline automation (GitHub Actions) | Verified |

---

## 3. Detailed Audit Findings

### 3.1 SEC-01 & SEC-02: Spring Security Permissive Configuration & Missing JWT Filter
- **Component:** `com.disaster.management.config.SecurityConfig`, `com.disaster.management.security.JwtUtil`
- **Severity:** **CRITICAL**
- **Evidence:**
  ```java
  // SecurityConfig.java
  http.authorizeHttpRequests(auth -> auth
      .requestMatchers("/api/auth/**").permitAll()
      .requestMatchers("/**").permitAll()
      .anyRequest().permitAll()
  );
  ```
- **Root Cause:** Developers bypassed Spring Security to simplify frontend integration, relying on manual `authHeader.substring(7)` checks scattered across individual controller methods.
- **Impact:** Any client can make HTTP requests to backend endpoints without going through Spring Security authentication. If a controller method fails to manually extract and check the token, the endpoint is completely open.
- **Recommended Fix:**
  1. Implement a proper `JwtAuthenticationFilter` extending `OncePerRequestFilter`.
  2. Parse the `Authorization: Bearer <token>` header, validate the JWT, extract username/email and role, and construct an `Authentication` token in the `SecurityContextHolder`.
  3. Update `SecurityConfig` to enforce role-based authorization:
     - Public: `/`, `/index.html`, `/role-loading.*`, `/loading-screen/**`, `/js/**`, `/api/auth/login`, `/api/auth/register`, `/api/auth/register-responder`, `/api/alerts/broadcasted`, `/api/weather`, `/actuator/health`.
     - Protected:
       - Citizen endpoints: `ROLE_Citizen`
       - Responder endpoints: `ROLE_Responder`
       - Admin endpoints: `ROLE_Admin`
- **Verification Method:** Send HTTP requests with (1) no token, (2) invalid token, (3) citizen token to admin endpoint; verify HTTP 401 Unauthorized / 403 Forbidden is returned.

---

### 3.2 DAT-01: Destructive Data Initialization on Application Boot
- **Component:** `com.disaster.management.DataInitializer`
- **Severity:** **CRITICAL**
- **Evidence:**
  ```java
  // DataInitializer.java lines 24-38
  alertRepo.deleteAll();
  disasterRepo.deleteAll();
  userRepo.deleteAll();
  ```
- **Root Cause:** Development convenience script left active in the default Spring Boot execution flow.
- **Impact:** Every time the backend application restarts (or when tests run), ALL users, alerts, disasters, and dependent records in the database are permanently erased.
- **Recommended Fix:**
  Replace destructive `deleteAll()` calls with conditional seed checks: only insert baseline administrative and demo users if `userRepo.count() == 0` or if a specific profile (e.g. `dev`) is active. Never delete existing records.
- **Verification Method:** Restart backend application multiple times and verify user counts and existing alerts are preserved.

---

### 3.3 FND-01 & FND-02: Disconnected Frontend Persistence (localStorage Workarounds)
- **Component:** `src/main/resources/static/citizen-dashboard.html`, `src/main/resources/static/responder-rescue.html`
- **Severity:** **CRITICAL**
- **Evidence:**
  - `citizen-dashboard.html:722`:
    ```javascript
    // For now, store in localStorage (in production, send to backend)
    const reports = JSON.parse(localStorage.getItem('citizenReports') || '[]');
    reports.push(reportData);
    localStorage.setItem('citizenReports', JSON.stringify(reports));
    ```
  - `responder-rescue.html:423`:
    ```javascript
    const photos = JSON.parse(localStorage.getItem('disasterPhotos') || '[]');
    ```
- **Root Cause:** Incomplete frontend-to-backend integration where stubbed prototype code remained in place.
- **Impact:**
  - Emergency disaster reports submitted by citizens are stored only in their local browser storage; neither responders nor administrators ever see the reports.
  - Responders cannot see real citizen emergency reports or photos uploaded via the camera modal in the rescue dashboard.
- **Recommended Fix:**
  1. Rewire `citizen-dashboard.html`'s `reportForm` submit handler to `POST /api/citizen-reports` with JWT Bearer authentication.
  2. Update `responder-rescue.html` to fetch real citizen reports and photos via `GET /api/citizen-reports/pending` and `GET /api/citizen-reports`.
  3. Ensure photos uploaded as Base64 strings are rendered in the responder incident feed.
- **Verification Method:** Submit a report with location, description, and photo from a Citizen session; log in as Responder and verify the incident and photo appear immediately in the rescue feed.

---

### 3.4 EXT-01: Synchronous External RSS Network Call Blocking Startup
- **Component:** `com.disaster.management.DisasterManagementApplication`, `com.disaster.management.service.DisasterService`
- **Severity:** **CRITICAL**
- **Evidence:**
  ```java
  @PostConstruct
  public void onStartup() {
      disasterService.fetchNDMAAlerts();
  }
  ```
- **Root Cause:** `@PostConstruct` blocks the Spring ApplicationContext initialization until `fetchNDMAAlerts()` completes.
- **Impact:** If the government NDMA server (`https://sachet.ndma.gov.in`) is slow, unreachable, or DNS fails, the entire application fails to boot or hangs during startup.
- **Recommended Fix:**
  1. Move the NDMA RSS sync to an asynchronous runner or scheduled task (`@Async` / `@Scheduled(fixedRate = 900000)`).
  2. Set connection and read timeouts on the HTTP feed connection.
  3. Wrap the fetch in robust try-catch logging so network failure never impacts application availability.
- **Verification Method:** Simulate network outage or unreachable host and verify application starts within 5 seconds without errors.

---

### 3.5 EXT-02: Missing Timeout and Parsing on External Weather Integration
- **Component:** `com.disaster.management.controller.DisasterController.getWeather`
- **Severity:** **CRITICAL**
- **Evidence:**
  ```java
  URL url = new URL(urlStr);
  HttpURLConnection conn = (HttpURLConnection) url.openConnection();
  conn.setRequestMethod("GET");
  // Missing conn.setConnectTimeout(...) and conn.setReadTimeout(...)
  ```
- **Root Cause:** Unconfigured `HttpURLConnection`.
- **Impact:** Slow or hanging external weather API requests tie up Tomcat HTTP worker threads, eventually leading to thread pool exhaustion and Denial of Service (DoS).
- **Recommended Fix:**
  Set `conn.setConnectTimeout(5000)` and `conn.setReadTimeout(5000)`. Implement fallback weather information when the external service is unavailable.
- **Verification Method:** Unit test mocking delayed response; verify endpoint aborts after 5s and returns fallback data without crashing.

---

### 3.6 SEC-03: Missing Admin Security PIN (Two-Factor / Step-Up Authorization)
- **Component:** Admin controllers & `User` entity
- **Severity:** **HIGH**
- **Evidence:** Admin actions (broadcasting global alerts, assigning responder zones, resolving emergencies, creating high-priority tasks) are performed immediately with standard JWT without secondary verification.
- **Root Cause:** Lack of secondary authorization layer.
- **Impact:** If an administrator's session token or browser is compromised, high-impact emergency actions can be performed without friction.
- **Recommended Fix:**
  1. Add fields to `User`: `adminPin` (hashed with BCrypt), `adminPinEnrolled` (boolean), `pinFailedAttempts` (int), `pinLockedUntil` (LocalDateTime).
  2. Create endpoints:
     - `POST /api/auth/admin/pin/enroll`: Enroll or update 6-digit administrative security PIN.
     - `POST /api/auth/admin/pin/verify`: Verify PIN, rate-limited (max 3 failed attempts before 15-minute lockout), generating a short-lived step-up token or scope.
     - `GET /api/auth/admin/pin/status`: Check whether PIN is enrolled and if step-up verification is active.
  3. Protect critical admin operations (e.g. broadcasting alerts, changing user roles, assigning zones) by requiring step-up verification.
- **Verification Method:** Verify admin cannot execute privileged actions without verified PIN; verify lock-out after 3 invalid attempts.

---

### 3.7 SEC-04: Fragile Token Parsing and Null Pointer Vulnerabilities
- **Component:** `IncidentReportController`, `AlertController`, `RescueTaskController`, `MessageController`
- **Severity:** **HIGH**
- **Evidence:**
  ```java
  String token = authHeader.substring(7);
  ```
- **Root Cause:** Code assumes `authHeader` is always non-null, starts with `"Bearer "`, and has length > 7.
- **Impact:** Any malformed or missing header triggers an unhandled `NullPointerException` or `StringIndexOutOfBoundsException`, yielding HTTP 500 error responses and leaking server stack traces.
- **Recommended Fix:**
  Centralize authentication via Spring Security filter so controllers inject `Authentication` or `@AuthenticationPrincipal` directly rather than manually parsing headers.
- **Verification Method:** Send requests with missing, malformed, or empty `Authorization` headers; verify clean HTTP 401 response without stack trace.

---

### 3.8 SEC-05 & SEC-06: Authentication Enumeration & Missing Brute-Force Rate Limiting
- **Component:** `com.disaster.management.controller.AuthController.login`
- **Severity:** **HIGH**
- **Evidence:**
  - Response returns `"User not found"` vs `"Invalid password"`.
  - No rate limiting or delay on consecutive failed login attempts.
- **Root Cause:** Differentiated error responses and lack of request throttling.
- **Impact:** Attackers can enumerate registered email addresses and launch automated password dictionary attacks against known users.
- **Recommended Fix:**
  1. Return generic response `"Invalid email or password"` for all login failures.
  2. Implement an in-memory or cache-based rate limiter (e.g., maximum 5 failed attempts per IP/email per 10 minutes with progressive delay).
- **Verification Method:** Test login with non-existent user and wrong password; verify identical generic error messages and verify lockout on repeated failures.

---

### 3.9 DAT-03: N+1 Query Loop in NDMA Feed Ingestion
- **Component:** `com.disaster.management.service.DisasterService`
- **Severity:** **HIGH**
- **Evidence:**
  ```java
  for (SyndEntry entry : entries) {
      ...
      List<Disaster> existingList = disasterRepository.findAll();
      ...
  }
  ```
- **Root Cause:** Inefficient deduplication logic calling `disasterRepository.findAll()` on every iteration of a 100-entry loop.
- **Impact:** Up to 100 full table scans executed sequentially, degrading database performance and memory.
- **Recommended Fix:**
  Query the database once before the loop (`Set<String> existingKeys`) or use repository existence queries (`existsByTypeAndLocation(type, location)`).
- **Verification Method:** Run feed sync with SQL query logging enabled; confirm query count drops from $N$ to 1.

---

### 3.10 TST-01: Inadequate Automated Testing & Database Contamination
- **Component:** `src/test/java/com/disaster/management/`
- **Severity:** **HIGH**
- **Evidence:**
  - Only `DisasterManagementApplicationTests.java` exists with empty `contextLoads()`.
  - Spring Boot test configuration directly uses `disaster_management` MySQL database instead of an isolated test database (e.g., H2 in-memory or dedicated test DB).
- **Root Cause:** Lack of unit, repository, controller, and integration tests.
- **Impact:** Zero confidence in regression safety; running tests mutates and drops developer or production database tables.
- **Recommended Fix:**
  1. Provide `src/test/resources/application-test.properties` using H2 in-memory database or an isolated test schema.
  2. Add unit tests for password hashing, JWT generation, and PIN verification.
  3. Add controller/integration tests for authentication, role authorization, citizen report lifecycle, and admin step-up PIN.
- **Verification Method:** Run `./mvnw test` with MySQL stopped; verify all tests pass completely in memory without touching MySQL.

---

### 3.11 DEP-01: Deployment Readiness Blockers
- **Component:** Build and configuration files
- **Severity:** **HIGH**
- **Evidence:**
  - No `Dockerfile` or `docker-compose.yml`.
  - No `.env.example` file.
  - Actuator endpoints (`/actuator/health`) not configured for liveness/readiness probes.
- **Impact:** Difficult to demonstrate or deploy reliably in containerized or cloud environments (Render, Railway, AWS, Docker).
- **Recommended Fix:**
  1. Add standard multi-stage `Dockerfile` (Eclipse Temurin JDK 21).
  2. Add `docker-compose.yml` defining web application and MySQL 8.0 services with network health checks.
  3. Add `.env.example` with safe defaults.
  4. Include `spring-boot-starter-actuator` with health endpoints.
- **Verification Method:** Build Docker container and spin up services with `docker compose up --build`.

---

### 3.12 UX-01: Artificial Loading Delays & Navigation Freezes
- **Component:** `src/main/resources/static/role-loading.js`, `index.html`, dashboard pages
- **Severity:** **MEDIUM**
- **Evidence:**
  - `role-loading.js` enforces a 3-second delay on every login redirect.
  - Container reuse bugs caused perpetual loading spinner on `admin-profile.html`.
- **Root Cause:** Heavy client-side transition overlay interfering with browser page loads.
- **Impact:** Users perceive the app as slow or completely unresponsive.
- **Recommended Fix:**
  Streamline `role-loading.js` to an unobtrusive transition with an immediate 500ms maximum display time and guaranteed removal on `DOMContentLoaded`.
- **Verification Method:** Verify instantaneous, smooth login redirection without stuck overlays.

---

## 4. Prioritized Action Plan

1. **Phase 2 (Backend & Database Reliability):**
   - Fix `DataInitializer` to never delete production/user data.
   - Externalize database credentials in `application.properties` with safe environment defaults.
   - Fix N+1 query issue in `DisasterService` and run NDMA sync asynchronously.
   - Add timeouts and graceful degradation to weather endpoint.
   - Add test profile with H2 in-memory database to protect live MySQL.

2. **Phase 3 (Authentication & Authorization):**
   - Implement `JwtAuthenticationFilter` and wire it into Spring Security `filterChain`.
   - Protect all REST endpoints with appropriate role-based authorization (`ROLE_Citizen`, `ROLE_Responder`, `ROLE_Admin`).
   - Fix user enumeration in `AuthController`.
   - Implement login rate limiting.

3. **Phase 4 (Admin Security PIN):**
   - Add PIN fields and status to `User`.
   - Implement enrollment, verification, rate limiting, and step-up authorization token.
   - Enforce PIN verification on sensitive operations.

4. **Phase 5 (Disaster Response Features & API Integration):**
   - Add `GET /api/citizen-reports/my-reports` for citizens to view personal history.
   - Connect citizen report form to backend API.
   - Connect responder rescue view to real citizen reports and photos from the database.
   - Add audit logging and CSV export for incident reports.

5. **Phase 6 (UI/UX Polish):**
   - Optimize `role-loading.js` to eliminate artificial delays and freezes.
   - Unify navigation bars across citizen, responder, and admin views.
   - Add accessible labels, focus indicators, and confirmation dialogs.

6. **Phase 7 (Automated Testing):**
   - Implement comprehensive unit and integration tests across services, controllers, and security.

7. **Phase 8 & 9 (Deployment & Documentation):**
   - Add `Dockerfile`, `docker-compose.yml`, `.env.example`.
   - Produce all architecture, security, API, and database documentation.

---
*Audit Completed on 2026-10-29. Proceeding to systematic implementation.*


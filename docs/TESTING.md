# Automated Testing & Quality Assurance Guide

## 1. Test Strategy Overview

The automated test suite provides coverage across all layers of the Disaster Management & Alert System without mutating or requiring a running local MySQL instance.

- **Test Framework:** JUnit 5 (Jupiter), Spring Boot Test, Spring Security Test, Mockito.
- **Isolation Mechanism:** Dedicated `src/test/resources/application.properties` configuring an in-memory **H2 database** in MySQL mode (`DB_CLOSE_DELAY=-1;MODE=MySQL`). Running `./mvnw test` never drops, alters, or pollutes the production or developer MySQL database.
- **Current Test Count:** 22 Automated Tests (100% Pass Rate).

---

## 2. Test Suites & Verification Coverage

### 2.1 Security & Web Integration Tests (`SecurityIntegrationTest.java`)
- `testPublicBroadcastedAlerts`: Validates that public alert broadcasts are accessible to all citizens without authentication (HTTP 200).
- `testPublicWeatherEndpoint`: Verifies unauthenticated access to regional weather forecasts and validates structured JSON response.
- `testPublicDisastersEndpoint`: Confirms regional disaster incident feeds are openly available to the public.
- `testProtectedAdminEndpointUnauthorized`: Verifies that unauthenticated requests to create alerts (`POST /api/alerts`) are rejected with HTTP 401 Unauthorized.
- `testInvalidLoginProducesGenericMessage`: Asserts that incorrect credentials yield a generic `"Invalid email or password"` message to prevent account enumeration.
- `testActuatorHealthEndpoint`: Confirms `/actuator/health` returns status `UP` for deployment liveness and readiness probes.

### 2.2 Admin Security PIN & 2FA Tests (`AdminPinServiceTest.java`)
- `testVerifyPinSuccess`: Verifies that presenting a valid PIN against a BCrypt-hashed record resets failed counters and generates a valid Step-Up JWT token.
- `testVerifyPinLockout`: Tests the brute-force lockout policy; asserts that after 3 consecutive invalid attempts, `adminUser.isPinLocked()` becomes true and further attempts return `locked: true`.
- `testEnrollPinSuccess`: Tests PIN enrollment requiring current primary password verification before storing the BCrypt-hashed PIN.

### 2.3 Citizen Incident Reporting Tests (`CitizenReportServiceTest.java`)
- `testCreateReport`: Confirms that emergency incident submissions are saved with initial status `Pending`.
- `testCreateReportValidation`: Ensures missing incident descriptions throw validation exceptions.
- `testAcknowledgeReport`: Verifies state transition from `Pending` to `Acknowledged` when a responder reviews an incident.
- `testResolveReport`: Verifies state transition to `Resolved` upon completion of rescue operations.
- `testGetReportsByCitizen`: Validates ownership filtering so citizens only retrieve their own reported incidents.

### 2.4 Rescue Task Dispatch Tests (`RescueTaskServiceTest.java`)
- `testCreateTaskSuccess`: Verifies task creation with required title, zone, and priority.
- `testCreateTaskInvalidPriority`: Validates rejection of non-standard priority values.
- `testAssignTask`: Asserts task assignment correctly updates responder email and responder name.
- `testUpdateTaskStatus`: Validates task status transitions (`Pending` -> `In Progress`) and timestamped progress notes.

### 2.5 Cryptographic Token Tests (`JwtUtilTest.java`)
- `testGenerateAndExtractToken`: Verifies generation and claim extraction (`sub`, `role`, `name`) for standard access tokens.
- `testAdminStepUpToken`: Verifies generation and validation of scoped step-up authorization tokens.
- `testInvalidToken`: Verifies malformed or expired tokens are rejected safely.

---

## 3. Running the Test Suite Locally

Execute the test suite via the Maven wrapper:

```bash
# Windows
.\mvnw.cmd test

# Linux / macOS
./mvnw test
```

Expected output:
```
[INFO] Tests run: 22, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```


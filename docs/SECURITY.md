# Security Architecture & Hardening Guide

## 1. Authentication & Session Architecture

### 1.1 Password Hashing & Secret Protection
- **Algorithm:** BCrypt (Blowfish-based adaptive key derivation function) with standard workload factor 10.
- **Passwords in Transit:** Transmitted via HTTPS POST payloads; never accepted in query parameters.
- **Passwords at Rest:** All user passwords in `users.password` and admin security PINs in `users.admin_pin` are stored strictly as salted BCrypt hashes.
- **Serialization Protection:** The `password` field in `User.java` is annotated with `@JsonProperty(access = JsonProperty.Access.WRITE_ONLY)` to guarantee passwords can never leak in JSON profile responses.

### 1.2 Stateless JWT Tokens
- **Signing Algorithm:** HMAC SHA-256 (`HS256`) using cryptographically strong keys (minimum 256 bits).
- **Token Claims:** `sub` (user email), `role` (`Citizen`, `Responder`, `Admin`), `name`, `type` (`ACCESS` or `ADMIN_STEP_UP`), `iat` (issued at), and `exp` (expiration).
- **Session Policy:** Configured as `SessionCreationPolicy.STATELESS` in Spring Security; tokens are stateless and verified per request.
- **Security Context Integration:** `JwtAuthenticationFilter` intercepts requests, parses `Authorization: Bearer <token>`, validates token signature and freshness, and injects authenticated principals with authorities (`ROLE_Citizen`, `ROLE_Responder`, `ROLE_Admin`) into `SecurityContextHolder`.

---

## 2. Strong Administrator Step-Up Authentication (Admin Security PIN)

### 2.1 Threat Model & Objective
A compromised administrative bearer token in a web browser could allow unauthorized disaster broadcasts, unauthorized responder reassignments, or emergency overrides. To prevent single-token compromise from causing public panic or disrupting emergency operations, the system enforces **Server-Side Step-Up Authorization**.

### 2.2 Mechanism
1. **Enrollment:**
   - The authenticated admin must provide their current primary account password before configuring a 4 to 8-digit numeric Security PIN.
   - Endpoint: `POST /api/auth/admin/pin/enroll`
   - The PIN is hashed using BCrypt and stored in `users.admin_pin`.
2. **Verification & Rate Limiting:**
   - Endpoint: `POST /api/auth/admin/pin/verify`
   - Consecutive failed attempts are monitored in `users.pin_failed_attempts`.
   - **Lockout Policy:** If 3 consecutive incorrect PINs are entered, the account's PIN verification is locked for 15 minutes (`users.pin_locked_until`).
3. **Short-Lived Step-Up Token:**
   - Upon successful PIN verification, the server issues a scoped, short-lived JWT step-up token with claim `type: ADMIN_STEP_UP` and a 15-minute expiration.
   - Clients supply this token in the `X-Admin-Step-Up-Token` HTTP header when performing sensitive administrative actions.
4. **Audit Logging:**
   - Every administrative attempt, enrollment, verification, and high-impact action is recorded in `admin_audit_logs` with actor email, action code, IP address, timestamp, and status.
   - PINs and passwords are never logged.

---

## 3. Defense-in-Depth & Access Controls

| Resource | Required Role | Additional Requirements |
|---|---|---|
| Emergency Incident Submission | `ROLE_Citizen`, `ROLE_Admin` | Authenticated Bearer Token |
| Personal Report History | `ROLE_Citizen`, `ROLE_Admin` | Ownership check (Citizen sees only their own reports) |
| Rescue Operations / Pending Requests | `ROLE_Responder`, `ROLE_Admin` | Authenticated Bearer Token |
| Task Assignment & Dispatch | `ROLE_Admin` | Authenticated Bearer Token |
| High-Severity Alert Broadcast | `ROLE_Admin` | Admin Step-Up Security PIN Token (`X-Admin-Step-Up-Token`) |
| Assign Responder Zone | `ROLE_Admin` | Admin Step-Up Security PIN Token (`X-Admin-Step-Up-Token`) |
| View Security Audit Trail | `ROLE_Admin` | Admin Step-Up Security PIN Token (`X-Admin-Step-Up-Token`) |
| Public Weather & Broadcasts | Public (Unauthenticated) | None (Open access) |

---

## 4. Input Validation & Fault Tolerance
- **Enumeration Prevention:** Failed login attempts return a uniform error message: `"Invalid email or password"`.
- **Brute-Force Rate Limiting:** Consecutive login failures trigger a temporary 5-minute cooldown.
- **External Network Timeouts:** External HTTP connections (Open-Meteo Weather API and NDMA RSS feeds) enforce 4-5 second connection and read timeouts to prevent thread starvation.
- **SQL Injection Defense:** All database interactions utilize Spring Data JPA parameterized queries and ORM object graphs.


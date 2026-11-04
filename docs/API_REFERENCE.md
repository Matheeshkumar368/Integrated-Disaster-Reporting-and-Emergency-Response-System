# REST API Reference Documentation

All protected endpoints require an HTTP `Authorization` header containing a valid Bearer JWT:
```http
Authorization: Bearer <your-jwt-token>
```
High-impact administrative endpoints also require the Step-Up PIN header:
```http
X-Admin-Step-Up-Token: <your-step-up-token>
```

---

## 1. Authentication & Admin Security PIN

### `POST /api/auth/login`
- **Access:** Public
- **Request Body:**
  ```json
  {
    "email": "admin@disaster.gov.in",
    "password": "admin123"
  }
  ```
- **Responses:**
  - `200 OK`: Returns JWT token, user role, name, and PIN enrollment status.
  - `401 Unauthorized`: Returns generic error `"Invalid email or password"`.
  - `429 Too Many Requests`: Account temporarily locked out after repeated failures.

### `POST /api/auth/register`
- **Access:** Public (Citizen self-registration)
- **Request Body:**
  ```json
  {
    "email": "citizen@gmail.com",
    "password": "citizen123",
    "name": "Citizen User",
    "phone": "9876543210",
    "location": "Chennai"
  }
  ```
- **Responses:**
  - `200 OK`: User registered successfully.
  - `409 Conflict`: Email already exists.

### `POST /api/auth/register-responder`
- **Access:** Public (Emergency responder registration)
- **Request Body:**
  ```json
  {
    "email": "responder@disaster.gov.in",
    "password": "responder123",
    "name": "Rescue Lead",
    "phone": "9876543211",
    "location": "Coimbatore",
    "groupClassification": "Leader"
  }
  ```

### `GET /api/auth/admin/pin/status`
- **Access:** Admin only
- **Response `200 OK`:**
  ```json
  {
    "enrolled": true,
    "locked": false
  }
  ```

### `POST /api/auth/admin/pin/enroll`
- **Access:** Admin only
- **Request Body:**
  ```json
  {
    "currentPassword": "admin123",
    "pin": "123456"
  }
  ```

### `POST /api/auth/admin/pin/verify`
- **Access:** Admin only
- **Request Body:**
  ```json
  {
    "pin": "123456"
  }
  ```
- **Response `200 OK`:**
  ```json
  {
    "success": true,
    "stepUpToken": "eyJhbGciOi...",
    "expiresInSeconds": 900
  }
  ```
- **Response `423 Locked`:** When locked out after 3 consecutive invalid attempts.

### `GET /api/auth/admin/audit-logs`
- **Access:** Admin only (Requires `X-Admin-Step-Up-Token`)
- **Response `200 OK`:** Returns array of recent administrative actions with timestamps and outcomes.

---

## 2. Citizen Reports (`/api/citizen-reports`)

### `POST /api/citizen-reports`
- **Access:** Citizen, Admin
- **Request Body:**
  ```json
  {
    "location": "Chennai - Velachery",
    "message": "[Flood] Severe street inundation, water entering homes",
    "photoData": "data:image/jpeg;base64,..."
  }
  ```
- **Response `200 OK`:** Returns created `CitizenReport` with status `Pending`.

### `GET /api/citizen-reports/my-reports`
- **Access:** Citizen, Admin
- **Response `200 OK`:** Returns array of reports submitted by the logged-in citizen.

### `GET /api/citizen-reports/pending`
- **Access:** Responder, Admin
- **Response `200 OK`:** Returns pending citizen rescue requests requiring response.

### `PUT /api/citizen-reports/{id}/acknowledge`
- **Access:** Responder, Admin
- **Response `200 OK`:** Updates report status to `Acknowledged`.

### `PUT /api/citizen-reports/{id}/resolve`
- **Access:** Responder, Admin
- **Response `200 OK`:** Updates report status to `Resolved`.

---

## 3. Disaster Alerts (`/api/alerts`)

### `POST /api/alerts`
- **Access:** Admin only (High-severity broadcasts require `X-Admin-Step-Up-Token`)
- **Request Body:**
  ```json
  {
    "title": "Cyclone Warning - Red Alert",
    "message": "Severe cyclonic storm approaching coastal areas.",
    "disasterType": "Cyclone",
    "severity": "High",
    "region": "Chennai"
  }
  ```
- **Response `200 OK`:** Broadcasts alert and auto-dispatches rescue tasks to zone responders.
- **Response `428 Precondition Required`:** If high-severity alert submitted without Step-Up PIN verification.

### `GET /api/alerts/broadcasted`
- **Access:** Public
- **Response `200 OK`:** Returns active disaster alerts for public citizens.

---

## 4. Rescue Tasks (`/api/tasks`)

### `POST /api/tasks`
- **Access:** Admin only
- **Request Body:**
  ```json
  {
    "title": "Evacuation Mission",
    "description": "Evacuate low-lying riverbank communities",
    "zone": "Coimbatore",
    "priority": "High"
  }
  ```

### `GET /api/tasks/my-tasks`
- **Access:** Responder, Admin
- **Response `200 OK`:** Returns tasks assigned to the authenticated responder.

### `PUT /api/tasks/{id}/status`
- **Access:** Responder, Admin
- **Request Body:**
  ```json
  {
    "status": "In Progress",
    "notes": "Emergency relief units deployed on site"
  }
  ```

---

## 5. Reporting & Weather Feeds

### `GET /api/weather?city=Chennai`
- **Access:** Public
- **Response `200 OK`:** Real meteorological temperature, apparent temperature, and precipitation data.

### `GET /api/reports/export/csv`
- **Access:** Responder, Admin
- **Response `200 OK`:** `text/csv` downloadable file containing all verified incident reports.

### `GET /actuator/health`
- **Access:** Public
- **Response `200 OK`:**
  ```json
  {
    "status": "UP"
  }
  ```


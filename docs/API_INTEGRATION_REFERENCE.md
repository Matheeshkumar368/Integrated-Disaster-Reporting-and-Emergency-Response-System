# COMPREHENSIVE REST API INTEGRATION REFERENCE
## Disaster Management & Emergency Response System (Enterprise Full-Stack Edition)

This document provides complete technical specifications for all REST API endpoints, authentication flows, request/response contracts, and role-based access control (RBAC) rules.

---

## 1. GLOBAL CONVENTIONS & HEADERS

### Base URL
All API routes are served under:
```
http://localhost:8080/api
```

### Standard HTTP Headers
* **Authentication Header (Protected Endpoints):**
  ```http
  Authorization: Bearer <jwt_token>
  ```
* **Step-Up 2FA Security Token (High-Consequence Administrative Operations):**
  ```http
  X-Admin-Step-Up-Token: <step_up_token>
  ```
* **Payload Content Type:**
  * Standard REST endpoints: `Content-Type: application/json`
  * Media upload endpoints: `Content-Type: multipart/form-data; boundary=----WebKitFormBoundary...`

### Error Response Schema
```json
{
  "timestamp": "2026-11-01T12:00:00.000Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Detailed description of error",
  "path": "/api/resource"
}
```

---

## 2. AUTHENTICATION & SECURITY GOVERNANCE

### `POST /api/auth/login`
Authenticates a user and issues a signed JWT token.
* **Access:** Public
* **Request Body:**
  ```json
  {
    "email": "admin@disaster.gov.in",
    "password": "admin123"
  }
  ```
* **Success Response (`200 OK`):**
  ```json
  {
    "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "role": "Admin",
    "name": "System Administrator",
    "email": "admin@disaster.gov.in",
    "pinEnrolled": true
  }
  ```
* **Error Response (`401 Unauthorized`):** Invalid credentials.
* **Error Response (`429 Too Many Requests`):** Account locked out after repeated failures.

---

### `POST /api/auth/register`
Self-service civilian citizen registration.
* **Access:** Public
* **Request Body:**
  ```json
  {
    "name": "Civilian User",
    "email": "citizen@gmail.com",
    "password": "citizen123",
    "phone": "9876543210",
    "location": "Peelamedu, Coimbatore"
  }
  ```
* **Success Response (`200 OK`):** User registered with role `Citizen`.
* **Error Response (`409 Conflict`):** Email already registered.

---

### `POST /api/auth/register-responder`
Onboarding endpoint for tactical emergency responders.
* **Access:** Public (or Admin pre-provisioned)
* **Request Body:**
  ```json
  {
    "name": "Rescue Unit 1",
    "email": "responder@disaster.gov.in",
    "password": "responder123",
    "phone": "9876543211",
    "location": "Coimbatore District",
    "groupClassification": "Leader"
  }
  ```
* **Success Response (`200 OK`):** User registered with role `Responder`.

---

### `GET /api/auth/me`
Retrieves identity profile and permissions for the authenticated caller.
* **Access:** Authenticated (`Bearer <token>`)
* **Success Response (`200 OK`):**
  ```json
  {
    "userId": 1,
    "name": "System Administrator",
    "email": "admin@disaster.gov.in",
    "role": "Admin",
    "phone": "9876543210",
    "location": "Tamil Nadu Emergency Center",
    "availabilityStatus": "Available"
  }
  ```

---

### `GET /api/auth/admin/pin/status`
Checks whether the administrator has enrolled a Step-Up 2FA Security PIN.
* **Access:** Admin only
* **Success Response (`200 OK`):**
  ```json
  {
    "enrolled": true,
    "locked": false
  }
  ```

---

### `POST /api/auth/admin/pin/verify`
Validates the 6-digit administrative PIN and returns a temporary Step-Up token.
* **Access:** Admin only
* **Request Body:**
  ```json
  {
    "pin": "123456"
  }
  ```
* **Success Response (`200 OK`):**
  ```json
  {
    "success": true,
    "stepUpToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
    "expiresInSeconds": 900
  }
  ```
* **Error Response (`423 Locked`):** Temporarily locked out after 3 consecutive failed attempts.

---

### `GET /api/auth/responder/availability`
Queries current operational availability for the calling responder.
* **Access:** Responder, Admin
* **Success Response (`200 OK`):**
  ```json
  {
    "email": "responder@disaster.gov.in",
    "availabilityStatus": "Available"
  }
  ```

---

### `PUT /api/auth/responder/availability`
Updates operational availability status (`Available`, `On Duty`, `Unavailable`).
* **Access:** Responder, Admin
* **Request Body:**
  ```json
  {
    "status": "On Duty"
  }
  ```
* **Success Response (`200 OK`):**
  ```json
  {
    "status": "success",
    "availabilityStatus": "On Duty"
  }
  ```
* **Error Response (`409 Conflict`):** Cannot set status to `Unavailable` while actively deployed on an ongoing incident.

---

## 3. CITIZEN DISTRESS REPORTS

### `POST /api/citizen-reports`
Submits an emergency civilian distress SOS with coordinates and optional photo attachment.
* **Access:** Citizen, Admin
* **Request Body:**
  ```json
  {
    "location": "Cross Cut Road, Coimbatore",
    "message": "[Flood | Severity: High] Water level reaching 4 feet.\n[Device GPS: 11.01684, 76.95583]",
    "photoData": "data:image/png;base64,iVBORw0KGgoAAAANSUhEU..."
  }
  ```
* **Success Response (`200 OK` or `201 Created`):**
  ```json
  {
    "reportId": 17,
    "status": "Submitted",
    "message": "Report logged successfully",
    "timestamp": "2026-11-01T12:05:00.000Z"
  }
  ```

---

### `GET /api/citizen-reports`
Retrieves all citizen emergency reports in the system.
* **Access:** Admin only
* **Success Response (`200 OK`):** Array of all distress reports with status, submitter info, and coordinates.

---

### `GET /api/citizen-reports/my-reports`
Retrieves only distress reports submitted by the authenticated citizen (ownership isolation).
* **Access:** Citizen, Admin
* **Success Response (`200 OK`):**
  ```json
  [
    {
      "reportId": 17,
      "location": "Cross Cut Road, Coimbatore",
      "message": "[Flood | Severity: High] Water level reaching 4 feet...",
      "status": "Submitted",
      "createdAt": "2026-11-01T12:05:00.000Z"
    }
  ]
  ```

---

### `PUT /api/citizen-reports/{id}/resolve`
Marks a civilian distress report as resolved once rescue operations are complete.
* **Access:** Responder, Admin
* **Success Response (`200 OK`):**
  ```json
  {
    "status": "success",
    "reportId": 17,
    "newStatus": "Resolved"
  }
  ```

---

## 4. RESCUE TASKS & SMART DISPATCH ENGINE

### `POST /api/tasks`
Creates a rescue mission and dispatches it to a field responder unit.
* **Access:** Admin only
* **Request Body:**
  ```json
  {
    "title": "Emergency Flood Evacuation - Cross Cut Road",
    "description": "Evacuate 3 trapped civilians from ground floor underpass.",
    "zone": "Coimbatore",
    "priority": "High",
    "locationName": "Cross Cut Road, Sector 4, Coimbatore",
    "latitude": 11.01684,
    "longitude": 76.95583,
    "assignedTo": "responder@disaster.gov.in",
    "assignedResponderName": "Emergency Responder Unit 1"
  }
  ```
* **Success Response (`200 OK`):**
  ```json
  {
    "status": "success",
    "task": {
      "taskId": 9,
      "title": "Emergency Flood Evacuation - Cross Cut Road",
      "status": "Assigned",
      "priority": "High",
      "assignedTo": "responder@disaster.gov.in",
      "latitude": 11.01684,
      "longitude": 76.95583
    }
  }
  ```

---

### `GET /api/tasks/my-tasks`
Retrieves tactical field tasks assigned to the calling responder.
* **Access:** Responder only
* **Success Response (`200 OK`):**
  ```json
  {
    "tasks": [
      {
        "taskId": 9,
        "title": "Emergency Flood Evacuation - Cross Cut Road",
        "status": "Assigned",
        "priority": "High",
        "locationName": "Cross Cut Road, Sector 4, Coimbatore",
        "latitude": 11.01684,
        "longitude": 76.95583
      }
    ]
  }
  ```

---

### `PUT /api/tasks/{id}/status`
Transitions the operational state of a rescue task through the 4-stage lifecycle.
* **Access:** Assigned Responder, Admin
* **Valid Statuses:** `Pending`, `Assigned`, `Acknowledged`, `En Route`, `Arrived`, `In Progress`, `Resolved`, `Completed`
* **Request Body:**
  ```json
  {
    "status": "En Route",
    "notes": "Unit 1 deployed, vehicle en route to Cross Cut underpass."
  }
  ```
* **Success Response (`200 OK`):**
  ```json
  {
    "status": "success",
    "task": {
      "taskId": 9,
      "status": "En Route",
      "updatedAt": "2026-11-01T12:08:00.000Z"
    }
  }
  ```
* **Error Response (`403 Forbidden`):** Caller is not the assigned responder.
* **Error Response (`409 Conflict`):** Invalid state transition (e.g., trying to reopen a Completed task).

---

### `GET /api/admin/assignments/recommendations/{taskId}`
Evaluates AI/heuristic suitability scores for all active responders against an incident's requirements.
* **Access:** Admin only
* **Success Response (`200 OK`):**
  ```json
  {
    "taskId": 9,
    "recommendations": [
      {
        "responderEmail": "responder@disaster.gov.in",
        "responderName": "Emergency Responder Unit 1",
        "suitabilityScore": 94,
        "distanceKm": 1.4,
        "currentLoad": 0,
        "status": "Available"
      }
    ]
  }
  ```

---

## 5. LIVE TELEMETRY & GPS TRACKING

### `POST /api/tracking/start`
Initializes a live telemetry broadcast session for an active rescue task.
* **Access:** Responder only
* **Request Body:**
  ```json
  {
    "assignmentId": 9,
    "latitude": 11.01250,
    "longitude": 76.95200,
    "accuracy": 5.2
  }
  ```
* **Success Response (`200 OK`):**
  ```json
  {
    "sessionId": 8,
    "assignmentId": 9,
    "status": "ACTIVE",
    "startedAt": "2026-11-01T12:08:15.000Z"
  }
  ```

---

### `POST /api/tracking/update`
Transmits periodic GPS heartbeat telemetry updates during response operations.
* **Access:** Responder only
* **Request Body:**
  ```json
  {
    "assignmentId": 9,
    "latitude": 11.01580,
    "longitude": 76.95450,
    "accuracy": 4.1,
    "heading": 42.0,
    "speed": 28.5
  }
  ```
* **Success Response (`200 OK`):**
  ```json
  {
    "assignmentId": 9,
    "latitude": 11.01580,
    "longitude": 76.95450,
    "recordedAt": "2026-11-01T12:08:30.000Z"
  }
  ```

---

### `GET /api/tracking/latest/{assignmentId}`
Retrieves the most recent known GPS telemetry breadcrumb for a given task.
* **Access:** Authenticated
* **Success Response (`200 OK`):**
  ```json
  {
    "assignmentId": 9,
    "latitude": 11.01580,
    "longitude": 76.95450,
    "accuracy": 4.1,
    "status": "ACTIVE",
    "updatedAt": "2026-11-01T12:08:30.000Z"
  }
  ```
* *Note: When the mission is concluded, `status` returns `"COMPLETED"`.*

---

### `GET /api/tracking/stream/{assignmentId}`
Server-Sent Events (SSE) live push stream for responder coordinates.
* **Access:** Authenticated
* **Output Stream:** `text/event-stream`
  ```
  data: {"assignmentId":9,"latitude":11.0158,"longitude":76.9545,"accuracy":4.1,"heading":42.0}
  ```

---

## 6. MEDIA & TACTICAL FIELD EVIDENCE

### `POST /api/media/upload`
Uploads tactical evidence (photo, video) captured in the field with geographic metadata.
* **Access:** Authenticated (`Bearer <token>`)
* **Content-Type:** `multipart/form-data`
* **Form Parameters:**
  * `file`: Binary file stream (JPEG, PNG, MP4, WebM)
  * `incidentId`: Integer identifier (e.g., `9`)
  * `incidentType`: String (`TASK` or `REPORT`)
  * `caption`: Text description
  * `latitude`: Float coordinate string (`11.01684`)
  * `longitude`: Float coordinate string (`76.95583`)
* **Success Response (`200 OK`):**
  ```json
  {
    "status": "success",
    "media": {
      "mediaId": 12,
      "incidentId": 9,
      "incidentType": "TASK",
      "fileName": "evidence_scene.png",
      "fileUrl": "/api/media/12",
      "contentType": "image/png",
      "caption": "Scene confirmation photo of submerged underpass",
      "latitude": 11.01684,
      "longitude": 76.95583
    }
  }
  ```

---

### `GET /api/media/{id}`
Streams binary media content with proper `Content-Type` headers.
* **Access:** Authenticated
* **Response:** Binary image/video payload.

---

## 7. TACTICAL COMMS & INCIDENT ROOM

### `POST /api/incidents/{id}/timeline/message?type={TASK|REPORT}`
Posts a field dispatch message or operational update to the incident timeline.
* **Access:** Authenticated
* **Request Body:**
  ```json
  {
    "message": "Evacuation in progress. 2 citizens brought to safety."
  }
  ```
* **Success Response (`200 OK`):**
  ```json
  {
    "status": "success",
    "timelineEntry": {
      "id": 45,
      "incidentId": 9,
      "incidentType": "TASK",
      "author": "responder@disaster.gov.in",
      "message": "Evacuation in progress. 2 citizens brought to safety.",
      "timestamp": "2026-11-01T12:10:00.000Z"
    }
  }
  ```

---

### `GET /api/incidents/{id}/room?type={TASK|REPORT}`
Retrieves complete synchronized audit trail and timeline messages for an incident.
* **Access:** Authenticated
* **Success Response (`200 OK`):**
  ```json
  {
    "incidentId": 9,
    "incidentType": "TASK",
    "messages": [
      {
        "author": "admin@disaster.gov.in",
        "message": "Task dispatched to Unit 1",
        "timestamp": "2026-11-01T12:06:00.000Z"
      },
      {
        "author": "responder@disaster.gov.in",
        "message": "Evacuation in progress. 2 citizens brought to safety.",
        "timestamp": "2026-11-01T12:10:00.000Z"
      }
    ]
  }
  ```

---

## 8. EMERGENCY ALERTS & BROADCASTING

### `POST /api/alerts`
Broadcasts an emergency regional warning to civilians.
* **Access:** Admin only
* **Headers:**
  ```http
  Authorization: Bearer <admin_jwt>
  X-Admin-Step-Up-Token: <step_up_token>
  ```
* **Request Body:**
  ```json
  {
    "title": "Municipal Flood Advisory: Cross Cut Sector Inundation",
    "message": "Severe stormwater drain overflow on Cross Cut Road.",
    "disasterType": "Flood",
    "severity": "High",
    "region": "Coimbatore",
    "latitude": 11.01684,
    "longitude": 76.95583,
    "broadcast": true
  }
  ```
* **Success Response (`200 OK`):**
  ```json
  {
    "status": "success",
    "alert": {
      "alertId": 384,
      "title": "Municipal Flood Advisory: Cross Cut Sector Inundation",
      "severity": "High",
      "region": "Coimbatore",
      "broadcast": true,
      "createdAt": "2026-11-01T12:07:00.000Z"
    }
  }
  ```
* **Error Response (`428 Precondition Required`):** `X-Admin-Step-Up-Token` missing or expired.

---

### `GET /api/alerts/active`
Retrieves all active emergency alerts for civilian display.
* **Access:** Public or Authenticated
* **Success Response (`200 OK`):** Array of active broadcasts within validity window.

---

## 9. OFFICIAL NDMA SACHET INGESTION

### `GET /api/official-alerts`
Returns parsed Common Alerting Protocol (CAP 1.2) alerts ingested from the national NDMA portal.
* **Access:** Authenticated
* **Query Parameters:** `state`, `severity`, `limit`
* **Success Response (`200 OK`):**
  ```json
  [
    {
      "identifier": "IN-TN-2026-11-01-001",
      "sender": "NDMA_SACHET",
      "headline": "Severe Thunderstorm Warning: Western Districts",
      "description": "Heavy rainfall expected across Coimbatore and Nilgiris...",
      "severity": "Severe",
      "areaDesc": "Coimbatore, Tamil Nadu",
      "source": "OFFICIAL NDMA",
      "sent": "2026-11-01T06:00:00.000Z"
    }
  ]
  ```

---

### `GET /api/official-alerts/status`
Returns ingestion pipeline telemetry, last sync timestamp, and total genuine alerts stored.
* **Access:** Public or Authenticated
* **Success Response (`200 OK`):**
  ```json
  {
    "status": "ACTIVE",
    "lastSyncTime": "2026-11-01T12:00:00.000Z",
    "totalAlertsCount": 42,
    "sourceUrl": "https://sachet.ndma.gov.in"
  }
  ```

---

## 10. ANALYTICS & SYSTEM METRICS

### `GET /api/analytics/summary`
Returns high-level system dashboard counts for administrative operations.
* **Access:** Admin only
* **Success Response (`200 OK`):**
  ```json
  {
    "activeIncidents": 5,
    "deployedResponders": 3,
    "resolvedIncidentsToday": 14,
    "activeBroadcasts": 2,
    "ndmaSyncStatus": "Healthy"
  }
  ```


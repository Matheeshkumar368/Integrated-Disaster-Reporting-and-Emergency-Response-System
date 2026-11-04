# Database Schema Documentation

## 1. Relational Entity-Relationship Diagram

```mermaid
erDiagram
    users ||--o{ alerts : "creates"
    users ||--o{ citizen_reports : "submits"
    users ||--o{ incident_reports : "files"
    users ||--o{ rescue_tasks : "assigned_to"
    users ||--o{ messages : "sends"
    users ||--o{ admin_audit_logs : "actor"

    users {
        BIGINT id PK
        VARCHAR email UK
        VARCHAR password
        VARCHAR name
        VARCHAR phone
        VARCHAR location
        VARCHAR role
        VARCHAR assigned_zone
        VARCHAR group_classification
        TEXT additional_details
        VARCHAR admin_pin
        BOOLEAN admin_pin_enrolled
        INT pin_failed_attempts
        TIMESTAMP pin_locked_until
        TIMESTAMP created_at
        TIMESTAMP last_login_at
    }

    alerts {
        BIGINT alert_id PK
        VARCHAR title
        TEXT message
        VARCHAR disaster_type
        VARCHAR severity
        VARCHAR region
        VARCHAR created_by
        TIMESTAMP created_at
        VARCHAR status
        VARCHAR media_url
        BOOLEAN broadcasted
        DOUBLE latitude
        DOUBLE longitude
    }

    citizen_reports {
        BIGINT report_id PK
        VARCHAR citizen_email
        TEXT message
        VARCHAR location
        LONGTEXT photo_data
        TIMESTAMP timestamp
        VARCHAR status
    }

    incident_reports {
        BIGINT report_id PK
        VARCHAR title
        TEXT description
        VARCHAR reported_by
        VARCHAR reporter_name
        TIMESTAMP timestamp
        DOUBLE latitude
        DOUBLE longitude
        VARCHAR location_name
        TEXT image_urls
        VARCHAR report_type
        VARCHAR status
        VARCHAR zone
    }

    rescue_tasks {
        BIGINT task_id PK
        VARCHAR title
        TEXT description
        VARCHAR zone
        VARCHAR priority
        VARCHAR status
        TIMESTAMP deadline
        TIMESTAMP created_at
        VARCHAR created_by
        VARCHAR assigned_to
        VARCHAR assigned_responder_name
        TEXT notes
        DOUBLE latitude
        DOUBLE longitude
        VARCHAR location_name
    }

    messages {
        BIGINT message_id PK
        VARCHAR sender_email
        VARCHAR sender_name
        VARCHAR sender_role
        VARCHAR receiver_email
        VARCHAR receiver_role
        TEXT message
        BIGINT related_alert_id
        TIMESTAMP sent_at
        VARCHAR status
    }

    admin_audit_logs {
        BIGINT id PK
        VARCHAR actor_email
        VARCHAR action
        VARCHAR target_entity
        VARCHAR target_id
        VARCHAR status
        VARCHAR details
        VARCHAR ip_address
        TIMESTAMP timestamp
    }
```

---

## 2. Table Specifications & Indexes

### Table: `users`
- Stores registered Citizens, Emergency Responders, and Administrators.
- **Primary Key:** `id` (BIGINT AUTO_INCREMENT)
- **Unique Key:** `email` (VARCHAR 255)
- **Indexes:**
  - `idx_email (email)`
  - `idx_role (role)`
  - `idx_role_assignedZone (role, assigned_zone)`

### Table: `alerts`
- Stores administrative disaster alerts broadcasted to citizens and responders.
- **Primary Key:** `alert_id` (BIGINT AUTO_INCREMENT)
- **Indexes:**
  - `idx_region (region)`
  - `idx_status (status)`
  - `idx_created_at (created_at)`

### Table: `citizen_reports`
- Stores live emergency reports submitted by citizens, including Base64 camera photo evidence.
- **Primary Key:** `report_id` (BIGINT AUTO_INCREMENT)
- **Indexes:**
  - `idx_report_citizen_email (citizen_email)`
  - `idx_report_status (status)`
  - `idx_report_timestamp (timestamp)`

### Table: `rescue_tasks`
- Stores dispatched rescue operations assigned to emergency responders.
- **Primary Key:** `task_id` (BIGINT AUTO_INCREMENT)
- **Indexes:**
  - `idx_assignedTo (assigned_to)`
  - `idx_zone (zone)`
  - `idx_status (status)`
  - `idx_assignedTo_status (assigned_to, status)`
  - `idx_zone_status (zone, status)`

### Table: `admin_audit_logs`
- Immutable security log for administrative Step-Up PIN events and privileged actions.
- **Primary Key:** `id` (BIGINT AUTO_INCREMENT)
- **Indexes:**
  - `idx_audit_actor (actor_email)`
  - `idx_audit_action (action)`
  - `idx_audit_timestamp (timestamp)`


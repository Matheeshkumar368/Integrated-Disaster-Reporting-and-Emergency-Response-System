# Real-Time Disaster Data Integration Specification
## NDMA SACHET Common Alerting Protocol (CAP 1.2) & IMD Weather Integration

### 1. Executive Summary

The Disaster Management & Alert System previously displayed seeded static disaster markers and sample alerts. This integration replaces fabricated data with genuine, live disaster warnings retrieved directly from the **National Disaster Management Authority (NDMA) SACHET Portal** (`https://sachet.ndma.gov.in/`) and weather observations from the **India Meteorological Department (IMD)** platform.

---

### 2. Architecture & Data Flow

```
[ Official NDMA SACHET Portal ]
       │
       ▼ (Periodic / Scheduled Ingestion: 5 mins)
[ NdmaSyncService.java ] ── (Safe XML Parser: XXE Disabled)
       │
       ├─► [ India CAP RSS ] (rss_india.xml: ~100 active alerts)
       ├─► [ CAP 1.2 XML ] (FetchXMLFile?identifier={guid})
       ├─► [ IndiaGeoGazetteer ] (Resolves District/State Centroids)
       │
       ▼ (Deduplication & Lifecycle Expiry Management)
[ MySQL Database: official_alerts table ]
       │
       ├─► GET /api/official-alerts/active (Leaflet Disaster Map)
       ├─► GET /api/official-alerts/status (Live Telemetry & Sync Health)
       ├─► POST /api/admin/ndma/sync (Admin On-Demand Synchronization)
       └─► GET /api/disasters (Legacy Bridge: 100% Genuine Events)
```

---

### 3. Core Components

#### A. OfficialAlert Entity (`com.disaster.management.entity.OfficialAlert`)
Dedicated JPA entity persisting CAP 1.2 attributes:
- `sourceAlertId`: Unique NDMA alert GUID (e.g. `1793294058125010`). Enforced with a unique database index.
- `capIdentifier`: Full CAP message identifier (e.g. `IN-1793294058125010_10`).
- `sourceAgency`: Issuing authority (e.g. `Assam-SDMA`, `IMD-Guwahati`, `CWC`, `Odisha-SDMA`).
- `sourceUrl`: Link to raw CAP XML file on NDMA portal.
- `hazardType`: Meteorological or geophysical hazard classification (e.g. `Thunderstorm with Lightning`, `Flood`, `Cyclone`, `Light Thunderstorms`).
- `severity`: Standard CAP severity level (`Extreme`, `Severe`, `Moderate`, `Minor`).
- `urgency`: Standard CAP urgency (`Immediate`, `Expected`, `Future`).
- `certainty`: Standard CAP certainty (`Observed`, `Likely`, `Possible`).
- `areaDescription`: Specific administrative description of affected territory (e.g. `Cachar district of Assam`, `13 districts of Odisha`).
- `latitude` & `longitude`: Geocoded coordinates.
- `locationPrecision`: Provenance level (`EXACT_COORDINATES`, `DISTRICT_CENTROID`, `STATE_CENTROID`, `NATIONAL_CENTROID`).
- `publishedTime`, `effectiveTime`, `onset`, `expiresTime`: ISO 8601 & RFC 1123 timestamps.
- `isExpired`: Boolean lifecycle flag automatically updated when current time exceeds `expiresTime`.

#### B. Safe XML Parsing & XXE Defense (`NdmaSyncService.java`)
All XML parsing disables XML External Entity (XXE) resolution and external DTD loading:
```java
DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
dbf.setNamespaceAware(true);
dbf.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
dbf.setFeature("http://xml.org/sax/features/external-general-entities", false);
dbf.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
dbf.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
dbf.setXIncludeAware(false);
dbf.setExpandEntityReferences(false);
dbf.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
```

#### C. Indian Geographic Gazetteer (`IndiaGeoGazetteer.java`)
Maps Indian administrative districts and states to verified centroid coordinates. Prioritizes the disaster's target `<areaDesc>` over issuing authority office metadata in `<headline>` and `<sender>`:
1. Matches district centroids across Tamil Nadu, Assam, Odisha, Kerala, Andhra Pradesh, West Bengal, Maharashtra, Gujarat, Karnataka, Delhi, and other states.
2. If district is unspecified, matches state centroid.
3. Fallback: National Centroid of India (`20.5937, 78.9629`).

#### D. Deduplication & Concurrency Guard
- **Non-Overlapping Execution**: Uses `AtomicBoolean isSyncing` so concurrent manual sync triggers and scheduled background tasks do not collide.
- **Deduplication**: Checks `officialAlertRepository.findBySourceAlertId(guid)`. Existing alerts are skipped or updated if revised; duplicate counter is incremented.
- **Automatic Expiry**: Calls `officialAlertRepository.markExpiredAlerts(now)` during each sync run to mark past alerts as expired.

#### E. Meteorological Integration (`ImdWeatherService.java`)
- Supports official IMD platform credentials (`IMD_API_KEY` or `IMD_JWT_TOKEN`).
- Provides transparent fallback to the World Meteorological Organization (WMO) Open-Meteo public feed, maintaining explicit source attribution (`isOfficialImd: false`, `source: "WMO Observation Network (Open-Meteo)"`).

---

### 4. REST Endpoints

| Method | Endpoint | Access | Purpose |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/official-alerts/active` | Public | Returns all active, unexpired government disaster alerts |
| `GET` | `/api/official-alerts` | Public | Lists official alerts with optional `severity`, `hazard`, `includeExpired` filters |
| `GET` | `/api/official-alerts/status` | Public | Live health metrics: last sync timestamp, records imported, duplicates filtered, error status |
| `POST` | `/api/admin/ndma/sync` | Admin Only | Triggers on-demand synchronization with NDMA SACHET |
| `GET` | `/api/disasters` | Public | Legacy bridge returning active official alerts formatted as disasters |
| `GET` | `/api/weather` | Public | Weather observation by city or coordinates (`lat`, `lon`) |

---

### 5. Verification Results

Live verification executed against live MySQL database and live NDMA SACHET portal:

```
================================================================
     DISASTER MANAGEMENT - NDMA SACHET LIVE FEED VERIFICATION   
================================================================
=== SECTION 1: FEED STATUS & TELEMETRY ===
 [PASS] NDMA Status Endpoint - Feed status: SUCCESS
 [PASS] NDMA Records Ingested - Imported records: 30
 [PASS] Active Alerts Available - Active unexpired alerts: 20
 [PASS] Error Handling - Last error: None

=== SECTION 2: OFFICIAL ALERTS REST API ===
 [PASS] Active Alerts Response - Count returned: 20
 [PASS] Alert Source Identifier - Source ID: 1793294771033015
 [PASS] Hazard Type Extracted - Hazard: Light Thunderstorms
 [PASS] CAP Severity Extracted - Severity: Moderate
 [PASS] Area Description Present - Area: Unakoti district of Tripura
 [PASS] Geographic Latitude - Latitude: 23.9408
 [PASS] Geographic Longitude - Longitude: 91.9882
 [PASS] Precision Classification - Precision: STATE_CENTROID

=== SECTION 3: LEGACY DISASTER ENDPOINT BRIDGE ===
 [PASS] Disasters Bridge Endpoint - Mapped items: 20
 [PASS] Mapped Disaster Hazard - Type: Light Thunderstorms
 [PASS] Mapped Disaster Coordinates - Coords: 23.9408, 91.9882

=== SECTION 4: WEATHER OBSERVATION INTEGRATION ===
 [PASS] Weather API Status - Status: success
 [PASS] Weather Source Provenance - Source: WMO Observation Network (Open-Meteo)
 [PASS] Weather Condition - Condition: Partly Cloudy

=== SECTION 5: ADMIN MANUAL FEED SYNCHRONIZATION ===
 [PASS] Manual Sync Execution - Message: NDMA SACHET synchronization completed successfully
 [PASS] Duplicate Prevention Logic - Duplicates filtered: 30

=== SECTION 6: DIRECT DATABASE VERIFICATION (MySQL) ===
 [PASS] MySQL Table: official_alerts exists - Total persisted rows: 30
 [PASS] MySQL Active Official Count Match - MySQL active: 20 == API active: 20

Total Verification Checks: 22
Passed Checks            : 22 (100%)
Failed Checks            : 0
================================================================
```


# External Data Sources Guide
## NDMA SACHET Portal & India Meteorological Department (IMD)

This guide documents the external feeds, API endpoints, authentication models, and data handling policies used in the Disaster Management & Alert System.

---

### 1. NDMA SACHET Disaster Alert Portal

#### Overview
- **Portal**: [https://sachet.ndma.gov.in/](https://sachet.ndma.gov.in/)
- **Authority**: National Disaster Management Authority (NDMA), Government of India
- **Standard**: OASIS Common Alerting Protocol (CAP) Version 1.2
- **Feed Directory**: [https://sachet.ndma.gov.in/CapFeed](https://sachet.ndma.gov.in/CapFeed)

#### Feed Endpoints
1. **India All-Hazards RSS Feed**:
   - `URL`: `https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml`
   - `Access`: Public / Unauthenticated
   - `Content-Type`: `application/xml`
   - `Payload Size`: ~70 KB to 120 KB
   - `Update Frequency`: Every 3 to 10 minutes
   - `Item Schema`:
     - `<title>`: Bengali/Odia/English alert headline
     - `<link>`: URL pointing to detailed CAP 1.2 XML file
     - `<author>`: E.g., `controlroom@ndma.gov.in (IMD Guwahati)`
     - `<guid>`: Unique 16-digit source alert ID (e.g. `1793294058125010`)
     - `<pubDate>`: RFC 1123 timestamp (e.g., `Thu, 29 Oct 2026 17:17:19 GMT`)

2. **CAP 1.2 XML Alert Detail Endpoint**:
   - `URL`: `https://sachet.ndma.gov.in/cap_public_website/FetchXMLFile?identifier={guid}`
   - `Access`: Public / Unauthenticated
   - `Content-Type`: `text/xml;charset=UTF-8`
   - `Key Fields Extracted`:
     - `<cap:identifier>`: Unique national message ID
     - `<cap:sender>`: State Disaster Management Authority or Regional Met Center
     - `<cap:event>`: Hazard type (Thunderstorm with Lightning, Flood, Cyclone, etc.)
     - `<cap:urgency>`: Immediate, Expected, Future
     - `<cap:severity>`: Extreme, Severe, Moderate, Minor
     - `<cap:certainty>`: Observed, Likely, Possible
     - `<cap:effective>`, `<cap:expires>`: ISO 8601 timestamps
     - `<cap:headline>`, `<cap:description>`, `<cap:instruction>`: Advisory text
     - `<cap:areaDesc>`: Affected district/state names

3. **Geospatial / Polygon Access Policy**:
   - `URL`: `FetchPolygonXMLFile?identifier={guid}`
   - `Behavior`: Returns HTTP 403 Forbidden to public external scrapers (session-protected).
   - `System Handling`: In compliance with standard web integrity and rate-limiting policies, the application does not scrape internal session tokens. Instead, affected regions are resolved through the built-in `IndiaGeoGazetteer` to precise district centroids and tagged as `locationPrecision: "DISTRICT_CENTROID"`.

---

### 2. India Meteorological Department (IMD) API

#### Overview
- **Portal**: [https://api.imd.gov.in/public/index.php](https://api.imd.gov.in/public/index.php)
- **Authority**: Ministry of Earth Sciences, Government of India
- **Authentication**: Bearer JWT / API Key

#### Onboarding Instructions for Production
To connect the application directly to the IMD official platform in production:
1. Register an institutional/academic account on the IMD API platform: `https://api.imd.gov.in/public/index.php`.
2. Generate an API Key or JWT Bearer Token from the API management console.
3. Configure the following environment variables in your `.env` or cloud hosting dashboard:
   ```env
   IMD_API_URL=https://api.imd.gov.in/api
   IMD_API_KEY=your_registered_imd_api_key
   # OR
   IMD_JWT_TOKEN=your_registered_imd_jwt_token
   ```

#### Graceful Fallback Policy
When IMD credentials are not configured (e.g., in local demonstration or offline academic evaluation), the system automatically uses the public WMO Observation Network (Open-Meteo) and labels the source transparently:
- `isOfficialImd`: `false`
- `source`: `"WMO Observation Network (Open-Meteo)"`
- `condition`: Standard WMO code description (e.g. `Clear Sky`, `Partly Cloudy`, `Rain Showers`)
No synthetic or fabricated numbers are ever presented as real IMD readings.

---

### 3. Upstream Outage & Fault Tolerance

The backend architecture incorporates several resiliency measures:
1. **Network Timeout Enforcement**: 10-second connect timeout and 15-second read timeout prevents hung background threads.
2. **Safe XML Parser**: Hardened against XXE, Billion Laughs, and external entity injection attacks.
3. **Database Fallback**: In the event of an upstream outage, cached unexpired alerts stored in MySQL continue to serve the dashboard and map.
4. **Stale Data Prevention**: The synchronization service marks expired alerts as `isExpired = true` after their `expiresTime` passes.


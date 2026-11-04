# ==============================================================================
# DISASTER MANAGEMENT SYSTEM - NDMA SACHET LIVE FEED VERIFICATION SCRIPT
# Tests Live RSS Ingestion, Safe Parsing, MySQL Persistence, Telemetry, and API
# ==============================================================================

$baseUrl = "http://localhost:8080"
$mysqlPath = if ($env:MYSQL_EXE) { $env:MYSQL_EXE } else { "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" }
$dbUser = if ($env:DB_USERNAME) { $env:DB_USERNAME } else { "root" }
$dbPass = if ($env:DB_PASSWORD) { "-p$env:DB_PASSWORD" } else { "" }

$totalTests = 0
$passedTests = 0

function Assert-Check {
    param([string]$name, [bool]$condition, [string]$detail)
    $script:totalTests++
    if ($condition) {
        $script:passedTests++
        Write-Host " [PASS] $name - $detail" -ForegroundColor Green
    } else {
        Write-Host " [FAIL] $name - $detail" -ForegroundColor Red
    }
}

Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "     DISASTER MANAGEMENT - NDMA SACHET LIVE FEED VERIFICATION   " -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan

# 1. Check Sync Telemetry Endpoint
Write-Host "`n=== SECTION 1: FEED STATUS & TELEMETRY ===" -ForegroundColor Yellow
$statusRes = Invoke-RestMethod -Uri "$baseUrl/api/official-alerts/status" -Method GET
Assert-Check "NDMA Status Endpoint" ($statusRes.status -eq "SUCCESS" -or $statusRes.status -eq "RUNNING") ("Feed status: " + $statusRes.status)
Assert-Check "NDMA Records Ingested" ($statusRes.totalRecordsImported -gt 0) ("Imported records: " + $statusRes.totalRecordsImported)
Assert-Check "Active Alerts Available" ($statusRes.activeOfficialAlertsCount -gt 0) ("Active unexpired alerts: " + $statusRes.activeOfficialAlertsCount)
Assert-Check "Error Handling" ($null -eq $statusRes.lastError -or $statusRes.lastError -eq "") ("Last error: None")

# 2. Check Active Official Alerts List
Write-Host "`n=== SECTION 2: OFFICIAL ALERTS REST API ===" -ForegroundColor Yellow
$activeAlerts = Invoke-RestMethod -Uri "$baseUrl/api/official-alerts/active" -Method GET
Assert-Check "Active Alerts Response" ($activeAlerts.Count -gt 0) ("Count returned: " + $activeAlerts.Count)

$sampleAlert = $activeAlerts[0]
Assert-Check "Alert Source Identifier" (-not [string]::IsNullOrEmpty($sampleAlert.sourceAlertId)) ("Source ID: " + $sampleAlert.sourceAlertId)
Assert-Check "Hazard Type Extracted" (-not [string]::IsNullOrEmpty($sampleAlert.hazardType)) ("Hazard: " + $sampleAlert.hazardType)
Assert-Check "CAP Severity Extracted" (-not [string]::IsNullOrEmpty($sampleAlert.severity)) ("Severity: " + $sampleAlert.severity)
Assert-Check "Area Description Present" (-not [string]::IsNullOrEmpty($sampleAlert.areaDescription)) ("Area: " + $sampleAlert.areaDescription)
Assert-Check "Geographic Latitude" ($sampleAlert.latitude -gt 0) ("Latitude: " + $sampleAlert.latitude)
Assert-Check "Geographic Longitude" ($sampleAlert.longitude -gt 0) ("Longitude: " + $sampleAlert.longitude)
Assert-Check "Precision Classification" (-not [string]::IsNullOrEmpty($sampleAlert.locationPrecision)) ("Precision: " + $sampleAlert.locationPrecision)

# 3. Check Mapped Disasters Endpoint
Write-Host "`n=== SECTION 3: LEGACY DISASTER ENDPOINT BRIDGE ===" -ForegroundColor Yellow
$disasters = Invoke-RestMethod -Uri "$baseUrl/api/disasters" -Method GET
Assert-Check "Disasters Bridge Endpoint" ($disasters.Count -gt 0) ("Mapped items: " + $disasters.Count)
$sampleDisaster = $disasters[0]
Assert-Check "Mapped Disaster Hazard" (-not [string]::IsNullOrEmpty($sampleDisaster.type)) ("Type: " + $sampleDisaster.type)
Assert-Check "Mapped Disaster Coordinates" ($sampleDisaster.latitude -gt 0 -and $sampleDisaster.longitude -gt 0) ("Coords: " + $sampleDisaster.latitude + ", " + $sampleDisaster.longitude)

# 4. Check Weather Observation
Write-Host "`n=== SECTION 4: WEATHER OBSERVATION INTEGRATION ===" -ForegroundColor Yellow
$weather = Invoke-RestMethod -Uri "$baseUrl/api/weather?city=Chennai" -Method GET
Assert-Check "Weather API Status" ($weather.status -eq "success") ("Status: " + $weather.status)
Assert-Check "Weather Source Provenance" (-not [string]::IsNullOrEmpty($weather.source)) ("Source: " + $weather.source)
Assert-Check "Weather Condition" (-not [string]::IsNullOrEmpty($weather.condition)) ("Condition: " + $weather.condition)

# 5. Check Admin Manual Feed Sync
Write-Host "`n=== SECTION 5: ADMIN MANUAL FEED SYNCHRONIZATION ===" -ForegroundColor Yellow
$loginBody = @{ email = "admin@disaster.gov.in"; password = "admin123" } | ConvertTo-Json
$adminLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method POST -Body $loginBody -ContentType "application/json"
$adminToken = $adminLogin.token

$syncHeaders = @{ Authorization = "Bearer $adminToken" }
$manualSyncRes = Invoke-RestMethod -Uri "$baseUrl/api/admin/ndma/sync" -Method POST -Headers $syncHeaders
Assert-Check "Manual Sync Execution" ($manualSyncRes.status -eq "success") ("Message: " + $manualSyncRes.message)
Assert-Check "Duplicate Prevention Logic" ($manualSyncRes.metrics.totalDuplicatesPrevented -ge 0) ("Duplicates filtered: " + $manualSyncRes.metrics.totalDuplicatesPrevented)

# 6. Direct MySQL Verification
Write-Host "`n=== SECTION 6: DIRECT DATABASE VERIFICATION (MySQL) ===" -ForegroundColor Yellow
$latestStatus = Invoke-RestMethod -Uri "$baseUrl/api/official-alerts/status" -Method GET
$mysqlCount = & $mysqlPath -u $dbUser "-p$dbPass" -N -e "USE disaster_management; SELECT COUNT(*) FROM official_alerts WHERE is_expired = FALSE;"
$mysqlTotal = & $mysqlPath -u $dbUser "-p$dbPass" -N -e "USE disaster_management; SELECT COUNT(*) FROM official_alerts;"
Assert-Check "MySQL Table: official_alerts exists" ($mysqlTotal.Trim() -gt 0) ("Total persisted rows: " + $mysqlTotal.Trim())
Assert-Check "MySQL Active Official Count Match" ($mysqlCount.Trim() -eq $latestStatus.activeOfficialAlertsCount) ("MySQL active: " + $mysqlCount.Trim() + " == API active: " + $latestStatus.activeOfficialAlertsCount)

Write-Host "`n================================================================" -ForegroundColor Cyan
Write-Host "                NDMA FEED VERIFICATION SUMMARY                  " -ForegroundColor Cyan
Write-Host "================================================================" -ForegroundColor Cyan
Write-Host "Total Verification Checks: $totalTests"
Write-Host "Passed Checks            : $passedTests"
Write-Host "Failed Checks            : $($totalTests - $passedTests)"

if ($passedTests -eq $totalTests) {
    Write-Host "`nSUCCESS: NDMA SACHET REAL-TIME DISASTER INTEGRATION IS 100% OPERATIONAL!" -ForegroundColor Green
} else {
    Write-Host "`nFAILURE: Some verification checks failed." -ForegroundColor Red
}
Write-Host "================================================================" -ForegroundColor Cyan


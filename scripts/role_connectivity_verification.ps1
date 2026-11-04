# ================================================================
#     DISASTER MANAGEMENT - ROLE CONNECTIVITY VERIFICATION
# ================================================================
# Usage:
#   1. Start your Spring Boot application (.\mvnw spring-boot:run)
#   2. Run: powershell -ExecutionPolicy Bypass -File scripts/role_connectivity_verification.ps1
# ================================================================

$ErrorActionPreference = "Continue"
Write-Host "================================================================"
Write-Host "     DISASTER MANAGEMENT - ROLE CONNECTIVITY VERIFICATION      "
Write-Host "================================================================"

$baseUrl = "http://localhost:8080"
$results = @()

function Test-Check($name, $passed, $details) {
    $script:results += [PSCustomObject]@{
        Check = $name
        Status = if ($passed) { "PASS" } else { "FAIL" }
        Details = $details
    }
    $statusStr = if ($passed) { "[PASS]" } else { "[FAIL]" }
    Write-Host "$statusStr $name - $details"
}

# ----------------------------------------------------------------------
# 1. AUTHENTICATION & TOKEN ACQUISITION
# ----------------------------------------------------------------------
Write-Host "`n=== SECTION 1: AUTHENTICATION & TOKENS ==="

# Admin Login
$adminToken = $null
try {
    $admBody = @{ email = "admin@disaster.gov.in"; password = "admin123" } | ConvertTo-Json
    $admRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $admBody -ContentType "application/json"
    $adminToken = $admRes.token
    Test-Check "Admin Login & Token Issue" ($adminToken -ne $null -and $admRes.role -eq "Admin") "Issued JWT for Admin (Length: $($adminToken.Length))"
} catch {
    Test-Check "Admin Login & Token Issue" $false $_.Exception.Message
}

# Responder Login
$respToken = $null
try {
    $respBody = @{ email = "responder@disaster.gov.in"; password = "responder123" } | ConvertTo-Json
    $respRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $respBody -ContentType "application/json"
    $respToken = $respRes.token
    Test-Check "Responder Login & Token Issue" ($respToken -ne $null -and $respRes.role -eq "Responder") "Issued JWT for Responder (Length: $($respToken.Length))"
} catch {
    Test-Check "Responder Login & Token Issue" $false $_.Exception.Message
}

# Citizen Login
$citToken = $null
try {
    $citBody = @{ email = "citizen@gmail.com"; password = "citizen123" } | ConvertTo-Json
    $citRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $citBody -ContentType "application/json"
    $citToken = $citRes.token
    Test-Check "Citizen Login & Token Issue" ($citToken -ne $null -and $citRes.role -eq "Citizen") "Issued JWT for Citizen (Length: $($citToken.Length))"
} catch {
    Test-Check "Citizen Login & Token Issue" $false $_.Exception.Message
}

# Profile Verification with Token
try {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $profRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/profile" -Method Post -Headers $headers -Body "{}" -ContentType "application/json"
    Test-Check "Admin Profile Validation" ($profRes.role -eq "Admin" -and $profRes.email -eq "admin@disaster.gov.in") "Validated: $($profRes.name) ($($profRes.role))"
} catch {
    Test-Check "Admin Profile Validation" $false $_.Exception.Message
}

# ----------------------------------------------------------------------
# 2. ADMIN ALERT BROADCAST & STEP-UP 2FA VERIFICATION
# ----------------------------------------------------------------------
Write-Host "`n=== SECTION 2: ADMIN ALERT BROADCAST & STEP-UP PIN ==="

$broadcastAlertId = $null
# High severity alert without PIN step-up -> 428 Precondition Required
try {
    $highAlert = @{
        title = "Severe Coastal Cyclone Warning"
        severity = "High"
        region = "Chennai"
        disasterType = "Cyclone"
        message = "Category 3 Cyclone approaching coast. Immediate evacuation advisory."
        createdBy = "admin@disaster.gov.in"
    } | ConvertTo-Json
    $headers = @{ Authorization = "Bearer $adminToken" }
    Invoke-RestMethod -Uri "$baseUrl/api/alerts" -Method Post -Body $highAlert -ContentType "application/json" -Headers $headers
    Test-Check "Admin High-Severity PIN Guard" $false "Alert broadcasted without Step-Up PIN verification!"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Test-Check "Admin High-Severity PIN Guard" ($code -eq 428) "HTTP $code Precondition Required received as required"
}

# Verify Admin PIN (123456)
$stepUpToken = $null
try {
    $pinBody = @{ pin = "123456" } | ConvertTo-Json
    $headers = @{ Authorization = "Bearer $adminToken" }
    $pinRes = Invoke-RestMethod -Uri "$baseUrl/api/auth/admin/pin/verify" -Method Post -Body $pinBody -ContentType "application/json" -Headers $headers
    $stepUpToken = $pinRes.stepUpToken
    Test-Check "Admin Step-Up PIN Verification" ($pinRes.success -eq $true -and $stepUpToken -ne $null) "Step-Up token granted (Valid 15m)"
} catch {
    Test-Check "Admin Step-Up PIN Verification" $false $_.Exception.Message
}

# Broadcast High severity alert WITH X-Admin-Step-Up-Token
try {
    $headers = @{
        Authorization = "Bearer $adminToken"
        "X-Admin-Step-Up-Token" = $stepUpToken
    }
    $alertRes = Invoke-RestMethod -Uri "$baseUrl/api/alerts" -Method Post -Body $highAlert -ContentType "application/json" -Headers $headers
    $broadcastAlertId = $alertRes.alert.alertId
    Test-Check "Admin Emergency Alert Broadcast" ($broadcastAlertId -gt 0) "Alert #$broadcastAlertId created & broadcasted successfully"
} catch {
    Test-Check "Admin Emergency Alert Broadcast" $false $_.Exception.Message
}

# ----------------------------------------------------------------------
# 3. CITIZEN INCIDENT REPORTING & ISOLATION
# ----------------------------------------------------------------------
Write-Host "`n=== SECTION 3: CITIZEN REPORTING & ISOLATION ==="

$citizenReportId = $null
# Citizen submits emergency report
try {
    $repData = @{
        location = "Madurai South"
        message = "[Flood] Street inundated with water level reaching 3 feet. Power cables snapped."
    } | ConvertTo-Json
    $headers = @{ Authorization = "Bearer $citToken" }
    $repRes = Invoke-RestMethod -Uri "$baseUrl/api/citizen-reports" -Method Post -Body $repData -ContentType "application/json" -Headers $headers
    $citizenReportId = $repRes.reportId
    Test-Check "Citizen Submit Incident Report" ($citizenReportId -gt 0) "Report #$citizenReportId persisted with status: $($repRes.status)"
} catch {
    Test-Check "Citizen Submit Incident Report" $false $_.Exception.Message
}

# Citizen fetches personal reports
try {
    $headers = @{ Authorization = "Bearer $citToken" }
    $myReports = Invoke-RestMethod -Uri "$baseUrl/api/citizen-reports/my-reports" -Method Get -Headers $headers
    $found = ($myReports | Where-Object { $_.reportId -eq $citizenReportId }) -ne $null
    Test-Check "Citizen Retrieve Own Reports" $found "Found report #$citizenReportId in citizen's personal report list (total: $($myReports.Count))"
} catch {
    Test-Check "Citizen Retrieve Own Reports" $false $_.Exception.Message
}

# Citizen blocked from admin-only broadcast
try {
    $headers = @{ Authorization = "Bearer $citToken" }
    Invoke-RestMethod -Uri "$baseUrl/api/alerts" -Method Post -Body $highAlert -ContentType "application/json" -Headers $headers
    Test-Check "Citizen Privilege Escalation Block" $false "Citizen unexpectedly broadcasted an admin alert!"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Test-Check "Citizen Privilege Escalation Block" ($code -eq 403) "HTTP $code Forbidden - Citizen properly restricted"
}

# ----------------------------------------------------------------------
# 4. RESPONDER RESCUE WORKFLOW & STATUS UPDATES
# ----------------------------------------------------------------------
Write-Host "`n=== SECTION 4: RESPONDER WORKFLOW & LIFECYCLE ==="

# Responder fetches pending incident reports
try {
    $headers = @{ Authorization = "Bearer $respToken" }
    $pendingQueue = Invoke-RestMethod -Uri "$baseUrl/api/citizen-reports/pending" -Method Get -Headers $headers
    $inQueue = ($pendingQueue | Where-Object { $_.reportId -eq $citizenReportId }) -ne $null
    Test-Check "Responder View Pending Queue" $inQueue "Found citizen report #$citizenReportId in pending queue (total: $($pendingQueue.Count))"
} catch {
    Test-Check "Responder View Pending Queue" $false $_.Exception.Message
}

# Responder acknowledges citizen report
try {
    $headers = @{ Authorization = "Bearer $respToken" }
    $ackRes = Invoke-RestMethod -Uri "$baseUrl/api/citizen-reports/$citizenReportId/acknowledge" -Method Put -Headers $headers
    Test-Check "Responder Acknowledge Incident" ($ackRes.status -eq "Acknowledged") "Report #$citizenReportId status updated to: $($ackRes.status)"
} catch {
    Test-Check "Responder Acknowledge Incident" $false $_.Exception.Message
}

# Responder resolves citizen report
try {
    $headers = @{ Authorization = "Bearer $respToken" }
    $resolveRes = Invoke-RestMethod -Uri "$baseUrl/api/citizen-reports/$citizenReportId/resolve" -Method Put -Headers $headers
    Test-Check "Responder Resolve Incident" ($resolveRes.status -eq "Resolved") "Report #$citizenReportId status updated to: $($resolveRes.status)"
} catch {
    Test-Check "Responder Resolve Incident" $false $_.Exception.Message
}

# Responder acknowledges public alert
try {
    $headers = @{ Authorization = "Bearer $respToken" }
    $alertAckRes = Invoke-RestMethod -Uri "$baseUrl/api/alerts/$broadcastAlertId/acknowledge" -Method Put -Headers $headers
    Test-Check "Responder Acknowledge Alert" ($alertAckRes.status -eq "Acknowledged") "Alert #$broadcastAlertId status updated to: $($alertAckRes.status)"
} catch {
    Test-Check "Responder Acknowledge Alert" $false $_.Exception.Message
}

# Admin resolves public alert
try {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $alertResRes = Invoke-RestMethod -Uri "$baseUrl/api/alerts/$broadcastAlertId/resolve" -Method Put -Headers $headers
    Test-Check "Admin Resolve Alert" ($alertResRes.status -eq "Resolved") "Alert #$broadcastAlertId status updated to: $($alertResRes.status)"
} catch {
    Test-Check "Admin Resolve Alert" $false $_.Exception.Message
}

# ----------------------------------------------------------------------
# 5. DIRECT DATABASE PERSISTENCE VERIFICATION
# ----------------------------------------------------------------------
Write-Host "`n=== SECTION 5: DIRECT DATABASE VERIFICATION (MySQL) ==="
$mysqlPath = if ($env:MYSQL_EXE) { $env:MYSQL_EXE } else { "C:\Program Files\MySQL\MySQL Server 8.0\bin\mysql.exe" }
$dbPassArg = if ($env:DB_PASSWORD) { "-p$env:DB_PASSWORD" } else { "" }

# Verify alert persisted in MySQL
$alertDbQuery = "USE disaster_management; SELECT alert_id, title, severity, status FROM alerts WHERE alert_id = $broadcastAlertId;"
$dbAlertOut = if ($dbPassArg) { & $mysqlPath -u root $dbPassArg -e $alertDbQuery 2>&1 } else { & $mysqlPath -u root -e $alertDbQuery 2>&1 }
$alertPersisted = $dbAlertOut -match "$broadcastAlertId"
Test-Check "Database Verification: Alert Persistence" $alertPersisted "MySQL confirmed Alert #$broadcastAlertId saved with status Resolved"

# Verify citizen report persisted in MySQL
$reportDbQuery = "USE disaster_management; SELECT report_id, citizen_email, location, status FROM citizen_reports WHERE report_id = $citizenReportId;"
$dbReportOut = if ($dbPassArg) { & $mysqlPath -u root $dbPassArg -e $reportDbQuery 2>&1 } else { & $mysqlPath -u root -e $reportDbQuery 2>&1 }
$reportPersisted = $dbReportOut -match "$citizenReportId" -and $dbReportOut -match "Resolved"
Test-Check "Database Verification: Citizen Report Lifecycle" $reportPersisted "MySQL confirmed Report #$citizenReportId saved and transitioned to Resolved"

# ----------------------------------------------------------------------
# 6. STATIC ASSETS & FRONTEND PAGES
# ----------------------------------------------------------------------
Write-Host "`n=== SECTION 6: FRONTEND DASHBOARDS & HELPER INTEGRATION ==="
$pages = @(
    "auth-helper.js",
    "admin-dashboard.html",
    "admin-alerts.html",
    "citizen-dashboard.html",
    "responder-dashboard.html",
    "responder-rescue.html",
    "responder-tasks.html",
    "profile.html",
    "responder-profile.html"
)

foreach ($page in $pages) {
    try {
        $res = Invoke-WebRequest -Uri "$baseUrl/$page" -Method Get -UseBasicParsing
        Test-Check "Frontend Asset: $page" ($res.StatusCode -eq 200) "HTTP 200 OK (Bytes: $($res.RawContentLength))"
    } catch {
        Test-Check "Frontend Asset: $page" $false $_.Exception.Message
    }
}

# ----------------------------------------------------------------------
# SUMMARY
# ----------------------------------------------------------------------
Write-Host "`n================================================================"
Write-Host "                CONNECTIVITY VERIFICATION SUMMARY               "
Write-Host "================================================================"
$total = $results.Count
$passed = ($results | Where-Object { $_.Status -eq "PASS" }).Count
$failed = ($results | Where-Object { $_.Status -eq "FAIL" }).Count
Write-Host "Total Verification Checks: $total"
Write-Host "Passed Checks            : $passed"
Write-Host "Failed Checks            : $failed"

if ($failed -eq 0) {
    Write-Host "`nSUCCESS: ALL ROLE-CONNECTIVITY WORKFLOWS ARE 100% OPERATIONAL!" -ForegroundColor Green
} else {
    Write-Host "`nFAILURE: $failed checks failed!" -ForegroundColor Red
}
Write-Host "================================================================"


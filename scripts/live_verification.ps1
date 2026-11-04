# ================================================================
#       DISASTER MANAGEMENT SYSTEM - LIVE VERIFICATION SUITE
# ================================================================
# Usage:
#   1. Start your Spring Boot application (.\mvnw spring-boot:run)
#   2. Run: powershell -ExecutionPolicy Bypass -File scripts/live_verification.ps1
# ================================================================

$ErrorActionPreference = "Continue"
Write-Host "================================================================"
Write-Host "       DISASTER MANAGEMENT SYSTEM - LIVE VERIFICATION SUITE       "
Write-Host "================================================================"

$baseUrl = "http://localhost:8080"
$results = @()

function Record-Result($testName, $passed, $details) {
    $script:results += [PSCustomObject]@{
        Test = $testName
        Status = if ($passed) { "PASS" } else { "FAIL" }
        Details = $details
    }
    $statusStr = if ($passed) { "[PASS]" } else { "[FAIL]" }
    Write-Host "$statusStr $testName : $details"
}

# 1. Health & Actuator
Write-Host "`n--- CHECK 1: Health & Database Connectivity ---"
try {
    $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method Get
    $dbStatus = $health.components.db.status
    Record-Result "Actuator Health Check" ($health.status -eq "UP") "Overall status: $($health.status), DB status: $dbStatus"
} catch {
    Record-Result "Actuator Health Check" $false $_.Exception.Message
}

# 2. Authentication Flow (Citizen, Responder, Admin, Bad Credentials)
Write-Host "`n--- CHECK 2: Authentication Flows ---"
$adminToken = $null
$responderToken = $null
$citizenToken = $null

# Bad credentials
try {
    $badLogin = @{ email = "baduser@example.com"; password = "wrongpassword" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $badLogin -ContentType "application/json"
    Record-Result "Bad Credentials Rejection" $false "Should have failed with 401"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Record-Result "Bad Credentials Rejection" ($statusCode -eq 401) "HTTP Status: $statusCode (Expected 401 Unauthorized)"
}

# Citizen Login
try {
    $citizenBody = @{ email = "citizen@gmail.com"; password = "citizen123" } | ConvertTo-Json
    $citResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $citizenBody -ContentType "application/json"
    $citizenToken = $citResp.token
    Record-Result "Citizen Login" ($citizenToken -ne $null -and $citResp.role -eq "Citizen") "Token received, Role: $($citResp.role)"
} catch {
    Record-Result "Citizen Login" $false $_.Exception.Message
}

# Responder Login (responder123)
try {
    $respBody = @{ email = "responder@disaster.gov.in"; password = "responder123" } | ConvertTo-Json
    $resResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $respBody -ContentType "application/json"
    $responderToken = $resResp.token
    Record-Result "Responder Login" ($responderToken -ne $null -and $resResp.role -eq "Responder") "Token received, Role: $($resResp.role)"
} catch {
    Record-Result "Responder Login" $false $_.Exception.Message
}

# Admin Login
try {
    $adminBody = @{ email = "admin@disaster.gov.in"; password = "admin123" } | ConvertTo-Json
    $admResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -Body $adminBody -ContentType "application/json"
    $adminToken = $admResp.token
    Record-Result "Admin Login" ($adminToken -ne $null -and $admResp.role -eq "Admin") "Token received, Role: $($admResp.role)"
} catch {
    Record-Result "Admin Login" $false $_.Exception.Message
}

# 3. Role-Based Access Control
Write-Host "`n--- CHECK 3: Role-Based Access Control (RBAC) ---"
# Unauthenticated attempt to POST /api/alerts
try {
    $dummyAlert = @{ title = "Unauthorized Alert"; severity = "Low"; message = "Test"; region = "City"; disasterType = "Flood"; createdBy = "Anonymous" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/alerts" -Method Post -Body $dummyAlert -ContentType "application/json"
    Record-Result "RBAC Unauthenticated Block" $false "Allowed unauthenticated alert creation"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Record-Result "RBAC Unauthenticated Block" ($code -eq 401) "HTTP Status: $code (Expected 401 Unauthorized)"
}

# Citizen token attempting Admin endpoint POST /api/alerts
try {
    $dummyAlert = @{ title = "Citizen Alert"; severity = "Low"; message = "Test"; region = "City"; disasterType = "Flood"; createdBy = "Citizen" } | ConvertTo-Json
    $headers = @{ Authorization = "Bearer $citizenToken" }
    Invoke-RestMethod -Uri "$baseUrl/api/alerts" -Method Post -Body $dummyAlert -ContentType "application/json" -Headers $headers
    Record-Result "RBAC Citizen Privilege Escalation Block" $false "Citizen successfully posted alert"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Record-Result "RBAC Citizen Privilege Escalation Block" ($code -eq 403) "HTTP Status: $code (Expected 403 Forbidden)"
}

# Citizen token attempting Admin CSV Export GET /api/reports/export/csv
try {
    $headers = @{ Authorization = "Bearer $citizenToken" }
    Invoke-RestMethod -Uri "$baseUrl/api/reports/export/csv" -Method Get -Headers $headers
    Record-Result "RBAC Citizen Export Block" $false "Citizen unexpectedly exported admin CSV report"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Record-Result "RBAC Citizen Export Block" ($code -eq 403) "HTTP Status: $code (Expected 403 Forbidden)"
}

# 4. Admin Step-Up Security PIN (2FA) Workflow
Write-Host "`n--- CHECK 4: Admin Step-Up PIN (2FA) Security ---"
$stepUpToken = $null
# Check PIN status
try {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $pinStatus = Invoke-RestMethod -Uri "$baseUrl/api/auth/admin/pin/status" -Method Get -Headers $headers
    Record-Result "Admin PIN Status Check" ($pinStatus.enrolled -eq $true) "Admin enrolled: $($pinStatus.enrolled)"
} catch {
    Record-Result "Admin PIN Status Check" $false $_.Exception.Message
}

# Attempt High-severity alert WITHOUT Step-Up token -> Should fail with 428 Precondition Required
try {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $highAlert = @{
        title = "Severe Flash Flood";
        severity = "High";
        region = "Zone A";
        message = "Water levels rising rapidly above danger mark";
        disasterType = "Flood";
        createdBy = "admin@disaster.gov.in"
    } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/alerts" -Method Post -Body $highAlert -ContentType "application/json" -Headers $headers
    Record-Result "Admin Step-Up High Severity Guard" $false "High severity alert created without PIN step-up!"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Record-Result "Admin Step-Up High Severity Guard" ($code -eq 428) "HTTP Status: $code (Expected 428 Precondition Required)"
}

# Verify invalid PIN (999999) -> returns 401 Unauthorized
try {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $invalidPinBody = @{ pin = "999999" } | ConvertTo-Json
    Invoke-RestMethod -Uri "$baseUrl/api/auth/admin/pin/verify" -Method Post -Body $invalidPinBody -ContentType "application/json" -Headers $headers
    Record-Result "Invalid Admin PIN Rejection" $false "Invalid PIN was accepted"
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    Record-Result "Invalid Admin PIN Rejection" ($code -eq 401) "HTTP Status: $code (Expected 401 Unauthorized)"
}

# Verify valid PIN (123456)
try {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $validPinBody = @{ pin = "123456" } | ConvertTo-Json
    $pinResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/admin/pin/verify" -Method Post -Body $validPinBody -ContentType "application/json" -Headers $headers
    $stepUpToken = $pinResp.stepUpToken
    Record-Result "Valid Admin PIN Verification" ($pinResp.success -eq $true -and $stepUpToken -ne $null) "Step-Up token granted: $($stepUpToken.Substring(0, 15))..."
} catch {
    Record-Result "Valid Admin PIN Verification" $false $_.Exception.Message
}

# Submit High-severity alert WITH X-Admin-Step-Up-Token -> Should succeed and auto-dispatch task
$createdAlertId = $null
try {
    $headers = @{
        Authorization = "Bearer $adminToken"
        "X-Admin-Step-Up-Token" = $stepUpToken
    }
    $highAlert = @{
        title = "Severe Flash Flood Automated Alert";
        severity = "High";
        region = "Zone A";
        message = "Water levels rising rapidly above danger mark - live test dispatch";
        disasterType = "Flood";
        createdBy = "admin@disaster.gov.in"
    } | ConvertTo-Json
    $alertResp = Invoke-RestMethod -Uri "$baseUrl/api/alerts" -Method Post -Body $highAlert -ContentType "application/json" -Headers $headers
    $createdAlertId = $alertResp.alert.alertId
    $alertTitle = $alertResp.alert.title
    Record-Result "High Severity Alert Dispatch with Step-Up" ($createdAlertId -gt 0) "Alert ID: $createdAlertId, Title: $alertTitle"
} catch {
    Record-Result "High Severity Alert Dispatch with Step-Up" $false $_.Exception.Message
}

# Check Admin Audit Logs
try {
    $headers = @{
        Authorization = "Bearer $adminToken"
        "X-Admin-Step-Up-Token" = $stepUpToken
    }
    $auditLogs = Invoke-RestMethod -Uri "$baseUrl/api/auth/admin/audit-logs" -Method Get -Headers $headers
    $hasLogs = ($auditLogs -ne $null -and $auditLogs.Count -gt 0)
    Record-Result "Admin Audit Logs Audit Trail" $hasLogs "Found $($auditLogs.Count) audit log entries"
} catch {
    Record-Result "Admin Audit Logs Audit Trail" $false $_.Exception.Message
}

# 5. Incident Lifecycle (Citizen Report -> Responder Acknowledge -> Resolve)
Write-Host "`n--- CHECK 5: Citizen Incident Lifecycle & Responder Workflow ---"
$reportId = $null
# Citizen submits report
try {
    $headers = @{ Authorization = "Bearer $citizenToken" }
    $reportData = @{
        message = "Bridge submerged by 2 feet of water. Vehicles stranded on main road."
        location = "Sector 4 Main Bridge"
        photoData = "data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mP8z8BQDwAEhQGAhKmMIQAAAABJRU5ErkJggg=="
    } | ConvertTo-Json
    $repResp = Invoke-RestMethod -Uri "$baseUrl/api/citizen-reports" -Method Post -Body $reportData -ContentType "application/json" -Headers $headers
    $reportId = $repResp.reportId
    Record-Result "Citizen Submit Incident Report" ($reportId -gt 0) "Report ID: $reportId, Status: $($repResp.status)"
} catch {
    Record-Result "Citizen Submit Incident Report" $false $_.Exception.Message
}

# Citizen queries my-reports
try {
    $headers = @{ Authorization = "Bearer $citizenToken" }
    $myReports = Invoke-RestMethod -Uri "$baseUrl/api/citizen-reports/my-reports" -Method Get -Headers $headers
    $found = ($myReports | Where-Object { $_.reportId -eq $reportId }) -ne $null
    Record-Result "Citizen My-Reports Retrieval" $found "Retrieved $($myReports.Count) personal reports, verified report #$reportId"
} catch {
    Record-Result "Citizen My-Reports Retrieval" $false $_.Exception.Message
}

# Responder fetches pending reports
try {
    $headers = @{ Authorization = "Bearer $responderToken" }
    $pending = Invoke-RestMethod -Uri "$baseUrl/api/citizen-reports/pending" -Method Get -Headers $headers
    $found = ($pending | Where-Object { $_.reportId -eq $reportId }) -ne $null
    Record-Result "Responder View Pending Reports" $found "Found report #$reportId in pending queue (total: $($pending.Count))"
} catch {
    Record-Result "Responder View Pending Reports" $false $_.Exception.Message
}

# Responder acknowledges report
try {
    $headers = @{ Authorization = "Bearer $responderToken" }
    $ackResp = Invoke-RestMethod -Uri "$baseUrl/api/citizen-reports/$reportId/acknowledge" -Method Put -Headers $headers
    Record-Result "Responder Acknowledge Report" ($ackResp.status -eq "Acknowledged") "Report status updated to: $($ackResp.status)"
} catch {
    Record-Result "Responder Acknowledge Report" $false $_.Exception.Message
}

# Responder resolves report
try {
    $headers = @{ Authorization = "Bearer $responderToken" }
    $resResp = Invoke-RestMethod -Uri "$baseUrl/api/citizen-reports/$reportId/resolve" -Method Put -Headers $headers
    Record-Result "Responder Resolve Report" ($resResp.status -eq "Resolved") "Report status updated to: $($resResp.status)"
} catch {
    Record-Result "Responder Resolve Report" $false $_.Exception.Message
}

# Responder fetches dispatched rescue tasks
try {
    $headers = @{ Authorization = "Bearer $responderToken" }
    $myTasks = Invoke-RestMethod -Uri "$baseUrl/api/tasks/my-tasks" -Method Get -Headers $headers
    Record-Result "Responder My-Tasks Retrieval" ($myTasks -ne $null) "Retrieved assigned tasks"
} catch {
    Record-Result "Responder My-Tasks Retrieval" $false $_.Exception.Message
}

# 6. Admin CSV Incident Export
Write-Host "`n--- CHECK 6: Incident Data Export ---"
try {
    $headers = @{ Authorization = "Bearer $adminToken" }
    $csvData = Invoke-RestMethod -Uri "$baseUrl/api/reports/export/csv" -Method Get -Headers $headers
    $hasHeader = $csvData -match "Report ID,Title,Zone,Report Type,Status,Reported By,Reporter Name,Timestamp"
    Record-Result "Admin CSV Export" $hasHeader "CSV Header verified. Length: $($csvData.Length) chars"
} catch {
    Record-Result "Admin CSV Export" $false $_.Exception.Message
}

# 7. Disasters and Weather APIs
Write-Host "`n--- CHECK 7: Feeds & Weather Resilience ---"
try {
    $disasters = Invoke-RestMethod -Uri "$baseUrl/api/disasters" -Method Get
    Record-Result "NDMA Disasters Feed" ($disasters.Count -ge 0) "Feed returned $($disasters.Count) disasters"
} catch {
    Record-Result "NDMA Disasters Feed" $false $_.Exception.Message
}

try {
    $weather = Invoke-RestMethod -Uri "$baseUrl/api/weather?city=Chennai" -Method Get
    Record-Result "Resilient Weather API" ($weather -ne $null -and $weather.city -eq "Chennai") "City: $($weather.city), Temp: $($weather.temperature)C, Condition: $($weather.weather_condition)"
} catch {
    Record-Result "Resilient Weather API" $false $_.Exception.Message
}

# 8. Static Web UI Page Serving
Write-Host "`n--- CHECK 8: Frontend Pages & Static Assets ---"
$staticFiles = @(
    "index.html",
    "home.html",
    "alerts.html",
    "citizen-dashboard.html",
    "responder-dashboard.html",
    "responder-rescue.html",
    "admin-dashboard.html",
    "admin-profile.html",
    "role-loading.js",
    "role-loading.css"
)
foreach ($file in $staticFiles) {
    try {
        $resp = Invoke-WebRequest -Uri "$baseUrl/$file" -Method Get -UseBasicParsing
        Record-Result "Static File: $file" ($resp.StatusCode -eq 200) "HTTP 200 OK (Content-Length: $($resp.RawContentLength))"
    } catch {
        Record-Result "Static File: $file" $false $_.Exception.Message
    }
}

# Summary
Write-Host "`n================================================================"
Write-Host "                     VERIFICATION SUMMARY                      "
Write-Host "================================================================"
$total = $results.Count
$passedCount = ($results | Where-Object { $_.Status -eq "PASS" }).Count
$failedCount = ($results | Where-Object { $_.Status -eq "FAIL" }).Count
Write-Host "Total Checks Run : $total"
Write-Host "Checks Passed    : $passedCount"
Write-Host "Checks Failed    : $failedCount"

if ($failedCount -eq 0) {
    Write-Host "`nALL LIVE ENDPOINTS AND SECURITY WORKFLOWS PASSED 100%!" -ForegroundColor Green
} else {
    Write-Host "`nWARNING: $failedCount checks failed." -ForegroundColor Red
}
Write-Host "================================================================"


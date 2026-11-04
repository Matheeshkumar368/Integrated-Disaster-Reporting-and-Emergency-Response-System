# ==============================================================================
# LIVE SITUATION CAPTURE & TRACKING VERIFICATION SUITE
# Disaster Management & Alert System - Academic Audit
# ==============================================================================

$baseUrl = "http://localhost:8080"
$passCount = 0
$failCount = 0

function Assert-Test {
    param(
        [string]$testName,
        [bool]$condition,
        [string]$details = ""
    )
    if ($condition) {
        Write-Host " [PASS] $testName" -ForegroundColor Green
        if ($details) { Write-Host "        $details" -ForegroundColor DarkGray }
        $script:passCount++
    } else {
        Write-Host " [FAIL] $testName" -ForegroundColor Red
        if ($details) { Write-Host "        $details" -ForegroundColor Yellow }
        $script:failCount++
    }
}

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host " STARTING VERIFICATION: LIVE SITUATION CAPTURE & RESPONDER TRACKING" -ForegroundColor Cyan
Write-Host "======================================================================" -ForegroundColor Cyan

# 1. Health Probe
try {
    $health = Invoke-RestMethod -Uri "$baseUrl/actuator/health" -Method Get
    Assert-Test "Actuator Health Check" ($health.status -eq "UP") "Status: $($health.status)"
} catch {
    Assert-Test "Actuator Health Check" $false "Server unreachable: $_"
}

# 2. Authentication: Admin, Responder, Citizen
$adminToken = ""
$responderToken = ""
$citizenToken = ""

try {
    $adminLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body (@{
        email = "admin@disaster.gov.in"
        password = "admin123"
    } | ConvertTo-Json)
    $adminToken = $adminLogin.token
    Assert-Test "Admin Authentication" ($adminToken -ne $null -and $adminToken.Length -gt 20) "Role: $($adminLogin.role)"
} catch {
    Assert-Test "Admin Authentication" $false "$_"
}

try {
    $respLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body (@{
        email = "responder@disaster.gov.in"
        password = "responder123"
    } | ConvertTo-Json)
    $responderToken = $respLogin.token
    Assert-Test "Responder Authentication" ($responderToken -ne $null -and $responderToken.Length -gt 20) "Role: $($respLogin.role)"
} catch {
    Assert-Test "Responder Authentication" $false "$_"
}

try {
    $citLogin = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" -Method Post -ContentType "application/json" -Body (@{
        email = "citizen@gmail.com"
        password = "citizen123"
    } | ConvertTo-Json)
    $citizenToken = $citLogin.token
    Assert-Test "Citizen Authentication" ($citizenToken -ne $null -and $citizenToken.Length -gt 20) "Role: $($citLogin.role)"
} catch {
    Assert-Test "Citizen Authentication" $false "$_"
}

# 3. Create a Rescue Task for Tracking Tests
$createdTaskId = $null
try {
    $taskBody = @{
        title = "Flood Evacuation & Relief - Sector 4"
        description = "Water levels rising at Mount Road. 4 families need boat evacuation."
        zone = "Chennai"
        priority = "High"
        latitude = 13.0827
        longitude = 80.2707
        locationName = "Mount Road, Chennai"
    } | ConvertTo-Json

    $taskRes = Invoke-RestMethod -Uri "$baseUrl/api/tasks" -Method Post -Headers @{ Authorization = "Bearer $adminToken" } -ContentType "application/json" -Body $taskBody
    $createdTaskId = $taskRes.task.taskId
    Assert-Test "Admin Task Creation for Tracking" ($taskRes.status -eq "success" -and $createdTaskId -gt 0) "Task ID: $createdTaskId"
} catch {
    Assert-Test "Admin Task Creation for Tracking" $false "$_"
}

# 4. Route Estimation & Driving Directions API
try {
    $routeRes = Invoke-RestMethod -Uri "$baseUrl/api/routing/directions?startLat=13.0700&startLng=80.2500&endLat=13.0827&endLng=80.2707" -Method Get -Headers @{ Authorization = "Bearer $citizenToken" }
    $hasDist = $routeRes.distanceMeters -gt 0
    $hasEta = $routeRes.etaText -ne $null
    $hasCoords = $routeRes.coordinates.Count -gt 0
    Assert-Test "Routing Engine (OSRM / Haversine)" ($hasDist -and $hasEta -and $hasCoords) "Source: $($routeRes.source) | Dist: $($routeRes.distanceKm)km | ETA: $($routeRes.etaText)"
} catch {
    Assert-Test "Routing Engine (OSRM / Haversine)" $false "$_"
}

# 5. Smart Responder Assignment Recommendation Engine
try {
    $smartRes = Invoke-RestMethod -Uri "$baseUrl/api/admin/assignments/recommendations/$createdTaskId" -Method Get -Headers @{ Authorization = "Bearer $adminToken" }
    $recs = $smartRes.recommendations
    $hasRecs = $recs.Count -gt 0
    $topRank = if ($hasRecs) { $recs[0] } else { $null }
    Assert-Test "Smart Responder Matching Algorithm" ($hasRecs -and $topRank.matchScore -gt 0) "Evaluated $($recs.Count) units. Top match: $($topRank.responderName) ($($topRank.matchScore)%)"
} catch {
    Assert-Test "Smart Responder Matching Algorithm" $false "$_"
}

# 6. Assign Responder to Task
try {
    $assignRes = Invoke-RestMethod -Uri "$baseUrl/api/tasks/$createdTaskId/assign?responderEmail=responder@disaster.gov.in" -Method Put -Headers @{ Authorization = "Bearer $adminToken" }
    Assert-Test "Admin Dispatches Responder" ($assignRes.status -eq "success") "Assigned To: $($assignRes.task.assignedTo)"
} catch {
    Assert-Test "Admin Dispatches Responder" $false "$_"
}

# 7. Live Responder Tracking Session: Start, Telemetry Update, Latest Telemetry
try {
    $startBody = @{
        assignmentId = $createdTaskId
        latitude = 13.0720
        longitude = 80.2550
        accuracy = 12.5
    } | ConvertTo-Json

    $startRes = Invoke-RestMethod -Uri "$baseUrl/api/tracking/start" -Method Post -Headers @{ Authorization = "Bearer $responderToken" } -ContentType "application/json" -Body $startBody
    Assert-Test "Responder Activates Live Tracking" ($startRes.status -eq "success") "Session ID: $($startRes.sessionId)"
} catch {
    Assert-Test "Responder Activates Live Tracking" $false "$_"
}

try {
    # Send moving GPS breadcrumb closer to target
    $updateBody = @{
        assignmentId = $createdTaskId
        latitude = 13.0770
        longitude = 80.2620
        accuracy = 8.0
        heading = 45.0
        speed = 8.5
    } | ConvertTo-Json

    $updateRes = Invoke-RestMethod -Uri "$baseUrl/api/tracking/update" -Method Post -Headers @{ Authorization = "Bearer $responderToken" } -ContentType "application/json" -Body $updateBody
    Assert-Test "Responder Transmits Telemetry Breadcrumb" ($updateRes.status -eq "success") "Lat: $($updateRes.latitude), Lng: $($updateRes.longitude)"
} catch {
    Assert-Test "Responder Transmits Telemetry Breadcrumb" $false "$_"
}

try {
    $telemetry = Invoke-RestMethod -Uri "$baseUrl/api/tracking/latest/$createdTaskId" -Method Get -Headers @{ Authorization = "Bearer $citizenToken" }
    $isLive = $telemetry.isStale -eq $false
    $hasEta = $telemetry.etaText -ne $null
    Assert-Test "Citizen Polls Live Responder Telemetry" ($telemetry.status -eq "ACTIVE" -and $isLive -and $hasEta) "Status: $($telemetry.status) | ETA: $($telemetry.etaText) | Stale: $($telemetry.isStale)"
} catch {
    Assert-Test "Citizen Polls Live Responder Telemetry" $false "$_"
}

try {
    $activeRadar = Invoke-RestMethod -Uri "$baseUrl/api/tracking/active" -Method Get -Headers @{ Authorization = "Bearer $adminToken" }
    $hasActive = ($activeRadar | Where-Object { $_.assignmentId -eq $createdTaskId }) -ne $null
    Assert-Test "Admin Mission Radar Fleet Tracking" $hasActive "Found active tracking sessions: $($activeRadar.Count)"
} catch {
    Assert-Test "Admin Mission Radar Fleet Tracking" $false "$_"
}

# 8. Media Upload & Storage Pipeline
$uploadedMediaId = $null
$storedFilename = $null

try {
    # Generate small valid JPEG test file
    $testImagePath = "$PSScriptRoot\test_evidence_photo.jpg"
    $bytes = [byte[]](0xFF, 0xD8, 0xFF, 0xE0, 0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01, 0x01, 0x01, 0x00, 0x60, 0x00, 0x60, 0x00, 0x00, 0xFF, 0xD9)
    [System.IO.File]::WriteAllBytes($testImagePath, $bytes)

    # Use curl.exe for multipart upload
    $curlOut = & curl.exe -s -X POST "$baseUrl/api/media/upload" `
        -H "Authorization: Bearer $citizenToken" `
        -F "file=@$testImagePath;type=image/jpeg" `
        -F "incidentId=$createdTaskId" `
        -F "incidentType=RESCUE_TASK" `
        -F "caption=Flooding reached residential threshold" `
        -F "latitude=13.0827" `
        -F "longitude=80.2707" `
        -F "accuracy=10.0"

    $mediaRes = $curlOut | ConvertFrom-Json
    $uploadedMediaId = $mediaRes.mediaId
    $storedFilename = $mediaRes.storedFilename

    Assert-Test "Media Upload & Integrity Verification" ($mediaRes.status -eq "success" -and $uploadedMediaId -gt 0) "Media ID: $uploadedMediaId | File: $storedFilename"

    if (Test-Path $testImagePath) { Remove-Item $testImagePath -Force }
} catch {
    Assert-Test "Media Upload & Integrity Verification" $false "$_"
}

try {
    # Verify downloading / streaming media file
    $downloadRes = Invoke-WebRequest -Uri "$baseUrl/api/media/$uploadedMediaId/download" -Method Get -UseBasicParsing
    Assert-Test "Media Download Endpoint (Inline Stream)" ($downloadRes.StatusCode -eq 200 -and $downloadRes.Headers["Content-Type"] -eq "image/jpeg") "Content-Type: $($downloadRes.Headers['Content-Type'])"
} catch {
    Assert-Test "Media Download Endpoint (Inline Stream)" $false "$_"
}

try {
    # Verify security defense: rejecting executable upload
    $evilScriptPath = "$PSScriptRoot\evil.exe"
    [System.IO.File]::WriteAllText($evilScriptPath, "dummy binary payload")

    $evilOut = & curl.exe -s -X POST "$baseUrl/api/media/upload" `
        -H "Authorization: Bearer $citizenToken" `
        -F "file=@$evilScriptPath;type=application/octet-stream"

    $evilRes = $evilOut | ConvertFrom-Json
    Assert-Test "Security Defense: Reject Executable Extensions" ($evilRes.status -eq "error" -and $evilRes.message -like "*not allowed*") "Rejected: $($evilRes.message)"

    if (Test-Path $evilScriptPath) { Remove-Item $evilScriptPath -Force }
} catch {
    Assert-Test "Security Defense: Reject Executable Extensions" $false "$_"
}

# 9. Incident Collaboration Room & Real-time Timeline
try {
    # Post a message from citizen
    $msgBody = @{
        message = "Boat rescue team needed urgently at Gate 2."
    } | ConvertTo-Json

    $msgRes = Invoke-RestMethod -Uri "$baseUrl/api/incidents/$createdTaskId/timeline/message?type=RESCUE_TASK" -Method Post -Headers @{ Authorization = "Bearer $citizenToken" } -ContentType "application/json" -Body $msgBody
    Assert-Test "Citizen Posts Message to Incident Room" ($msgRes.status -eq "success") "Event ID: $($msgRes.event.id)"
} catch {
    Assert-Test "Citizen Posts Message to Incident Room" $false "$_"
}

try {
    # Query room timeline and verify events
    $room = Invoke-RestMethod -Uri "$baseUrl/api/incidents/$createdTaskId/room?type=RESCUE_TASK" -Method Get -Headers @{ Authorization = "Bearer $responderToken" }
    $hasTimeline = $room.timeline.Count -ge 2
    $hasMedia = $room.media.Count -ge 1
    Assert-Test "Incident Collaboration Room Aggregation" ($hasTimeline -and $hasMedia) "Timeline Events: $($room.timeline.Count) | Attached Media: $($room.media.Count)"
} catch {
    Assert-Test "Incident Collaboration Room Aggregation" $false "$_"
}

# 10. Responder Completes Mission & Stops Tracking
try {
    $stopBody = @{
        assignmentId = $createdTaskId
    } | ConvertTo-Json

    $stopRes = Invoke-RestMethod -Uri "$baseUrl/api/tracking/stop" -Method Post -Headers @{ Authorization = "Bearer $responderToken" } -ContentType "application/json" -Body $stopBody
    Assert-Test "Responder Completes & Terminates Tracking" ($stopRes.status -eq "success") "Status: $($stopRes.message)"
} catch {
    Assert-Test "Responder Completes & Terminates Tracking" $false "$_"
}

# 11. Static Frontend Assets Verification
$assets = @(
    "media-capture.css",
    "media-capture.js",
    "responder-tracking.css",
    "responder-tracking.js",
    "incident-room.css",
    "incident-room.js",
    "smart-assignment.css",
    "smart-assignment.js"
)

foreach ($asset in $assets) {
    try {
        $assetRes = Invoke-WebRequest -Uri "$baseUrl/$asset" -Method Get -UseBasicParsing
        Assert-Test "Frontend Asset: $asset" ($assetRes.StatusCode -eq 200) "Size: $($assetRes.RawContentLength) bytes"
    } catch {
        Assert-Test "Frontend Asset: $asset" $false "$_"
    }
}

Write-Host "======================================================================" -ForegroundColor Cyan
Write-Host " VERIFICATION COMPLETE: $passCount PASSED, $failCount FAILED" -ForegroundColor $(if ($failCount -eq 0) { "Green" } else { "Red" })
Write-Host "======================================================================" -ForegroundColor Cyan

if ($failCount -gt 0) {
    exit 1
} else {
    exit 0
}

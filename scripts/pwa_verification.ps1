Write-Host "================================================================"
Write-Host "     DISASTER MANAGEMENT - PWA ASSET & ENDPOINT VERIFICATION    "
Write-Host "================================================================"

$endpoints = @(
    '/manifest.webmanifest',
    '/manifest.json',
    '/sw.js',
    '/offline.html',
    '/pwa-manager.js',
    '/icons/icon-192.png',
    '/icons/icon-512.png',
    '/icons/icon-maskable-192.png',
    '/icons/icon-maskable-512.png',
    '/icons/icon.svg',
    '/favicon.ico'
)

$passCount = 0
$failCount = 0

foreach ($ep in $endpoints) {
    try {
        $res = Invoke-WebRequest -Uri ("http://localhost:8080" + $ep) -Method Get -TimeoutSec 5 -UseBasicParsing
        if ($res.StatusCode -eq 200) {
            Write-Host " [PASS] $ep - HTTP 200 OK (Content Length: $($res.RawContentLength) bytes)" -ForegroundColor Green
            $passCount++
        } else {
            Write-Host " [FAIL] $ep - HTTP $($res.StatusCode)" -ForegroundColor Red
            $failCount++
        }
    } catch {
        Write-Host " [FAIL] $ep - $($_.Exception.Message)" -ForegroundColor Red
        $failCount++
    }
}

Write-Host "================================================================"
Write-Host "                     PWA VERIFICATION SUMMARY                   "
Write-Host "================================================================"
Write-Host "Total Endpoints Checked: $($endpoints.Count)"
Write-Host "Passed Endpoints       : $passCount"
Write-Host "Failed Endpoints       : $failCount"

if ($failCount -eq 0) {
    Write-Host "`nSUCCESS: ALL PWA ASSETS ARE PROPERLY CONFIGURED AND SERVED!" -ForegroundColor Green
} else {
    Write-Host "`nWARNING: Some PWA assets failed verification." -ForegroundColor Yellow
}
Write-Host "================================================================"

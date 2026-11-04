const fs = require('fs');
const path = require('path');
const { execFileSync } = require('child_process');

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const ARTIFACT_DIR = 'C:\\Users\\Lenovo\\.gemini\\antigravity\\brain\\3607002c-3f0c-4749-ad1c-a3bd8b8a1652\\screenshots';

// Create a standalone HTML test showcase page that displays all 4 toasts and the custom dialog
const showcaseHtml = `<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="UTF-8">
    <title>Toast Showcase</title>
    <link rel="stylesheet" href="https://cdnjs.cloudflare.com/ajax/libs/font-awesome/6.5.0/css/all.min.css"/>
    <link rel="stylesheet" href="toast-manager.css?v=2">
    <script src="toast-manager.js?v=2"></script>
    <style>
        body { background: #050811; color: #fff; font-family: sans-serif; padding: 2rem; }
    </style>
</head>
<body>
    <h2>NDMA Tactical Notification Engine</h2>
    <script>
        document.addEventListener('DOMContentLoaded', () => {
            window.showToast('Dispatch accepted — Unit is now En Route.', 'success', 20000);
            setTimeout(() => {
                window.showToast('Critical Flood Warning: Evacuate Sector 4 immediately.', 'error', 20000);
            }, 100);
            setTimeout(() => {
                window.showToast('High winds detected: Structural caution advised.', 'warning', 20000);
            }, 200);
            setTimeout(() => {
                window.showToast('Live GPS streaming active to central command.', 'info', 20000);
            }, 300);
        });
    </script>
</body>
</html>`;

fs.writeFileSync('src/main/resources/static/toast_showcase.html', showcaseHtml, 'utf8');
fs.writeFileSync('target/classes/static/toast_showcase.html', showcaseHtml, 'utf8');

const outDesktop = path.join(ARTIFACT_DIR, 'tactical_toast_notifications_desktop.png');
const outMobile = path.join(ARTIFACT_DIR, 'tactical_toast_notifications_mobile.png');

execFileSync(CHROME_PATH, [
    '--headless=new',
    '--disable-gpu',
    '--hide-scrollbars',
    '--no-sandbox',
    '--window-size=1440,900',
    `--screenshot=${outDesktop}`,
    'http://localhost:8080/toast_showcase.html'
]);

execFileSync(CHROME_PATH, [
    '--headless=new',
    '--disable-gpu',
    '--hide-scrollbars',
    '--no-sandbox',
    '--window-size=390,844',
    `--screenshot=${outMobile}`,
    'http://localhost:8080/toast_showcase.html'
]);

console.log('Toast showcase screenshots generated:');
console.log(' - ' + outDesktop + ' (' + fs.statSync(outDesktop).size + ' bytes)');
console.log(' - ' + outMobile + ' (' + fs.statSync(outMobile).size + ' bytes)');


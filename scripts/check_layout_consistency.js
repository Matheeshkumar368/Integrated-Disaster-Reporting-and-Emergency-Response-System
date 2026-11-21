const fs = require('fs');
const path = require('path');
const staticDir = path.join(__dirname, '..', 'src', 'main', 'resources', 'static');

const pages = [
    'admin-dashboard.html',
    'citizen-dashboard.html',
    'responder-dashboard.html',
    'reports.html',
    'admin-alerts.html',
    'admin-tasks.html',
    'admin-users.html',
    'emergency-videos.html',
    'admin-profile.html',
    'profile.html',
    'responder-profile.html'
];

console.log('=== CHECKING CSS HEADERS AND CONTAINERS ===');
pages.forEach(p => {
    const html = fs.readFileSync(path.join(staticDir, p), 'utf8');
    const hasHudHeader = /class=["'][^"']*cc-hud-header[^"']*["']/.test(html);
    const hasNavBar = /class=["'][^"']*cc-nav-bar[^"']*["']/.test(html);
    const hasMainContainer = /class=["'][^"']*(cc-main-container|cc-citizen-container|cc-responder-container|content-wrap|main-content)[^"']*["']/.test(html);
    console.log(`${p.padEnd(25)} | HUD Header: ${hasHudHeader ? 'YES' : ' NO'} | Nav Bar: ${hasNavBar ? 'YES' : ' NO'} | Container: ${hasMainContainer ? 'YES' : ' NO'}`);
});

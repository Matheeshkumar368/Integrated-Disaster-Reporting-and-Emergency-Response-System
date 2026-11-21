const fs = require('fs');
const path = require('path');

const staticDir = path.join(__dirname, '..', 'src', 'main', 'resources', 'static');

const pagesToAudit = [
    // Admin
    'admin-dashboard.html',
    'admin-notifications.html',
    'admin-users.html',
    'admin-alerts.html',
    'reports.html',
    'admin-tasks.html',
    'admin-profile.html',
    // Citizen
    'citizen-dashboard.html',
    'citizen-notifications.html',
    'emergency-videos.html',
    'profile.html',
    // Responder
    'responder-dashboard.html',
    'responder-notifications.html',
    'responder-tasks.html',
    'responder-rescue.html',
    'responder-profile.html'
];

const jsToAudit = [
    'command-center.js',
    'responder-tracking.js',
    'incident-room.js',
    'direct-messaging.js',
    'notifications.js',
    'auth-helper.js',
    'auth-guard.js',
    'toast-manager.js'
];

console.log('========================================================================');
console.log('AUDIT: HARDCODED HTML CONTENT & STATS');
console.log('========================================================================');

pagesToAudit.forEach(page => {
    const filePath = path.join(staticDir, page);
    if (!fs.existsSync(filePath)) {
        console.log(`[MISSING] ${page}`);
        return;
    }
    const html = fs.readFileSync(filePath, 'utf8');

    // Check for hardcoded stats / numbers inside common stat classes
    const statMatches = html.match(/<(div|span|h[1-6]|p)[^>]*class=["'][^"']*(stat|metric|count|badge|number|val)[^"']*["'][^>]*>\s*([0-9]+[a-zA-Z%,\.\s]*)\s*<\/\1>/gi);
    if (statMatches) {
        console.log(`\n--- ${page} --- (Hardcoded Stats in HTML: ${statMatches.length})`);
        statMatches.slice(0, 8).forEach(m => console.log('   ' + m.trim().replace(/\s+/g, ' ')));
    }

    // Check for dummy / sample / placeholder text
    const textMatches = html.match(/(sample|dummy|mock|fake|lorem|placeholder|jane doe|john doe|demo user)/gi);
    if (textMatches) {
        console.log(`\n--- ${page} --- (Found potential placeholder words: ${[...new Set(textMatches.map(s => s.toLowerCase()))].join(', ')})`);
    }

    // Check for non-functional buttons or anchor tags acting as buttons
    const btnMatches = html.match(/<(button|a)[^>]*>(.*?)<\/\1>/gis);
    const nonFunctional = [];
    if (btnMatches) {
        btnMatches.forEach(btn => {
            const isButton = btn.startsWith('<button');
            const hasOnclick = /onclick\s*=/i.test(btn);
            const hasId = /id\s*=/i.test(btn);
            const hrefMatch = btn.match(/href\s*=\s*["']([^"']*)["']/i);
            const href = hrefMatch ? hrefMatch[1].trim() : null;
            const text = btn.replace(/<[^>]*>/g, '').trim().replace(/\s+/g, ' ');

            if (isButton && !hasOnclick && !hasId) {
                nonFunctional.push({ tag: 'button', text: text.slice(0, 30), reason: 'No id or onclick' });
            } else if (!isButton && (href === '#' || href === 'javascript:void(0)' || href === '') && !hasOnclick && !hasId) {
                nonFunctional.push({ tag: 'a', text: text.slice(0, 30), reason: 'Empty href & no onclick/id' });
            }
        });
    }
    if (nonFunctional.length > 0) {
        console.log(`\n--- ${page} --- (Potentially unhandled buttons: ${nonFunctional.length})`);
        nonFunctional.slice(0, 8).forEach(b => console.log(`   <${b.tag}> text: "${b.text}" (${b.reason})`));
    }
});

console.log('\n========================================================================');
console.log('AUDIT: HARDCODED ARRAYS / RANDOM DATA IN JS');
console.log('========================================================================');

pagesToAudit.concat(jsToAudit).forEach(f => {
    const filePath = path.join(staticDir, f);
    if (!fs.existsSync(filePath)) return;
    const content = fs.readFileSync(filePath, 'utf8');

    // Look for static mock arrays like [ { id: ... } ]
    const mockArrayMatches = content.match(/(const|let|var)\s+(\w+)\s*=\s*\[\s*\{\s*["']?(id|name|title|lat|lng|latitude|status)/gi);
    if (mockArrayMatches) {
        console.log(`\n--- ${f} --- (Static Object Arrays detected: ${mockArrayMatches.length})`);
        mockArrayMatches.forEach(m => console.log('   ' + m.trim()));
    }

    // Look for fake distance/time calculations (e.g. "km away", "mins away", "ETA")
    const etaMatches = content.match(/["'`][0-9\.]+\s*(km|mins|minutes|hrs|hours)\s*(away)?["'`]/gi);
    if (etaMatches) {
        console.log(`\n--- ${f} --- (Static Distance/ETA strings: ${etaMatches.length})`);
        etaMatches.slice(0, 5).forEach(m => console.log('   ' + m.trim()));
    }
});

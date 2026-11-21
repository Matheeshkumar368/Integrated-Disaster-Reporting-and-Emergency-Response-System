const fs = require('fs');
const path = require('path');
const staticDir = path.join(__dirname, '..', 'src', 'main', 'resources', 'static');

const files = [
    'admin-dashboard.html',
    'citizen-dashboard.html',
    'responder-dashboard.html',
    'responder-rescue.html',
    'admin-users.html',
    'admin-tasks.html',
    'responder-tasks.html',
    'command-center.js',
    'responder-tracking.js'
];

files.forEach(f => {
    const content = fs.readFileSync(path.join(staticDir, f), 'utf8');
    const lines = content.split('\n');
    lines.forEach((l, idx) => {
        if (/\beta\b/i.test(l) || /km\s*away/i.test(l) || /mins?\s*away/i.test(l)) {
            console.log(`${f}:${idx+1}: ${l.trim().slice(0, 120)}`);
        }
    });
});

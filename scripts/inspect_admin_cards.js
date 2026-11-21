const fs = require('fs');
const path = require('path');

const html = fs.readFileSync(path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'admin-dashboard.html'), 'utf8');

// Match all major sections
console.log('=== ADMIN DASHBOARD MAJOR HEADINGS / SECTIONS ===');
const headingRegex = /<(h[1-4])[^>]*>([\s\S]*?)<\/\1>/gi;
let m;
while ((m = headingRegex.exec(html)) !== null) {
    const text = m[2].replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
    console.log(`[${m[1]}] ${text}`);
}

console.log('\n=== STAT NUMBERS & LABELS ===');
const statBlockRegex = /<div class="cc-stat-card">([\s\S]*?)<\/div>/gi;
while ((m = statBlockRegex.exec(html)) !== null) {
    const text = m[1].replace(/<[^>]*>/g, ' ').replace(/\s+/g, ' ').trim();
    console.log('STAT: ' + text);
}

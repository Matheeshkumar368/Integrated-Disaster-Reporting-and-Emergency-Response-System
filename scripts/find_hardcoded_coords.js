const fs = require('fs');
const path = require('path');
const staticDir = path.join(__dirname, '..', 'src', 'main', 'resources', 'static');

const files = fs.readdirSync(staticDir).filter(f => f.endsWith('.html') || f.endsWith('.js'));

files.forEach(f => {
    const content = fs.readFileSync(path.join(staticDir, f), 'utf8');
    const lines = content.split('\n');
    lines.forEach((l, idx) => {
        // Look for lat/lng assignments with hardcoded numbers, except standard map center fallbacks like [20.5937, 78.9629] (India)
        const match = l.match(/(lat|lng|latitude|longitude)\s*[:=]\s*([0-9]+\.[0-9]+)/i);
        if (match) {
            const val = parseFloat(match[2]);
            // Exclude India center ~20.59, 78.96
            if (Math.abs(val - 20.5937) > 0.1 && Math.abs(val - 78.9629) > 0.1) {
                console.log(`${f}:${idx+1}: ${l.trim().slice(0, 100)}`);
            }
        }
    });
});

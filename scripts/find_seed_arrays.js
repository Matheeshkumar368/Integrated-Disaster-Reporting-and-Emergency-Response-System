const fs = require('fs');
const path = require('path');
const staticDir = path.join(__dirname, '..', 'src', 'main', 'resources', 'static');

const files = fs.readdirSync(staticDir).filter(f => f.endsWith('.html') || f.endsWith('.js'));

files.forEach(f => {
    const content = fs.readFileSync(path.join(staticDir, f), 'utf8');
    const lines = content.split('\n');
    lines.forEach((l, idx) => {
        if (/(\[\s*\{\s*title\s*:|\[\s*\{\s*name\s*:|\[\s*\{\s*id\s*:)/i.test(l)) {
            console.log(`[SEED/MOCK ARRAY] ${f}:${idx+1}: ${l.trim().slice(0, 100)}`);
        }
    });
});

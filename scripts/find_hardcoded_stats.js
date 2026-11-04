const fs = require('fs');
const path = require('path');

const staticDir = 'src/main/resources/static';
const files = fs.readdirSync(staticDir).filter(f => f.endsWith('.html') || f.endsWith('.js'));

files.forEach(file => {
    const content = fs.readFileSync(path.join(staticDir, file), 'utf8');
    const lines = content.split('\n');
    lines.forEach((l, idx) => {
        if (/(\.innerText|\.textContent)\s*=\s*['"][0-9]+['"]/i.test(l)) {
            console.log(`${file}:${idx + 1}: ${l.trim()}`);
        }
    });
});


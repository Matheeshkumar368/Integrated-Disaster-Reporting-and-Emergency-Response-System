const fs = require('fs');
const path = require('path');

const staticDir = path.join(__dirname, '..', 'src', 'main', 'resources', 'static');

function auditFile(filename) {
    const filePath = path.join(staticDir, filename);
    const content = fs.readFileSync(filePath, 'utf8');

    console.log(`\n========================================================================`);
    console.log(`AUDIT: ${filename} (${content.length} bytes)`);
    console.log(`========================================================================`);

    // 1. Buttons
    const buttonRegex = /<(button|a)[^>]*>(.*?)<\/\1>/gis;
    let m;
    const buttons = [];
    while ((m = buttonRegex.exec(content)) !== null) {
        const fullTag = m[0];
        const tag = m[1];
        const innerText = m[2].replace(/<[^>]*>/g, '').trim().replace(/\s+/g, ' ');
        const idMatch = fullTag.match(/id=["']([^"']*)["']/i);
        const onclickMatch = fullTag.match(/onclick=["']([^"']*)["']/i);
        const hrefMatch = fullTag.match(/href=["']([^"']*)["']/i);

        buttons.push({
            tag,
            id: idMatch ? idMatch[1] : null,
            onclick: onclickMatch ? onclickMatch[1] : null,
            href: hrefMatch ? hrefMatch[1] : null,
            text: innerText.slice(0, 40)
        });
    }

    console.log('Total buttons/links:', buttons.length);
    buttons.forEach(b => {
        const isDead = 
            (b.tag === 'button' && !b.id && !b.onclick && !b.type) ||
            (b.tag === 'a' && (!b.href || b.href === '#' || b.href.startsWith('javascript:void')) && !b.onclick && !b.id);
        if (isDead) {
            console.log(`  [DEAD/UNLINKED] <${b.tag}> text: "${b.text}" (id: ${b.id}, onclick: ${b.onclick}, href: ${b.href})`);
        }
    });

    // 2. Fetch endpoints
    const fetchMatches = content.match(/fetch\s*\(\s*[`'"][^`'"]+[`'"]/g) || [];
    console.log('Fetch API endpoints:');
    [...new Set(fetchMatches)].forEach(f => console.log('  ' + f));

    // 3. Look for fake distance/time/coordinates/sample/placeholder
    const fakeKeywords = content.match(/(km\s*away|mins?\s*away|ETA\b|random|sample|mock|dummy|placeholder)/gi) || [];
    if (fakeKeywords.length > 0) {
        console.log('Potential fake/placeholder keywords found:', [...new Set(fakeKeywords.map(k => k.toLowerCase()))]);
    }
}

auditFile('citizen-dashboard.html');
auditFile('responder-dashboard.html');
auditFile('responder-rescue.html');
auditFile('admin-users.html');
auditFile('admin-tasks.html');
auditFile('responder-tasks.html');

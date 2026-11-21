const fs = require('fs');
const path = require('path');
const staticDir = path.join(__dirname, '..', 'src', 'main', 'resources', 'static');

const pages = [
    'home.html',
    'alerts.html',
    'training.html',
    'admin-alerts.html',
    'admin-profile.html',
    'responder-profile.html'
];

pages.forEach(p => {
    const content = fs.readFileSync(path.join(staticDir, p), 'utf8');
    const buttonRegex = /<(button|a)[^>]*>(.*?)<\/\1>/gis;
    let m;
    const dead = [];
    while ((m = buttonRegex.exec(content)) !== null) {
        const fullTag = m[0];
        const tag = m[1];
        const text = m[2].replace(/<[^>]*>/g, '').trim().replace(/\s+/g, ' ');
        const id = (fullTag.match(/id=["']([^"']*)["']/i) || [])[1];
        const onclick = (fullTag.match(/onclick=["']([^"']*)["']/i) || [])[1];
        const type = (fullTag.match(/type=["']([^"']*)["']/i) || [])[1];
        const href = (fullTag.match(/href=["']([^"']*)["']/i) || [])[1];

        const isDead = 
            (tag === 'button' && !id && !onclick && type !== 'submit') ||
            (tag === 'a' && (!href || href === '#' || href.startsWith('javascript:void')) && !id && !onclick);
        if (isDead) {
            dead.push({ tag, text: text.slice(0, 30), fullTag: fullTag.slice(0, 80) });
        }
    }
    console.log(`\n=== ${p} === (Dead/Unlinked Elements: ${dead.length})`);
    dead.forEach(d => console.log(`  <${d.tag}> "${d.text}" => ${d.fullTag}`));
});

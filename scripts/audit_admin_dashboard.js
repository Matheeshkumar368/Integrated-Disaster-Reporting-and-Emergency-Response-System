const fs = require('fs');
const path = require('path');

const filePath = path.join(__dirname, '..', 'src', 'main', 'resources', 'static', 'admin-dashboard.html');
const content = fs.readFileSync(filePath, 'utf8');

console.log('=== ADMIN DASHBOARD AUDIT ===');
console.log('File size:', content.length, 'bytes');

// 1. Identify buttons and their actions
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

console.log('Total buttons/links found:', buttons.length);
console.log('\n--- POTENTIALLY BROKEN / DECORATIVE BUTTONS ---');
buttons.forEach(b => {
    const isPotentiallyBroken = 
        (b.tag === 'button' && !b.id && !b.onclick) ||
        (b.tag === 'a' && (!b.href || b.href === '#' || b.href.startsWith('javascript:void')) && !b.onclick && !b.id);
    if (isPotentiallyBroken) {
        console.log(`[DEAD BUTTON] <${b.tag}> text: "${b.text}"`);
    }
});

// 2. Identify all scripts & event listeners
console.log('\n--- SCRIPT FUNCTIONS & API ENDPOINTS ---');
const fetchMatches = content.match(/fetch\s*\(\s*[`'"][^`'"]+[`'"]/g) || [];
console.log('Fetch API endpoints in admin-dashboard.html:');
[...new Set(fetchMatches)].forEach(f => console.log('  ' + f));

// 3. Check for any dummy / hardcoded records
console.log('\n--- CHECK FOR MOCK ARRAYS IN ADMIN DASHBOARD ---');
const arrayMatches = content.match(/(const|let|var)\s+(\w+)\s*=\s*\[/g) || [];
arrayMatches.forEach(a => console.log('  ' + a));

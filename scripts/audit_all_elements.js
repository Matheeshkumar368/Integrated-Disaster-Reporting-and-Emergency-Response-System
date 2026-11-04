const fs = require('fs');
const path = require('path');

const staticDir = 'src/main/resources/static';
const htmlFiles = [
    'index.html',
    'index-new-login.html',
    'admin-dashboard.html',
    'admin-alerts.html',
    'admin-tasks.html',
    'admin-profile.html',
    'reports.html',
    'citizen-dashboard.html',
    'profile.html',
    'alerts.html',
    'home.html',
    'responder-dashboard.html',
    'responder-tasks.html',
    'responder-rescue.html',
    'responder-profile.html',
    'responder-register.html',
    'training.html',
    'offline.html'
];

const auditResults = [];

htmlFiles.forEach(fileName => {
    const filePath = path.join(staticDir, fileName);
    if (!fs.existsSync(filePath)) return;
    
    const content = fs.readFileSync(filePath, 'utf8');
    const lines = content.split(/\r?\n/);
    
    // Track sections/containers
    let currentSection = 'Global';
    
    lines.forEach((line, idx) => {
        const lineNum = idx + 1;
        const trimmed = line.trim();
        
        // Detect section comments or headers
        const sectionMatch = trimmed.match(/<!--\s*={3,}\s*([^=]+)\s*={3,}\s*-->/i) ||
                             trimmed.match(/<!--\s*(SECTION|PAGE|TAB|HEADER|NAV|MODAL|SIDEBAR):\s*([^>]+)-->/i) ||
                             trimmed.match(/<section[^>]*id=["']([^"']+)["']/i);
        if (sectionMatch) {
            currentSection = (sectionMatch[1] || sectionMatch[2] || sectionMatch[0]).replace(/<!--|-->/g, '').trim();
        }
        
        // Find buttons
        if (trimmed.includes('<button') || (trimmed.includes('<a ') && (trimmed.includes('onclick') || trimmed.includes('btn') || trimmed.includes('javascript:')))) {
            const isButton = trimmed.includes('<button');
            const idMatch = trimmed.match(/id=["']([^"']+)["']/i);
            const onclickMatch = trimmed.match(/onclick=["']([^"']+)["']/i);
            const textMatch = trimmed.match(/>([^<]+)</);
            const classMatch = trimmed.match(/class=["']([^"']+)["']/i);
            
            auditResults.push({
                page: fileName,
                section: currentSection,
                line: lineNum,
                type: isButton ? 'BUTTON' : 'LINK_BUTTON',
                id: idMatch ? idMatch[1] : null,
                classes: classMatch ? classMatch[1] : null,
                text: textMatch ? textMatch[1].trim() : '[Icon or Nested Content]',
                handler: onclickMatch ? onclickMatch[1] : '[No inline onclick / Bound via JS listener]',
                raw: trimmed.substring(0, 140)
            });
        }
        
        // Find forms
        if (trimmed.includes('<form')) {
            const idMatch = trimmed.match(/id=["']([^"']+)["']/i);
            const actionMatch = trimmed.match(/action=["']([^"']+)["']/i);
            const onsubmitMatch = trimmed.match(/onsubmit=["']([^"']+)["']/i);
            auditResults.push({
                page: fileName,
                section: currentSection,
                line: lineNum,
                type: 'FORM',
                id: idMatch ? idMatch[1] : null,
                action: actionMatch ? actionMatch[1] : null,
                handler: onsubmitMatch ? onsubmitMatch[1] : '[Bound via submit listener]',
                raw: trimmed.substring(0, 140)
            });
        }
        
        // Find modals
        if (trimmed.includes('class="modal') || trimmed.includes('class="cc-modal') || trimmed.includes('id="modal') || (trimmed.includes('role="dialog"'))) {
            const idMatch = trimmed.match(/id=["']([^"']+)["']/i);
            auditResults.push({
                page: fileName,
                section: currentSection,
                line: lineNum,
                type: 'MODAL',
                id: idMatch ? idMatch[1] : '[No ID]',
                raw: trimmed.substring(0, 140)
            });
        }
    });
});

console.log(`Audited ${auditResults.length} interactive elements across ${htmlFiles.length} pages.`);
fs.writeFileSync('scripts/elements_audit.json', JSON.stringify(auditResults, null, 2), 'utf8');


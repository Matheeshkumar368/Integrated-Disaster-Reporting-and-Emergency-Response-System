const fs = require('fs');
const path = require('path');

const elements = JSON.parse(fs.readFileSync('scripts/elements_audit.json', 'utf8'));

// Group by page
const byPage = {};
elements.forEach(el => {
    if (!byPage[el.page]) byPage[el.page] = [];
    byPage[el.page].push(el);
});

for (const [page, items] of Object.entries(byPage)) {
    console.log(`\n=================== ${page} (${items.length} elements) ===================`);
    const content = fs.readFileSync(path.join('src/main/resources/static', page), 'utf8');
    
    items.forEach(item => {
        let status = 'UNKNOWN';
        let detail = '';
        
        if (item.handler && item.handler !== '[No inline onclick / Bound via JS listener]' && item.handler !== '[Bound via submit listener]') {
            // Extract function name from onclick
            const fnMatch = item.handler.match(/^([a-zA-Z0-9_$]+)\s*\(/);
            if (fnMatch) {
                const fnName = fnMatch[1];
                if (content.includes(`function ${fnName}`) || content.includes(`${fnName} =`) || content.includes(`${fnName}(`)) {
                    status = 'FUNCTION_FOUND';
                    detail = `calls ${fnName}()`;
                } else if (fnName === 'alert' || fnName === 'showToast' || fnName === 'confirm') {
                    status = 'BUILTIN_OR_TOAST';
                    detail = fnName;
                } else {
                    status = 'FUNCTION_MISSING';
                    detail = `missing ${fnName}()`;
                }
            } else if (item.handler.includes('window.location.href')) {
                status = 'NAVIGATION';
                detail = item.handler;
            } else {
                status = 'INLINE_JS';
                detail = item.handler;
            }
        } else if (item.id) {
            // Check if there is an addEventListener or document.getElementById for this ID
            if (content.includes(`'${item.id}'`) || content.includes(`"${item.id}"`)) {
                status = 'JS_BOUND_BY_ID';
                detail = `bound in script by id #${item.id}`;
            } else {
                status = 'UNBOUND_BUTTON';
                detail = `id #${item.id} not referenced in script!`;
            }
        } else {
            status = 'UNBOUND_OR_SUBMIT';
            detail = item.text;
        }
        
        console.log(`[${status}] line ${item.line} | ${item.type} | ID: ${item.id || 'none'} | Text: "${item.text}" | ${detail}`);
    });
}


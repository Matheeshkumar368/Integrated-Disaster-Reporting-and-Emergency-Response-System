const fs = require('fs');
const path = require('path');

// 1. Scan Backend Controllers
const controllerDir = 'src/main/java/com/disaster/management/controller';
const controllerFiles = fs.readdirSync(controllerDir).filter(f => f.endsWith('.java'));

const endpoints = [];

controllerFiles.forEach(file => {
    const content = fs.readFileSync(path.join(controllerDir, file), 'utf8');
    
    // Class level mapping
    let basePath = '';
    const classMappingMatch = content.match(/@RequestMapping\(\s*["']([^"']+)["']\s*\)/);
    if (classMappingMatch) {
        basePath = classMappingMatch[1];
    }
    
    const lines = content.split('\n');
    let currentMethod = '';
    let currentPath = '';
    let currentJavaMethod = '';
    
    for (let i = 0; i < lines.length; i++) {
        const line = lines[i].trim();
        
        const mappingMatch = line.match(/@(GetMapping|PostMapping|PutMapping|DeleteMapping|RequestMapping)\s*(\(\s*["']?([^"'\)]*)["']?\s*\))?/);
        if (mappingMatch) {
            let httpMethod = mappingMatch[1].replace('Mapping', '').toUpperCase();
            if (httpMethod === 'REQUEST') httpMethod = 'ALL';
            let subPath = mappingMatch[3] || '';
            if (subPath.startsWith('value =')) {
                const subMatch = subPath.match(/value\s*=\s*["']([^"']+)["']/);
                if (subMatch) subPath = subMatch[1];
            }
            
            let fullPath = (basePath + '/' + subPath).replace(/\/+/g, '/');
            if (fullPath.endsWith('/') && fullPath.length > 1) fullPath = fullPath.slice(0, -1);
            
            // Look ahead for the method signature
            let methodName = '';
            for (let j = i + 1; j < Math.min(i + 8, lines.length); j++) {
                const sigLine = lines[j].trim();
                const methodSigMatch = sigLine.match(/(?:public|protected|private)\s+[\w<>,\s?]+\s+(\w+)\s*\(/);
                if (methodSigMatch) {
                    methodName = methodSigMatch[1];
                    break;
                }
            }
            
            endpoints.push({
                controller: file,
                httpMethod: httpMethod,
                path: fullPath,
                javaMethod: methodName,
                line: i + 1
            });
        }
    }
});

console.log(`Discovered ${endpoints.length} backend endpoints across ${controllerFiles.length} controllers.`);

// 2. Scan Frontend HTML files
const staticDir = 'src/main/resources/static';
const htmlFiles = fs.readdirSync(staticDir).filter(f => f.endsWith('.html'));

const pages = [];

htmlFiles.forEach(file => {
    const fullPath = path.join(staticDir, file);
    const content = fs.readFileSync(fullPath, 'utf8');
    
    const titleMatch = content.match(/<title>([^<]+)<\/title>/i);
    const title = titleMatch ? titleMatch[1].trim() : file;
    
    // Find buttons
    const buttons = [];
    const buttonMatches = content.matchAll(/<button[^>]*>([\s\S]*?)<\/button>/gi);
    for (const match of buttonMatches) {
        const fullTag = match[0];
        const innerText = match[1].replace(/<[^>]+>/g, '').trim();
        const idMatch = fullTag.match(/id=["']([^"']+)["']/i);
        const classMatch = fullTag.match(/class=["']([^"']+)["']/i);
        const onclickMatch = fullTag.match(/onclick=["']([^"']+)["']/i);
        buttons.push({
            text: innerText || '[Icon/Empty]',
            id: idMatch ? idMatch[1] : null,
            className: classMatch ? classMatch[1] : null,
            onclick: onclickMatch ? onclickMatch[1] : null,
            tag: fullTag.substring(0, Math.min(fullTag.indexOf('>') + 1, 120))
        });
    }
    
    // Find links acting as buttons
    const linkButtons = [];
    const linkMatches = content.matchAll(/<a[^>]+(?:class=["'][^"']*(?:btn|button|nav|tab)[^"']*["']|onclick=["'][^"']+["'])[^>]*>([\s\S]*?)<\/a>/gi);
    for (const match of linkMatches) {
        const fullTag = match[0];
        const innerText = match[1].replace(/<[^>]+>/g, '').trim();
        const idMatch = fullTag.match(/id=["']([^"']+)["']/i);
        const hrefMatch = fullTag.match(/href=["']([^"']+)["']/i);
        const onclickMatch = fullTag.match(/onclick=["']([^"']+)["']/i);
        linkButtons.push({
            text: innerText || '[Link Icon]',
            id: idMatch ? idMatch[1] : null,
            href: hrefMatch ? hrefMatch[1] : null,
            onclick: onclickMatch ? onclickMatch[1] : null
        });
    }
    
    // Find forms
    const forms = [];
    const formMatches = content.matchAll(/<form[^>]*>([\s\S]*?)<\/form>/gi);
    for (const match of formMatches) {
        const fullTag = match[0];
        const idMatch = fullTag.match(/id=["']([^"']+)["']/i);
        const actionMatch = fullTag.match(/action=["']([^"']+)["']/i);
        const onsubmitMatch = fullTag.match(/onsubmit=["']([^"']+)["']/i);
        forms.push({
            id: idMatch ? idMatch[1] : null,
            action: actionMatch ? actionMatch[1] : null,
            onsubmit: onsubmitMatch ? onsubmitMatch[1] : null
        });
    }
    
    // Find inputs & selects
    const inputs = [];
    const inputMatches = content.matchAll(/<(?:input|select|textarea)[^>]*>/gi);
    for (const match of inputMatches) {
        const fullTag = match[0];
        const idMatch = fullTag.match(/id=["']([^"']+)["']/i);
        const nameMatch = fullTag.match(/name=["']([^"']+)["']/i);
        const typeMatch = fullTag.match(/type=["']([^"']+)["']/i);
        inputs.push({
            tag: fullTag.startsWith('<input') ? 'input' : (fullTag.startsWith('<select') ? 'select' : 'textarea'),
            id: idMatch ? idMatch[1] : null,
            name: nameMatch ? nameMatch[1] : null,
            type: typeMatch ? typeMatch[1] : 'text'
        });
    }
    
    // Find tabs
    const tabs = [];
    const tabMatches = content.matchAll(/(?:data-tab=["']([^"']+)["']|role=["']tab["'][^>]*>([\s\S]*?)<\/(?:button|a)>)/gi);
    for (const match of tabMatches) {
        tabs.push(match[1] || match[2].replace(/<[^>]+>/g, '').trim());
    }
    
    // Find API fetch calls in this file
    const fetchCalls = [];
    const fetchMatches = content.matchAll(/fetch\s*\(\s*[`"']([^`"'\$\)]+)[`"']/g);
    for (const match of fetchMatches) {
        fetchCalls.push(match[1]);
    }
    
    pages.push({
        file: file,
        title: title,
        buttonsCount: buttons.length,
        buttons: buttons,
        linkButtonsCount: linkButtons.length,
        linkButtons: linkButtons,
        formsCount: forms.length,
        forms: forms,
        inputsCount: inputs.length,
        tabsCount: tabs.length,
        tabs: tabs,
        fetchCallsCount: fetchCalls.length,
        fetchCalls: Array.from(new Set(fetchCalls))
    });
});

console.log(`Discovered ${pages.length} frontend HTML pages.`);

// 3. Scan External JS files for API calls and click handlers
const jsFiles = fs.readdirSync(staticDir).filter(f => f.endsWith('.js') && !f.includes('min.js'));
const jsSummary = [];

jsFiles.forEach(file => {
    const fullPath = path.join(staticDir, file);
    const content = fs.readFileSync(fullPath, 'utf8');
    
    const fetchCalls = [];
    const fetchMatches = content.matchAll(/fetch\s*\(\s*[`"']([^`"'\$\)]+)[`"']/g);
    for (const match of fetchMatches) {
        fetchCalls.push(match[1]);
    }
    
    const listeners = [];
    const listenerMatches = content.matchAll(/addEventListener\s*\(\s*['"]click['"]\s*,\s*([^,\)]+)/g);
    for (const match of listenerMatches) {
        listeners.push(match[1].trim());
    }
    
    jsSummary.push({
        file: file,
        fetchCalls: Array.from(new Set(fetchCalls)),
        clickListenersCount: listeners.length
    });
});

const output = {
    generatedAt: new Date().toISOString(),
    endpoints: endpoints,
    pages: pages,
    jsSummary: jsSummary
};

fs.writeFileSync('scripts/discovery_output.json', JSON.stringify(output, null, 2), 'utf8');
console.log('Saved discovery data to scripts/discovery_output.json');


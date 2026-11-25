const { spawn } = require('child_process');
const http = require('http');

async function main() {
    console.log('Launching headless Chrome with remote debugging...');
    const chromeProc = spawn('C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', [
        '--headless=new',
        '--remote-debugging-port=9222',
        '--disable-gpu',
        '--no-sandbox',
        '--user-data-dir=C:\\Users\\Lenovo\\AppData\\Local\\Temp\\chrome-test-profile'
    ]);

    // Wait 2 seconds for Chrome to bind port 9222
    await new Promise(r => setTimeout(r, 2000));

    try {
        const versionData = await new Promise((resolve, reject) => {
            http.get('http://127.0.0.1:9222/json/version', res => {
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => resolve(JSON.parse(data)));
            }).on('error', reject);
        });

        console.log('Connected to Chrome version:', versionData['Browser']);
        const wsUrl = versionData['webSocketDebuggerUrl'];

        // Get existing target/page or create via PUT
        const pages = await new Promise((resolve, reject) => {
            http.get('http://127.0.0.1:9222/json/list', res => {
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => resolve(JSON.parse(data)));
            }).on('error', reject);
        });

        const targetPage = pages.find(p => p.type === 'page') || pages[0];
        console.log('Target page URL:', targetPage.url);
        const pageWsUrl = targetPage.webSocketDebuggerUrl;

        // Use native WebSocket (available in Node 21+)
        const ws = new WebSocket(pageWsUrl);

        let id = 1;
        const pending = new Map();
        function send(method, params = {}) {
            return new Promise((resolve, reject) => {
                const reqId = id++;
                pending.set(reqId, { resolve, reject });
                ws.send(JSON.stringify({ id: reqId, method, params }));
            });
        }

        ws.onmessage = (event) => {
            const msg = JSON.parse(event.data);
            if (msg.method === 'Runtime.consoleAPICalled') {
                const args = msg.params.args.map(a => a.value || a.description || JSON.stringify(a)).join(' ');
                console.log(`[PAGE CONSOLE ${msg.params.type.toUpperCase()}]:`, args);
            } else if (msg.method === 'Runtime.exceptionThrown') {
                console.log(`[PAGE UNCAUGHT EXCEPTION]:`, msg.params.exceptionDetails);
            }

            if (msg.id && pending.has(msg.id)) {
                const { resolve } = pending.get(msg.id);
                pending.delete(msg.id);
                resolve(msg.result);
            }
        };

        await new Promise(r => ws.onopen = r);
        console.log('WebSocket connected to DevTools page.');

        await send('Runtime.enable');
        await send('Page.enable');

        // Let's authenticate citizen by setting localStorage and navigating to citizen-dashboard.html
        // First fetch token from backend directly
        const tokenRes = await fetch('http://localhost:8080/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: 'citizen@gmail.com', password: 'citizen123' })
        });
        const tokenData = await tokenRes.json();
        const token = tokenData.token;
        console.log('Obtained citizen token:', token ? 'YES' : 'NO');

        // Navigate to http://localhost:8080/index-new-login.html first to set origin localStorage
        console.log('Navigating to index-new-login.html to establish origin...');
        await send('Page.navigate', { url: 'http://localhost:8080/index-new-login.html' });
        await new Promise(r => setTimeout(r, 2000));

        // Evaluate setting tokens in page origin
        await send('Runtime.evaluate', {
            expression: `
                localStorage.setItem('token_Citizen', '${token}');
                localStorage.setItem('token', '${token}');
                localStorage.setItem('userEmail', 'citizen@gmail.com');
                localStorage.setItem('userRole', 'Citizen');
                sessionStorage.setItem('token_Citizen', '${token}');
                sessionStorage.setItem('token', '${token}');
            `
        });

        // Navigate to citizen-dashboard.html
        console.log('Navigating to citizen-dashboard.html...');
        await send('Page.navigate', { url: 'http://localhost:8080/citizen-dashboard.html' });

        // Wait 4 seconds for page load & initial fetches
        await new Promise(r => setTimeout(r, 4000));

        // Click on tabBtnMyReports
        console.log('Clicking on My Reports tab...');
        const clickRes = await send('Runtime.evaluate', {
            expression: `
                const btn = document.getElementById('tabBtnMyReports');
                if (btn) {
                    btn.click();
                    'Clicked tabBtnMyReports';
                } else {
                    'tabBtnMyReports not found!';
                }
            `
        });
        console.log('Click result:', clickRes.result.value);

        // Wait 2 seconds
        await new Promise(r => setTimeout(r, 2000));

        // Inspect #myReportsList
        const inspectRes = await send('Runtime.evaluate', {
            expression: `
                const list = document.getElementById('myReportsList');
                const badge = document.getElementById('badgeMyReportCount');
                ({
                    badgeText: badge ? badge.innerText : null,
                    listChildrenCount: list ? list.children.length : null,
                    listInnerHTML: list ? list.innerHTML.substring(0, 500) : null,
                    rawMyReportsLength: typeof rawMyReports !== 'undefined' ? rawMyReports.length : 'undefined',
                    lastRenderedReportsHash: typeof lastRenderedReportsHash !== 'undefined' ? lastRenderedReportsHash.substring(0, 100) : 'undefined'
                })
            `,
            returnByValue: true
        });
        console.log('myReportsList state:', inspectRes.result.value);

        // Now test citizen-messages.html
        console.log('----------------------------------------------------');
        console.log('Navigating to citizen-messages.html...');
        await send('Page.navigate', { url: 'http://localhost:8080/citizen-messages.html' });
        await new Promise(r => setTimeout(r, 3000));

        // Inspect messages and click first message card
        const msgInspect = await send('Runtime.evaluate', {
            expression: `
                const cards = document.querySelectorAll('.msg-card');
                const detail = document.getElementById('msgDetailPane');
                const firstCard = cards[0];
                let clicked = false;
                if (firstCard) {
                    firstCard.click();
                    clicked = true;
                }
                ({
                    totalCards: cards.length,
                    clickedFirst: clicked,
                    detailHTML: detail ? detail.innerHTML.substring(0, 300) : null
                })
            `,
            returnByValue: true
        });
        console.log('citizen-messages state before/after click:', msgInspect.result.value);

        // Wait 2 seconds to see if any console errors occurred
        await new Promise(r => setTimeout(r, 2000));

        const detailAfter = await send('Runtime.evaluate', {
            expression: `
                const detail = document.getElementById('msgDetailPane');
                detail ? detail.innerHTML.substring(0, 500) : null;
            `
        });
        console.log('msgDetailPane HTML after click:', detailAfter.result.value);

        ws.close();
    } catch (e) {
        console.error('Error during CDP audit:', e);
    } finally {
        chromeProc.kill();
        console.log('Headless Chrome closed.');
    }
}

main();

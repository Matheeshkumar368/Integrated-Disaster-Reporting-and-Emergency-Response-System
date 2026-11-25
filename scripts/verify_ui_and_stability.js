const { spawn } = require('child_process');
const http = require('http');

async function login(email, password) {
    const res = await fetch('http://localhost:8080/api/auth/login', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
    });
    const data = await res.json();
    return data.token;
}

async function main() {
    const chromeProc = spawn('C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', [
        '--headless=new',
        '--remote-debugging-port=9226',
        '--disable-gpu',
        '--no-sandbox',
        '--user-data-dir=C:\\Users\\Lenovo\\AppData\\Local\\Temp\\chrome-ui-audit'
    ]);

    await new Promise(r => setTimeout(r, 2000));

    try {
        const pages = await new Promise((resolve, reject) => {
            http.get('http://127.0.0.1:9226/json/list', res => {
                let d = '';
                res.on('data', c => d += c);
                res.on('end', () => resolve(JSON.parse(d)));
            }).on('error', reject);
        });
        const ws = new WebSocket((pages.find(p => p.type === 'page') || pages[0]).webSocketDebuggerUrl);
        let id = 1;
        const pending = new Map();
        const send = (method, params = {}) => new Promise((resolve, reject) => {
            const reqId = id++;
            pending.set(reqId, { resolve, reject });
            ws.send(JSON.stringify({ id: reqId, method, params }));
        });

        const exceptions = [];
        ws.onmessage = (ev) => {
            const msg = JSON.parse(ev.data);
            if (msg.method === 'Runtime.exceptionThrown') {
                exceptions.push(msg.params.exceptionDetails.text || 'Uncaught exception');
            }
            if (msg.id && pending.has(msg.id)) {
                pending.get(msg.id).resolve(msg.result);
                pending.delete(msg.id);
            }
        };

        await new Promise(r => ws.onopen = r);
        await send('Runtime.enable');
        await send('Page.enable');
        await send('Emulation.setDeviceMetricsOverride', { width: 1366, height: 768, deviceScaleFactor: 1, mobile: false });

        const tokens = {
            Citizen: await login('citizen@gmail.com', 'citizen123'),
            Admin: await login('admin@disaster.gov.in', 'admin123'),
            Responder: await login('responder@disaster.gov.in', 'responder123')
        };

        // 1. Check index.html (login page) on Desktop & Mobile
        await send('Page.navigate', { url: 'http://localhost:8080/index.html' });
        await new Promise(r => setTimeout(r, 1500));
        const loginCheck = await send('Runtime.evaluate', {
            expression: `(() => {
                const text = document.body.innerText;
                const citLink = Array.from(document.querySelectorAll('a')).find(a => a.textContent.includes('Register for Citizen Safety Hub'));
                const respLink = Array.from(document.querySelectorAll('a')).find(a => a.textContent.includes('Apply for Responder Unit Accreditation'));
                return {
                    hasRemovedTitle: text.includes('NDMA Tactical Operations Command'),
                    hasRemovedHttps: text.includes('SECURE HTTPS'),
                    hasRemovedFooter: text.includes('Final Year Academic Project'),
                    citLinkOk: !!citLink,
                    respLinkOk: !!respLink && respLink.getAttribute('href') === '/responder-register.html',
                    overflowX: document.documentElement.scrollWidth > window.innerWidth + 2
                };
            })()`,
            returnByValue: true
        });
        console.log('Login page check (1366x768):', JSON.stringify(loginCheck.result.value));

        // 2. Check all 3 dashboards on Desktop (1366x768) & Mobile (390x844)
        const pagesToAudit = [
            { role: 'Citizen', url: 'http://localhost:8080/citizen-dashboard.html', email: 'citizen@gmail.com' },
            { role: 'Admin', url: 'http://localhost:8080/admin-dashboard.html', email: 'admin@disaster.gov.in' },
            { role: 'Responder', url: 'http://localhost:8080/responder-dashboard.html', email: 'responder@disaster.gov.in' }
        ];

        for (const p of pagesToAudit) {
            await send('Emulation.setDeviceMetricsOverride', { width: 1366, height: 768, deviceScaleFactor: 1, mobile: false });
            await send('Runtime.evaluate', {
                expression: `
                    localStorage.setItem('token_${p.role}', '${tokens[p.role]}');
                    localStorage.setItem('token', '${tokens[p.role]}');
                    localStorage.setItem('userEmail', '${p.email}');
                    localStorage.setItem('userRole', '${p.role}');
                    sessionStorage.setItem('token_${p.role}', '${tokens[p.role]}');
                    sessionStorage.setItem('token', '${tokens[p.role]}');
                `
            });
            await send('Page.navigate', { url: p.url });
            await new Promise(r => setTimeout(r, 2500));

            const desktopRes = await send('Runtime.evaluate', {
                expression: `(() => {
                    const btns = Array.from(document.querySelectorAll('button, .cc-btn')).filter(b => b.offsetParent !== null);
                    const overlapDetails = [];
                    const rects = btns.map(b => b.getBoundingClientRect());
                    for (let i = 0; i < rects.length; i++) {
                        for (let j = i + 1; j < rects.length; j++) {
                            if (btns[i].contains(btns[j]) || btns[j].contains(btns[i])) continue;
                            const a = rects[i], b = rects[j];
                            if (a.width > 0 && b.width > 0 && a.left < b.right - 2 && a.right > b.left + 2 && a.top < b.bottom - 2 && a.bottom > b.top + 2) {
                                overlapDetails.push((btns[i].innerText.trim().slice(0,20) || btns[i].id || btns[i].className) + ' <-> ' + (btns[j].innerText.trim().slice(0,20) || btns[j].id || btns[j].className));
                            }
                        }
                    }
                    return { visibleButtons: btns.length, overlaps: overlapDetails.length, overlapDetails, overflowX: document.documentElement.scrollWidth > window.innerWidth + 2 };
                })()`,
                returnByValue: true
            });

            await send('Emulation.setDeviceMetricsOverride', { width: 390, height: 844, deviceScaleFactor: 2, mobile: true });
            await new Promise(r => setTimeout(r, 500));
            const mobileRes = await send('Runtime.evaluate', {
                expression: `({ overflowX: document.documentElement.scrollWidth > window.innerWidth + 2 })`,
                returnByValue: true
            });

            // Repeatedly open/close IncidentRoom 5 times if available
            const roomRes = await send('Runtime.evaluate', {
                expression: `(() => {
                    if (!window.IncidentRoom || typeof window.IncidentRoom.open !== 'function') return { tested: false };
                    for (let i = 0; i < 5; i++) {
                        window.IncidentRoom.open(197, 'CITIZEN_REPORT', 'Peelamedu');
                        if (typeof window.IncidentRoom.close === 'function') window.IncidentRoom.close();
                    }
                    return { tested: true, openLoops: 5 };
                })()`,
                returnByValue: true
            });

            console.log(`${p.role} (${p.url.split('/').pop()}): desktop=${JSON.stringify(desktopRes.result.value)}, mobile=${JSON.stringify(mobileRes.result.value)}, room=${JSON.stringify(roomRes.result.value)}`);
        }

        console.log('Total uncaught JS exceptions:', exceptions.length, exceptions);
        ws.close();
    } finally {
        chromeProc.kill();
    }
}

main();

const { spawn } = require('child_process');
const http = require('http');

async function waitForServer(url) {
    for (let i = 0; i < 25; i++) {
        try {
            const r = await fetch(url);
            if (r.ok) return true;
        } catch (e) {}
        await new Promise(r => setTimeout(r, 1000));
    }
    throw new Error('Server did not respond at ' + url);
}

async function login(base, email, password) {
    const r = await fetch(`${base}/api/auth/login`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ email, password })
    });
    return (await r.json()).token;
}

async function main() {
    await waitForServer('http://localhost:8080/actuator/health');
    await waitForServer('http://192.168.0.196:8080/actuator/health');
    console.log('[PASS] Both http://localhost:8080 and http://192.168.0.196:8080 are UP');

    const chrome = spawn('C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', [
        '--headless=new',
        '--remote-debugging-port=9228',
        '--disable-gpu',
        '--no-sandbox',
        '--user-data-dir=C:\\Users\\Lenovo\\AppData\\Local\\Temp\\chrome-lan-check'
    ]);
    await new Promise(r => setTimeout(r, 2000));

    try {
        const pages = await new Promise((res, rej) => http.get('http://127.0.0.1:9228/json/list', r => {
            let d = ''; r.on('data', c => d += c); r.on('end', () => res(JSON.parse(d)));
        }).on('error', rej));
        const ws = new WebSocket((pages.find(p => p.type === 'page') || pages[0]).webSocketDebuggerUrl);
        let id = 1; const pend = new Map();
        const send = (m, p = {}) => new Promise((res, rej) => {
            const i = id++; pend.set(i, { res, rej }); ws.send(JSON.stringify({ id: i, method: m, params: p }));
        });
        const exceptions = [];
        ws.onmessage = e => {
            const msg = JSON.parse(e.data);
            if (msg.method === 'Runtime.exceptionThrown') exceptions.push(msg.params.exceptionDetails);
            if (msg.id && pend.has(msg.id)) { pend.get(msg.id).res(msg.result); pend.delete(msg.id); }
        };
        await new Promise(r => ws.onopen = r);
        await send('Runtime.enable'); await send('Page.enable');

        const respTok = await login('http://localhost:8080', 'responder@disaster.gov.in', 'responder123');
        const citTok = await login('http://192.168.0.196:8080', 'citizen@gmail.com', 'citizen123');

        // 1. Check responder-tasks.html on localhost:8080
        await send('Page.navigate', { url: 'http://localhost:8080/index.html' });
        await new Promise(r => setTimeout(r, 800));
        await send('Runtime.evaluate', {
            expression: `
                localStorage.setItem('token_Responder', '${respTok}');
                localStorage.setItem('token', '${respTok}');
                localStorage.setItem('userEmail', 'responder@disaster.gov.in');
                localStorage.setItem('userRole', 'Responder');
                sessionStorage.setItem('token_Responder', '${respTok}');
                sessionStorage.setItem('token', '${respTok}');
            `
        });
        await send('Page.navigate', { url: 'http://localhost:8080/responder-tasks.html' });
        await new Promise(r => setTimeout(r, 2500));

        const tasksUi = await send('Runtime.evaluate', {
            expression: `(() => {
                const toolBtns = Array.from(document.querySelectorAll('.task-tool-btn'));
                const firstColor = toolBtns[0] ? getComputedStyle(toolBtns[0]).color : null;
                const firstBg = toolBtns[0] ? getComputedStyle(toolBtns[0]).backgroundColor : null;
                // Click Incident Reports tab
                const repTab = Array.from(document.querySelectorAll('.tab')).find(t => t.innerText.includes('Incident Reports'));
                if (repTab) repTab.click();
                const repCards = document.querySelectorAll('#reportsContainer .report-card');
                const viewBtn = toolBtns[0];
                if (viewBtn) viewBtn.click();
                const detailModalOpen = !!document.getElementById('task-detail-modal');
                return {
                    taskCount: document.querySelectorAll('#tasksContainer .task-card').length,
                    toolBtnCount: toolBtns.length,
                    firstToolBtnColor: firstColor,
                    firstToolBtnBg: firstBg,
                    assignedReportCardsCount: repCards.length,
                    detailModalOpen
                };
            })()`,
            returnByValue: true
        });
        console.log('responder-tasks.html UI:', JSON.stringify(tasksUi.result.value));

        // 2. Check LAN HTTP origin (http://192.168.0.196:8080) graceful fallback for MediaCapture & VideoCall
        await send('Page.navigate', { url: 'http://192.168.0.196:8080/index.html' });
        await new Promise(r => setTimeout(r, 800));
        await send('Runtime.evaluate', {
            expression: `
                localStorage.setItem('token_Citizen', '${citTok}');
                localStorage.setItem('token', '${citTok}');
                localStorage.setItem('userEmail', 'citizen@gmail.com');
                localStorage.setItem('userRole', 'Citizen');
                sessionStorage.setItem('token_Citizen', '${citTok}');
                sessionStorage.setItem('token', '${citTok}');
            `
        });
        await send('Page.navigate', { url: 'http://192.168.0.196:8080/citizen-dashboard.html' });
        await new Promise(r => setTimeout(r, 2500));

        const lanRes = await send('Runtime.evaluate', {
            expression: `(async () => {
                const isLanHttp = window.EmergencyVideoCall ? window.EmergencyVideoCall.isLanHttpOrigin() : null;
                const streamRes = window.EmergencyVideoCall ? await window.EmergencyVideoCall.initLocalStream() : 'missing';
                return {
                    origin: window.location.origin,
                    isSecureContext: window.isSecureContext,
                    isLanHttpDetected: isLanHttp,
                    initLocalStreamResult: streamRes === null ? 'graceful-null' : 'stream-ok'
                };
            })()`,
            awaitPromise: true,
            returnByValue: true
        });
        console.log('LAN HTTP origin check:', JSON.stringify(lanRes.result.value));
        console.log('Total uncaught JS exceptions:', exceptions.length);
        ws.close();
    } finally {
        chrome.kill();
    }
}

main();

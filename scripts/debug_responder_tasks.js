const { spawn } = require('child_process');
const http = require('http');
const path = require('path');

const CDP_PORT = 9224;
const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';

function sleep(ms) { return new Promise(r => setTimeout(r, ms)); }
function httpGet(url) {
    return new Promise((resolve, reject) => {
        http.get(url, res => {
            let d = ''; res.on('data', c => d += c); res.on('end', () => resolve(JSON.parse(d)));
        }).on('error', reject);
    });
}

async function debugPage() {
    const tempDir = path.join(__dirname, '..', 'scratch', 'chrome_debug_prof');
    const chrome = spawn(CHROME_PATH, [
        '--headless=new',
        `--remote-debugging-port=${CDP_PORT}`,
        `--user-data-dir=${tempDir}`,
        'about:blank'
    ]);

    await sleep(1500);
    const targets = await httpGet(`http://localhost:${CDP_PORT}/json`);
    const ws = new globalThis.WebSocket(targets[0].webSocketDebuggerUrl);

    let msgId = 1;
    function send(method, params = {}) {
        return new Promise((resolve) => {
            const id = msgId++;
            const handler = (event) => {
                const msg = JSON.parse(event.data);
                if (msg.id === id) {
                    ws.removeEventListener('message', handler);
                    resolve(msg.result);
                }
            };
            ws.addEventListener('message', handler);
            ws.send(JSON.stringify({ id, method, params }));
        });
    }

    ws.onmessage = (event) => {
        const msg = JSON.parse(event.data);
        if (msg.method === 'Runtime.consoleAPICalled') {
            console.log(`[CONSOLE ${msg.params.type.toUpperCase()}]`, msg.params.args.map(a => a.value || a.description).join(' '));
        }
    };

    await new Promise(r => ws.onopen = r);
    await send('Page.enable');
    await send('Runtime.enable');

    // Login as responder
    await send('Page.navigate', { url: 'http://localhost:8080/index-new-login.html' });
    await sleep(800);
    await send('Runtime.evaluate', {
        expression: `(async () => {
            const res = await fetch('/api/auth/login', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify({ email: 'responder@disaster.gov.in', password: 'responder123' })
            });
            const d = await res.json();
            localStorage.setItem('token', d.token);
            localStorage.setItem('userRole', d.role);
            localStorage.setItem('userName', d.name);
            localStorage.setItem('userEmail', 'responder@disaster.gov.in');
        })()`,
        awaitPromise: true
    });
    await sleep(500);

    console.log('Navigating to responder-tasks.html...');
    await send('Page.navigate', { url: 'http://localhost:8080/responder-tasks.html' });
    await sleep(2500);

    const url = await send('Runtime.evaluate', { expression: 'window.location.href', returnByValue: true });
    console.log('Final URL:', url.result.value);

    ws.close();
    chrome.kill();
}

debugPage();

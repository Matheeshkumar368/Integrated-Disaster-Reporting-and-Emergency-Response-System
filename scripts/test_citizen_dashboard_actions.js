const { spawn } = require('child_process');
const http = require('http');

async function main() {
    const chromeProc = spawn('C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', [
        '--headless=new',
        '--remote-debugging-port=9224',
        '--disable-gpu',
        '--no-sandbox',
        '--user-data-dir=C:\\Users\\Lenovo\\AppData\\Local\\Temp\\chrome-dash-test'
    ]);

    await new Promise(r => setTimeout(r, 2000));

    try {
        const pages = await new Promise((resolve, reject) => {
            http.get('http://127.0.0.1:9224/json/list', res => {
                let data = '';
                res.on('data', chunk => data += chunk);
                res.on('end', () => resolve(JSON.parse(data)));
            }).on('error', reject);
        });

        const targetPage = pages.find(p => p.type === 'page') || pages[0];
        const ws = new WebSocket(targetPage.webSocketDebuggerUrl);

        let id = 1;
        const pending = new Map();
        function send(method, params = {}) {
            return new Promise((resolve, reject) => {
                const reqId = id++;
                pending.set(reqId, { resolve, reject });
                ws.send(JSON.stringify({ id: reqId, method, params }));
            });
        }

        const uncaughtExceptions = [];
        ws.onmessage = (event) => {
            const msg = JSON.parse(event.data);
            if (msg.method === 'Runtime.consoleAPICalled') {
                const args = msg.params.args.map(a => a.value || a.description || JSON.stringify(a)).join(' ');
                if (msg.params.type === 'error') {
                    console.log(`[PAGE CONSOLE ERROR]:`, args);
                }
            } else if (msg.method === 'Runtime.exceptionThrown') {
                console.log(`[PAGE UNCAUGHT EXCEPTION]:`, JSON.stringify(msg.params.exceptionDetails));
                uncaughtExceptions.push(msg.params.exceptionDetails);
            }
            if (msg.id && pending.has(msg.id)) {
                pending.get(msg.id).resolve(msg.result);
                pending.delete(msg.id);
            }
        };

        await new Promise(r => ws.onopen = r);
        await send('Runtime.enable');
        await send('Page.enable');

        const tokenRes = await fetch('http://localhost:8080/api/auth/login', {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ email: 'citizen@gmail.com', password: 'citizen123' })
        });
        const token = (await tokenRes.json()).token;

        console.log('Navigating to index-new-login.html to establish origin...');
        await send('Page.navigate', { url: 'http://localhost:8080/index-new-login.html' });
        await new Promise(r => setTimeout(r, 2000));

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

        console.log('Navigating to citizen-dashboard.html...');
        await send('Page.navigate', { url: 'http://localhost:8080/citizen-dashboard.html' });
        await new Promise(r => setTimeout(r, 3000));

        // 1. Check My Reports tab
        console.log('Testing My Reports Tab click...');
        const tabRes = await send('Runtime.evaluate', {
            expression: `(() => {
                const tab = document.getElementById('tabBtnMyReports');
                if (tab) tab.click();
                const list = document.getElementById('myReportsList');
                return {
                    reportsRendered: list ? list.children.length : 0,
                    firstCardTitle: list && list.children[0] ? list.children[0].querySelector('.cc-card-header')?.innerText : null
                };
            })()`,
            returnByValue: true
        });
        console.log('My Reports Tab result:', tabRes.result.value);

        // 2. Test button clicks on the first report card
        console.log('Testing Comms Room button click...');
        const commsClick = await send('Runtime.evaluate', {
            expression: `(() => {
                const commsBtn = document.querySelector('#myReportsList .cc-btn-primary');
                if (commsBtn) {
                    commsBtn.click();
                    return 'Clicked Comms Room button';
                }
                return 'Comms button not found';
            })()`,
            returnByValue: true
        });
        console.log('Comms click result:', commsClick.result.value);

        // 3. Test keyboard Tab navigation
        console.log('Testing Tab keypress handling...');
        const tabKeyRes = await send('Runtime.evaluate', {
            expression: `(() => {
                const event = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
                document.dispatchEvent(event);
                return {
                    defaultPrevented: event.defaultPrevented,
                    emergencyAlertPlaying: typeof EmergencyAlertSystem !== 'undefined' ? EmergencyAlertSystem.isPlaying() : null
                };
            })()`,
            returnByValue: true
        });
        console.log('Single Tab keypress result (should NOT be prevented to allow normal navigation):', tabKeyRes.result.value);

        // 4. Test SOS double tap Tab
        console.log('Testing rapid double-tap Tab for SOS...');
        const doubleTabRes = await send('Runtime.evaluate', {
            expression: `(() => {
                const e1 = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
                document.dispatchEvent(e1);
                const e2 = new KeyboardEvent('keydown', { key: 'Tab', bubbles: true, cancelable: true });
                document.dispatchEvent(e2);
                const isPlaying = typeof EmergencyAlertSystem !== 'undefined' ? EmergencyAlertSystem.isPlaying() : null;
                // Stop alarm
                if (isPlaying && typeof EmergencyAlertSystem !== 'undefined') EmergencyAlertSystem.stopAlarm();
                return {
                    doubleTapTriggered: e2.defaultPrevented,
                    alarmTriggered: isPlaying
                };
            })()`,
            returnByValue: true
        });
        console.log('Double Tab result:', doubleTabRes.result.value);

        console.log('Total uncaught exceptions recorded:', uncaughtExceptions.length);
        ws.close();
    } finally {
        chromeProc.kill();
        console.log('Test complete. Chrome closed.');
    }
}

main();

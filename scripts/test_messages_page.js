const { spawn } = require('child_process');
const http = require('http');

async function main() {
    const chromeProc = spawn('C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe', [
        '--headless=new',
        '--remote-debugging-port=9223',
        '--disable-gpu',
        '--no-sandbox',
        '--user-data-dir=C:\\Users\\Lenovo\\AppData\\Local\\Temp\\chrome-msg-test'
    ]);

    await new Promise(r => setTimeout(r, 2000));

    try {
        const pages = await new Promise((resolve, reject) => {
            http.get('http://127.0.0.1:9223/json/list', res => {
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

        ws.onmessage = (event) => {
            const msg = JSON.parse(event.data);
            if (msg.method === 'Runtime.consoleAPICalled') {
                const args = msg.params.args.map(a => a.value || a.description || JSON.stringify(a)).join(' ');
                console.log(`[PAGE CONSOLE ${msg.params.type.toUpperCase()}]:`, args);
            } else if (msg.method === 'Runtime.exceptionThrown') {
                console.log(`[PAGE UNCAUGHT EXCEPTION]:`, JSON.stringify(msg.params.exceptionDetails));
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

        console.log('Navigating directly to citizen-messages.html...');
        await send('Page.navigate', { url: 'http://localhost:8080/citizen-messages.html' });
        await new Promise(r => setTimeout(r, 3000));

        const res = await send('Runtime.evaluate', {
            expression: `
                const cards = document.querySelectorAll('.msg-card');
                const detail = document.getElementById('msgDetailPane');
                const title = document.querySelector('.msg-title');
                ({
                    pageTitle: title ? title.innerText : null,
                    cardCount: cards.length,
                    firstCardText: cards[0] ? cards[0].innerText.substring(0, 100) : null,
                    detailHTML: detail ? detail.innerHTML.substring(0, 200) : null
                })
            `,
            returnByValue: true
        });
        console.log('Direct citizen-messages test result:', res.result.value);

        // Click first card
        console.log('Clicking first message card...');
        const clickRes = await send('Runtime.evaluate', {
            expression: `(() => {
                const cardList = document.querySelectorAll('.msg-card');
                if (cardList[0]) {
                    cardList[0].click();
                    return 'Clicked first card';
                }
                return 'No cards found to click';
            })()`,
            returnByValue: true
        });
        console.log('Click result raw:', JSON.stringify(clickRes));
        await new Promise(r => setTimeout(r, 1000));

        const detailAfter = await send('Runtime.evaluate', {
            expression: `(() => {
                const pane = document.getElementById('msgDetailPane');
                const active = document.querySelector('.msg-card.active');
                return {
                    hasActiveCard: !!active,
                    activeCardId: active ? active.getAttribute('data-id') : null,
                    detailText: pane ? pane.innerText.substring(0, 200) : null
                };
            })()`,
            returnByValue: true
        });
        console.log('Detail pane after click raw:', JSON.stringify(detailAfter));

        ws.close();
    } finally {
        chromeProc.kill();
        console.log('Test complete. Chrome closed.');
    }
}

main();

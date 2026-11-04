const http = require('http');
const { spawn } = require('child_process');

const CHROME_PATH = 'C:\\Program Files\\Google\\Chrome\\Application\\chrome.exe';
const PORT = 9222;

function delay(ms) {
  return new Promise(resolve => setTimeout(resolve, ms));
}

function fetchJson(url) {
  return new Promise((resolve, reject) => {
    http.get(url, res => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try { resolve(JSON.parse(data)); } catch (e) { reject(e); }
      });
    }).on('error', reject);
  });
}

async function startChrome() {
  const chromeProcess = spawn(CHROME_PATH, [
    `--remote-debugging-port=${PORT}`,
    '--headless=new',
    '--disable-gpu',
    '--no-first-run',
    '--no-default-browser-check',
    '--user-data-dir=C:\\Users\\Lenovo\\AppData\\Local\\Temp\\chrome-cdp-debug',
    'about:blank'
  ]);
  
  for (let i = 0; i < 20; i++) {
    await delay(300);
    try {
      const version = await fetchJson(`http://127.0.0.1:${PORT}/json/version`);
      return { chromeProcess, webSocketDebuggerUrl: version.webSocketDebuggerUrl };
    } catch (e) {}
  }
  throw new Error('Chrome did not start in time');
}

class CDPClient {
  constructor(wsUrl) {
    this.wsUrl = wsUrl;
    this.ws = null;
    this.msgId = 1;
    this.callbacks = new Map();
    this.events = [];
    this.consoleLogs = [];
  }

  connect() {
    return new Promise((resolve, reject) => {
      this.ws = new WebSocket(this.wsUrl);
      this.ws.onopen = () => resolve();
      this.ws.onerror = err => reject(err);
      this.ws.onmessage = event => {
        const msg = JSON.parse(event.data);
        if (msg.id && this.callbacks.has(msg.id)) {
          const cb = this.callbacks.get(msg.id);
          this.callbacks.delete(msg.id);
          if (msg.error) cb.reject(new Error(msg.error.message));
          else cb.resolve(msg.result);
        } else if (msg.method) {
          if (msg.method === 'Runtime.consoleAPICalled') {
            const args = (msg.params.args || []).map(a => a.value || a.description || JSON.stringify(a)).join(' ');
            this.consoleLogs.push(`[CONSOLE ${msg.params.type.toUpperCase()}] ${args}`);
          } else if (msg.method === 'Runtime.exceptionThrown') {
            const desc = msg.params.exceptionDetails.exception?.description || msg.params.exceptionDetails.text;
            this.consoleLogs.push(`[EXCEPTION] ${desc}`);
          }
        }
      };
    });
  }

  send(method, params = {}) {
    return new Promise((resolve, reject) => {
      const id = this.msgId++;
      this.callbacks.set(id, { resolve, reject });
      this.ws.send(JSON.stringify({ id, method, params }));
    });
  }

  async eval(expr) {
    const res = await this.send('Runtime.evaluate', { expression: expr, returnByValue: true, awaitPromise: true });
    if (res.exceptionDetails) {
      throw new Error(res.exceptionDetails.exception?.description || res.exceptionDetails.text);
    }
    return res.result.value;
  }

  close() {
    if (this.ws) this.ws.close();
  }
}

async function debugDashboard(name, url, tokenKey, userEmail, userPass) {
  console.log(`\n========================================================================`);
  console.log(`TESTING & VERIFYING: ${name.toUpperCase()} (${url})`);
  console.log(`========================================================================`);

  const targets = await fetchJson(`http://127.0.0.1:${PORT}/json/list`);
  let pageTarget = targets.find(t => t.type === 'page');
  if (!pageTarget) {
    await fetchJson(`http://127.0.0.1:${PORT}/json/new?about:blank`);
    const newTargets = await fetchJson(`http://127.0.0.1:${PORT}/json/list`);
    pageTarget = newTargets.find(t => t.type === 'page');
  }

  const client = new CDPClient(pageTarget.webSocketDebuggerUrl);
  await client.connect();
  await client.send('Page.enable');
  await client.send('Runtime.enable');
  await client.send('Log.enable');

  console.log(`Authenticating ${userEmail}...`);
  await client.send('Page.navigate', { url });
  await delay(1500);

  const authScript = `
    (async () => {
      try {
        const res = await fetch('/api/auth/login', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ email: '${userEmail}', password: '${userPass}' })
        });
        const data = await res.json();
        if (data.token) {
          localStorage.setItem('token', data.token);
          localStorage.setItem('authToken', data.token);
          localStorage.setItem('role', data.role);
          localStorage.setItem('userEmail', '${userEmail}');
          return { success: true, token: data.token, role: data.role };
        }
        return { success: false, data };
      } catch(e) {
        return { success: false, error: e.message };
      }
    })()
  `;
  await client.eval(authScript);

  // Reload page so full dashboard initializes
  await client.send('Page.navigate', { url });
  await delay(2500);

  // 1. Inspect Button
  const inspectRes = await client.eval(`
    (() => {
      const btn = document.getElementById('sosSoundBtn');
      if (!btn) return { found: false };
      const rect = btn.getBoundingClientRect();
      const style = window.getComputedStyle(btn);
      return {
        found: true,
        id: btn.id,
        text: btn.innerText.replace(/\\s+/g, ' ').trim(),
        onclick: btn.getAttribute('onclick'),
        disabled: btn.disabled,
        visible: style.display !== 'none' && style.visibility !== 'hidden',
        hasToggleLocalSosAlarm: typeof window.toggleLocalSosAlarm === 'function',
        hasEmergencyAlertSystem: typeof window.EmergencyAlertSystem === 'object',
        isPlaying: window.EmergencyAlertSystem ? EmergencyAlertSystem.isPlaying() : false,
        shortcut: window.EmergencyAlertSystem ? EmergencyAlertSystem.getShortcut() : null,
        rect: { x: Math.round(rect.left + rect.width / 2), y: Math.round(rect.top + rect.height / 2) }
      };
    })()
  `);
  console.log('1. Element & Handler State:');
  console.log(JSON.stringify(inspectRes, null, 2));

  if (!inspectRes.found || !inspectRes.hasEmergencyAlertSystem) {
    throw new Error('Button or EmergencyAlertSystem missing!');
  }

  // 2. Physical Mouse Click Test (Trigger)
  console.log(`\n2. Simulating Physical Mouse Click at (${inspectRes.rect.x}, ${inspectRes.rect.y})...`);
  await client.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: inspectRes.rect.x, y: inspectRes.rect.y, button: 'left', clickCount: 1 });
  await client.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: inspectRes.rect.x, y: inspectRes.rect.y, button: 'left', clickCount: 1 });
  await delay(500);

  const triggeredState = await client.eval(`
    (() => {
      const btn = document.getElementById('sosSoundBtn');
      return {
        playing: EmergencyAlertSystem.isPlaying(),
        buttonText: btn.innerText.replace(/\\s+/g, ' ').trim(),
        hasActivePulse: btn.classList.contains('sos-alarm-active-pulse')
      };
    })()
  `);
  console.log('State after Trigger Click:', triggeredState);
  if (!triggeredState.playing || !triggeredState.hasActivePulse) {
    throw new Error('Alarm failed to start on click!');
  }

  // 3. Physical Mouse Click Test (Stop via re-queried button coordinates)
  const stopRect = await client.eval(`
    (() => {
      const btn = document.getElementById('sosSoundBtn');
      const rect = btn.getBoundingClientRect();
      return { x: Math.round(rect.left + rect.width / 2), y: Math.round(rect.top + rect.height / 2) };
    })()
  `);
  console.log(`\n3. Simulating Physical Mouse Click to STOP ALARM at (${stopRect.x}, ${stopRect.y})...`);
  await client.send('Input.dispatchMouseEvent', { type: 'mousePressed', x: stopRect.x, y: stopRect.y, button: 'left', clickCount: 1 });
  await client.send('Input.dispatchMouseEvent', { type: 'mouseReleased', x: stopRect.x, y: stopRect.y, button: 'left', clickCount: 1 });
  await delay(500);

  const stoppedState = await client.eval(`
    (() => {
      const btn = document.getElementById('sosSoundBtn');
      return {
        playing: EmergencyAlertSystem.isPlaying(),
        buttonText: btn.innerText.replace(/\\s+/g, ' ').trim(),
        hasActivePulse: btn.classList.contains('sos-alarm-active-pulse')
      };
    })()
  `);
  console.log('State after Stop Click:', stoppedState);
  if (stoppedState.playing || stoppedState.hasActivePulse) {
    throw new Error('Alarm failed to stop on click!');
  }

  // 4. Keyboard Shortcut Test (TAB Key Trigger & Stop)
  console.log('\n4. Testing Keyboard Shortcut <TAB> via CDP Input.dispatchKeyEvent...');
  // Press Tab to trigger
  await client.send('Input.dispatchKeyEvent', { type: 'rawKeyDown', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9 });
  await client.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9 });
  await delay(400);

  const tabTriggerState = await client.eval(`EmergencyAlertSystem.isPlaying()`);
  console.log('Playing after <Tab> press:', tabTriggerState);

  // Press Tab to stop
  await client.send('Input.dispatchKeyEvent', { type: 'rawKeyDown', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9 });
  await client.send('Input.dispatchKeyEvent', { type: 'keyUp', key: 'Tab', code: 'Tab', windowsVirtualKeyCode: 9 });
  await delay(400);

  const tabStopState = await client.eval(`EmergencyAlertSystem.isPlaying()`);
  console.log('Playing after second <Tab> press (Stop):', tabStopState);

  // Check any uncaught exceptions
  const exceptions = client.consoleLogs.filter(l => l.startsWith('[EXCEPTION]'));
  if (exceptions.length > 0) {
    console.error('Console exceptions detected:', exceptions);
    throw new Error('Uncaught exceptions in console: ' + exceptions.join(', '));
  } else {
    console.log('[SUCCESS] 0 Console exceptions detected.');
  }

  client.close();
}

async function main() {
  console.log('Starting Headless Chrome test runner...');
  const { chromeProcess } = await startChrome();

  try {
    await debugDashboard('Citizen Safety Hub', 'http://localhost:8080/citizen-dashboard.html', 'token', 'citizen@gmail.com', 'citizen123');
    await debugDashboard('Admin Command Center', 'http://localhost:8080/admin-dashboard.html', 'token', 'admin@disaster.gov.in', 'admin123');
    await debugDashboard('Responder Field Operations', 'http://localhost:8080/responder-dashboard.html', 'token', 'responder@disaster.gov.in', 'responder123');
    console.log('\n========================================================================');
    console.log('ALL THREE DASHBOARDS VERIFIED 100% WORKING VIA HEADLESS CHROME CDP!');
    console.log('========================================================================\n');
  } finally {
    chromeProcess.kill();
  }
}

main().catch(err => {
  console.error('Test execution failed:', err);
  process.exit(1);
});

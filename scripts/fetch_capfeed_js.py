import urllib.request
import ssl
import re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

req = urllib.request.Request('https://sachet.ndma.gov.in/CapFeed', headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req, timeout=10, context=ctx) as resp:
    html = resp.read().decode('utf-8', errors='ignore')

scripts = re.findall(r'<script[^>]+src=[\'"]([^\'"]+)[\'"]', html)
for s in scripts:
    print("Script:", s)
    if 'CapFeed' in s:
        js_url = 'https://sachet.ndma.gov.in' + s if s.startswith('/') else s
        print("Fetching JS:", js_url)
        js_req = urllib.request.Request(js_url, headers={'User-Agent': 'Mozilla/5.0'})
        with urllib.request.urlopen(js_req, timeout=10, context=ctx) as js_resp:
            js_code = js_resp.read().decode('utf-8', errors='ignore')
            with open('scripts/CapFeed_chunk.js', 'w', encoding='utf-8') as f:
                f.write(js_code)
            print("Saved CapFeed_chunk.js! Length:", len(js_code))

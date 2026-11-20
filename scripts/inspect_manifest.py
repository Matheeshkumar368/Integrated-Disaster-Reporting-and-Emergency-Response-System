import urllib.request
import ssl
import re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

req = urllib.request.Request('https://sachet.ndma.gov.in/_next/static/b1o3Cxu9lUxuV1BJvgMtl/_buildManifest.js', headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req, timeout=10, context=ctx) as resp:
    code = resp.read().decode('utf-8', errors='ignore')

print("Pages in manifest:")
pages = re.findall(r'"(/[a-zA-Z0-9_\-\/]+)":', code)
print(pages)

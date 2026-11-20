import urllib.request
import ssl
import re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

req = urllib.request.Request('https://sachet.ndma.gov.in/CapFeed', headers={'User-Agent': 'Mozilla/5.0'})
with urllib.request.urlopen(req, timeout=10, context=ctx) as resp:
    html = resp.read().decode('utf-8', errors='ignore')

table_match = re.search(r'<table[^>]*>(.*?)</table>', html, re.S)
if table_match:
    table_html = table_match.group(1)
    rows = re.findall(r'<tr[^>]*>(.*?)</tr>', table_html, re.S)
    print("Row 0:\n", rows[0][:500])
    print("\nRow 1:\n", rows[1][:500])
    print("\nRow 2:\n", rows[2][:500])

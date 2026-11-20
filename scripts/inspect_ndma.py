import urllib.request
import ssl
import re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE

def test_url(url, headers=None):
    if headers is None:
        headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64)'}
    print(f"\n--- Testing: {url} ---")
    try:
        req = urllib.request.Request(url, headers=headers)
        with urllib.request.urlopen(req, timeout=10, context=ctx) as resp:
            print("Status:", resp.status)
            print("Headers:")
            for k, v in resp.getheaders():
                if k.lower() in ['etag', 'last-modified', 'content-type', 'content-length', 'server']:
                    print(f"  {k}: {v}")
            body = resp.read()
            print("Body length:", len(body))
            print("Snippet:", body[:300].decode('utf-8', errors='ignore'))
            return resp.status, dict(resp.getheaders()), body
    except urllib.error.HTTPError as he:
        print("HTTP Error:", he.code, he.reason)
        return he.code, dict(he.headers), None
    except Exception as e:
        print("Error:", e)
        return None, {}, None

# Test the configured rss_india.xml
s, h, b = test_url("https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml")

# Test with ETag If-None-Match if ETag was returned
if h.get('ETag') or h.get('etag'):
    etag = h.get('ETag') or h.get('etag')
    print(f"\nTesting 304 with ETag: {etag}")
    test_url("https://sachet.ndma.gov.in/cap_public_website/rss/rss_india.xml", 
             {'User-Agent': 'Mozilla/5.0', 'If-None-Match': etag})

# Test potential state feeds for Tamil Nadu
candidate_state_feeds = [
    "https://sachet.ndma.gov.in/cap_public_website/rss/rss_tamilnadu.xml",
    "https://sachet.ndma.gov.in/cap_public_website/rss/rss_tamil_nadu.xml",
    "https://sachet.ndma.gov.in/cap_public_website/rss/rss_tn.xml",
    "https://sachet.ndma.gov.in/cap_public_website/rss/rss_state_33.xml" # TN census code 33
]

for url in candidate_state_feeds:
    test_url(url)

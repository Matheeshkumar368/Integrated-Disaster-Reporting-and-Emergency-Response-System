import os
import re

static_dir = 'src/main/resources/static'
html_files = [f for f in os.listdir(static_dir) if f.endswith('.html')]
broken_links = []

for hf in sorted(html_files):
    path = os.path.join(static_dir, hf)
    with open(path, 'r', encoding='utf-8', errors='ignore') as f:
        content = f.read()
    
    # Extract href and location assignments
    hrefs = re.findall(r'href=["\']([^"\']+)["\']', content)
    locs = re.findall(r'location(?:\.href)?\s*=\s*["\']([^"\']+)["\']', content)
    
    for raw in hrefs + locs:
        if raw.startswith('http://') or raw.startswith('https://') or raw.startswith('tel:') or raw.startswith('mailto:') or raw.startswith('#'):
            continue
        clean = raw.split('?')[0].split('#')[0].lstrip('/')
        if clean.endswith('.html'):
            target_path = os.path.join(static_dir, clean)
            if not os.path.exists(target_path):
                broken_links.append((hf, raw, clean))

print(f"Total HTML files checked: {len(html_files)}")
print(f"Broken internal links found: {len(broken_links)}")
for src, raw, clean in broken_links:
    print(f"  {src} -> '{raw}' (Target missing: '{clean}')")

import glob, re

for f in sorted(glob.glob('src/main/resources/static/*.html') + glob.glob('src/main/resources/static/*.js')):
    with open(f, 'r', encoding='utf-8', errors='ignore') as fp:
        content = fp.read()
    matches = re.findall(r'href=[\'"]([^\'"]*notif[^\'"]*)[\'"]', content, re.IGNORECASE)
    if matches:
        print(f'{f}: {matches}')

print("\n--- Checking all window.location and navigation redirects ---")
for f in sorted(glob.glob('src/main/resources/static/*.html') + glob.glob('src/main/resources/static/*.js')):
    with open(f, 'r', encoding='utf-8', errors='ignore') as fp:
        content = fp.read()
    matches = re.findall(r'(?:location\.href|location\.replace|window\.location)\s*=\s*[\'"]([^\'"]*)[\'"]', content, re.IGNORECASE)
    notif_redirects = [m for m in matches if 'notif' in m.lower() or 'dashboard' in m.lower() or 'index' in m.lower()]
    if notif_redirects:
        print(f'{f}: {notif_redirects}')

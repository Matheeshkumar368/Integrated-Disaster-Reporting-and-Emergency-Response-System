import os, glob, re

html_files = sorted(glob.glob('src/main/resources/static/*.html'))
print(f"Total HTML files: {len(html_files)}")

for f in html_files:
    fname = os.path.basename(f)
    with open(f, 'r', encoding='utf-8', errors='ignore') as fp:
        content = fp.read()
    
    # Check for sidebar vs top nav
    has_sidebar = bool(re.search(r'class=[\'"][^\'"]*sidebar[^\'"]*[\'"]', content, re.IGNORECASE))
    has_topnav = bool(re.search(r'class=[\'"][^\'"]*(top-nav|navbar|header-nav|nav-links|header_nav|main-nav)[^\'"]*[\'"]', content, re.IGNORECASE))
    
    # Extract nav / sidebar section specifically
    nav_blocks = re.findall(r'(<(?:nav|aside|div)[^>]*(?:class|id)=[\'"][^\'"]*(?:sidebar|navbar|nav-container|navigation)[^\'"]*[\'"][^>]*>.*?</(?:nav|aside|div)>)', content, re.IGNORECASE | re.DOTALL)
    
    links = re.findall(r'<a\s+[^>]*href=[\'"]([^\'"#?]+)[\'"][^>]*>(.*?)</a>', content, re.IGNORECASE | re.DOTALL)
    nav_links = []
    for href, text in links:
        clean_text = re.sub(r'<[^>]+>', '', text).strip()
        clean_text = ' '.join(clean_text.split())[:35]
        if any(k in href.lower() for k in ['dashboard', 'notification', 'task', 'alert', 'user', 'profile', 'report', 'rescue', 'home', 'index', 'login', 'logout', 'broadcast', 'video', 'training', 'setting']):
            nav_links.append((href, clean_text))
            
    print(f"\n[{fname}] - sidebar={has_sidebar}, topnav={has_topnav}, total_nav_links={len(nav_links)}")
    for h, t in nav_links:
        print(f"   -> {h} : '{t}'")

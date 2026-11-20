"""
Comprehensive Static Asset, Navigation, and API Mapping Auditor for Prompt 6.
"""

import os
import re
import json

STATIC_DIR = os.path.abspath("src/main/resources/static")
JAVA_DIR = os.path.abspath("src/main/java")

def audit_html_pages():
    html_files = [f for f in os.listdir(STATIC_DIR) if f.endswith('.html')]
    print("=" * 70)
    print(f"1. AUDITING {len(html_files)} HTML PAGES FOR ASSETS AND LINKS")
    print("=" * 70)

    broken_links = {}
    broken_assets = {}

    for h in html_files:
        path = os.path.join(STATIC_DIR, h)
        with open(path, 'r', encoding='utf-8', errors='ignore') as f:
            content = f.read()

        # Check stylesheets
        css_matches = re.findall(r'<link[^>]+href=["\']([^"\']+)["\']', content, re.IGNORECASE)
        for c in css_matches:
            if c.startswith('http') or c.startswith('//') or c.startswith('data:'):
                continue
            clean_c = c.split('?')[0].split('#')[0].lstrip('/')
            full_path = os.path.join(STATIC_DIR, clean_c)
            if not os.path.exists(full_path):
                broken_assets.setdefault(h, []).append(f"CSS missing: {c}")

        # Check script tags
        js_matches = re.findall(r'<script[^>]+src=["\']([^"\']+)["\']', content, re.IGNORECASE)
        for j in js_matches:
            if j.startswith('http') or j.startswith('//'):
                continue
            clean_j = j.split('?')[0].split('#')[0].lstrip('/')
            full_path = os.path.join(STATIC_DIR, clean_j)
            if not os.path.exists(full_path):
                broken_assets.setdefault(h, []).append(f"JS missing: {j}")

        # Check href links
        href_matches = re.findall(r'<a[^>]+href=["\']([^"\']+)["\']', content, re.IGNORECASE)
        for hr in href_matches:
            if hr.startswith('http') or hr.startswith('//') or hr.startswith('#') or hr.startswith('javascript:') or hr.startswith('tel:') or hr.startswith('mailto:'):
                continue
            clean_hr = hr.split('?')[0].split('#')[0].lstrip('/')
            if not clean_hr:
                continue
            # Some href may be like admin-dashboard without .html or with .html
            full_path = os.path.join(STATIC_DIR, clean_hr)
            full_path_html = os.path.join(STATIC_DIR, clean_hr + ".html")
            if not os.path.exists(full_path) and not os.path.exists(full_path_html):
                broken_links.setdefault(h, []).append(hr)

    if broken_assets:
        print("[!] BROKEN ASSETS DETECTED:")
        for page, assets in broken_assets.items():
            for a in assets:
                print(f"   {page} -> {a}")
    else:
        print("[PASS] All referenced CSS stylesheets and JavaScript bundles exist.")

    if broken_links:
        print("[!] BROKEN HREF LINKS DETECTED:")
        for page, links in broken_links.items():
            for l in links:
                print(f"   {page} -> {l}")
    else:
        print("[PASS] All HTML href navigation targets are valid.")

def audit_backend_controllers():
    print("=" * 70)
    print("2. AUDITING BACKEND SPRING CONTROLLERS AND REST ENDPOINTS")
    print("=" * 70)

    controller_files = []
    for root, dirs, files in os.walk(JAVA_DIR):
        for f in files:
            if f.endswith("Controller.java"):
                controller_files.append(os.path.join(root, f))

    print(f"Found {len(controller_files)} Spring Boot Controller classes:")
    all_endpoints = []

    for cpath in controller_files:
        cname = os.path.basename(cpath)
        with open(cpath, 'r', encoding='utf-8') as f:
            code = f.read()

        # Check base RequestMapping
        base_match = re.search(r'@RequestMapping\(\s*["\']([^"\']+)["\']', code)
        base_path = base_match.group(1) if base_match else ""

        # Find methods
        mappings = re.findall(r'@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\s*(\([^)]*\))?', code)
        methods_found = 0
        for mtype, margs in mappings:
            # Extract path from margs
            path_match = re.search(r'["\']([^"\']+)["\']', margs)
            subpath = path_match.group(1) if path_match else ""
            full_ep = (base_path + ("" if subpath.startswith("/") or not subpath else "/") + subpath).replace("//", "/")
            if not full_ep.startswith("/"):
                full_ep = "/" + full_ep
            m_verb = mtype.replace("Mapping", "").upper() if mtype != "RequestMapping" else "ALL"
            all_endpoints.append((m_verb, full_ep, cname))
            methods_found += 1

        print(f" - {cname:30} : Base='{base_path}' ({methods_found} endpoints)")

    print(f"\nTotal REST Endpoints Registered: {len(all_endpoints)}")
    return all_endpoints

def audit_frontend_api_calls(backend_endpoints):
    print("=" * 70)
    print("3. AUDITING FRONTEND API CALLS AGAINST BACKEND ENDPOINTS")
    print("=" * 70)

    frontend_calls = set()
    for root, dirs, files in os.walk(STATIC_DIR):
        for f in files:
            if f.endswith(".html") or f.endswith(".js"):
                fpath = os.path.join(root, f)
                with open(fpath, 'r', encoding='utf-8', errors='ignore') as file:
                    content = file.read()
                # Find fetch('/api/...') or url: '/api/...'
                calls = re.findall(r'fetch\(\s*[`"\'](/api/[^`"\'?#\s]+)', content)
                calls += re.findall(r'url:\s*[`"\'](/api/[^`"\'?#\s]+)', content)
                for call in calls:
                    # Clean template literals like ${...}
                    clean_call = re.sub(r'\$\{[^}]+\}', '{param}', call)
                    frontend_calls.add((clean_call, f))

    print(f"Extracted {len(frontend_calls)} distinct /api frontend calls.")
    backend_paths = set(ep[1] for ep in backend_endpoints)

    unmatched = []
    for call, fname in frontend_calls:
        # Check if direct match or path variable pattern match
        matched = False
        for bp in backend_paths:
            # Simple regex convert of {id} or *
            pattern = re.sub(r'\{[^}]+\}', '[^/]+', bp)
            pattern = pattern.replace('/**', '/?.*').replace('/*', '/[^/]+')
            if re.fullmatch(pattern, call) or re.fullmatch(pattern, call.rstrip('/')):
                matched = True
                break
            # Also check if call with param matches
            call_pattern = call.replace('{param}', '[^/]+')
            if re.fullmatch(call_pattern, bp):
                matched = True
                break
        if not matched:
            unmatched.append((call, fname))

    if unmatched:
        print("[!] POTENTIALLY UNMATCHED API CALLS:")
        for call, fname in unmatched:
            print(f"   {fname:25} -> {call}")
    else:
        print("[PASS] All frontend API calls map to verified backend controllers!")

if __name__ == "__main__":
    audit_html_pages()
    endpoints = audit_backend_controllers()
    audit_frontend_api_calls(endpoints)

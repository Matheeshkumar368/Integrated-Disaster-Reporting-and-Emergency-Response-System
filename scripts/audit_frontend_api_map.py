import os
import re

static_dir = r'src/main/resources/static'
api_calls = {}

for root, dirs, files in os.walk(static_dir):
    for file in files:
        if file.endswith('.html') or file.endswith('.js'):
            path = os.path.join(root, file)
            with open(path, 'r', encoding='utf-8', errors='ignore') as f:
                content = f.read()
            # find fetch / axios / xhr
            matches = re.findall(r'[\'"`](/api/[a-zA-Z0-9_\-\/]+)', content)
            unique_matches = sorted(list(set(matches)))
            if unique_matches:
                api_calls[file] = unique_matches

for file, calls in sorted(api_calls.items()):
    print(f'=== {file} ===')
    for c in calls:
        print(f'  {c}')

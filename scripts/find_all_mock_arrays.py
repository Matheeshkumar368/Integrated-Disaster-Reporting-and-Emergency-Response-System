import os, re

static_dir = r"src\main\resources\static"

for f in os.listdir(static_dir):
    if not (f.endswith(".html") or f.endswith(".js")):
        continue
    path = os.path.join(static_dir, f)
    with open(path, "r", encoding="utf-8", errors="ignore") as fp:
        content = fp.read()
    
    # Search for patterns where an array of objects is assigned
    matches = re.finditer(r"(?:const|let|var|\=)\s*\[\s*\{\s*['\"]?(?:title|id|name|type|headline|message|severity|role)", content, re.IGNORECASE)
    for m in matches:
        # get line number
        line_no = content[:m.start()].count("\n") + 1
        snippet = content[m.start():m.start()+120].replace("\n", " ")
        print(f"{f}:{line_no}: {snippet}")

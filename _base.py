import os, base64, sys
BASE = r"c:\report-centre"
def w(path, content):
    full = os.path.join(BASE, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(content)
    print(f"  OK: {path}")
def wb(path, b64):
    full = os.path.join(BASE, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "wb") as fh:
        fh.write(base64.b64decode(b64))
    print(f"  OK: {path}")

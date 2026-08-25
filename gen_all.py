import os, sys
BASE = r"c:\report-centre"

def w(path, content):
    full = os.path.join(BASE, path)
    os.makedirs(os.path.dirname(full), exist_ok=True)
    with open(full, "w", encoding="utf-8", newline="\n") as fh:
        fh.write(content)
    print(f"  OK: {path}")

exec(open(os.path.join(BASE, "_gen_part1.py"), encoding="utf-8").read())
exec(open(os.path.join(BASE, "_gen_part2.py"), encoding="utf-8").read())
exec(open(os.path.join(BASE, "_gen_part3.py"), encoding="utf-8").read())
exec(open(os.path.join(BASE, "_gen_part4.py"), encoding="utf-8").read())
exec(open(os.path.join(BASE, "_gen_part5.py"), encoding="utf-8").read())
print("\n=== All files generated successfully ===")

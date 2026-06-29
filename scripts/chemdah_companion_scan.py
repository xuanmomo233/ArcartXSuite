import zipfile
import os
import re

LIB = os.path.join(os.path.dirname(__file__), "..", "libs")

def scan_class(path, cls_path, label):
    with zipfile.ZipFile(path) as z:
        data = z.read(cls_path)
    print(f"=== {label} ({len(data)} bytes) ===")
    patterns = [
        b"FREE", b"free", b"MySQL", b"SQL", b"LOCAL", b"DatabaseSQL",
        "免费".encode(), "付费".encode(), b"isFree", b"FREE.txt",
    ]
    for p in patterns:
        if p in data:
            print(f"  contains: {p!r}")
    # extract readable strings 4+
    strs = set()
    for m in re.finditer(rb"[\x20-\x7e]{4,}", data):
        strs.add(m.group().decode())
    for s in sorted(strs):
        if any(k in s for k in ["FREE", "free", "SQL", "LOCAL", "MySQL", "DatabaseSQL", "付费", "免费", "disable"]):
            print(f"  str: {s}")
    print()

for jar, label in [
    ("Chemdah-1.1.33-FREE.jar", "33 FREE"),
    ("Chemdah-1.1.8.jar", "1.1.8"),
]:
    path = os.path.join(LIB, jar)
    scan_class(path, "ink/ptms/chemdah/core/database/Database$Companion.class", f"Database$Companion {label}")
    scan_class(path, "ink/ptms/chemdah/Chemdah.class", f"Chemdah {label}")

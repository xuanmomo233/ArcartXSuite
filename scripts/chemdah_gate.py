import zipfile
import os
import re

LIB = os.path.join(os.path.dirname(__file__), "..", "libs")

for j in ["Chemdah-1.1.8.jar", "Chemdah-1.1.19-FREE.jar", "Chemdah-1.1.33-FREE.jar"]:
    path = os.path.join(LIB, j)
    print(f"=== {j} ===")
    with zipfile.ZipFile(path) as z:
        for e in z.namelist():
            if not e.endswith(".class"):
                continue
            data = z.read(e)
            has_free = b"isFree" in data
            has_freeze = b"isFreeze" in data
            only_free = has_free and not has_freeze
            if only_free:
                print(f"  isFree only: {e}")
            if "免费".encode() in data or "付费".encode() in data or b"MySQL" in data:
                print(f"  chinese/mysql msg: {e}")

        dc_path = "ink/ptms/chemdah/core/database/Database$Companion.class"
        if dc_path in z.namelist():
            dc = z.read(dc_path)
            for s in re.findall(rb"[\x20-\x7e]{3,}", dc):
                d = s.decode()
                if any(k in d for k in ["SQL", "LOCAL", "MySQL", "Free", "free", "DatabaseSQL", "SQLite", "付费", "免费"]):
                    print(f"  Database$Companion: {d}")

        chemdah = z.read("ink/ptms/chemdah/Chemdah.class")
        for pat in [b"FREE", b"free", b"MySQL", b"SQL", b"LOCAL"]:
            if pat in chemdah:
                print(f"  Chemdah.class contains: {pat.decode()}")

        sql_classes = [e for e in z.namelist() if "DatabaseSQL" in e]
        print(f"  DatabaseSQL classes: {len(sql_classes)}")
    print()

"""Deep compare Database layer and verify patched jar."""
import zipfile
import os
import struct

LIB = os.path.join(os.path.dirname(__file__), "..", "libs")

def class_info(data):
    if data[:4] != b"\xca\xfe\xba\xbe":
        return {}
    minor, major = struct.unpack(">HH", data[4:8])
    cp_count = struct.unpack(">H", data[8:10])[0]
    return {"major": major, "minor": minor, "size": len(data)}


def compare_class(jar_a, jar_b, cls_path, label_a, label_b):
    with zipfile.ZipFile(jar_a) as za, zipfile.ZipFile(jar_b) as zb:
        if cls_path not in za.namelist() or cls_path not in zb.namelist():
            return None
        da, db = za.read(cls_path), zb.read(cls_path)
        ia, ib = class_info(da), class_info(db)
        same = da == db
        return {
            "path": cls_path,
            label_a: ia,
            label_b: ib,
            "identical": same,
            "size_diff": len(da) - len(db),
        }


jars = {
    "1.1.8": os.path.join(LIB, "Chemdah-1.1.8.jar"),
    "1.1.33-FREE": os.path.join(LIB, "Chemdah-1.1.33-FREE.jar"),
    "patched": os.path.join(LIB, "Chemdah-1.1.33-FREE-patched.jar"),
}

# Compare shared database classes between 1.1.8 and 1.1.33
shared_db = []
with zipfile.ZipFile(jars["1.1.8"]) as z8, zipfile.ZipFile(jars["1.1.33-FREE"]) as z33:
    c8 = {e for e in z8.namelist() if "core/database/" in e and e.endswith(".class")}
    c33 = {e for e in z33.namelist() if "core/database/" in e and e.endswith(".class")}
    shared_db = sorted(c8 & c33)

print("=== Shared database classes: identical between 1.1.8 and 1.1.33-FREE? ===")
diff_classes = []
for cls in shared_db:
    r = compare_class(jars["1.1.8"], jars["1.1.33-FREE"], cls, "18", "33")
    if r and not r["identical"]:
        diff_classes.append((cls, r["size_diff"]))

print(f"  Total shared: {len(shared_db)}, different: {len(diff_classes)}")
for cls, diff in diff_classes[:20]:
    short = cls.split("/")[-1]
    print(f"    {short}: size diff {diff} bytes")
if len(diff_classes) > 20:
    print(f"    ... and {len(diff_classes) - 20} more")

# DatabaseSQL from 1.1.8 vs patched
print("\n=== Patched jar verification ===")
if os.path.exists(jars["patched"]):
    with zipfile.ZipFile(jars["patched"]) as zp, zipfile.ZipFile(jars["1.1.8"]) as z8:
        sql_p = [e for e in zp.namelist() if "DatabaseSQL" in e and e.endswith(".class")]
        sql_8 = [e for e in z8.namelist() if "DatabaseSQL" in e and e.endswith(".class")]
        print(f"  Patched DatabaseSQL count: {len(sql_p)} (1.1.8 has {len(sql_8)})")
        mismatched = []
        for e in sql_8:
            if e in zp.namelist():
                if zp.read(e) != z8.read(e):
                    mismatched.append(e)
        print(f"  DatabaseSQL bytes mismatch vs 1.1.8: {len(mismatched)}")
        # Check DatabaseSQL.class major version
        ds = zp.read("ink/ptms/chemdah/core/database/DatabaseSQL.class")
        print(f"  DatabaseSQL.class: {class_info(ds)}")

# What FREE lacks besides DatabaseSQL
print("\n=== Classes only in 1.1.8 (premium stripped from FREE) ===")
with zipfile.ZipFile(jars["1.1.8"]) as z8, zipfile.ZipFile(jars["1.1.33-FREE"]) as z33:
    only_8 = sorted(set(z8.namelist()) - set(z33.namelist()))
    chemdah_only = [e for e in only_8 if e.startswith("ink/ptms/chemdah/") and e.endswith(".class")]
    pkgs = {}
    for e in chemdah_only:
        parts = e.replace(".class", "").split("/")
        pkg = "/".join(parts[3:6]) if len(parts) >= 6 else "/".join(parts[3:])
        pkgs.setdefault(pkg, []).append(parts[-1])
    for pkg, cls in sorted(pkgs.items(), key=lambda x: -len(x[1])):
        tops = [c for c in cls if "$" not in c]
        print(f"  {pkg}: {len(cls)} ({tops[:5]})")

# FREE.txt content
print("\n=== FREE.txt ===")
with zipfile.ZipFile(jars["1.1.33-FREE"]) as z:
    print(z.read("FREE.txt").decode("utf-8"))

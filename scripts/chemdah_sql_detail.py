import zipfile
import os

LIB = os.path.join(os.path.dirname(__file__), "..", "libs")

for j in ["Chemdah-1.1.8.jar", "Chemdah-1.1.33-FREE.jar", "Chemdah-1.1.33-FREE-patched.jar"]:
    path = os.path.join(LIB, j)
    if not os.path.exists(path):
        continue
    with zipfile.ZipFile(path) as z:
        sql = sorted([e for e in z.namelist() if "DatabaseSQL" in e and e.endswith(".class")])
        tops = [e.split("/")[-1] for e in sql if "$" not in e.split("/")[-1]]
        print(f"=== {os.path.basename(j)}: {len(sql)} entries, top-level: {tops} ===")
        if j.endswith("33-FREE.jar") and not "patched" in j:
            missing_from_18 = set()
            with zipfile.ZipFile(os.path.join(LIB, "Chemdah-1.1.8.jar")) as z18:
                all18 = {e for e in z18.namelist() if "DatabaseSQL" in e}
            print("  missing vs 1.1.8:", sorted(all18 - set(sql))[:15], "...")

# Compare Database$Companion file sizes
for j in ["Chemdah-1.1.8.jar", "Chemdah-1.1.33-FREE.jar"]:
    with zipfile.ZipFile(os.path.join(LIB, j)) as z:
        info = z.getinfo("ink/ptms/chemdah/core/database/Database$Companion.class")
        print(f"{j} Database$Companion size: {info.file_size}")

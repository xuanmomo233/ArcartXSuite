"""Verify the persisted JSON schema against the real CI-obfuscated module."""
import pathlib
import re
import subprocess

root = pathlib.Path("build/ArcartX-Suite/modules-obf")
mappings = [p for p in root.glob("*-proguard-mapping.txt") if "abyss" in p.name.lower()]
assert len(mappings) == 1, f"Expected the Abyss mapping, found {mappings}"
mapping = mappings[0]
classes = {}
current = None
for line in mapping.read_text(encoding="utf-8").splitlines():
    if line and not line.startswith((" ", "#")) and " -> " in line:
        original, renamed = line.removesuffix(":").split(" -> ")
        current = classes.setdefault(original, {"renamed": renamed, "fields": {}})
    elif current is not None and " -> " in line and "(" not in line:
        original, renamed = line.strip().split(" -> ")
        current["fields"][original.rsplit(" ", 1)[-1]] = renamed

expected = {
    "RunLedger$Data": {"run", "owner", "state", "reviveLimit", "revision", "roster", "departed", "used", "leases", "loot"},
    "ReviveTransactions$Lease": {"owner", "operation", "run", "player", "reason", "expected", "death", "state", "code", "expires", "epoch"},
    "RunOperations$Stored": {"request", "receipt"},
    "RunLedger$Delivery": {"run", "key", "player", "rewards", "entries"},
}
jar = mapping.with_name(mapping.name.replace("-proguard-mapping.txt", ".jar"))
assert jar.is_file(), jar
for suffix, fields in expected.items():
    original = "xuanmo.arcartxsuite.abyss.service." + suffix
    actual = classes[original]
    for name in fields:
        assert actual["fields"].get(name) == name, f"Persisted field renamed: {original}.{name}"
    if suffix.endswith(("$Stored", "$Delivery")):
        output = subprocess.run(["javap", "-v", "-p", "-classpath", str(jar), actual["renamed"]],
                                check=True, capture_output=True, text=True).stdout
        assert re.search(r"^Record:\s*$", output, re.MULTILINE), f"Missing record metadata: {original}"
print("PASS durable Abyss JSON fields and record metadata in the obfuscated Jar")

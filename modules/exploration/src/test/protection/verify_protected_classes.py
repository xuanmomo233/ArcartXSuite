"""Verify that Exploration encrypts real business classes, including named-package classes."""

from pathlib import Path
import re
from zipfile import ZipFile


def main():
    version = re.search(r"(?m)^version=(\S+)", Path("gradle.properties").read_text(encoding="utf-8"))
    if version is None:
        raise SystemExit("Missing Suite version")

    root = Path("build/ArcartX-Suite")
    jar_name = f"ArcartXSuite-Exploration-{version[1]}.jar"
    with ZipFile(root / "modules" / jar_name) as original, ZipFile(root / "modules-enc" / jar_name) as protected:
        descriptor = original.read("module.yml").decode("utf-8")
        main_class = re.search(r"(?m)^main:[ \t]*(\S+)", descriptor)
        if main_class is None:
            raise SystemExit("Missing Exploration module entry")
        entry = main_class[1].replace(".", "/")
        real_classes = {name for name in original.namelist() if name.endswith(".class")}
        bootstrap = {name for name in real_classes if name == f"{entry}.class" or name.startswith(f"{entry}$")}
        business_classes = real_classes - bootstrap
        if f"{entry}.class" not in bootstrap or not business_classes:
            raise SystemExit("Exploration input is missing its entry or business classes")

        protected_files = set(protected.namelist())
        expected_encrypted = {f"ENCRYPTED/{name.removesuffix('.class')}.enc" for name in business_classes}
        missing = expected_encrypted - protected_files
        if missing or business_classes & protected_files or not bootstrap <= protected_files:
            raise SystemExit("Exploration must keep its named entry and encrypt every other real class")
    if not (root / "module-axb" / "exploration.axb").stat().st_size:
        raise SystemExit("Missing Exploration AXB payload")
    print(f"Verified Exploration: {len(business_classes)} real encrypted classes and {len(bootstrap)} bootstrap classes")


if __name__ == "__main__":
    main()

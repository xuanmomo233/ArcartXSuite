"""Verify edition markers on delivered artefacts and source isolation before obfuscation."""

import argparse
import base64
import gzip
import io
import json
from pathlib import Path
import re
from zipfile import BadZipFile, ZipFile


EDITION_RESOURCE = "META-INF/axs-edition.txt"


def check_edition(jar, expected, label):
    if jar.namelist().count(EDITION_RESOURCE) != 1:
        raise ValueError(f"{label}: expected one edition marker")
    if jar.read(EDITION_RESOURCE).decode("utf-8").strip() != expected:
        raise ValueError(f"{label}: wrong build edition")


def check_source_classes(jar, source_directory, edition, label):
    classes = {member for member in jar.namelist() if member.endswith(".class")}
    customer_sources = source_directory / "customer" / "java"
    free_sources = source_directory / "free" / "java"
    for source in customer_sources.rglob("*.java"):
        relative = source.relative_to(customer_sources)
        name = relative.with_suffix("").as_posix()
        if edition == "customer":
            if name + ".class" not in classes:
                raise ValueError(f"{label}: customer class missing: {relative}")
        elif not (free_sources / relative).is_file() and any(
            member == name + ".class" or member.startswith(name + "$") for member in classes
        ):
            raise ValueError(f"{label}: customer-only class included: {relative}")


def verify_host(edition, raw, protected):
    with ZipFile(raw) as jar:
        check_edition(jar, edition, raw.name)
        check_source_classes(jar, Path("src"), edition, raw.name)
        if edition == "free":
            forbidden = ("redis/", "xuanmo/arcartxsuite/auth/minecraft/ProxyProofs",
                         "xuanmo/arcartxsuite/auth/minecraft/ProxyKeys")
            if any(name.startswith(forbidden) for name in jar.namelist()):
                raise ValueError(f"{raw.name}: paid transport resources or proof implementation included")
            catalog = jar.read("META-INF/axs-libraries.properties")
            if b"redis/clients/jedis/" in catalog or b".id=jedis-" in catalog:
                raise ValueError(f"{raw.name}: Redis client included in the runtime catalog")
    with ZipFile(protected) as jar:
        check_edition(jar, edition, protected.name)


def module_id(jar):
    descriptor = jar.read("module.yml").decode("utf-8")
    match = re.search(r"(?m)^id:[ \t]*([a-z][a-z0-9_-]*)[ \t]*\r?$", descriptor)
    return match[1] if match else None


def verify_module(edition, module, output):
    from cryptography.exceptions import InvalidTag
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM

    if not re.fullmatch(r"[a-z][a-z0-9_-]*", module) or module == "debug":
        raise ValueError(f"Invalid production module: {module}")
    directory = Path("modules") / module
    checked = 0
    for candidate in (directory / "build" / "libs").glob("*.jar"):
        with ZipFile(candidate) as jar:
            if "module.yml" not in jar.namelist() or module_id(jar) != module:
                continue
            check_edition(jar, edition, candidate.name)
            check_source_classes(jar, directory / "src", edition, candidate.name)
            checked += 1
    if checked != 1:
        raise ValueError(f"{module}: expected one pre-obfuscation module JAR")
    axb = output / f"{module}.axb"
    data = axb.read_bytes()
    metadata = json.loads(axb.with_suffix(".meta.json").read_text(encoding="utf-8"))
    iv = base64.b64decode(metadata["moduleIv"], validate=True)
    if len(data) != metadata["size"] or len(iv) != 12 or data[4:16] != iv:
        raise ValueError(f"{module}: invalid AXB framing")
    key = base64.b64decode(metadata["moduleKey"], validate=True)
    try:
        contents = gzip.decompress(AESGCM(key).decrypt(iv, data[16:], None))
    except InvalidTag as error:
        raise ValueError(f"{module}: AXB integrity check failed") from error
    with ZipFile(io.BytesIO(contents)) as jar:
        if module_id(jar) != module:
            raise ValueError(f"{module}: AXB has the wrong module ID")
        check_edition(jar, edition, axb.name)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--edition", choices=("free", "customer"), required=True)
    commands = parser.add_subparsers(dest="command", required=True)
    host = commands.add_parser("host")
    host.add_argument("--raw", type=Path, required=True)
    host.add_argument("--protected", type=Path, required=True)
    module = commands.add_parser("modules")
    module.add_argument("--selection", required=True)
    module.add_argument("--output", type=Path, default=Path("build/ArcartX-Suite/module-axb"))
    args = parser.parse_args()
    try:
        if args.command == "host":
            verify_host(args.edition, args.raw, args.protected)
            print(f"Verified {args.edition} host edition")
        else:
            selection = json.loads(args.selection)
            if (
                not isinstance(selection, list) or not selection
                or any(not isinstance(module_id_value, str) for module_id_value in selection)
                or len(set(selection)) != len(selection)
            ):
                raise ValueError("Invalid module selection")
            for module_id_value in selection:
                verify_module(args.edition, module_id_value, args.output)
            print(f"Verified {len(selection)} {args.edition} module artefacts")
    except (BadZipFile, KeyError, OSError, ValueError) as error:
        parser.exit(1, f"Suite edition verification failed: {error}\n")


if __name__ == "__main__":
    main()

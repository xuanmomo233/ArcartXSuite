"""Collect a signed customer export into one administrator download."""

import argparse
import base64
import json
from pathlib import Path
import re
from shutil import copyfile
from tempfile import TemporaryDirectory
from zipfile import ZipFile, is_zipfile


INSTALLERS = ("ArcartX-Suite-windows-x86_64.zip", "ArcartX-Suite-linux-x86_64.zip")
REGISTRATION = "register-module-variants.json"
MODULE_PATH = re.compile(r"plugins/ArcartX-Suite/modules/([a-z][a-z0-9_-]{0,63})\.axb")
LIBRARY_PATH = re.compile(r"plugins/ArcartX-Suite/runtime-libraries/[0-9a-f]{64}\.jar")


def inspect_installer(installer):
    with ZipFile(installer) as archive:
        members = archive.namelist()
    required = {"INSTALL.txt", "plugins/ArcartX-Suite.jar"}
    modules = set()
    for member in members:
        match = MODULE_PATH.fullmatch(member)
        if match:
            modules.add(match[1])
        elif member not in required and not LIBRARY_PATH.fullmatch(member):
            raise ValueError(f"Unexpected file inside {installer.name}: {member!r}")
    if (len(members) != len(set(members)) or not required.issubset(members)
            or not 1 <= len(modules) <= 64 or "debug" in modules):
        raise ValueError(f"Incomplete or invalid installer: {installer.name}")
    return modules


def check_registration(registration, modules):
    if registration.name != REGISTRATION or not registration.is_file() or registration.stat().st_size > 266_000:
        raise ValueError(f"Expected an export file named {REGISTRATION}")
    record = json.loads(registration.read_text(encoding="utf-8"))
    if (not isinstance(record, dict) or set(record) != {"format", "records"}
            or record["format"] != "axs-module-variants/v1" or not isinstance(record["records"], list)
            or len(record["records"]) != len(modules)):
        raise ValueError("Expected encrypted registration records matching the installer module count")
    if any(not isinstance(value, str) or len(base64.b64decode(value, validate=True)) <= 28
           for value in record["records"]):
        raise ValueError("Invalid encrypted registration record")


def proxy_files(source):
    proxies = []
    for platform in ("Bungee", "Velocity"):
        name = re.compile(rf"ArcartXSuite-Proxy-{platform}-[A-Za-z0-9][A-Za-z0-9._+-]*\.jar")
        found = [file for file in source.rglob("*.jar") if name.fullmatch(file.name)]
        if len(found) != 1 or not is_zipfile(found[0]):
            raise ValueError(f"Expected one {platform} proxy JAR")
        proxies.extend(found)
    return proxies


def bundle(installers, registration, proxies, output, account):
    if re.fullmatch(r"[0-9]{5,20}", account) is None:
        raise ValueError("Expected a numeric QQ account of 5 to 20 digits")
    if output.exists():
        raise FileExistsError(output)
    packages = [installers / name for name in INSTALLERS]
    windows, linux = (inspect_installer(file) for file in packages)
    if windows != linux:
        raise ValueError("Windows and Linux installers contain different modules")
    check_registration(registration, windows)
    files = packages + proxy_files(proxies)

    output.parent.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(prefix=".customer-delivery-", dir=output.parent) as work:
        staged = Path(work) / "bundle"
        staged.mkdir()
        with ZipFile(staged / f"{account}.zip", "x") as archive:
            for file in files:
                archive.write(file, file.name)
        copyfile(registration, staged / REGISTRATION)
        staged.rename(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--installers", type=Path, required=True)
    parser.add_argument("--registration", type=Path, required=True)
    parser.add_argument("--proxies", type=Path, required=True)
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--account", required=True)
    args = parser.parse_args()
    bundle(args.installers, args.registration, args.proxies, args.output, args.account)


if __name__ == "__main__":
    main()

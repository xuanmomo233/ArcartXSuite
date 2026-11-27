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
INSTRUCTIONS = """客户水印包交付说明

这个压缩包供管理员使用。
1. 在云端后台「授权管理 → 登记客户水印包」导入 admin-only/register-module-variants.json，核对收件账号和模块。
2. 从 customer/installers 取与客户后端操作系统对应的安装 ZIP。需要跨服代理时，可再从 customer/optional-proxy 取对应的 Velocity 或 BungeeCord JAR。
3. 客户填写自己的云端账号和验证密匙，启动宿主完成服务器绑定。管理员为交付所需的服务器发放授权。
请只把需要的客户安装包和代理 JAR 发给客户。整份管理员交付包和登记文件留在管理员侧。
"""


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
    if source is None:
        return []
    proxies = []
    for platform in ("Bungee", "Velocity"):
        name = re.compile(rf"ArcartXSuite-Proxy-{platform}-[A-Za-z0-9][A-Za-z0-9._+-]*\.jar")
        found = [file for file in source.rglob("*.jar") if name.fullmatch(file.name)]
        if len(found) != 1 or not is_zipfile(found[0]):
            raise ValueError(f"Expected one {platform} proxy JAR")
        proxies.extend(found)
    return proxies


def bundle(installers, registration, proxies, output):
    if output.exists():
        raise FileExistsError(output)
    packages = [installers / name for name in INSTALLERS]
    windows, linux = (inspect_installer(file) for file in packages)
    if windows != linux:
        raise ValueError("Windows and Linux installers contain different modules")
    check_registration(registration, windows)
    files = [(file, f"customer/installers/{file.name}") for file in packages]
    files += [(file, f"customer/optional-proxy/{file.name}") for file in proxy_files(proxies)]
    files.append((registration, f"admin-only/{REGISTRATION}"))

    output.parent.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(prefix=".customer-delivery-", dir=output.parent) as work:
        staged = Path(work) / "bundle"
        for source, name in files:
            target = staged / name
            target.parent.mkdir(parents=True, exist_ok=True)
            copyfile(source, target)
        (staged / "README.txt").write_text(INSTRUCTIONS, encoding="utf-8")
        staged.rename(output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--installers", type=Path, required=True)
    parser.add_argument("--registration", type=Path, required=True)
    parser.add_argument("--proxies", type=Path)
    parser.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    bundle(args.installers, args.registration, args.proxies, args.output)


if __name__ == "__main__":
    main()

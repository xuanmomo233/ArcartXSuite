"""Prepare customer installer archives and encrypted registration metadata offline."""
import argparse
import base64
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import tempfile
import zipfile

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

DOMAIN = b"ArcartXSuite/module-variant-registration/v1"
PLATFORMS = ("windows-x86_64", "linux-x86_64")
MODULES_DIR = Path("build/ArcartX-Suite/module-axb")


def check(value, pattern, name):
    if not re.fullmatch(pattern, value):
        raise ValueError(f"Invalid {name}")
    return value


def release_name(commit, properties=Path("gradle.properties")):
    check(commit, r"[0-9a-f]{40}", "commit")
    version = next((line[8:].strip() for line in properties.read_text(encoding="utf-8").splitlines()
                    if line.startswith("version=")), "")
    check(version, r"[A-Za-z0-9._-]{1,40}", "version")
    return f"{version}-local.{commit}-suite.0.0-ci.{commit[:7]}"


def read_key(encoded):
    raw = base64.b64decode(encoded, validate=True)
    if len(raw) != 32 or base64.b64encode(raw).decode("ascii") != encoded:
        raise ValueError("Expected a canonical 32-byte Base64 key")
    return raw


def seal_record(record, registration_key):
    nonce = os.urandom(12)
    raw = json.dumps(record, separators=(",", ":"), ensure_ascii=True).encode("utf-8")
    return base64.b64encode(nonce + AESGCM(registration_key).encrypt(nonce, raw, DOMAIN)).decode("ascii")


def load_watermark():
    spec = importlib.util.spec_from_file_location("suite_watermark", Path(__file__).with_name("watermark-module.py"))
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def collect_modules(modules, release, account, recipient, delivery_id, watermark_key, signing_key, registration_key):
    watermark = load_watermark()
    records = []
    for module_id in modules:
        axb = MODULES_DIR / (module_id + ".axb")
        meta = MODULES_DIR / (module_id + ".meta.json")
        key = json.loads(meta.read_text(encoding="utf-8"))["moduleKey"]
        stamp = watermark.inspect_axb(axb, read_key(key), watermark_key.public_key())
        for field, value in (("moduleId", module_id), ("release", release), ("recipient", recipient), ("licenseId", delivery_id)):
            if stamp[field] != value:
                raise ValueError(f"Watermark {field} mismatch for {module_id}")
        data = axb.read_bytes()
        record = {
            "moduleId": module_id, "release": release, "account": account, "recipient": recipient,
            "deliveryId": delivery_id, "watermarkId": stamp["watermarkId"],
            "checksum": hashlib.sha256(data).hexdigest(), "moduleKey": key,
            "signature": base64.b64encode(signing_key.sign(data)).decode("ascii"),
        }
        records.append(seal_record(record, registration_key))
    return records


def installer(output, platform, release, modules):
    hosts = list(Path("suite-release/hosts", f"axs-jar-encrypted-{platform}").glob("*-encrypted.jar"))
    if len(hosts) != 1:
        raise ValueError(f"Expected one host for {platform}")
    with zipfile.ZipFile(hosts[0]) as host:
        provenance = json.loads(host.read("axs-build.json"))
        if provenance.get("release") != release:
            raise ValueError(f"Host release mismatch for {platform}")
        props = dict(line.split("=", 1) for line in host.read("META-INF/axs-libraries.properties").decode("ascii").splitlines()
                     if line)
        hashes = [check(props[f"artifact.{index}.sha256"], r"[0-9a-f]{64}", "runtime digest")
                  for index in range(int(props["artifact.count"]))]
    prefix = "plugins/ArcartX-Suite/"
    with zipfile.ZipFile(output / f"ArcartX-Suite-{platform}.zip", "x", compression=zipfile.ZIP_STORED) as archive:
        archive.write(hosts[0], "plugins/ArcartX-Suite.jar")
        for digest in hashes:
            jar = Path("suite-release/libraries", f"axs-runtime-libraries-{platform}", f"{digest}.jar")
            with jar.open("rb") as source:
                if hashlib.file_digest(source, "sha256").hexdigest() != digest:
                    raise ValueError(f"Runtime hash mismatch for {platform}")
            archive.write(jar, prefix + "runtime-libraries/" + jar.name)
        for module_id in modules:
            archive.write(MODULES_DIR / f"{module_id}.axb", prefix + f"modules/{module_id}.axb")
        archive.writestr("INSTALL.txt",
            f"Suite release: {release}\n"
            "Copy the plugins tree into each game backend. Keep each backend's own installation identity.\n"
            "The distributor must register this export before you can load the protected modules.\n"
            "Enter your cloud credentials in the generated config and start online to bind an authorized server.\n"
            "Once authorized, the same installation can use cached authorization for up to seven days when the cloud times out.\n")


def export(args):
    if args.output.exists():
        raise FileExistsError(args.output)
    release = release_name(args.commit)
    account = check(os.environ["AXS_EXPORT_ACCOUNT"], r"[0-9]{5,20}", "account")
    recipient = check(os.environ["AXS_EXPORT_RECIPIENT"], r"[A-Za-z0-9_-]{1,80}", "recipient")
    delivery_id = check(os.environ["AXS_EXPORT_DELIVERY_ID"], r"[A-Za-z0-9_-]{1,100}", "delivery ID")
    modules = [check(value, r"[a-z][a-z0-9_-]{0,63}", "module ID") for value in json.loads(args.modules)]
    if not 1 <= len(modules) <= 64 or len(set(modules)) != len(modules) or "debug" in modules:
        raise ValueError("Invalid module selection")
    watermark_key = serialization.load_pem_private_key(args.watermark_key.read_bytes(), password=None)
    if not isinstance(watermark_key, Ed25519PrivateKey):
        raise ValueError("Expected Ed25519 watermark key")
    signing_key = Ed25519PrivateKey.from_private_bytes(read_key(os.environ["AXB_SIGN_SEED_B64"]))
    registration_key = read_key(os.environ["AXS_VARIANT_REGISTRATION_KEY_B64"])
    records = collect_modules(modules, release, account, recipient, delivery_id, watermark_key, signing_key, registration_key)
    args.output.parent.mkdir(parents=True, exist_ok=True)
    with tempfile.TemporaryDirectory(prefix=f".{args.output.name}-", dir=args.output.parent) as work:
        staged = Path(work)
        for platform in PLATFORMS:
            installer(staged, platform, release, modules)
        (staged / "register-module-variants.json").write_text(
            json.dumps({"format": "axs-module-variants/v1", "records": records}), encoding="utf-8")
        if args.output.exists():
            raise FileExistsError(args.output)
        staged.rename(args.output)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    name = commands.add_parser("release")
    name.add_argument("--commit", required=True)
    pack = commands.add_parser("export")
    pack.add_argument("--commit", required=True)
    pack.add_argument("--modules", required=True)
    pack.add_argument("--watermark-key", type=Path, required=True)
    pack.add_argument("--output", type=Path, required=True)
    args = parser.parse_args()
    if args.command == "release":
        print(release_name(args.commit))
    else:
        export(args)


if __name__ == "__main__":
    main()

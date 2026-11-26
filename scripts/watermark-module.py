#!/usr/bin/env python3
"""Stamp the final module JAR after class transformation and before AXB encryption/signing."""
import argparse
import base64
import gzip
import hashlib
import io
import json
import os
from pathlib import Path
import re
import struct
import tempfile
from datetime import datetime, timezone
import uuid
import zipfile

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey
from cryptography.hazmat.primitives.serialization import (Encoding, PrivateFormat, PublicFormat, NoEncryption,
    load_pem_private_key, load_pem_public_key)
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

MARK = "META-INF/axs-watermark.json"
SIGNATURE = "META-INF/axs-watermark.sig"
DOMAIN = b"ArcartX-Suite/module-watermark/v1\n"
MAX_ARCHIVE = 128 * 1024 * 1024
MAX_EXPANDED = 256 * 1024 * 1024


def entries(jar, allow_mark=False):
    found = {}
    expanded = 0
    infos = jar.infolist()
    if len(infos) > 10_000:
        raise ValueError("Too many JAR entries")
    for info in infos:
        name = info.filename
        if name in found or "\\" in name or name.startswith("/") or ".." in name.split("/") or ":" in name:
            raise ValueError("Duplicate or unsafe JAR entry")
        if not allow_mark and name in (MARK, SIGNATURE):
            raise ValueError("JAR already contains a watermark")
        expanded += info.file_size
        if expanded > MAX_EXPANDED or info.file_size > MAX_ARCHIVE:
            raise ValueError("Expanded JAR exceeds the size limit")
        found[name] = jar.read(info)
    return found


def payload_digest(contents):
    digest = hashlib.sha256()
    for name in sorted(contents):
        if name in (MARK, SIGNATURE):
            continue
        encoded = name.encode("utf-8")
        digest.update(struct.pack(">I", len(encoded)))
        digest.update(encoded)
        digest.update(hashlib.sha256(contents[name]).digest())
    return digest.hexdigest()


def stamp(input_jar, output_jar, private_key, module_id, release, recipient, license_id, overwrite=False):
    source, output = Path(input_jar), Path(output_jar)
    if source.resolve() == output.resolve():
        raise ValueError("Input and output must differ")
    if output.exists() and not overwrite:
        raise ValueError("Output exists; choose a new path")
    if not source.is_file() or source.stat().st_size > MAX_ARCHIVE:
        raise ValueError("Input module JAR is missing or too large")
    if not re.fullmatch(r"[a-z][a-z0-9_-]{0,63}", module_id):
        raise ValueError("Invalid module ID")
    for value in (release, recipient, license_id):
        if not value or len(value) > 160 or any(ord(char) < 32 for char in value):
            raise ValueError("Invalid watermark identity")
    if not isinstance(private_key, Ed25519PrivateKey):
        raise ValueError("Watermark signing requires an Ed25519 private key")
    with zipfile.ZipFile(source) as jar:
        contents = entries(jar)
    descriptor = contents.get("module.yml", b"").decode("utf-8-sig")
    match = re.search(r"(?m)^id:\s*['\"]?([a-z][a-z0-9_-]*)", descriptor)
    if not match or match.group(1) != module_id:
        raise ValueError("module.yml ID does not match --module-id")
    if any(re.fullmatch(r"META-INF/[^/]+\.(SF|RSA|DSA|EC)", name, re.I) for name in contents):
        raise ValueError("Stamp before final JAR signing")
    body = {
        "format": "axs-module-watermark/v1", "watermarkId": str(uuid.uuid4()), "moduleId": module_id,
        "release": release, "recipient": recipient, "licenseId": license_id,
        "createdAt": datetime.now(timezone.utc).isoformat(),
        "payloadSha256": payload_digest(contents), "inputArchiveSha256": hashlib.sha256(source.read_bytes()).hexdigest(),
    }
    raw = json.dumps(body, ensure_ascii=False, sort_keys=True, separators=(",", ":")).encode("utf-8")
    contents[MARK] = raw
    contents[SIGNATURE] = private_key.sign(DOMAIN + raw)
    output.parent.mkdir(parents=True, exist_ok=True)
    owned = False
    temporary = None
    try:
        if overwrite:
            fd, name = tempfile.mkstemp(prefix=".axs-watermark-", suffix=".jar", dir=output.parent)
            os.close(fd)
            temporary = Path(name)
            target = temporary
        else:
            # Exclusive creation prevents a racing invocation from overwriting another delivery.
            output.open("xb").close()
            owned = True
            target = output
        with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as jar:
            for name, data in contents.items():
                jar.writestr(name, data)
        if temporary is not None:
            os.replace(temporary, output)
            temporary = None
        return body
    except BaseException:
        if owned:
            output.unlink(missing_ok=True)
        raise
    finally:
        if temporary is not None:
            temporary.unlink(missing_ok=True)


def inspect_contents(contents, public_key):
    if not isinstance(public_key, Ed25519PublicKey):
        raise ValueError("Use the trusted watermark Ed25519 public key")
    raw, signature = contents.get(MARK), contents.get(SIGNATURE)
    if raw is None or signature is None or len(raw) > 8192 or len(signature) != 64:
        raise ValueError("Watermark is missing or malformed")
    public_key.verify(signature, DOMAIN + raw)
    body = json.loads(raw)
    if body.get("format") != "axs-module-watermark/v1" or body.get("payloadSha256") != payload_digest(contents):
        raise ValueError("Watermark payload mismatch")
    return body


def inspect(jar_path, public_key):
    source = Path(jar_path)
    if not source.is_file() or source.stat().st_size > MAX_ARCHIVE:
        raise ValueError("Input JAR is missing or too large")
    with zipfile.ZipFile(source) as jar:
        return inspect_contents(entries(jar, allow_mark=True), public_key)


def inspect_axb(axb_path, module_key, public_key):
    source = Path(axb_path)
    if not source.is_file() or not 32 <= source.stat().st_size <= MAX_ARCHIVE or len(module_key) != 32:
        raise ValueError("Invalid AXB file or module key")
    data = source.read_bytes()
    compressed = AESGCM(module_key).decrypt(data[4:16], data[16:], None)
    with gzip.GzipFile(fileobj=io.BytesIO(compressed)) as stream:
        payload = stream.read(MAX_ARCHIVE + 1)
    if len(payload) > MAX_ARCHIVE:
        raise ValueError("Decrypted module exceeds the size limit")
    with zipfile.ZipFile(io.BytesIO(payload)) as jar:
        return inspect_contents(entries(jar, allow_mark=True), public_key)


def keygen(private_path, public_path):
    private_path, public_path = Path(private_path), Path(public_path)
    if private_path.resolve() == public_path.resolve() or private_path.exists() or public_path.exists():
        raise ValueError("Choose two unused key paths")
    private_path.parent.mkdir(parents=True, exist_ok=True)
    public_path.parent.mkdir(parents=True, exist_ok=True)
    key = Ed25519PrivateKey.generate()
    created = False
    try:
        fd = os.open(private_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
        created = True
        with os.fdopen(fd, "wb") as stream:
            stream.write(key.private_bytes(Encoding.PEM, PrivateFormat.PKCS8, NoEncryption()))
        with public_path.open("xb") as stream:
            stream.write(key.public_key().public_bytes(Encoding.PEM, PublicFormat.SubjectPublicKeyInfo))
    except BaseException:
        if created:
            private_path.unlink(missing_ok=True)
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    generate = commands.add_parser("keygen")
    generate.add_argument("--private-key", type=Path, required=True)
    generate.add_argument("--public-key", type=Path, required=True)
    issue = commands.add_parser("stamp")
    issue.add_argument("--input", type=Path, required=True)
    issue.add_argument("--output", type=Path, required=True)
    issue.add_argument("--private-key", type=Path, required=True)
    issue.add_argument("--module-id", required=True)
    issue.add_argument("--release", required=True)
    issue.add_argument("--recipient", required=True)
    issue.add_argument("--license-id", required=True)
    issue.add_argument("--overwrite", action="store_true", help="Replace an owned build output; input JARs are never overwritten")
    verify = commands.add_parser("inspect")
    source = verify.add_mutually_exclusive_group(required=True)
    source.add_argument("--jar", type=Path)
    source.add_argument("--axb", type=Path)
    verify.add_argument("--module-key-file", type=Path)
    verify.add_argument("--public-key", type=Path, required=True)
    args = parser.parse_args()
    try:
        if args.action == "keygen":
            keygen(args.private_key, args.public_key)
            print(json.dumps({"publicKey": str(args.public_key)}))
        elif args.action == "stamp":
            key = load_pem_private_key(args.private_key.read_bytes(), password=None)
            body = stamp(args.input, args.output, key, args.module_id, args.release, args.recipient, args.license_id, args.overwrite)
            print(json.dumps({"watermarkId": body["watermarkId"], "output": str(args.output)}, ensure_ascii=False))
        else:
            key = load_pem_public_key(args.public_key.read_bytes())
            if args.axb:
                if not args.module_key_file:
                    raise ValueError("--axb requires the maintainer's --module-key-file")
                module_key = base64.b64decode(args.module_key_file.read_text(encoding="utf-8").strip(), validate=True)
                result = inspect_axb(args.axb, module_key, key)
            else:
                result = inspect(args.jar, key)
            print(json.dumps(result, ensure_ascii=False))
    except Exception as error:
        # Never echo key bytes or an unverified recipient claim on failure.
        message = str(error) if isinstance(error, ValueError) else type(error).__name__
        parser.exit(1, "Watermark operation failed: " + message + "\n")


if __name__ == "__main__":
    main()

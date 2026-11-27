"""Transfer verified module files across jobs without exposing module keys."""

import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
from tempfile import TemporaryDirectory

from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives import hashes
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from cryptography.hazmat.primitives.kdf.hkdf import HKDF


MODULES = Path("build/ArcartX-Suite/module-axb")
TRANSFER = Path("suite-module-stage")
MAX_SEAL_BYTES = 128 * 1024


def digest(path):
    with path.open("rb") as stream:
        return hashlib.file_digest(stream, "sha256").hexdigest()


def validate_selection(selection):
    if (not isinstance(selection, list) or not 1 <= len(selection) <= 64
            or any(not isinstance(module, str) or module == "debug"
                   or not re.fullmatch(r"[a-z][a-z0-9_-]{0,63}", module) for module in selection)
            or len(set(selection)) != len(selection)):
        raise ValueError("Invalid module selection")


def staging_context(run_id, commit, edition):
    if not re.fullmatch(r"[0-9]+", run_id) or not re.fullmatch(r"[0-9a-f]{40}", commit) or edition not in {"free", "customer"}:
        raise ValueError("Invalid workflow identity for module staging")
    return f"Suite CI module staging v1:{run_id}:{commit}:{edition}".encode("ascii")


def staging_cipher(secret, context):
    if not secret:
        raise ValueError("Module staging secret is missing")
    key = HKDF(algorithm=hashes.SHA256(), length=32, salt=None, info=context).derive(secret.encode("utf-8"))
    return AESGCM(key)


def validate_metadata(value, axb):
    if not isinstance(value, dict) or set(value) != {"moduleKey", "moduleIv", "size"}:
        raise ValueError(f"Invalid module metadata for {axb.name}")
    if type(value["size"]) is not int or value["size"] != axb.stat().st_size:
        raise ValueError(f"Incorrect module size for {axb.name}")
    for field, length in (("moduleKey", 32), ("moduleIv", 12)):
        data = value[field]
        if not isinstance(data, str) or len(data) != (length + 2) // 3 * 4:
            raise ValueError(f"Invalid module metadata for {axb.name}")
        decoded = base64.b64decode(data, validate=True)
        if len(decoded) != length or base64.b64encode(decoded).decode("ascii") != data:
            raise ValueError(f"Invalid module metadata for {axb.name}")


def seal_modules(source, transfer, selection, secret, context):
    validate_selection(selection)
    cipher = staging_cipher(secret, context)
    if transfer.exists():
        raise FileExistsError(transfer)
    transfer.parent.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(prefix=".suite-modules-", dir=transfer.parent) as work:
        output = Path(work) / "stage"
        output.mkdir()
        entries = {}
        for module in selection:
            axb = source / f"{module}.axb"
            metadata = json.loads(axb.with_suffix(".meta.json").read_text(encoding="utf-8"))
            validate_metadata(metadata, axb)
            shutil.copyfile(axb, output / axb.name)
            entries[module] = {"sha256": digest(output / axb.name), "metadata": metadata}
        nonce = os.urandom(12)
        payload = json.dumps({"modules": selection, "entries": entries}, separators=(",", ":")).encode("utf-8")
        (output / "metadata.enc").write_bytes(nonce + cipher.encrypt(nonce, payload, context))
        output.rename(transfer)


def open_modules(transfer, destination, selection, secret, context):
    validate_selection(selection)
    cipher = staging_cipher(secret, context)
    if destination.exists():
        raise FileExistsError(destination)
    with (transfer / "metadata.enc").open("rb") as stream:
        sealed = stream.read(MAX_SEAL_BYTES + 1)
    if not 28 < len(sealed) <= MAX_SEAL_BYTES:
        raise ValueError("Invalid sealed module metadata size")
    payload = json.loads(cipher.decrypt(sealed[:12], sealed[12:], context))
    if payload["modules"] != selection or set(payload["entries"]) != set(selection):
        raise ValueError("Staged modules do not match the current workflow selection")

    destination.parent.mkdir(parents=True, exist_ok=True)
    with TemporaryDirectory(prefix=".suite-modules-", dir=destination.parent) as work:
        output = Path(work) / "modules"
        output.mkdir()
        for module in selection:
            axb = output / f"{module}.axb"
            shutil.copyfile(transfer / axb.name, axb)
            entry = payload["entries"][module]
            metadata = entry["metadata"]
            validate_metadata(metadata, axb)
            if digest(axb) != entry["sha256"]:
                raise ValueError(f"Staged content changed for {module}")
            axb.with_suffix(".meta.json").write_text(json.dumps(metadata), encoding="utf-8")
        output.rename(destination)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("operation", choices=("seal", "open"))
    args = parser.parse_args()
    try:
        modules = json.loads(os.environ["RELEASE_MODULES"])
        secret = os.environ["AXS_MODULE_STAGE_SECRET"]
        context = staging_context(os.environ["GITHUB_RUN_ID"], os.environ["GITHUB_SHA"], os.environ["AXS_EDITION"])
        if args.operation == "seal":
            seal_modules(MODULES, TRANSFER, modules, secret, context)
        else:
            open_modules(TRANSFER, MODULES, modules, secret, context)
    except InvalidTag:
        parser.exit(1, "Module staging does not belong to this workflow run.\n")
    except (KeyError, OSError, TypeError, ValueError) as error:
        parser.exit(1, f"Module staging failed: {error}\n")


if __name__ == "__main__":
    main()

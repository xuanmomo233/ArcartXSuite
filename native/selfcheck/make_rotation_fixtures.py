"""Build synthetic old/new signature vectors; never package signing seeds."""

import base64
import gzip
import hashlib
import io
import os
import secrets
from pathlib import Path
from zipfile import ZipFile

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def write_signatures(directory, label, data, env_name, public_key_file):
    old_seed = hashlib.sha256(("retired test signing key for " + label).encode()).digest()
    keys = {
        "old": Ed25519PrivateKey.from_private_bytes(old_seed),
        "new": Ed25519PrivateKey.from_private_bytes(base64.b64decode(os.environ[env_name], validate=True)),
    }
    for role, key in keys.items():
        public = key.public_key()
        raw = public.public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)
        assert (raw == public_key_file.read_bytes()) == (role == "new")
        if role == "old":
            old_keys = public_key_file.parent / "old"
            old_keys.mkdir(exist_ok=True)
            (old_keys / public_key_file.name).write_bytes(raw)
        signature = key.sign(data)
        public.verify(signature, data)
        (directory / (label + "-" + role + ".sig")).write_bytes(signature)


def main():
    keys = Path("native-public-keys")
    directory = keys / "rotation"
    directory.mkdir(exist_ok=False)
    payload = io.BytesIO()
    with ZipFile(payload, "w") as jar:
        jar.writestr("module.yml", "id: rotation-fixture\n")
    original = payload.getvalue()
    encryption_key = secrets.token_bytes(32)
    iv = secrets.token_bytes(12)
    axb = b"AXST" + iv + AESGCM(encryption_key).encrypt(iv, gzip.compress(original), None)
    (directory / "module.axb").write_bytes(axb)
    (directory / "module.jar").write_bytes(original)
    (directory / "module-aes.bin").write_bytes(encryption_key)
    write_signatures(directory, "module", axb, "AXB_SIGN_SEED_B64", keys / "ed25519_public_raw.bin")
    timestamp = "1794390000000"
    body = '{"ok":true,"module":"轮换预演"}'.encode()
    (directory / "timestamp.txt").write_text(timestamp, encoding="ascii")
    (directory / "response.json").write_bytes(body)
    write_signatures(directory, "response", timestamp.encode() + b"\n" + body,
                     "AXB_RESP_SIGN_SEED_B64", keys / "response_ed25519_public_raw.bin")


if __name__ == "__main__":
    main()

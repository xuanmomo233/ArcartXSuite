#!/usr/bin/env python3
"""Create a detached Ed25519 signature for a local Suite module JAR."""

import argparse
import hashlib
from pathlib import Path

from cryptography.hazmat.primitives.serialization import load_pem_private_key
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("jar", type=Path, help="Final module JAR")
    parser.add_argument("--private-key", type=Path, required=True, help="Ed25519 PEM key file")
    args = parser.parse_args()

    key = load_pem_private_key(args.private_key.read_bytes(), password=None)
    if not isinstance(key, Ed25519PrivateKey):
        parser.error("--private-key must contain an Ed25519 key")

    with args.jar.open("rb") as source:
        digest = hashlib.file_digest(source, "sha256").digest()
    signature = key.sign(b"AXS local module JAR v1\x00" + digest)
    output = args.jar.with_name(args.jar.name + ".sig")
    output.write_bytes(signature)
    print(output)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""CI-test-only helper for signing raw .axb bytes with Ed25519.

This script is intentionally minimal and only exists so CI can generate a
self-contained positive/negative test vector for NativeBridge.n10().
"""
from __future__ import annotations

import argparse
import base64
import sys
from pathlib import Path

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey


def load_seed(args: argparse.Namespace) -> bytes:
    if args.seed_b64_file:
        seed_text = Path(args.seed_b64_file).read_text(encoding="utf-8").strip()
        return base64.b64decode(seed_text)
    if args.seed_b64:
        return base64.b64decode(args.seed_b64)
    raise SystemExit("one of --seed-b64-file or --seed-b64 is required")


def main() -> int:
    parser = argparse.ArgumentParser(description="CI-test-only Ed25519 signer for raw .axb files")
    parser.add_argument("--axb", required=True, help="Path to the raw .axb file to sign")
    parser.add_argument("--output", required=True, help="Path to write the 64-byte signature")
    parser.add_argument("--seed-b64-file", help="Path to a file containing the Base64-encoded 32-byte Ed25519 seed")
    parser.add_argument("--seed-b64", help="Direct Base64-encoded 32-byte Ed25519 seed")
    args = parser.parse_args()

    seed = load_seed(args)
    if len(seed) != 32:
        print(f"[sign-axb-test] seed must be 32 bytes, got {len(seed)}", file=sys.stderr)
        return 2

    axb_path = Path(args.axb)
    out_path = Path(args.output)
    axb_bytes = axb_path.read_bytes()
    private_key = Ed25519PrivateKey.from_private_bytes(seed)
    signature = private_key.sign(axb_bytes)
    if len(signature) != 64:
        print(f"[sign-axb-test] unexpected signature length {len(signature)}", file=sys.stderr)
        return 1

    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_bytes(signature)
    print(f"[sign-axb-test] wrote signature: {out_path} ({len(signature)} bytes)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

#!/usr/bin/env python3
"""
inject-self-hash.py - post-link native self-hash injector.

Defines the self hash as SHA-256 of the entire on-disk native artifact with the
32-byte expected_self_hash slot zeroed. The slot is located immediately after
the unique 16-byte magic marker in integrity_check.cpp.
"""

import argparse
import hashlib
import sys
from pathlib import Path


MAGIC = bytes([
    0xA3, 0x51, 0x7D, 0x9C, 0xE4, 0x2B, 0x18, 0xF0,
    0x6D, 0xC7, 0x91, 0x34, 0xBA, 0x5E, 0xD2, 0x88,
])
SLOT_SIZE = 32


def find_magic_offsets(blob: bytes) -> list[int]:
    offsets: list[int] = []
    start = 0
    while True:
        index = blob.find(MAGIC, start)
        if index < 0:
            break
        offsets.append(index)
        start = index + 1
    return offsets


def locate_slot(blob: bytes) -> int:
    offsets = find_magic_offsets(blob)
    if not offsets:
        raise ValueError("self-hash magic not found")
    if len(offsets) > 1:
        raise ValueError(f"self-hash magic found {len(offsets)} times; expected exactly once")

    slot_offset = offsets[0] + len(MAGIC)
    if slot_offset + SLOT_SIZE > len(blob):
        raise ValueError("self-hash slot extends beyond end of file")
    return slot_offset


def compute_slot_excluded_hash(blob: bytes) -> bytes:
    slot_offset = locate_slot(blob)
    material = bytearray(blob)
    material[slot_offset:slot_offset + SLOT_SIZE] = b"\x00" * SLOT_SIZE
    return hashlib.sha256(material).digest()


def inject_self_hash_bytes(blob: bytes) -> bytes:
    slot_offset = locate_slot(blob)
    digest = compute_slot_excluded_hash(blob)
    patched = bytearray(blob)
    patched[slot_offset:slot_offset + SLOT_SIZE] = digest
    return bytes(patched)


def inject_self_hash_file(path: Path) -> bytes:
    blob = path.read_bytes()
    patched = inject_self_hash_bytes(blob)
    path.write_bytes(patched)
    return patched


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description="Inject native self-hash into linked artifacts")
    parser.add_argument("artifacts", nargs="+", help="Path(s) to linked .so/.dll artifacts")
    args = parser.parse_args(argv)

    for artifact in args.artifacts:
        path = Path(artifact)
        if not path.is_file():
            print(f"[ERROR] file not found: {path}", file=sys.stderr)
            return 1
        try:
            inject_self_hash_file(path)
            print(f"[OK] injected self-hash: {path}")
        except Exception as exc:
            print(f"[ERROR] {path}: {exc}", file=sys.stderr)
            return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())

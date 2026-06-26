#!/usr/bin/env python3
"""
V2: Native DLL/SO 加密打包工具

两种模式：
1. --gen-key --patch-java <NativeBridge.java>
   生成 32 字节 DLL 加密密钥，将密钥片段嵌入 NativeBridge.java，输出 dll_key.bin。

2. --encrypt --key <dll_key.bin> --native-dir <dir>
   用 dll_key.bin 加密目录下所有 native 库（.dll → .enc, .so → .enc, .dylib → .enc）。

加密格式（每个 .enc 文件）：
  [4 bytes: magic "AXNE"]
  [4 bytes: version = 0x01000000 (big-endian)]
  [12 bytes: random IV]
  [N bytes: AES-256-GCM ciphertext (= 原始 DLL 大小)]
  [16 bytes: GCM authentication tag]

密钥嵌入策略：
  32 字节密钥 split 为 4 × 8 字节 fragment。
  每个 fragment XOR 一个随机 8 字节 mask。
  NativeBridge.java 中嵌入 4 个 masked-fragment + 4 个 mask（作为 long 常量）。
  运行时：key[i*8:(i+1)*8] = fragment[i] ^ mask[i]。
  进一步：对重组后的 32 字节做 SHA-256，取前 32 字节作为真正的 AES key
  （加一层间接，防止直接 XOR 结果就是 key）。

用法（CI 中）：
  # gen-keys 阶段：
  python encrypt-native.py --gen-key \
    --patch-java src/main/java/.../NativeBridge.java \
    --output-key protect-out/dll_key.bin

  # build-encrypted 阶段：
  python encrypt-native.py --encrypt \
    --key protect-material/dll_key.bin \
    --native-dir src/main/resources/native/
"""

import argparse
import hashlib
import os
import struct
import sys
from pathlib import Path

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    HAS_CRYPTO = True
except ImportError:
    HAS_CRYPTO = False

MAGIC = b"AXNE"
VERSION = 1
IV_LEN = 12
TAG_LEN = 16  # GCM tag appended by AESGCM
NATIVE_EXTENSIONS = {".dll", ".so", ".dylib"}

# ─── 密钥派生：raw_key_material → SHA-256 → actual AES key ───
def derive_aes_key(raw_material: bytes) -> bytes:
    """raw 32 字节 key material → SHA-256 → 真正的 AES-256 key"""
    assert len(raw_material) == 32
    return hashlib.sha256(raw_material + b"axs-native-enc-v1").digest()


def bytes_to_long(b: bytes) -> int:
    """8 bytes → Java long (big-endian signed)"""
    val = int.from_bytes(b, "big")
    if val >= (1 << 63):
        val -= (1 << 64)
    return val


def long_to_hex_literal(val: int) -> str:
    """Python int → Java long hex literal (e.g., 0x7F3A...L)"""
    if val < 0:
        # Java: negative long literal → complement
        val = val + (1 << 64)
    return f"0x{val:016X}L"


def gen_key_and_patch(java_path: Path, output_key: Path):
    """Generate DLL encryption key, embed fragments in NativeBridge.java, output key file."""
    if not java_path.exists():
        print(f"[FATAL] Java source not found: {java_path}")
        sys.exit(1)

    # Generate 32-byte raw key material
    raw_key = os.urandom(32)

    # Split into 4 × 8-byte fragments
    fragments = [raw_key[i*8:(i+1)*8] for i in range(4)]

    # Generate 4 random 8-byte masks
    masks = [os.urandom(8) for _ in range(4)]

    # Masked fragments = fragment XOR mask
    masked = [bytes(a ^ b for a, b in zip(f, m)) for f, m in zip(fragments, masks)]

    # Build Java constant declarations
    java_constants = []
    for i in range(4):
        mf_val = bytes_to_long(masked[i])
        mk_val = bytes_to_long(masks[i])
        java_constants.append(f"    private static final long _KF{i} = {long_to_hex_literal(mf_val)};")
        java_constants.append(f"    private static final long _KM{i} = {long_to_hex_literal(mk_val)};")

    java_block = "\n".join(java_constants)

    # Read Java source
    source = java_path.read_text(encoding="utf-8")

    # Replace placeholder block
    marker_start = "// ═══ V2_KEY_FRAGMENTS_START ═══"
    marker_end = "// ═══ V2_KEY_FRAGMENTS_END ═══"

    if marker_start not in source:
        print(f"[FATAL] Marker '{marker_start}' not found in {java_path}")
        print("        NativeBridge.java must contain the V2 key fragment placeholder block.")
        sys.exit(1)

    idx_start = source.index(marker_start)
    idx_end = source.index(marker_end) + len(marker_end)
    patched = source[:idx_start] + marker_start + "\n" + java_block + "\n    " + marker_end + source[idx_end:]

    java_path.write_text(patched, encoding="utf-8")
    print(f"[OK] Patched {java_path} with 4 masked key fragments")

    # Derive the actual AES key for verification
    aes_key = derive_aes_key(raw_key)

    # Write raw key material (NOT the derived AES key — both sides derive identically)
    output_key.parent.mkdir(parents=True, exist_ok=True)
    output_key.write_bytes(raw_key)
    print(f"[OK] Written raw key material to {output_key} ({len(raw_key)} bytes)")
    print(f"     AES key (SHA256 derived) = {aes_key.hex()[:16]}...")

    return raw_key


def encrypt_native_libs(key_path: Path, native_dir: Path):
    """Encrypt all native libraries in native_dir using the key from key_path."""
    if not HAS_CRYPTO:
        print("[FATAL] cryptography library required: pip install cryptography")
        sys.exit(1)

    if not key_path.exists():
        print(f"[FATAL] Key file not found: {key_path}")
        sys.exit(1)

    raw_key = key_path.read_bytes()
    if len(raw_key) != 32:
        print(f"[FATAL] Key must be 32 bytes, got {len(raw_key)}")
        sys.exit(1)

    aes_key = derive_aes_key(raw_key)
    aesgcm = AESGCM(aes_key)

    if not native_dir.exists():
        print(f"[FATAL] Native directory not found: {native_dir}")
        sys.exit(1)

    encrypted_count = 0
    for f in sorted(native_dir.iterdir()):
        if f.suffix.lower() in NATIVE_EXTENSIONS:
            plaintext = f.read_bytes()
            original_size = len(plaintext)

            iv = os.urandom(IV_LEN)
            ciphertext = aesgcm.encrypt(iv, plaintext, None)  # ct + tag (appended)

            # Build .enc file
            header = MAGIC + struct.pack(">I", VERSION) + iv
            enc_data = header + ciphertext

            enc_path = f.with_suffix(".enc")
            enc_path.write_bytes(enc_data)

            # Remove plaintext
            f.unlink()

            print(f"[OK] {f.name} ({original_size:,} bytes) → {enc_path.name} ({len(enc_data):,} bytes)")
            encrypted_count += 1

    if encrypted_count == 0:
        print("[WARN] No native libraries found to encrypt")
    else:
        print(f"[OK] Encrypted {encrypted_count} native library/libraries")


def selftest():
    """内部自测：生成密钥 → 加密 → 解密 → 验证一致性"""
    if not HAS_CRYPTO:
        print("[FATAL] cryptography library required for selftest")
        sys.exit(1)

    print("="*60)
    print("  V2 Native DLL 加密自测")
    print("="*60)

    # Generate key
    raw_key = os.urandom(32)
    aes_key = derive_aes_key(raw_key)

    # Simulate DLL content
    fake_dll = os.urandom(500_000)  # 500KB

    # Encrypt
    aesgcm = AESGCM(aes_key)
    iv = os.urandom(IV_LEN)
    ct = aesgcm.encrypt(iv, fake_dll, None)

    # Build .enc
    enc_data = MAGIC + struct.pack(">I", VERSION) + iv + ct

    # Parse .enc (simulating NativeBridge runtime)
    assert enc_data[:4] == MAGIC
    ver = struct.unpack(">I", enc_data[4:8])[0]
    assert ver == VERSION
    parsed_iv = enc_data[8:20]
    parsed_ct = enc_data[20:]

    # Reconstruct key from fragments (simulate Java)
    fragments = [raw_key[i*8:(i+1)*8] for i in range(4)]
    masks = [os.urandom(8) for _ in range(4)]
    masked = [bytes(a ^ b for a, b in zip(f, m)) for f, m in zip(fragments, masks)]
    # Reassemble
    reconstructed = b""
    for i in range(4):
        reconstructed += bytes(a ^ b for a, b in zip(masked[i], masks[i]))
    assert reconstructed == raw_key, "Key reconstruction failed!"
    rec_aes_key = derive_aes_key(reconstructed)
    assert rec_aes_key == aes_key

    # Decrypt
    aesgcm2 = AESGCM(rec_aes_key)
    decrypted = aesgcm2.decrypt(parsed_iv, parsed_ct, None)
    assert decrypted == fake_dll, "Decryption mismatch!"
    print("[PASS] 加密→解密往返一致 (500KB 模拟 DLL)")

    # Tamper detection
    tampered_ct = bytearray(parsed_ct)
    tampered_ct[100] ^= 0xFF
    try:
        aesgcm2.decrypt(parsed_iv, bytes(tampered_ct), None)
        print("[FAIL] 篡改后不应解密成功")
        sys.exit(1)
    except Exception:
        pass
    print("[PASS] 篡改检测正确（GCM tag 校验）")

    # Wrong key detection
    wrong_key = derive_aes_key(os.urandom(32))
    wrong_aesgcm = AESGCM(wrong_key)
    try:
        wrong_aesgcm.decrypt(parsed_iv, parsed_ct, None)
        print("[FAIL] 错误密钥不应解密成功")
        sys.exit(1)
    except Exception:
        pass
    print("[PASS] 错误密钥正确拒绝")

    # Multi-round
    for i in range(100):
        k = os.urandom(32)
        ak = derive_aes_key(k)
        dll = os.urandom(i * 1000 + 100)
        iv2 = os.urandom(12)
        ct2 = AESGCM(ak).encrypt(iv2, dll, None)
        dec2 = AESGCM(ak).decrypt(iv2, ct2, None)
        assert dec2 == dll
    print("[PASS] 100 组随机往返全部通过")

    print("="*60)
    print("  ALL PASS — V2 DLL 加密/解密参考模型验证通过")
    print("="*60)


def main():
    parser = argparse.ArgumentParser(description="V2 Native DLL encryption tool")
    sub = parser.add_subparsers(dest="mode")

    # gen-key mode
    gen = sub.add_parser("gen-key", help="Generate key + patch Java")
    gen.add_argument("--patch-java", required=True, type=Path, help="Path to NativeBridge.java")
    gen.add_argument("--output-key", required=True, type=Path, help="Output dll_key.bin path")

    # encrypt mode
    enc = sub.add_parser("encrypt", help="Encrypt native libraries")
    enc.add_argument("--key", required=True, type=Path, help="Path to dll_key.bin")
    enc.add_argument("--native-dir", required=True, type=Path, help="Directory containing native libs")

    # selftest mode
    sub.add_parser("selftest", help="Run internal self-test")

    args = parser.parse_args()

    if args.mode == "gen-key":
        gen_key_and_patch(args.patch_java, args.output_key)
    elif args.mode == "encrypt":
        encrypt_native_libs(args.key, args.native_dir)
    elif args.mode == "selftest":
        selftest()
    else:
        parser.print_help()
        sys.exit(1)


if __name__ == "__main__":
    main()

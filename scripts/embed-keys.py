#!/usr/bin/env python3
"""
embed-keys.py - 生成并嵌入 native 密钥材料（C++ ↔ Python 一致性的唯一权威）

背景:
  native/src/class_decrypt.cpp 的 assemble_root_seed() 用 XOR 公式从
  seed_part_a/b/c/d 重组出 root_seed:
      out[i]      = a[i] ^ c[7-i]
      out[8+i]    = b[i] ^ d[7-i]
      out[16+i]   = c[i] ^ a[7-i]
      out[24+i]   = d[i] ^ b[7-i]
  该方程组对任意 root_seed 不可逆（受 out[16+i]==out[7-i] 约束）。
  因此 root_seed 必须【由 4 个分片定义】，而不能反推。

  本脚本是 C++ 与 Python 加密两端 root_seed 的唯一权威:
    1. 随机生成 4 个 8 字节分片 (a,b,c,d)
    2. 用与 C++ 完全相同的 XOR 公式计算 root_seed
    3. 把计算出的 root_seed 写回 keys/<ver>/root_seed.bin
       (encrypt-jar.py 用它做 HKDF 派生，保证与 native 重组结果一致)
    4. 把 4 个分片 patch 进 class_decrypt.cpp
    5. 把 Ed25519 公钥 patch 进 integrity_check.cpp

用法:
    python scripts/embed-keys.py --keys-dir keys/current --native-dir native/src
"""

import argparse
import base64
import os
import re
import secrets
import sys
from pathlib import Path

try:
    from cryptography.hazmat.primitives import serialization
    HAS_CRYPTO = True
except ImportError:
    HAS_CRYPTO = False


def assemble_root_seed(a, b, c, d) -> bytes:
    """与 C++ assemble_root_seed() 逐字节等价。"""
    out = bytearray(32)
    for i in range(8):
        out[i]      = a[i] ^ c[7 - i]
        out[8 + i]  = b[i] ^ d[7 - i]
        out[16 + i] = c[i] ^ a[7 - i]
        out[24 + i] = d[i] ^ b[7 - i]
    return bytes(out)


# ─── seed 分片 at-rest 包裹（须与 native/src/class_decrypt.cpp 解包逻辑逐字节一致）───
SEED_WRAP_SALT = b"AXS-seed-wrap-salt-v1"
SEED_WRAP_INFO = b"axs-seed-unwrap-v1"
_BUILD_SALT_L1 = b"AXS-class-enc-master-salt-v1"
_BUILD_SALT_L2 = b"AXS-class-enc-session-salt-v1"


def _hkdf(salt, ikm, info, length=32):
    import hashlib
    import hmac as _hmac
    prk = _hmac.new(salt, ikm, hashlib.sha256).digest()
    okm = b""
    t = b""
    counter = 1
    while len(okm) < length:
        t = _hmac.new(prk, t + info + bytes([counter]), hashlib.sha256).digest()
        okm += t
        counter += 1
    return okm[:length]


def seed_keystream() -> bytes:
    """与 C++ compute_seed_keystream() 逐字节等价。"""
    import hashlib
    wrap_ikm = hashlib.sha256(_BUILD_SALT_L1 + b"|" + _BUILD_SALT_L2).digest()
    return _hkdf(SEED_WRAP_SALT, wrap_ikm, SEED_WRAP_INFO, 32)


def wrap_seed_parts(a, b, c, d):
    """明文分片 -> 密文分片：cipher = plaintext XOR keystream（确定性，无随机 IV）。"""
    ks = seed_keystream()
    plain = bytes(a) + bytes(b) + bytes(c) + bytes(d)
    cipher = bytes(x ^ ks[i] for i, x in enumerate(plain))
    return (list(cipher[0:8]), list(cipher[8:16]),
            list(cipher[16:24]), list(cipher[24:32]))


def generate_seed_parts():
    """生成 4 个 8 字节 seed 分片。

    优先复用固定 env AXS_SEED_PARTS_B64（32 字节的 Base64）——这样每次 CI 算出的
    canonical root_seed 完全一致，已部署的本体 native 才能解密【单独重建的模块】的
    逐类加密类（即支持“只构建变更的模块”而无需同时重建本体）。
    缺省（未配置该 Secret）则随机生成，仅适合每次都同时重建并分发本体的场景。
    """
    fixed_b64 = os.environ.get("AXS_SEED_PARTS_B64", "").strip()
    if fixed_b64:
        raw = base64.b64decode(fixed_b64)
        if len(raw) != 32:
            print(f"[!] AXS_SEED_PARTS_B64 解码后必须为 32 字节, 实际 {len(raw)}")
            sys.exit(1)
        print("[*] 复用固定 seed 分片 (env AXS_SEED_PARTS_B64) -> root_seed 跨 CI 稳定")
        return (
            list(raw[0:8]),
            list(raw[8:16]),
            list(raw[16:24]),
            list(raw[24:32]),
        )
    return (
        list(secrets.token_bytes(8)),
        list(secrets.token_bytes(8)),
        list(secrets.token_bytes(8)),
        list(secrets.token_bytes(8)),
    )


def read_ed25519_public_key(keys_dir: Path) -> bytes:
    """读取 Ed25519 公钥的原始 32 字节。"""
    pem_file = keys_dir / "ed25519_public.pem"
    bin_file = keys_dir / "ed25519_public_raw.bin"

    if pem_file.exists() and HAS_CRYPTO:
        public_key = serialization.load_pem_public_key(pem_file.read_bytes())
        return public_key.public_bytes(
            encoding=serialization.Encoding.Raw,
            format=serialization.PublicFormat.Raw,
        )
    if bin_file.exists():
        return bin_file.read_bytes()[:32]
    print(f"[!] Ed25519 公钥未找到（需要 {pem_file} 或 {bin_file}）")
    print("[!] 请确认已安装 cryptography 库: pip install cryptography")
    sys.exit(1)


def format_seed_array(data, label) -> str:
    hex_values = ", ".join(f"0x{b:02X}" for b in data)
    return f"static volatile uint8_t seed_part_{label}[] = {{ {hex_values} }};"


def replace_seed_in_source(source: str, a, b, c, d) -> str:
    for label, data in [('a', a), ('b', b), ('c', c), ('d', d)]:
        pattern = rf'static volatile uint8_t seed_part_{label}\[\]\s*=\s*\{{[^}}]+\}};'
        replacement = format_seed_array(data, label)
        source, count = re.subn(pattern, replacement, source)
        if count != 1:
            print(f"[!] seed_part_{label} 匹配数异常: {count}（期望 1）")
            sys.exit(1)
    return source


def replace_pubkey_in_source(source: str, pubkey: bytes) -> str:
    lines = []
    for row in range(4):
        offset = row * 8
        hex_values = ", ".join(f"0x{pubkey[offset + i]:02X}" for i in range(8))
        lines.append(f"    {hex_values}")
    new_array = "static const uint8_t ed25519_public_key[32] = {\n" + ",\n".join(lines) + "\n};"

    pattern = r'static const uint8_t ed25519_public_key\[32\]\s*=\s*\{[^}]+\};'
    source, count = re.subn(pattern, new_array, source)
    if count != 1:
        print(f"[!] ed25519_public_key 匹配数异常: {count}（期望 1）")
        sys.exit(1)
    return source


def resolve_keys_dir(keys_base: Path) -> Path:
    if (keys_base / "ed25519_public.pem").exists() or (keys_base / "root_seed.bin").exists():
        return keys_base
    current_file = keys_base / "current_version.txt"
    if current_file.exists():
        return keys_base / current_file.read_text().strip()
    subdirs = sorted([d for d in keys_base.iterdir() if d.is_dir()])
    if subdirs:
        return subdirs[-1]
    print(f"[!] 密钥目录为空: {keys_base}")
    sys.exit(1)


def main():
    parser = argparse.ArgumentParser(description="生成并嵌入 native 密钥材料")
    parser.add_argument("--keys-dir", required=True, help="密钥目录（generate-keys.py 的输出）")
    parser.add_argument("--native-dir", default="native/src", help="native 源码目录")
    args = parser.parse_args()

    keys_dir = resolve_keys_dir(Path(args.keys_dir))
    native_dir = Path(args.native_dir)
    print(f"[*] 密钥目录: {keys_dir}")
    print(f"[*] Native 源码目录: {native_dir}")

    decrypt_file = native_dir / "class_decrypt.cpp"
    integrity_file = native_dir / "integrity_check.cpp"
    for f in (decrypt_file, integrity_file):
        if not f.exists():
            print(f"[!] 文件不存在: {f}")
            sys.exit(1)

    # 1. 生成分片并计算 canonical root_seed
    print("\n[1/4] 生成 seed 分片并计算 canonical root_seed...")
    a, b, c, d = generate_seed_parts()
    root_seed = assemble_root_seed(a, b, c, d)
    print(f"    root_seed (canonical) = {root_seed.hex()[:16]}...")

    # 2. 写回 root_seed.bin（Python 加密端的权威值）
    print("\n[2/4] 写回 root_seed.bin（供 encrypt-jar.py 使用）...")
    seed_out = keys_dir / "root_seed.bin"
    seed_out.write_bytes(root_seed)
    print(f"    已写入: {seed_out}")
    print("    [!] 注意: 已覆盖 generate-keys.py 生成的随机 root_seed.bin，")
    print("        因为 native XOR 重组要求 root_seed 必须由分片定义。")

    # 3. patch class_decrypt.cpp
    print("\n[3/4] 嵌入 seed 分片到 class_decrypt.cpp...")
    source = decrypt_file.read_text(encoding='utf-8')
    ca, cb, cc, cd = wrap_seed_parts(a, b, c, d)
    print(f"    seed 分片已 HKDF-keystream 包裹（密文分片 a = {bytes(ca).hex()}）")
    source = replace_seed_in_source(source, ca, cb, cc, cd)
    decrypt_file.write_text(source, encoding='utf-8')
    print(f"    已更新: {decrypt_file}")

    # 4. patch integrity_check.cpp
    print("\n[4/4] 嵌入 Ed25519 公钥到 integrity_check.cpp...")
    pubkey = read_ed25519_public_key(keys_dir)
    print(f"    ed25519_public_key = {pubkey.hex()[:16]}...")
    source = integrity_file.read_text(encoding='utf-8')
    source = replace_pubkey_in_source(source, pubkey)
    integrity_file.write_text(source, encoding='utf-8')
    print(f"    已更新: {integrity_file}")

    print("\n[+] 密钥嵌入完成！C++ 与 Python 两端 root_seed 现已一致。")
    print("[!] 安全提醒:")
    print("    - 嵌入后的 class_decrypt.cpp / integrity_check.cpp 含敏感密钥材料")
    print("    - 公钥嵌入安全；seed 分片已 HKDF-keystream 包裹（抗 strings/静态提取），但对完整逆向非密码学级保密")
    print("    - 建议将含真实密钥的 .cpp 排除出公开仓库，或在 CI 构建时动态注入")


if __name__ == "__main__":
    main()

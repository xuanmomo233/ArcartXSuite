#!/usr/bin/env python3
"""
selftest-crypto.py - 加密/解密自测（Tier 1：纯 Python 往返）

验证 encrypt-jar.py 的 .enc 格式 + AEAD 加密 + 密钥派生内部自洽：
  1. 取一个真实 .class（或任意字节）
  2. 用 encrypt-jar.derive_class_key + encrypt_class_data 加密
  3. 用相同派生 key 在 Python 侧解密（逻辑镜像 native class_decrypt.cpp）
  4. 断言解密结果 == 原始字节
  5. 同时把 .enc 写盘 + 打印 className，供 Tier 2 的 JNI 测试（RoundTripTest.java）使用

用法:
  python scripts/selftest-crypto.py \
      --class-file axs-core/build/classes/java/main/xuanmo/arcartxsuite/security/NativeBridge.class \
      --class-name xuanmo.arcartxsuite.security.NativeBridge \
      [--keys-dir keys/current] [--out test.enc]

若不提供 --keys-dir，则生成临时 root_seed（仅自测，不可用于真实运行时解密）。
"""

import argparse
import hashlib
import hmac
import importlib.util
import secrets
import sys
from pathlib import Path

ALG_AES_256_GCM = 0x01
ALG_CHACHA20_POLY1305 = 0x02


def load_encrypt_jar(scripts_dir: Path):
    """动态加载 encrypt-jar.py（文件名含连字符，不能直接 import）。"""
    path = scripts_dir / "encrypt-jar.py"
    spec = importlib.util.spec_from_file_location("encrypt_jar", path)
    mod = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(mod)
    return mod


def py_decrypt(enc_data: bytes, class_name: str, root_seed: bytes, ej) -> bytes:
    """镜像 native class_decrypt.cpp::decryptClass 的解密逻辑。"""
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM, ChaCha20Poly1305

    class_name_hash = hashlib.sha256(class_name.encode("utf-8")).digest()

    alg = enc_data[0]
    iv = enc_data[1:13]
    ct_len = int.from_bytes(enc_data[13:17], "big")
    ciphertext = enc_data[17:17 + ct_len]
    tag = enc_data[17 + ct_len:17 + ct_len + 16]
    embedded_hash = enc_data[17 + ct_len + 16:17 + ct_len + 16 + 32]

    assert embedded_hash == class_name_hash, "嵌入的类名哈希不匹配"

    class_key = ej.derive_class_key(root_seed, class_name_hash)
    aad = class_name_hash[:16]

    if alg == ALG_AES_256_GCM:
        cipher = AESGCM(class_key)
    elif alg == ALG_CHACHA20_POLY1305:
        cipher = ChaCha20Poly1305(class_key)
    else:
        raise ValueError(f"未知算法: {alg}")

    return cipher.decrypt(iv, ciphertext + tag, aad)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--class-file", required=True)
    ap.add_argument("--class-name", required=True)
    ap.add_argument("--keys-dir", default=None)
    ap.add_argument("--out", default="test.enc")
    ap.add_argument("--scripts-dir", default=str(Path(__file__).resolve().parent))
    args = ap.parse_args()

    ej = load_encrypt_jar(Path(args.scripts_dir))

    class_file = Path(args.class_file)
    if not class_file.exists():
        print(f"[!] class 文件不存在: {class_file}")
        sys.exit(1)
    original = class_file.read_bytes()

    if args.keys_dir:
        seed_path = Path(args.keys_dir) / "root_seed.bin"
        if not seed_path.exists():
            print(f"[!] root_seed.bin 不存在: {seed_path}")
            sys.exit(1)
        root_seed = seed_path.read_bytes()
        print(f"[*] 已加载 root_seed: {seed_path}")
    else:
        root_seed = secrets.token_bytes(32)
        print("[!] 未提供 --keys-dir，使用临时 root_seed（仅自测）")

    class_name_hash = hashlib.sha256(args.class_name.encode("utf-8")).digest()
    class_key = ej.derive_class_key(root_seed, class_name_hash)

    # 加密（两种算法各测一次）
    all_ok = True
    for alg, label in [(ALG_AES_256_GCM, "AES-256-GCM"), (ALG_CHACHA20_POLY1305, "ChaCha20-Poly1305")]:
        enc_data, used = ej.encrypt_class_data(args.class_name, original, class_key, alg)
        decrypted = py_decrypt(enc_data, args.class_name, root_seed, ej)
        ok = decrypted == original
        all_ok = all_ok and ok
        print(f"    [{label}] enc={len(enc_data)}B  解密回原文: {'OK' if ok else 'FAIL'}")

    # 写出一个 AES-GCM 的 .enc 供 JNI 测试使用
    enc_data, _ = ej.encrypt_class_data(args.class_name, original, class_key, ALG_AES_256_GCM)
    Path(args.out).write_bytes(enc_data)
    print(f"\n[*] 已写出 .enc: {args.out}  (className={args.class_name})")
    print(f"[*] className SHA-256: {class_name_hash.hex()}")

    if all_ok:
        print("\n[+] Tier 1 自测通过：.enc 格式 + AEAD + 密钥派生内部自洽。")
        sys.exit(0)
    else:
        print("\n[!] Tier 1 自测失败。")
        sys.exit(2)


if __name__ == "__main__":
    main()

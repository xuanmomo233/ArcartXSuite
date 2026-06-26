#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
selftest-seed-wrap.py - 漏洞2 (native root_seed 明文分片) 修复的参考模型与往返验证。

证明 C++ (class_decrypt.cpp) 与 Python (embed-keys.py) 两端在不编译 DLL 的前提下逻辑一致：
  embed 端: 明文分片 a,b,c,d -> canonical root_seed (XOR 公式)；
            cipher = (a||b||c||d) XOR HKDF-keystream，存入 .cpp 的 seed_part_*。
  C++  端: 从 seed_part_*(密文) 重算 keystream 解包 -> 明文分片 -> 同一 XOR 公式 -> root_seed。
断言: C++ 还原的 root_seed == embed 计算的 canonical root_seed（即 encrypt-jar.py 用的 root_seed.bin）。
并验证: 存入二进制的 seed_part_* 已非明文（抗 strings/静态提取）。
"""
import hashlib
import hmac
import secrets

SEED_WRAP_SALT = b"AXS-seed-wrap-salt-v1"
SEED_WRAP_INFO = b"axs-seed-unwrap-v1"
BUILD_SALT_L1 = b"AXS-class-enc-master-salt-v1"
BUILD_SALT_L2 = b"AXS-class-enc-session-salt-v1"


def hkdf(salt, ikm, info, length=32):
    prk = hmac.new(salt, ikm, hashlib.sha256).digest()
    okm, t, c = b"", b"", 1
    while len(okm) < length:
        t = hmac.new(prk, t + info + bytes([c]), hashlib.sha256).digest()
        okm += t
        c += 1
    return okm[:length]


def seed_keystream():
    # 镜像 C++ compute_seed_keystream(): wrap_ikm = SHA256(L1 + "|" + L2)
    wrap_ikm = hashlib.sha256(BUILD_SALT_L1 + b"|" + BUILD_SALT_L2).digest()
    return hkdf(SEED_WRAP_SALT, wrap_ikm, SEED_WRAP_INFO, 32)


def assemble_root_seed(a, b, c, d):
    # 镜像 C++ 内层 XOR 重组公式
    out = bytearray(32)
    for i in range(8):
        out[i] = a[i] ^ c[7 - i]
        out[8 + i] = b[i] ^ d[7 - i]
        out[16 + i] = c[i] ^ a[7 - i]
        out[24 + i] = d[i] ^ b[7 - i]
    return bytes(out)


def embed_wrap(a, b, c, d):
    # 镜像 embed-keys.wrap_seed_parts()
    ks = seed_keystream()
    plain = bytes(a) + bytes(b) + bytes(c) + bytes(d)
    cipher = bytes(x ^ ks[i] for i, x in enumerate(plain))
    return cipher[0:8], cipher[8:16], cipher[16:24], cipher[24:32]


def cpp_assemble_from_stored(ca, cb, cc, cd):
    # 镜像 C++ assemble_root_seed(): 从存储的密文分片解包再 XOR 重组
    ks = seed_keystream()
    a = bytes(ca[i] ^ ks[i] for i in range(8))
    b = bytes(cb[i] ^ ks[8 + i] for i in range(8))
    c = bytes(cc[i] ^ ks[16 + i] for i in range(8))
    d = bytes(cd[i] ^ ks[24 + i] for i in range(8))
    return assemble_root_seed(a, b, c, d)


def run():
    ks = seed_keystream()
    print(f"[*] seed keystream (固定/确定性) = {ks.hex()}")

    # 固定测试向量（可与 C++ 单步比对）
    a = bytes.fromhex("1122334455667788")
    b = bytes.fromhex("99aabbccddeeff00")
    c = bytes.fromhex("0f1e2d3c4b5a6978")
    d = bytes.fromhex("a1b2c3d4e5f60718")
    canonical = assemble_root_seed(a, b, c, d)
    ca, cb, cc, cd = embed_wrap(a, b, c, d)
    stored = bytes(ca + cb + cc + cd)
    plain = a + b + c + d
    recovered = cpp_assemble_from_stored(ca, cb, cc, cd)

    print("\n[固定向量]")
    print(f"  明文分片 a||b||c||d = {plain.hex()}")
    print(f"  存入.cpp 的密文分片 = {stored.hex()}")
    print(f"  canonical root_seed = {canonical.hex()}")
    print(f"  C++ 解包还原 root  = {recovered.hex()}")
    assert recovered == canonical, "FAIL: C++ 还原 root_seed != canonical"
    assert stored != plain, "FAIL: 存储分片仍是明文（未起到 at-rest 包裹作用）"
    print("  [OK] 还原一致 且 存储分片已非明文")

    # 随机模糊测试
    trials = 5000
    for _ in range(trials):
        ra = secrets.token_bytes(8); rb = secrets.token_bytes(8)
        rc = secrets.token_bytes(8); rd = secrets.token_bytes(8)
        can = assemble_root_seed(ra, rb, rc, rd)
        wa, wb, wc, wd = embed_wrap(ra, rb, rc, rd)
        rec = cpp_assemble_from_stored(wa, wb, wc, wd)
        assert rec == can
        assert bytes(wa + wb + wc + wd) != ra + rb + rc + rd
    print(f"\n[随机模糊] {trials} 组随机分片：C++ 解包还原 == canonical，存储均非明文")
    print("\nSEED_WRAP_SELFTEST_PASS")


if __name__ == "__main__":
    run()

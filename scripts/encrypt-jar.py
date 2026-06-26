#!/usr/bin/env python3
"""
encrypt-jar.py - JAR 加密打包脚本

将原始 JAR 加密打包为受保护的 JAR：
1. 逐个加密 .class 文件（AES-256-GCM / ChaCha20-Poly1305 混合模式）
2. 注入 NativeClassLoader 和引导类
3. 嵌入 native 库到 JAR 资源
4. 生成 PROTECTION.MF 保护元数据
5. 使用 Ed25519 签名完整性哈希

用法:
    python encrypt-jar.py --input plugin.jar --output plugin-encrypted.jar
                          --native-dir native/build
                          --keys-dir keys/current
                          [--protection-level high]
                          [--bootstrap-classes NativeBridge,NativeClassLoader,PluginEntry]
"""

import argparse
import hashlib
import json
import os
import random
import secrets
import struct
import sys
import time
import zipfile
from pathlib import Path
from datetime import datetime

# 加密算法标志
ALG_AES_256_GCM = 0x01
ALG_CHACHA20_POLY1305 = 0x02

# .enc 文件格式
# [1 byte]  algorithm flag
# [12 bytes] IV / Nonce
# [4 bytes]  ciphertext length (big-endian uint32)
# [N bytes]  ciphertext
# [16 bytes] authentication tag
# [32 bytes] class name hash (SHA-256)

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM, ChaCha20Poly1305
    HAS_CRYPTO = True
except ImportError:
    HAS_CRYPTO = False

try:
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
    from cryptography.hazmat.primitives import serialization
    HAS_SIGN = True
except ImportError:
    HAS_SIGN = False


# 固定 build salt（方案 B）。必须与 native/src/class_decrypt.cpp 中的同名常量逐字节一致。
BUILD_SALT_L1 = b"AXS-class-enc-master-salt-v1"
BUILD_SALT_L2 = b"AXS-class-enc-session-salt-v1"


def derive_class_key(root_seed, class_name_hash):
    """
    与 native 层 protection_init_keys()/derive_class_key() 逐位等价（方案 B）。

    类解密 key 仅由 root_seed + 固定 build salt 派生，不混入硬件指纹/时间窗，
    保证"构建期加密一次、任意机器任意时刻解密一致"。
    机器绑定由云端 moduleKey 的指纹 HKDF 绑定层负责。

    HKDF-SHA256: root_seed → master_key → session_key → class_key
    """
    import hmac as hmac_mod

    def hkdf_extract(salt, ikm):
        return hmac_mod.new(salt, ikm, hashlib.sha256).digest()

    def hkdf_expand(prk, info, length=32):
        okm = b""
        t = b""
        i = 1
        while len(okm) < length:
            t = hmac_mod.new(prk, t + info + bytes([i]), hashlib.sha256).digest()
            okm += t
            i += 1
        return okm[:length]

    # Layer 1: master_key = HKDF(root_seed, salt=build_salt_l1)
    prk = hkdf_extract(BUILD_SALT_L1, root_seed)
    master_key = hkdf_expand(prk, b"master_key_v1", 32)

    # Layer 2: session_key = HKDF(master_key, salt=build_salt_l2)
    prk2 = hkdf_extract(BUILD_SALT_L2, master_key)
    session_key = hkdf_expand(prk2, b"session_key_v1", 32)

    # Layer 3: class_key = HKDF(session_key, salt=class_name_hash)
    prk3 = hkdf_extract(class_name_hash, session_key)
    class_key = hkdf_expand(prk3, b"class_key_v1", 32)

    return class_key


def encrypt_class_data(class_name, class_data, class_key, algorithm=None):
    """
    加密单个类的字节码
    随机选择 AES-256-GCM 或 ChaCha20-Poly1305（混合模式）
    """
    class_name_hash = hashlib.sha256(class_name.encode('utf-8')).digest()
    aad = class_name_hash[:16]  # AAD = 类名哈希前 16 字节

    if algorithm is None:
        # 混合模式：随机选择算法
        algorithm = ALG_AES_256_GCM if secrets.randbelow(2) == 0 else ALG_CHACHA20_POLY1305

    iv = secrets.token_bytes(12)

    if algorithm == ALG_AES_256_GCM:
        cipher = AESGCM(class_key)
        ct_with_tag = cipher.encrypt(iv, class_data, aad)
        ciphertext = ct_with_tag[:-16]
        tag = ct_with_tag[-16:]
    elif algorithm == ALG_CHACHA20_POLY1305:
        cipher = ChaCha20Poly1305(class_key)
        ct_with_tag = cipher.encrypt(iv, class_data, aad)
        ciphertext = ct_with_tag[:-16]
        tag = ct_with_tag[-16:]
    else:
        raise ValueError(f"未知算法: {algorithm}")

    # 构建 .enc 文件
    enc_data = struct.pack('B', algorithm)
    enc_data += iv
    enc_data += struct.pack('>I', len(ciphertext))
    enc_data += ciphertext
    enc_data += tag
    enc_data += class_name_hash

    return enc_data, algorithm


def generate_fake_classes(count, enc_format_func):
    """
    生成虚假加密类（噪声注入）
    这些类不存在但使用真实加密格式，增加逆向工作量
    """
    fake_classes = {}
    fake_names = [
        "com/plugin/core/PermissionManager",
        "com/plugin/core/LicenseValidator",
        "com/plugin/core/ConfigLoader",
        "com/plugin/api/NetworkHandler",
        "com/plugin/api/DatabaseConnector",
        "com/plugin/util/CryptoUtils",
        "com/plugin/util/Logger",
        "com/plugin/hook/EventDispatcher",
        "com/plugin/hook/PacketInterceptor",
        "com/plugin/security/AntiTamper",
    ]

    for i in range(count):
        name = random.choice(fake_names) + f"_{i}"
        fake_data = secrets.token_bytes(random.randint(200, 2000))
        fake_key = secrets.token_bytes(32)
        enc_data, alg = encrypt_class_data(name, fake_data, fake_key)
        fake_classes[name.replace('/', '.')] = enc_data

    return fake_classes


def build_hash_tree(encrypted_files):
    """
    构建 Merkle 哈希树
    返回 root hash
    """
    if not encrypted_files:
        return hashlib.sha256(b"").digest()

    # 计算每个文件的哈希
    leaf_hashes = []
    for name, data in sorted(encrypted_files.items()):
        h = hashlib.sha256(data).digest()
        leaf_hashes.append(h)

    # 构建 Merkle 树
    while len(leaf_hashes) > 1:
        next_level = []
        for i in range(0, len(leaf_hashes), 2):
            if i + 1 < len(leaf_hashes):
                combined = leaf_hashes[i] + leaf_hashes[i + 1]
            else:
                combined = leaf_hashes[i] + leaf_hashes[i]
            next_level.append(hashlib.sha256(combined).digest())
        leaf_hashes = next_level

    return leaf_hashes[0]


def sign_hash(root_hash, private_key_path):
    """使用 Ed25519 签名 root hash"""
    if not HAS_SIGN:
        # 回退：返回占位签名
        return secrets.token_bytes(64)

    key_data = Path(private_key_path).read_bytes()
    private_key = serialization.load_pem_private_key(key_data, password=None)
    signature = private_key.sign(root_hash)
    return signature


def create_protection_mf(encrypted_count, algorithms_used, native_platforms, signature, root_hash):
    """创建 PROTECTION.MF 保护元数据。
    root_hash 必须由调用方按【最终 JAR 条目名排序】计算，确保与 Java/native 端
    computeMerkleRoot 逐位一致。
    """
    content = f"""Protection-Version: 1.0
Encryption-Algorithms: {', '.join(algorithms_used)}
Key-Derivation: HKDF-SHA256
Native-Required: true
Native-Platforms: {', '.join(native_platforms)}
Integrity-Hash: {root_hash.hex()}
Signature: {signature.hex()}
Encrypted-Class-Count: {encrypted_count}
Created-At: {datetime.now().isoformat()}
"""
    return content


def is_bootstrap_class(class_name, bootstrap_classes):
    """检查是否为引导类（不加密）。
    class_name 是 JAR 条目名，如 'xuanmo/arcartxsuite/security/NativeBridge.class'。
    bootstrap_classes 可以是全限定名（推荐，精确）或简单类名。
    """
    fqn = class_name
    if fqn.endswith('.class'):
        fqn = fqn[:-len('.class')]
    fqn = fqn.replace('/', '.')
    simple = fqn.rsplit('.', 1)[-1]
    for bc in bootstrap_classes:
        bc = bc.strip()
        if not bc:
            continue
        if fqn == bc or simple == bc:
            return True
        # 内部类（Main$1 / Main$Inner）随其外部引导类保持明文，否则会与外部类
        # 跨 ClassLoader 定义（外部=PluginClassLoader，内部=ProtectedClassLoader）触发 nestmate 错误。
        if fqn.startswith(bc + '$') or simple.startswith(bc + '$'):
            return True
    return False


def should_encrypt(fqn, encrypt_prefixes, keep_prefixes, encrypt_mode="prefix"):
    """是否加密该类。
    - encrypt_mode=="default-package": 仅加密"默认包"（无 '.' 的 FQN）的类。用于模块：
      ProGuard `-repackageclasses ''` 把模块自身代码扁平化到默认包（如 a、b、c），
      而 shaded 第三方库（com.zaxxer.* 等）与被 -keep 的入口类仍保留点分包名，
      据此精确区分"模块自身代码"与"库/入口"，无需逐库枚举 keep 前缀；
    - encrypt_mode=="prefix"（默认，用于 core）: encrypt_prefixes 非空时仅加密以其开头的类；
    - 两种模式下，命中 keep_prefixes 的类一律保持明文。
    """
    if any(fqn.startswith(p) for p in keep_prefixes):
        return False
    if encrypt_mode == "default-package":
        return "." not in fqn
    if encrypt_prefixes and not any(fqn.startswith(p) for p in encrypt_prefixes):
        return False
    return True


def real_enc_entry(class_entry_name):
    """原始 .class 条目名 → 最终加密条目名 ENCRYPTED/<path>.enc。"""
    assert class_entry_name.endswith('.class'), class_entry_name
    return "ENCRYPTED/" + class_entry_name[:-len('.class')] + ".enc"


def main():
    parser = argparse.ArgumentParser(description="JAR 加密打包工具")
    parser.add_argument("--input", required=True, help="输入 JAR 文件路径")
    parser.add_argument("--output", required=True, help="输出加密 JAR 文件路径")
    parser.add_argument("--native-dir", required=True, help="native 库目录")
    parser.add_argument("--keys-dir", default=None, help="密钥目录（包含 root_seed.bin）")
    parser.add_argument("--protection-level", default="high",
                       choices=["standard", "high", "extreme"],
                       help="保护级别")
    parser.add_argument("--bootstrap-classes", default="NativeBridge,NativeClassLoader,PluginEntry",
                       help="不加密的引导类（逗号分隔）")
    parser.add_argument("--fake-classes-count", type=int, default=0,
                       help="虚假加密类数量（噪声注入）")
    parser.add_argument("--no-native", action="store_true", help="不嵌入 native 库")
    parser.add_argument("--encrypt-prefixes", default="xuanmo.arcartxsuite.",
                       help="仅加密 FQN 以这些前缀开头的类（逗号分隔）。默认仅加密插件自身代码，"
                            "shaded 第三方库保持明文（由 ProtectedClassLoader 明文分支加载）。传空串表示加密全部。")
    parser.add_argument("--keep-prefixes",
                       default="xuanmo.arcartxsuite.api.,xuanmo.arcartxsuite.auth.",
                       help="即使匹配 encrypt-prefixes 也保持明文的前缀（逗号分隔）。"
                            "默认排除 api（模块编译/运行依赖）与 auth（MixedYggdrasilProxy 以独立进程启动，无法解密）。")
    parser.add_argument("--encrypt-mode", default="prefix", choices=["prefix", "default-package"],
                       help="加密选择策略。prefix（默认，core 用）按 encrypt-prefixes 匹配；"
                            "default-package（模块用）仅加密默认包（无点分包名）的类，"
                            "即 ProGuard -repackageclasses '' 扁平化后的模块自身代码。")
    args = parser.parse_args()

    if not HAS_CRYPTO:
        print("[!] cryptography 库未安装")
        print("[!] 请安装: pip install cryptography")
        sys.exit(1)

    input_jar = Path(args.input)
    output_jar = Path(args.output)
    native_dir = Path(args.native_dir)
    bootstrap_classes = args.bootstrap_classes.split(",")
    encrypt_prefixes = [p.strip() for p in args.encrypt_prefixes.split(",") if p.strip()]
    keep_prefixes = [p.strip() for p in args.keep_prefixes.split(",") if p.strip()]

    # 根据保护级别设置参数
    if args.protection_level == "extreme":
        args.fake_classes_count = max(args.fake_classes_count, 30)
        mixed_mode = True
    elif args.protection_level == "high":
        args.fake_classes_count = max(args.fake_classes_count, 10)
        mixed_mode = True
    else:
        mixed_mode = False

    print(f"[*] 保护级别: {args.protection_level}")
    print(f"[*] 输入: {input_jar}")
    print(f"[*] 输出: {output_jar}")

    # 加载 root seed
    root_seed = None
    if args.keys_dir:
        seed_path = Path(args.keys_dir) / "root_seed.bin"
        if seed_path.exists():
            root_seed = seed_path.read_bytes()
            print(f"[*] 已加载 root seed: {seed_path}")

    if root_seed is None:
        root_seed = secrets.token_bytes(32)
        print("[!] 未找到 root_seed.bin，使用随机种子（仅用于测试，运行时无法解密）")

    # 读取输入 JAR
    print(f"\n[1/6] 读取输入 JAR...")
    with zipfile.ZipFile(input_jar, 'r') as zin:
        all_files = {name: zin.read(name) for name in zin.namelist()}

    class_files = {k: v for k, v in all_files.items() if k.endswith('.class')}
    resource_files = {k: v for k, v in all_files.items() if not k.endswith('.class')}

    print(f"    类文件: {len(class_files)}")
    print(f"    资源文件: {len(resource_files)}")

    # 分类：引导类 vs 加密类
    bootstrap = {}
    to_encrypt = {}
    for name, data in class_files.items():
        fqn = name[:-len('.class')].replace('/', '.') if name.endswith('.class') else name.replace('/', '.')
        if is_bootstrap_class(name, bootstrap_classes) or not should_encrypt(fqn, encrypt_prefixes, keep_prefixes, args.encrypt_mode):
            bootstrap[name] = data
        else:
            to_encrypt[name] = data

    print(f"    加密前缀: {encrypt_prefixes or '(全部)'}  保留明文前缀: {keep_prefixes}")
    print(f"    明文类（引导/库/api/auth，不加密）: {len(bootstrap)}")
    print(f"    待加密类: {len(to_encrypt)}")

    # 加密类文件
    print(f"\n[2/6] 加密类文件...")
    encrypted_files = {}
    algorithms_used = set()

    for name, data in to_encrypt.items():
        class_name = name.replace('/', '.').replace('.class', '')
        class_name_hash = hashlib.sha256(class_name.encode('utf-8')).digest()

        # 派生类密钥（方案 B：仅 root_seed + 固定 build salt）
        class_key = derive_class_key(root_seed, class_name_hash)

        # 加密
        if mixed_mode:
            enc_data, alg = encrypt_class_data(class_name, data, class_key)
        else:
            enc_data, alg = encrypt_class_data(class_name, data, class_key, ALG_AES_256_GCM)

        encrypted_files[name] = enc_data
        algorithms_used.add("AES-256-GCM" if alg == ALG_AES_256_GCM else "ChaCha20-Poly1305")

    print(f"    已加密: {len(encrypted_files)} 个类")
    print(f"    使用算法: {', '.join(algorithms_used)}")

    # 生成虚假加密类（键 = 最终 ENCRYPTED/ 条目名）
    fake_entries = {}
    if args.fake_classes_count > 0:
        print(f"\n[3/6] 生成 {args.fake_classes_count} 个虚假加密类...")
        fake_classes = generate_fake_classes(args.fake_classes_count, None)
        for name, enc_data in fake_classes.items():
            enc_path = name.replace('.', '/') + ".enc"
            fake_entries["ENCRYPTED/" + enc_path] = enc_data
        print(f"    虚假类已注入: {len(fake_entries)}")
    else:
        print(f"\n[3/6] 跳过虚假类生成")

    # 准备 native 库
    # 注意：CI 流程中 native 库（axs-native.dll / libaxs-native.so）已由 gradle 作为
    # src/main/resources/native/ 资源打包进输入 JAR，且 NativeBridge 从 /native/ 加载，
    # 因此通常使用 --no-native，仅保留输入 JAR 中已有的 native/ 资源。
    print(f"\n[4/6] 准备 native 库...")
    native_files = {}
    native_platforms = []

    if not args.no_native:
        win_dll = native_dir / "windows" / "protection-x86_64.dll"
        if not win_dll.exists():
            win_dll = native_dir / "protection.dll"
        if win_dll.exists():
            native_files["native/windows/protection-x86_64.dll"] = win_dll.read_bytes()
            native_platforms.append("windows-x86_64")
            print(f"    Windows: {win_dll}")
        else:
            print(f"    [!] Windows native 库未找到")

        linux_so = native_dir / "linux" / "libprotection-x86_64.so"
        if not linux_so.exists():
            linux_so = native_dir / "libprotection.so"
        if linux_so.exists():
            native_files["native/linux/libprotection-x86_64.so"] = linux_so.read_bytes()
            native_platforms.append("linux-x86_64")
            print(f"    Linux: {linux_so}")
        else:
            print(f"    [!] Linux native 库未找到")
    else:
        print(f"    跳过 native 库嵌入（沿用输入 JAR 中已有的 native/ 资源）")

    # 从输入 JAR 的 native/ 资源探测平台（仅用于 PROTECTION.MF 元数据展示）
    if any(k.startswith("native/") and k.endswith(".dll") for k in resource_files):
        if "windows-x86_64" not in native_platforms:
            native_platforms.append("windows-x86_64")
    if any(k.startswith("native/") and k.endswith(".so") for k in resource_files):
        if "linux-x86_64" not in native_platforms:
            native_platforms.append("linux-x86_64")

    # 构建完整性哈希树并签名
    # 关键：叶子集合与排序必须与 Java/native 端 computeMerkleRoot 完全一致——
    #   即【所有 ENCRYPTED/*.enc 条目（真实 + 虚假），按最终 JAR 条目名字典序】，
    #   叶子哈希 = SHA256(enc_data)。否则 root 不一致 → 完整性校验必失败。
    print(f"\n[5/6] 构建完整性哈希树并签名...")
    final_enc_entries = {}
    for name, enc_data in encrypted_files.items():   # name = xuanmo/.../Foo.class
        final_enc_entries[real_enc_entry(name)] = enc_data
    final_enc_entries.update(fake_entries)

    root_hash = build_hash_tree(final_enc_entries)

    signature = b'\x00' * 64
    if args.keys_dir:
        priv_key_path = Path(args.keys_dir) / "ed25519_private.pem"
        if priv_key_path.exists():
            signature = sign_hash(root_hash, priv_key_path)
            print(f"    已使用 Ed25519 签名 (root={root_hash.hex()[:16]}...)")
        else:
            print(f"    [!] Ed25519 私钥未找到，使用占位签名")

    protection_mf = create_protection_mf(
        len(final_enc_entries), list(algorithms_used), native_platforms, signature, root_hash
    )

    # 保留原始 MANIFEST.MF 字节，不做任何修改
    # Protection-Enabled 标记已移至 PROTECTION.MF，无需写入 MANIFEST.MF
    manifest_bytes = all_files.get("META-INF/MANIFEST.MF", b"Manifest-Version: 1.0\r\n\r\n")
    print(f"    MANIFEST.MF 原始字节: {repr(manifest_bytes[:120])}")

    def _is_sig_file(n):
        u = n.upper()
        return u.startswith("META-INF/") and (
            u.endswith(".SF") or u.endswith(".RSA") or u.endswith(".DSA") or u.endswith(".EC"))

    # 写入输出 JAR
    print(f"\n[6/6] 写入加密 JAR...")
    output_jar.parent.mkdir(parents=True, exist_ok=True)

    with zipfile.ZipFile(output_jar, 'w', zipfile.ZIP_DEFLATED) as zout:
        # MANIFEST.MF 使用 STORED（不压缩），匹配标准 JAR 惯例
        mf_info = zipfile.ZipInfo("META-INF/MANIFEST.MF")
        mf_info.compress_type = zipfile.ZIP_STORED
        zout.writestr(mf_info, manifest_bytes)
        zout.writestr("META-INF/PROTECTION.MF", protection_mf)

        # 额外嵌入的 native 库（--no-native 时为空；正常 CI 走资源保留分支）
        for name, data in native_files.items():
            zout.writestr(name, data)

        # 引导类（不加密）：写在【原始路径】，供 Bukkit PluginClassLoader 正常加载主类等
        for name, data in bootstrap.items():
            zout.writestr(name, data)

        # 加密类（真实）：ENCRYPTED/<path>.enc
        for name, enc_data in encrypted_files.items():
            zout.writestr(real_enc_entry(name), enc_data)

        # 虚假加密类
        for entry, enc_data in fake_entries.items():
            zout.writestr(entry, enc_data)

        # 资源文件（不加密）：保留除 MANIFEST/PROTECTION/JAR 签名文件外的所有原始资源
        # （含 plugin.yml、native/ 库、语言文件等），确保插件运行所需资源完整
        for name, data in resource_files.items():
            if name in ("META-INF/MANIFEST.MF", "META-INF/PROTECTION.MF"):
                continue
            if _is_sig_file(name):
                continue
            zout.writestr(name, data)

    jar_size = output_jar.stat().st_size
    print(f"\n[+] 加密 JAR 生成完成！")
    print(f"    输出: {output_jar}")
    print(f"    大小: {jar_size / 1024:.1f} KB")
    print(f"    加密类: {len(encrypted_files)}")
    print(f"    引导类: {len(bootstrap)}")
    print(f"    Native 平台: {', '.join(native_platforms) if native_platforms else '无'}")
    print(f"    算法: {', '.join(algorithms_used)}")
    print(f"    虚假类: {args.fake_classes_count}")
    print(f"\n[*] 下一步: 运行 verify-protection.py 验证保护完整性")


if __name__ == "__main__":
    main()

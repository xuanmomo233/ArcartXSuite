#!/usr/bin/env python3
"""
generate-keys.py - 密钥生成脚本

生成 Ed25519 签名密钥对 + AES 主密钥种子 + per-class 密钥派生参数。
所有密钥材料输出到指定目录，用于后续 native 编译和 JAR 加密。

用法:
    python generate-keys.py --output keys/ [--version v1]
"""

import argparse
import hashlib
import hmac
import json
import os
import secrets
import struct
import sys
from pathlib import Path
from datetime import datetime

try:
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
    from cryptography.hazmat.primitives import serialization
    HAS_CRYPTO = True
except ImportError:
    HAS_CRYPTO = False


def generate_ed25519_keypair():
    """生成 Ed25519 签名密钥对"""
    if HAS_CRYPTO:
        private_key = Ed25519PrivateKey.generate()
        public_key = private_key.public_key()

        private_pem = private_key.private_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PrivateFormat.PKCS8,
            encryption_algorithm=serialization.NoEncryption()
        )
        public_pem = public_key.public_bytes(
            encoding=serialization.Encoding.PEM,
            format=serialization.PublicFormat.SubjectPublicKeyInfo
        )
        return private_pem, public_pem
    else:
        # 回退：使用 secrets 生成随机种子（不生成真正的 Ed25519 密钥）
        print("[!] cryptography 库未安装，使用随机种子替代 Ed25519 密钥")
        print("[!] 请安装: pip install cryptography")
        private_seed = secrets.token_bytes(32)
        public_seed = hashlib.sha256(private_seed).digest()
        return private_seed, public_seed


def generate_root_seed():
    """生成 256-bit root seed（用于 native 层密钥派生）"""
    return secrets.token_bytes(32)


def generate_class_key_params(class_count=100):
    """
    为每个类生成密钥派生参数
    每个类获得一个独立的 salt，用于 HKDF 派生
    """
    params = []
    for i in range(class_count):
        salt = secrets.token_bytes(32)
        info = secrets.token_bytes(32)
        params.append({
            "index": i,
            "salt": salt.hex(),
            "info": info.hex()
        })
    return params


def split_seed_for_native(seed):
    """
    将 root seed 分割为 4 部分，用于 native 层分散存储
    返回 C/C++ 数组初始化代码
    """
    parts = [seed[i:i+8] for i in range(0, 32, 8)]
    code_lines = []
    labels = ['a', 'b', 'c', 'd']
    for i, (part, label) in enumerate(zip(parts, labels)):
        hex_values = ", ".join(f"0x{b:02X}" for b in part)
        code_lines.append(
            f"static volatile uint8_t seed_part_{label}[] = {{ {hex_values} }};"
        )
    return "\n".join(code_lines)


def generate_hardware_fingerprint_template():
    """生成硬件指纹采集模板配置"""
    return {
        "windows": {
            "sources": [
                "GetVolumeInformation (volume serial)",
                "GetAdaptersInfo (MAC address)",
                "__cpuid (CPU ID)",
                "GetDiskFreeSpaceEx (disk identifier)"
            ],
            "hash_algorithm": "SHA-256",
            "output_length": 32
        },
        "linux": {
            "sources": [
                "/proc/cpuinfo (CPU serial)",
                "/sys/class/net/eth0/address (MAC)",
                "/etc/machine-id (machine ID)",
                "stat / (root filesystem inode)"
            ],
            "hash_algorithm": "SHA-256",
            "output_length": 32
        }
    }


def main():
    parser = argparse.ArgumentParser(description="生成加密密钥和派生参数")
    parser.add_argument("--output", required=True, help="输出目录路径")
    parser.add_argument("--version", default=None, help="密钥版本号（默认自动生成）")
    parser.add_argument("--class-count", type=int, default=100, help="预生成类密钥参数数量")
    args = parser.parse_args()

    output_dir = Path(args.output)
    version = args.version or f"v{datetime.now().strftime('%Y%m%d')}"

    version_dir = output_dir / version
    version_dir.mkdir(parents=True, exist_ok=True)

    print(f"[*] 生成密钥到: {version_dir}")
    print(f"[*] 版本: {version}")

    # 1. 生成 Ed25519 密钥对
    print("[1/5] 生成 Ed25519 签名密钥对...")
    private_key, public_key = generate_ed25519_keypair()

    # 注意：crypto 分支返回的是 PEM 字节（也是 bytes），不能用 isinstance(bytes) 区分，
    # 必须用 HAS_CRYPTO 判定，否则会永远走回退命名、永远不产出 .pem。
    if HAS_CRYPTO:
        (version_dir / "ed25519_private.pem").write_bytes(private_key)
        (version_dir / "ed25519_public.pem").write_bytes(public_key)
    else:
        # 回退模式（无 cryptography）：写原始字节，文件名与 embed-keys 期望的 *_raw.bin 对齐
        (version_dir / "ed25519_private_raw.bin").write_bytes(private_key)
        (version_dir / "ed25519_public_raw.bin").write_bytes(public_key)

    # 2. 生成 root seed
    print("[2/5] 生成 root seed...")
    root_seed = generate_root_seed()
    (version_dir / "root_seed.bin").write_bytes(root_seed)

    # 生成 native 层分散存储代码
    native_code = split_seed_for_native(root_seed)
    (version_dir / "root_seed_native.txt").write_text(
        f"// 将以下代码粘贴到 native-loader.cpp 中\n"
        f"// 替换现有的 seed_part_a/b/c/d 数组\n\n"
        f"{native_code}\n"
    )

    # 3. 生成 per-class 密钥派生参数
    print(f"[3/5] 生成 {args.class_count} 个类的密钥派生参数...")
    class_params = generate_class_key_params(args.class_count)
    (version_dir / "class_key_params.json").write_text(
        json.dumps(class_params, indent=2)
    )

    # 4. 生成硬件指纹模板
    print("[4/5] 生成硬件指纹采集配置...")
    hw_config = generate_hardware_fingerprint_template()
    (version_dir / "hardware_fingerprint.json").write_text(
        json.dumps(hw_config, indent=2)
    )

    # 5. 生成密钥版本信息
    print("[5/5] 生成密钥版本信息...")
    version_info = {
        "version": version,
        "created_at": datetime.now().isoformat(),
        "algorithm": "Ed25519 + HKDF-SHA256",
        "root_seed_length": 32,
        "class_count": args.class_count,
        "notes": [
            "root_seed.bin: 256-bit 主密钥种子，用于 native 层 HKDF 派生",
            "ed25519_private.pem: Ed25519 签名私钥，用于签名 JAR 完整性哈希",
            "ed25519_public.pem: Ed25519 签名公钥，嵌入 native 库用于验证",
            "root_seed_native.txt: root seed 的 C/C++ 分散存储代码",
            "class_key_params.json: 每个类的 HKDF salt 和 info 参数",
            "hardware_fingerprint.json: 硬件指纹采集源配置"
        ],
        "security_warnings": [
            "私钥和 root seed 必须妥善保管，切勿提交到版本控制",
            "建议使用 HSM 或密钥管理服务存储私钥",
            "密钥轮换周期建议: 90 天",
            "旧版本密钥在宽限期后应安全销毁"
        ]
    }
    (version_dir / "key_version.json").write_text(
        json.dumps(version_info, indent=2, ensure_ascii=False)
    )

    # 更新 current 软链接/标记文件
    current_marker = output_dir / "current_version.txt"
    current_marker.write_text(version)

    print(f"\n[+] 密钥生成完成！")
    print(f"    输出目录: {version_dir}")
    print(f"    当前版本: {version}")
    print(f"\n[!] 安全警告:")
    print(f"    - 妥善保管 {version_dir}/root_seed.bin 和 ed25519_private.pem")
    print(f"    - 切勿将密钥文件提交到 Git")
    print(f"    - 建议将 keys/ 目录加入 .gitignore")


if __name__ == "__main__":
    main()

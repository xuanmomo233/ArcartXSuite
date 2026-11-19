#!/usr/bin/env python3
"""
verify-protection.py - 保护验证脚本

验证加密 JAR 的保护完整性：
1. 加密覆盖率（类文件加密比例）
2. Native 库可用性（平台覆盖）
3. 完整性签名验证
4. 混淆强度评分

用法:
    python verify-protection.py --input plugin-encrypted.jar
                                [--keys-dir keys/current]
                                [--verbose]
"""

import argparse
import hashlib
import json
import struct
import sys
import zipfile
from pathlib import Path


def runtime_target_native(jar):
    layouts = {
        "universal": ("windows,linux", "any", {"native/axs-native.enc", "native/libaxs-native.enc"}),
        "windows-x86_64": ("windows", "x86_64", {"native/axs-native.enc"}),
        "linux-x86_64": ("linux", "x86_64", {"native/libaxs-native.enc"}),
    }
    if "META-INF/axs-runtime.properties" not in jar.namelist():
        return "universal", layouts["universal"][2]
    properties = {}
    for raw in jar.read("META-INF/axs-runtime.properties").decode("utf-8").splitlines():
        if not raw.strip() or raw.lstrip().startswith("#"):
            continue
        key, value = raw.split("=", 1)
        if key in properties:
            raise ValueError("duplicate runtime property")
        properties[key] = value
    target = properties.get("target")
    if target not in layouts:
        raise ValueError("unknown runtime target")
    os_name, arch, native = layouts[target]
    if properties.get("format") != "1" or properties.get("os") != os_name or properties.get("arch") != arch:
        raise ValueError("invalid runtime target metadata")
    return target, native

ALG_AES_256_GCM = 0x01
ALG_CHACHA20_POLY1305 = 0x02

try:
    from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PublicKey
    from cryptography.hazmat.primitives import serialization
    HAS_VERIFY = True
except ImportError:
    HAS_VERIFY = False


def verify_enc_format(data, name):
    """验证 .enc 文件格式合法性"""
    if len(data) < 1 + 12 + 4 + 16 + 32:
        return False, f"文件过短: {len(data)} bytes"

    alg = data[0]
    if alg not in (ALG_AES_256_GCM, ALG_CHACHA20_POLY1305):
        return False, f"未知算法标志: 0x{alg:02X}"

    ct_len = struct.unpack('>I', data[13:17])[0]
    expected_len = 1 + 12 + 4 + ct_len + 16 + 32
    if len(data) != expected_len:
        return False, f"长度不匹配: 实际={len(data)}, 预期={expected_len}"

    return True, "OK"


def build_hash_tree(encrypted_files):
    """构建 Merkle 哈希树"""
    if not encrypted_files:
        return hashlib.sha256(b"").digest()

    leaf_hashes = []
    for name in sorted(encrypted_files.keys()):
        h = hashlib.sha256(encrypted_files[name]).digest()
        leaf_hashes.append(h)

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


def verify_signature(root_hash, signature_hex, public_key_path):
    """验证 Ed25519 签名"""
    if not HAS_VERIFY:
        return None, "cryptography 库未安装"

    if not public_key_path or not Path(public_key_path).exists():
        return None, "公钥文件不存在"

    try:
        key_data = Path(public_key_path).read_bytes()
        public_key = serialization.load_pem_public_key(key_data)
        signature = bytes.fromhex(signature_hex)
        public_key.verify(signature, root_hash)
        return True, "签名验证通过"
    except Exception as e:
        return False, f"签名验证失败: {e}"


def score_protection(results):
    """计算混淆强度评分（0-100）"""
    score = 0
    max_score = 100

    # 1. 加密覆盖率 (0-15)
    enc_ratio = results.get("encryption_ratio", 0)
    score += int(enc_ratio * 15)

    # 2. 混合算法使用 (0-10)
    algos = results.get("algorithms_used", set())
    if len(algos) >= 2:
        score += 10
    elif len(algos) == 1:
        score += 5

    # 3. Native 库可用性 (0-15)
    native_count = results.get("native_platform_count", 0)
    score += min(native_count * 8, 15)

    # 4. 虚假类注入 (0-10)
    fake_ratio = results.get("fake_class_ratio", 0)
    score += min(int(fake_ratio * 40), 10)

    # 5. 完整性签名 (0-15)
    if results.get("signature_valid") is True:
        score += 15
    elif results.get("has_signature"):
        score += 5

    # 6. PROTECTION.MF 存在 (0-5)
    if results.get("has_protection_mf"):
        score += 5

    # 7. 引导类最小化 (0-10)
    bootstrap = results.get("bootstrap_count", 0)
    total = results.get("total_classes", 1)
    if total > 0 and bootstrap / max(total, 1) < 0.1:
        score += 10
    elif total > 0 and bootstrap / max(total, 1) < 0.2:
        score += 5

    # 8. 文件格式合规 (0-10)
    format_ok = results.get("format_valid_ratio", 0)
    score += int(format_ok * 10)

    # 9. JAR 结构正确 (0-10)
    if results.get("structure_valid"):
        score += 10

    return min(score, max_score)


def main():
    from host_seal import HOST_MANIFEST, HOST_SIGNATURE, verify_host_seal

    parser = argparse.ArgumentParser(description="验证 JAR 加密保护完整性")
    parser.add_argument("--input", required=True, help="加密 JAR 文件路径")
    parser.add_argument("--keys-dir", default=None, help="密钥目录（包含公钥）")
    parser.add_argument("--verbose", action="store_true", help="详细输出")
    parser.add_argument("--strict", action="store_true", help="按发行版必需的校验项决定退出状态")
    args = parser.parse_args()
    if args.strict and not args.keys_dir:
        parser.error("--strict 需要 --keys-dir")

    input_jar = Path(args.input)
    if not input_jar.exists():
        print(f"[!] 文件不存在: {input_jar}")
        sys.exit(1)

    print(f"[*] 验证: {input_jar}")
    print(f"    大小: {input_jar.stat().st_size / 1024:.1f} KB")

    results = {}

    with zipfile.ZipFile(input_jar, 'r') as zin:
        all_names = zin.namelist()

        # 分类文件
        encrypted = {n: zin.read(n) for n in all_names if n.startswith("ENCRYPTED/") and n.endswith(".enc")}
        bootstrap = {n: zin.read(n) for n in all_names if n.startswith("BOOTSTRAP/") and n.endswith(".class")}
        native = [n for n in all_names if n.startswith("native/")]
        has_pmf = "META-INF/PROTECTION.MF" in all_names

        results["encrypted_count"] = len(encrypted)
        results["bootstrap_count"] = len(bootstrap)
        results["total_classes"] = len(encrypted) + len(bootstrap)
        try:
            target, release_native = runtime_target_native(zin)
            results["runtime_target_valid"] = True
        except (ValueError, UnicodeError) as error:
            print(f"    [!] 平台清单无效: {error}")
            target, release_native = "invalid", set()
            results["runtime_target_valid"] = False
        results["required_native_platform_count"] = len(release_native)
        native_platforms = {
            platform for platform, paths in (
                ("windows", {"native/axs-native.dll", "native/axs-native.enc"}),
                ("linux", {"native/libaxs-native.so", "native/libaxs-native.enc"}),
            )
            if paths.intersection(all_names)
        }
        results["native_platform_count"] = len(native_platforms)
        results["release_native"] = results["runtime_target_valid"] and all(
            name in all_names and zin.getinfo(name).file_size > 16 for name in release_native
        ) and not ({"native/axs-native.enc", "native/libaxs-native.enc"} - release_native).intersection(all_names)
        results["has_protection_mf"] = has_pmf
        results["encryption_ratio"] = len(encrypted) / max(len(encrypted) + len(bootstrap), 1)

        print(f"\n[1/5] 文件统计:")
        print(f"    加密类: {len(encrypted)}")
        print(f"    引导类: {len(bootstrap)}")
        print(f"    Native 平台: {results['native_platform_count']}")
        print(f"    发行目标: {target}")
        print(f"    加密覆盖率: {results['encryption_ratio']*100:.1f}%")

        # 验证 .enc 格式
        print(f"\n[2/5] 格式验证:")
        valid_count = 0
        algorithms_used = set()
        for name, data in encrypted.items():
            ok, msg = verify_enc_format(data, name)
            if ok:
                valid_count += 1
                algorithms_used.add("AES-256-GCM" if data[0] == ALG_AES_256_GCM else "ChaCha20-Poly1305")
            elif args.verbose:
                print(f"    [!] {name}: {msg}")

        results["format_valid_ratio"] = valid_count / max(len(encrypted), 1)
        results["algorithms_used"] = algorithms_used
        print(f"    格式合规: {valid_count}/{len(encrypted)}")
        print(f"    算法: {', '.join(algorithms_used) if algorithms_used else '无'}")

        # 完整性校验
        print(f"\n[3/5] 完整性校验:")
        results["has_signature"] = False
        results["signature_valid"] = None
        results["hash_match"] = False
        if has_pmf:
            pmf_content = zin.read("META-INF/PROTECTION.MF").decode('utf-8')
            pmf_lines = {}
            for line in pmf_content.strip().split('\n'):
                if ':' in line:
                    k, v = line.split(':', 1)
                    pmf_lines[k.strip()] = v.strip()

            stored_hash = pmf_lines.get("Integrity-Hash", "")
            stored_sig = pmf_lines.get("Signature", "")
            results["has_signature"] = bool(stored_sig)

            # 计算 Merkle 根并比对
            computed_root = build_hash_tree(encrypted)
            hash_match = computed_root.hex() == stored_hash
            results["hash_match"] = hash_match
            print(f"    Merkle 根匹配: {'YES' if hash_match else 'NO'}")

            if stored_sig and args.keys_dir:
                pub_key = Path(args.keys_dir) / "ed25519_public.pem"
                valid, msg = verify_signature(computed_root, stored_sig, pub_key)
                results["signature_valid"] = valid
                print(f"    Ed25519 签名: {msg}")
            else:
                print(f"    Ed25519 签名: 未验证（需要 --keys-dir）")
        else:
            print(f"    [!] 未找到 PROTECTION.MF")

        results["host_seal_valid"] = None
        if args.strict or HOST_MANIFEST in all_names or HOST_SIGNATURE in all_names:
            if not args.keys_dir:
                print("    [!] 未提供公钥，无法验证宿主签名清单")
            else:
                try:
                    from cryptography.hazmat.primitives.serialization import load_pem_public_key

                    public_key = load_pem_public_key((Path(args.keys_dir) / "ed25519_public.pem").read_bytes())
                    count = verify_host_seal(zin, public_key)
                except Exception as exc:
                    results["host_seal_valid"] = False
                    print(f"    [!] 宿主签名清单验证失败: {type(exc).__name__}: {exc}")
                else:
                    results["host_seal_valid"] = True
                    print(f"    宿主签名清单及明文边界检查通过（{count} 个条目）")

        # 结构验证
        print(f"\n[4/5] JAR 结构:")
        structure_ok = True
        if not has_pmf:
            print(f"    [!] 缺少 META-INF/PROTECTION.MF")
            structure_ok = False
        if len(encrypted) == 0:
            print(f"    [!] 无加密类文件")
            structure_ok = False
        if len(native) == 0:
            print(f"    [!] 无 native 库")
            structure_ok = False
        if structure_ok:
            print(f"    结构完整")
        results["structure_valid"] = structure_ok

        # 虚假类检测（启发式：类名看起来真实但解密会失败）
        # 简单估算：超出正常包结构的类
        results["fake_class_ratio"] = 0  # 无法准确检测，设为 0

    # 评分
    print(f"\n[5/5] 保护强度评分:")
    score = score_protection(results)
    print(f"    总分: {score}/100")
    if score >= 80:
        level = "极高"
    elif score >= 60:
        level = "高"
    elif score >= 30:
        level = "中"
    else:
        level = "低"
    print(f"    等级: {level}")

    print(f"\n{'='*50}")
    print(f"  PROTECTED: {len(encrypted)} 类已加密")
    if not structure_ok:
        print(f"  WARNINGS: JAR 结构不完整")
    gaps = []
    if results["native_platform_count"] < results["required_native_platform_count"]:
        gaps.append("缺少发行目标所需的 native 库")
    if not results["runtime_target_valid"]:
        gaps.append("平台清单无效")
    if results["signature_valid"] is not True:
        gaps.append("签名未验证")
    if results["host_seal_valid"] is False:
        gaps.append("宿主签名清单校验失败")
    if gaps:
        print(f"  GAPS: {'; '.join(gaps)}")
    print(f"{'='*50}")

    if args.strict:
        passed = (
            len(all_names) == len(set(all_names))
            and results["structure_valid"]
            and results["format_valid_ratio"] == 1
            and results["release_native"]
            and results["hash_match"]
            and results["signature_valid"] is True
            and results["host_seal_valid"] is True
        )
        print("  发行版完整性校验通过" if passed else "  发行版完整性校验失败")
        return 0 if passed else 1

    return 0 if score >= 60 else 1


if __name__ == "__main__":
    sys.exit(main())

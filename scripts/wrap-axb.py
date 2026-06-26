#!/usr/bin/env python3
"""把一个 Jar 封装成 .axb（与 buildSrc/EncryptModuleAxbTask.kt 逐字节等价）。

.axb 格式： magic(4 随机) | iv(12) | AES-256-GCM( gzip(jarBytes), key, iv ) [密文尾部含 16B GCM tag]

用于 CI：方案 ③ 把"逐类加密后的核心 payload jar"封装成 .axb 上传云端，
运行时由 native n4 解密（先 AES-GCM 解密、再 gunzip 还原 payload jar）。

输出：
  - <output>           .axb 文件
  - <output>.meta.json {"moduleKey": b64(32B key), "moduleIv": b64(12B iv), "size": N}
密钥随机生成（除非 --key/--iv 显式提供）。CI 读取 meta.json 的 moduleKey 上传云端。
"""
import argparse
import base64
import gzip
import json
import os
import secrets
import sys

try:
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
except ImportError:
    sys.stderr.write("[wrap-axb] 需要 cryptography： pip install cryptography\n")
    sys.exit(2)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--input", required=True, help="输入 Jar 路径")
    ap.add_argument("--output", required=True, help="输出 .axb 路径")
    ap.add_argument("--key", default=None, help="可选：32 字节 AES key（Base64）")
    ap.add_argument("--iv", default=None, help="可选：12 字节 IV（Base64）")
    args = ap.parse_args()

    with open(args.input, "rb") as f:
        plain = f.read()

    if args.key and args.iv:
        key = base64.b64decode(args.key)
        iv = base64.b64decode(args.iv)
        if len(key) != 32:
            sys.exit("--key 必须是 32 字节（Base64）")
        if len(iv) != 12:
            sys.exit("--iv 必须是 12 字节（Base64）")
    else:
        key = secrets.token_bytes(32)
        iv = secrets.token_bytes(12)

    gz = gzip.compress(plain)
    # AESGCM.encrypt 返回 ciphertext||tag（与 Java AES/GCM/NoPadding 一致）
    ct = AESGCM(key).encrypt(iv, gz, None)
    magic = secrets.token_bytes(4)
    blob = magic + iv + ct

    out_dir = os.path.dirname(os.path.abspath(args.output))
    if out_dir:
        os.makedirs(out_dir, exist_ok=True)
    with open(args.output, "wb") as f:
        f.write(blob)

    meta_path = args.output[:-4] + ".meta.json" if args.output.endswith(".axb") else args.output + ".meta.json"
    meta = {
        "moduleKey": base64.b64encode(key).decode("ascii"),
        "moduleIv": base64.b64encode(iv).decode("ascii"),
        "size": len(blob),
    }
    with open(meta_path, "w", encoding="utf-8") as f:
        json.dump(meta, f)

    print(f"[wrap-axb] {args.input} -> {args.output} ({len(blob)} bytes)")
    print(f"[wrap-axb] meta: {meta_path}")


if __name__ == "__main__":
    main()

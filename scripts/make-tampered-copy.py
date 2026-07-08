#!/usr/bin/env python3
"""生成一个"被篡改"的 native 库副本，供运行时自校验负例使用。

策略：复制源文件，然后翻转文件 1/3 偏移处的一个字节。
  - 该偏移远离文件头（ELF/PE 头在最前几 KB），副本仍可被加载器正常加载；
  - 该字节属于被 SHA-256 覆盖的内容且不在被置零的 32 字节哈希槽内，
    故整文件哈希必然改变，运行时 verify_native_self_integrity 应判定为篡改。
"""
import shutil
import sys


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: make-tampered-copy.py <src> <dst>", file=sys.stderr)
        return 2
    src, dst = sys.argv[1], sys.argv[2]
    shutil.copyfile(src, dst)
    with open(dst, "r+b") as f:
        f.seek(0, 2)
        size = f.tell()
        if size < 64:
            print(f"[ERROR] file too small to tamper: {size} bytes", file=sys.stderr)
            return 1
        off = size // 3
        f.seek(off)
        orig = f.read(1)[0]
        f.seek(off)
        f.write(bytes([orig ^ 0xFF]))
    print(f"tampered copy: {dst} (flipped byte@{off} {orig:#04x} -> {orig ^ 0xFF:#04x}, size={size})")
    return 0


if __name__ == "__main__":
    sys.exit(main())

#!/usr/bin/env python3
"""生成一个"被篡改"的 native 库副本，供运行时自校验负例使用。

在库末尾追加标记，保留 ELF/PE 的加载结构。自校验覆盖整个文件，副本应在 n5 被拒绝。
"""
import shutil
import sys


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: make-tampered-copy.py <src> <dst>", file=sys.stderr)
        return 2
    src, dst = sys.argv[1], sys.argv[2]
    shutil.copyfile(src, dst)
    with open(dst, "ab") as f:
        f.write(b"AXS self-check tamper fixture")
    print(f"tampered copy: {dst}")
    return 0


if __name__ == "__main__":
    sys.exit(main())

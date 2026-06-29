"""
为 Chemdah 1.1.33-FREE 补回付费版 MySQL 支持（DatabaseSQL 类）。

官方说明：免费版与付费版唯一差异为无法使用 MySQL，见
https://plugins.ptms.ink/plugin/chemdah/service

实现方式：从同仓库 1.1.8 拷贝缺失的 DatabaseSQL*.class 到 FREE 包。
注意：1.1.8 的 DatabaseSQL 与 1.1.33 的 Database$Companion 并非同一构建，
      需在测试服验证；生产环境更推荐 Wiki 中的 registerDatabaseImpl 自定义数据库。
"""
import argparse
import os
import zipfile

ROOT = os.path.join(os.path.dirname(__file__), "..")
LIB = os.path.join(ROOT, "libs")

DEFAULT_SRC = os.path.join(LIB, "Chemdah-1.1.8.jar")
DEFAULT_BASE = os.path.join(LIB, "Chemdah-1.1.33-FREE.jar")
DEFAULT_OUT = os.path.join(LIB, "Chemdah-1.1.33-FREE-patched.jar")

SQL_PREFIX = "ink/ptms/chemdah/core/database/DatabaseSQL"


def is_databasesql_class(path: str) -> bool:
    """仅匹配 DatabaseSQL，排除 DatabaseSQLite。"""
    if not path.endswith(".class"):
        return False
    name = path.rsplit("/", 1)[-1]
    return name == "DatabaseSQL.class" or name.startswith("DatabaseSQL$")


def build_patched_jar(src_jar: str, base_jar: str, out_jar: str) -> int:
    with zipfile.ZipFile(src_jar) as src_z:
        sql_entries = [e for e in src_z.namelist() if is_databasesql_class(e)]
        sql_data = {e: src_z.read(e) for e in sql_entries}

    with zipfile.ZipFile(base_jar) as base_z:
        base_entries = list(base_z.namelist())
        base_data = {e: base_z.read(e) for e in base_entries}

    added = 0
    for e, data in sql_data.items():
        if e not in base_data:
            base_data[e] = data
            added += 1
        elif base_data[e] != data:
            base_data[e] = data
            added += 1

    with zipfile.ZipFile(out_jar, "w", compression=zipfile.ZIP_DEFLATED) as out_z:
        for name in base_data:
            out_z.writestr(name, base_data[name])

    return added


def main():
    parser = argparse.ArgumentParser(description="Patch Chemdah FREE jar with DatabaseSQL")
    parser.add_argument("--src", default=DEFAULT_SRC)
    parser.add_argument("--base", default=DEFAULT_BASE)
    parser.add_argument("--out", default=DEFAULT_OUT)
    args = parser.parse_args()

    for p in (args.src, args.base):
        if not os.path.isfile(p):
            raise SystemExit(f"Missing file: {p}")

    added = build_patched_jar(args.src, args.base, args.out)
    with zipfile.ZipFile(args.out) as z:
        sql_count = sum(1 for e in z.namelist() if is_databasesql_class(e))
    print(f"Output: {args.out}")
    print(f"Added/updated DatabaseSQL entries: {added}")
    print(f"DatabaseSQL class files in output: {sql_count}")


if __name__ == "__main__":
    main()

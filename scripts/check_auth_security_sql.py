"""Fault checks using SQL extracted from current Java sources and disposable SQLite only.

Does not compile/run Java, contact a server, or open application databases.
EventPacket's journal is exercised by :modules:eventpacket:test.
"""
from pathlib import Path
import json
import re
import sqlite3
import textwrap

ROOT = Path(__file__).resolve().parents[1]
STRING = r'"(?:[^"\\]|\\.)*"'


def sql_strings(relative):
    source = (ROOT / relative).read_text(encoding="utf-8-sig")
    values = []
    for match in re.finditer(r'"""([\s\S]*?)"""|' + STRING + r'(?:\s*\+\s*' + STRING + r')*', source):
        if match.group(1) is not None:
            value = textwrap.dedent(match.group(1)).strip()
        else:
            value = "".join(json.loads(part) for part in re.findall(STRING, match.group()))
        values.append(value)
    return values


def statement(values, prefix):
    return next(value for value in values if value.startswith(prefix))


def schema(connection, values, *tables):
    for table in tables:
        connection.execute(statement(values, f"CREATE TABLE IF NOT EXISTS {table} ("))


bp = sql_strings("modules/battlepass/src/main/java/xuanmo/arcartxsuite/battlepass/storage/JdbcBattlePassRepository.java")
engine = sql_strings("auth-engine/src/main/java/xuanmo/arcartxsuite/auth/engine/JdbcAuthStore.java")

with sqlite3.connect(":memory:", isolation_level=None) as database:
    schema(database, engine, "axs_auth_presence_v1")
    insert = statement(engine, "INSERT INTO axs_auth_presence_v1 (realm_id,account_id,business_date) VALUES")
    pending = statement(engine, "SELECT business_date FROM axs_auth_presence_v1")
    ack = statement(engine, "UPDATE axs_auth_presence_v1 SET delivered=1")
    database.execute(insert, ("realm", "account", "2026-10-01"))
    database.execute(insert, ("realm", "account", "2026-11-08"))
    assert database.execute(pending, ("realm", "account", "2026-11-08")).fetchone()[0] == "2026-10-01"
    database.execute(ack, ("realm", "account", "2026-10-01"))
    assert database.execute(pending, ("realm", "account", "2026-11-08")).fetchone()[0] == "2026-11-08"

with sqlite3.connect(":memory:", isolation_level=None) as database:
    schema(database, bp, "bp_auth_receipts", "bp_player_progress", "bp_player_tasks")
    progress = statement(bp, "INSERT INTO bp_player_progress")
    task = statement(bp, "INSERT INTO bp_player_tasks")
    receipt = statement(bp, "INSERT INTO bp_auth_receipts")
    base = ("player", "season", 1, 0, "FREE", 0, "2026-11-08", "2026-11-08", 1)
    instance = ("player", "season", "instance", "login", "DAILY", 100, 0, 0, "2026-11-08", 0)
    database.execute(progress, base)
    database.execute(task, instance)
    database.execute("CREATE TRIGGER fail_task BEFORE UPDATE ON bp_player_tasks WHEN NEW.current_progress=99 BEGIN SELECT RAISE(ABORT,'injected failure'); END")

    def commit_fact(fact, xp, count):
        database.execute("BEGIN")
        try:
            database.execute(receipt, ("player", "season", fact))
            database.execute(progress, base[:3] + (xp,) + base[4:])
            database.execute(task, instance[:6] + (count,) + instance[7:])
            database.execute("COMMIT")
        except sqlite3.Error:
            database.execute("ROLLBACK")
            raise

    try:
        commit_fact("failed", 50, 99)
        raise AssertionError("Fault injection did not fire")
    except sqlite3.IntegrityError:
        pass
    assert database.execute("SELECT COUNT(*) FROM bp_auth_receipts").fetchone()[0] == 0
    assert database.execute("SELECT current_xp FROM bp_player_progress").fetchone()[0] == 0
    assert database.execute("SELECT current_progress FROM bp_player_tasks").fetchone()[0] == 0
    commit_fact("accepted", 10, 1)
    try:
        commit_fact("accepted", 999, 99)
        raise AssertionError("Duplicate fact was accepted")
    except sqlite3.IntegrityError:
        pass
    assert database.execute("SELECT current_xp FROM bp_player_progress").fetchone()[0] == 10
    assert database.execute("SELECT current_progress FROM bp_player_tasks").fetchone()[0] == 1

print("PASS SQL fault checks: transactional progress/XP/receipt, duplicate facts, backlog ordering; no Java execution")

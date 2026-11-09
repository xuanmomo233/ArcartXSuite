"""Fault checks using SQL extracted from current Java sources and disposable SQLite only.

Does not compile/run Java, contact a server, or open application databases.
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


def schema(connection, values):
    tables = set()
    for value in values:
        match = re.match(r"CREATE TABLE IF NOT EXISTS\s+(\w+)", value)
        if match and match.group(1) not in tables:
            connection.execute(value)
            tables.add(match.group(1))


bp = sql_strings("modules/battlepass/src/main/java/xuanmo/arcartxsuite/battlepass/storage/JdbcBattlePassRepository.java")
inbox = sql_strings("modules/eventpacket/src/main/java/xuanmo/arcartxsuite/eventpacket/storage/JdbcEventPacketRepository.java")
engine = sql_strings("auth-engine/src/main/java/xuanmo/arcartxsuite/auth/engine/JdbcAuthStore.java")

with sqlite3.connect(":memory:", isolation_level=None) as database:
    schema(database, engine)
    insert = statement(engine, "INSERT INTO axs_auth_presence_v1 (realm_id,account_id,business_date) VALUES")
    pending = statement(engine, "SELECT business_date FROM axs_auth_presence_v1")
    ack = statement(engine, "UPDATE axs_auth_presence_v1 SET delivered=1")
    database.execute(insert, ("realm", "account", "2026-10-01"))
    database.execute(insert, ("realm", "account", "2026-11-08"))
    assert database.execute(pending, ("realm", "account", "2026-11-08")).fetchone()[0] == "2026-10-01"
    database.execute(ack, ("realm", "account", "2026-10-01"))
    assert database.execute(pending, ("realm", "account", "2026-11-08")).fetchone()[0] == "2026-11-08"

with sqlite3.connect(":memory:", isolation_level=None) as database:
    schema(database, bp)
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

with sqlite3.connect(":memory:", isolation_level=None) as database:
    schema(database, inbox)
    enqueue = statement(inbox, "INSERT INTO eventpacket_auth_inbox")
    claim = statement(inbox, "UPDATE eventpacket_auth_inbox SET state='RUNNING'")
    recover = statement(inbox, "UPDATE eventpacket_auth_inbox SET state='REVIEW'")
    resolve = statement(inbox, "UPDATE eventpacket_auth_inbox SET state='PENDING',step=step+?")
    finish = statement(inbox, "UPDATE eventpacket_auth_inbox SET state=?,step=?")
    database.execute(enqueue, ("fact", "player", "original plan"))
    database.execute(enqueue, ("fact", "player", "replacement plan"))
    assert database.execute("SELECT plan FROM eventpacket_auth_inbox").fetchone()[0] == "original plan"
    assert database.execute(claim, ("node-a", "fact", 0)).rowcount == 1
    assert database.execute(recover, ("node-b",)).rowcount == 0, "Another node may still be running the action"
    assert database.execute(finish, ("DONE", 0, "fact", 0, "node-b")).rowcount == 0
    assert database.execute(recover, ("node-a",)).rowcount == 1
    assert database.execute(claim, ("node-b", "fact", 0)).rowcount == 0, "Interrupted side effects must not replay automatically"
    assert database.execute(resolve, (0, "fact")).rowcount == 1
    assert database.execute(claim, ("node-b", "fact", 0)).rowcount == 1
    assert database.execute(finish, ("DONE", 0, "fact", 0, "node-b")).rowcount == 1
    assert database.execute(claim, ("node-b", "fact", 0)).rowcount == 0

print("PASS SQL fault checks: transactional progress/XP/receipt, duplicate facts, backlog ordering, durable inbox and crash review; no Java execution")

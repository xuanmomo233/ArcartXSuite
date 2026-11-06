"""Source-only checks. Does not invoke Java, Gradle, servers, or application tests."""
from pathlib import Path
import json
import re
import sqlite3
import sys

import javalang.tokenizer
import yaml

ROOT = Path(__file__).resolve().parents[1]
errors = []
paths = []
for folder in (
    "axs-api/src/main/java/xuanmo/arcartxsuite/api/auth",
    "src/main/java/xuanmo/arcartxsuite/auth/runtime",
    "auth-minecraft/src/main/java",
    "auth-engine/src/main/java",
    "auth-engine/src/test/java",
    "auth-protocol/src/main/java",
    "auth-protocol/src/test/java",
    "auth-center/src/main/java",
    "auth-center/src/test/java",
    "modules/loginview/src/main/java",
    "axs-api/src/test/java/xuanmo/arcartxsuite/api/auth",
    "axs-core/src/test/java/xuanmo/arcartxsuite/auth/runtime",
    "auth-minecraft/src/test/java",
):
    paths.extend((ROOT / folder).rglob("*.java"))
for relative in (
    "axs-api/src/main/java/xuanmo/arcartxsuite/api/capability/AuthRuntimeCapability.java",
    "axs-api/src/main/java/xuanmo/arcartxsuite/api/capability/BindingService.java",
):
    paths.append(ROOT / relative)
for relative in (
    "modules/chat/src/main/java/xuanmo/arcartxsuite/chat/service/ChatService.java",
    "modules/chat/src/main/java/xuanmo/arcartxsuite/chat/service/RedPacketService.java",
    "modules/eventpacket/src/main/java/xuanmo/arcartxsuite/eventpacket/listener/PlayerEventPacketListener.java",
    "modules/eventpacket/src/main/java/xuanmo/arcartxsuite/eventpacket/service/EventPacketDispatchService.java",
    "modules/battlepass/src/main/java/xuanmo/arcartxsuite/battlepass/service/BattlePassTaskEngine.java",
    "modules/qqbot/src/main/java/xuanmo/arcartxsuite/qqbot/service/QQBotBindService.java",
    "modules/qqbot/src/main/java/xuanmo/arcartxsuite/qqbot/service/QQBotCommandRouter.java",
    "modules/qqbot/src/main/java/xuanmo/arcartxsuite/qqbot/service/QQBotUiService.java",
    "modules/qqbot/src/main/java/xuanmo/arcartxsuite/qqbot/command/QQBotPlayerCommand.java",
    "modules/qqbot/src/main/java/xuanmo/arcartxsuite/qqbot/storage/JdbcQQBotRepository.java",
    "modules/title/src/main/java/xuanmo/arcartxsuite/title/listener/PlayerTitleListener.java",
    "modules/title/src/main/java/xuanmo/arcartxsuite/title/service/TitleService.java",
    "modules/tab/src/main/java/xuanmo/arcartxsuite/tab/sync/TabSyncService.java",
    "src/main/java/xuanmo/arcartxsuite/chat/ChatSignBypassService.java",
    "src/main/java/xuanmo/arcartxsuite/bridge/DefaultPendingRewardService.java",
    "src/main/java/xuanmo/arcartxsuite/module/GlobalBridgeFactory.java",
    "src/main/java/xuanmo/arcartxsuite/module/ModuleRegistry.java",
    "src/main/java/xuanmo/arcartxsuite/module/DefaultModuleContext.java",
    "src/main/java/xuanmo/arcartxsuite/module/ClientPacketRouter.java",
    "src/main/java/xuanmo/arcartxsuite/module/SimpleEventBus.java",
    "src/main/java/xuanmo/arcartxsuite/lifecycle/ClientEventLifecycleManager.java",
    "axs-api/src/main/java/xuanmo/arcartxsuite/api/SecurityContext.java",
    "proxy/velocity/src/main/java/xuanmo/arcartxsuite/proxy/velocity/VelocityAuthListener.java",
    "proxy/bungee/src/main/java/xuanmo/arcartxsuite/proxy/bungee/BungeeAuthListener.java",
    "src/main/java/xuanmo/arcartxsuite/module/ModuleDescriptorParser.java",
    "axs-core/src/test/java/xuanmo/arcartxsuite/module/ModuleDescriptorParserTest.java",
    "axs-core/src/test/java/xuanmo/arcartxsuite/module/SimpleEventBusSubscriberTest.java",
    "proxy/velocity/src/main/java/xuanmo/arcartxsuite/proxy/velocity/VelocityProofForwarder.java",
    "proxy/velocity/src/main/java/xuanmo/arcartxsuite/proxy/velocity/ArcartXSuiteVelocity.java",
    "proxy/bungee/src/main/java/xuanmo/arcartxsuite/proxy/bungee/ArcartXSuiteBungee.java",
    "proxy/common/src/main/java/xuanmo/arcartxsuite/proxy/common/config/ProxyConfig.java",
):
    paths.append(ROOT / relative)
for path in paths:
    try:
        stack = []
        for token in javalang.tokenizer.tokenize(path.read_text(encoding="utf-8-sig")):
            if token.value in ("{", "(", "["):
                stack.append(token.value)
            elif token.value in ("}", ")", "]"):
                if not stack or stack.pop() != {"}": "{", ")": "(", "]": "["}[token.value]:
                    raise ValueError(f"unbalanced delimiters at {token.position}")
        if stack:
            raise ValueError("unclosed delimiters")
    except Exception as error:
        errors.append(f"{path.relative_to(ROOT)}: {error}")

def unique_yaml_mapping(loader, node, deep=False):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in result:
            raise ValueError(f"duplicate YAML key: {key}")
        result[key] = loader.construct_object(value_node, deep=deep)
    return result

class UniqueLoader(yaml.SafeLoader):
    pass

UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_yaml_mapping)
yaml_paths = (
    "src/main/resources/authentication.yml",
    "auth-center/config/node.example.yml",
    "modules/loginview/src/main/resources/LoginView.yml",
    "modules/loginview/src/main/resources/module.yml",
    "modules/loginview/src/main/resources/arcartx/ui/auth_v1.yml",
    "src/main/resources/config.yml",
    "proxy/common/src/main/resources/proxy-config.yml",
    "modules/battlepass/src/main/resources/tasks/daily.yml",
    "modules/battlepass/src/main/resources/tasks/season.yml",
    "modules/eventpacket/src/main/resources/rules/examples.yml",
)
for relative in yaml_paths:
    try:
        yaml.load((ROOT / relative).read_text(encoding="utf-8-sig"), Loader=UniqueLoader)
    except Exception as error:
        errors.append(f"{relative}: {error}")

def unique_json_mapping(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError(f"duplicate JSON key: {key}")
        result[key] = value
    return result

json_paths = ("auth-center/config/center.example.json", "auth-center/config/center.external.example.json",
              "docs/auth-refactor/progress.json", "docs/auth-refactor/p3-contract.json", "docs/auth-refactor/p4-contract.json")
for relative in json_paths:
    try:
        json.loads((ROOT / relative).read_text(encoding="utf-8-sig"), object_pairs_hook=unique_json_mapping)
    except Exception as error:
        errors.append(f"{relative}: {error}")

for path in (ROOT / "modules").rglob("module.yml"):
    if "build" in path.parts:
        continue
    manifest = yaml.safe_load(path.read_text(encoding="utf-8-sig"))
    if str(manifest.get("api-version")) != "2.0":
        errors.append(f"{path.relative_to(ROOT)}: module must declare API 2.0")

gateway_prefix = "xuanmo.arcartxsuite.auth.minecraft."
for relative in ("scripts/encrypt-jar.py", "gradle/encryption-tasks.gradle.kts", ".github/workflows/build-encrypted.yml"):
    if gateway_prefix not in (ROOT / relative).read_text(encoding="utf-8-sig"):
        errors.append(f"{relative}: standalone gateway plaintext boundary missing")
if "-keep class " + gateway_prefix + "**" not in (ROOT / "proguard/axs-core.pro").read_text(encoding="utf-8-sig"):
    errors.append("proguard/axs-core.pro: standalone gateway entry point can be removed or renamed")
if "xuanmo/arcartxsuite/auth/minecraft/" not in (ROOT / "buildSrc/src/main/kotlin/StringEncryptTask.kt").read_text(encoding="utf-8-sig"):
    errors.append("StringEncryptTask: standalone gateway depends on plugin string decryption")
if "-keep class xuanmo.arcartxsuite.auth.protocol.AuthWire$*" not in (ROOT / "proguard/axs-core.pro").read_text(encoding="utf-8-sig"):
    errors.append("proguard/axs-core.pro: center wire DTO names must survive obfuscation")
for module in ("auth-engine", "auth-protocol", "auth-center"):
    gradle = (ROOT / module / "build.gradle.kts").read_text(encoding="utf-8-sig")
    if 'project(":axs-' in gradle or "spigot-api" in gradle or "paper-api" in gradle:
        errors.append(f"{module}: independent auth module depends on Minecraft host")
    for path in (ROOT / module / "src/main/java").rglob("*.java"):
        content = path.read_text(encoding="utf-8-sig")
        if re.search(r"^import\s+(?:org\.bukkit\.|xuanmo\.arcartxsuite\.api\.)", content, re.M):
            errors.append(f"{path.relative_to(ROOT)}: host-specific auth dependency")

for root in (ROOT / "modules", ROOT / "axs-api/src/main/java", ROOT / "src/main/java"):
    for path in root.rglob("*"):
        if path.suffix not in (".java", ".yml") or "build" in path.parts:
            continue
        content = path.read_text(encoding="utf-8-sig")
        for removed in ("LoginViewQueryable", "LoginViewService", "LoginViewModuleConfiguration", "QQBindCapable", "axsloginview", "axs.loginview.login_success"):
            if removed in content:
                errors.append(f"{path.relative_to(ROOT)}: removed contract reference {removed}")

own_types = set()
for folder in ("axs-api/src", "src", "modules", "proxy", "auth-minecraft/src", "auth-engine/src", "auth-protocol/src", "auth-center/src"):
    for path in (ROOT / folder).rglob("*.java"):
        if "build" in path.parts:
            continue
        source = path.read_text(encoding="utf-8-sig")
        package = re.search(r"^package\s+([\w.]+)\s*;", source, re.M)
        if package:
            own_types.add(package.group(1) + "." + path.stem)
for path in paths:
    source = path.read_text(encoding="utf-8-sig")
    for imported in re.findall(r"^import\s+(xuanmo\.arcartxsuite\.[\w.]+);", source, re.M):
        parts = imported.split(".")
        while ".".join(parts) not in own_types and len(parts) > 2:
            parts.pop()
        if len(parts) <= 2:
            errors.append(f"{path.relative_to(ROOT)}: missing source import {imported}")
    if re.search(r"@ApiStability\.Stable\s+@ApiStability\.Stable", source):
        errors.append(f"{path.relative_to(ROOT)}: duplicate stability annotation")

# Prepare literal auth SQL against a disposable in-memory schema. No application queries execute.
sql_sources = (
    "auth-engine/src/main/java/xuanmo/arcartxsuite/auth/engine/JdbcAuthStore.java",
    "auth-center/src/main/java/xuanmo/arcartxsuite/auth/center/CenterLedger.java",
    "auth-center/src/main/java/xuanmo/arcartxsuite/auth/center/CenterOutbox.java",
    "auth-center/src/main/java/xuanmo/arcartxsuite/auth/center/GameExternalStore.java",
)
sql_literal = r'"(?:[^"\\]|\\.)*"'
sql_statements = set()
for relative in sql_sources:
    source = (ROOT / relative).read_text(encoding="utf-8-sig")
    for expression in re.findall(sql_literal + r'(?:\s*\+\s*' + sql_literal + r')*', source):
        statement = "".join(json.loads(literal) for literal in re.findall(sql_literal, expression)).strip()
        if re.match(r"^(CREATE TABLE|CREATE INDEX|SELECT|INSERT|UPDATE|DELETE)\s", statement):
            if statement.endswith(" AND"):
                for predicate in ("lower_name=?", "account_id=?", "profile_id=?", "profile_id=? AND identity_provider=?"):
                    sql_statements.add(statement + " " + predicate)
            else:
                sql_statements.add(statement)
try:
    with sqlite3.connect(":memory:") as database:
        for prefix in ("CREATE TABLE", "CREATE INDEX"):
            for statement in sorted(sql_statements):
                if statement.startswith(prefix):
                    database.execute(statement)
        for statement in sorted(sql_statements):
            if not statement.startswith("CREATE "):
                database.execute("EXPLAIN " + statement, (None,) * statement.count("?"))
except sqlite3.Error as error:
    errors.append(f"SQLite statement preparation failed: {error}: {statement}")

if errors:
    print("\n".join(errors))
    sys.exit(1)
print(f"PASS source-only checks: {len(paths)} Java files, {len(yaml_paths)} YAML files, {len(json_paths)} JSON files, {len(sql_statements)} SQLite statements, imports and auth boundaries")

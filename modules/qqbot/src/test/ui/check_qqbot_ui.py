"""QQBot UI packet/template contract; optionally compile every expression with an actual client Aria jar.

Requires PyYAML. No Minecraft process is started and no script is executed.
Example: python check_qqbot_ui.py --aria-jar <ArcartX-client.jar> --java-bin <verified-JDK/bin>
"""

from __future__ import annotations

import argparse
from pathlib import Path
import re
import shutil
import subprocess
import tempfile

import yaml


class UniqueLoader(yaml.SafeLoader):
    pass


def unique_mapping(loader, node, deep=False):
    result = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in result:
            raise ValueError(f"Duplicate YAML key: {key}")
        result[key] = loader.construct_object(value_node, deep=deep)
    return result


UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_mapping)


def scripts(node, path="", section=""):
    if isinstance(node, dict):
        for key, value in node.items():
            location = f"{path}/{key}"
            if key in ("action", "packetHandler", "attribute"):
                yield from scripts(value, location, key)
            elif isinstance(value, str) and section in ("action", "packetHandler"):
                yield location, value
            elif isinstance(value, str) and section == "attribute" and not value.startswith("~"):
                yield location, f"return {value}"
            elif isinstance(value, (dict, list)):
                yield from scripts(value, location, "")
    elif isinstance(node, list):
        for index, value in enumerate(node):
            yield from scripts(value, f"{path}/{index}", section)


JAVA_PROBE = r"""
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import priv.seventeen.artist.aria.Aria;

public final class QQBotAriaProbe {
    public static void main(String[] args) throws Exception {
        int failed = 0, checked = 0;
        try (var files = Files.list(Path.of(args[0]))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".aria")).sorted().toList()) {
                checked++;
                try {
                    Aria.compile(file.getFileName().toString(), Files.readString(file, StandardCharsets.UTF_8));
                } catch (Exception error) {
                    failed++;
                    System.err.println(file.getFileName() + ": " + error.getMessage());
                }
            }
        }
        System.out.println("Aria strict compilation: " + checked + " scripts, " + failed + " failures");
        if (failed > 0) System.exit(1);
    }
}
"""


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--aria-jar", type=Path)
    parser.add_argument("--java-bin", type=Path)
    parser.add_argument("--dependency", type=Path, action="append", default=[])
    args = parser.parse_args()
    module = Path(__file__).resolve().parents[3]
    resources = module / "src/main/resources/arcartx/ui"
    server = module / "src/main/java/xuanmo/arcartxsuite/qqbot/service/QQBotUiService.java"
    actions = set(re.findall(r'case "([a-z_]+)"', server.read_text(encoding="utf-8")))
    compiled = []
    for path in sorted(resources.glob("*.yml")):
        content = path.read_text(encoding="utf-8")
        doc = yaml.load(content, Loader=UniqueLoader)
        for action, tail in re.findall(r"Packet.send\(var.packetId, '([^']+)'([^\n]*)", content):
            assert action in actions, (path.name, action)
            assert tail.lstrip().startswith(", var.actionToken"), (path.name, action, "missing token")
        assert "self.key" not in content, (path.name, "uninstantiated observer key")
        assert "Time.now()" not in content, (path.name, "unsupported time API")
        for location, code in scripts(doc):
            compiled.append((path.name + location, code))
        print(f"{path.name}: YAML and client/server action-token contract passed")
    if not args.aria_jar:
        print("Aria syntax was not checked; pass the actual client jar with --aria-jar")
        return
    java = str(args.java_bin / "java.exe") if args.java_bin else shutil.which("java")
    javac = str(args.java_bin / "javac.exe") if args.java_bin else shutil.which("javac")
    if not java or not javac:
        raise RuntimeError("A verified JDK with java and javac is required")
    import os
    with tempfile.TemporaryDirectory(prefix="qqbot-ui-aria-") as directory:
        work = Path(directory)
        (work / "QQBotAriaProbe.java").write_text(JAVA_PROBE, encoding="utf-8")
        for index, (location, code) in enumerate(compiled):
            name = re.sub(r"[^a-zA-Z0-9_.-]", "_", location)
            (work / f"{index:03d}_{name}.aria").write_text(code, encoding="utf-8")
        classpath = os.pathsep.join(map(str, [args.aria_jar, *args.dependency, work]))
        subprocess.run([javac, "-encoding", "UTF-8", "-cp", classpath, str(work / "QQBotAriaProbe.java")], check=True)
        subprocess.run([java, "-Dfile.encoding=UTF-8", "-cp", classpath, "QQBotAriaProbe", str(work)], check=True)


if __name__ == "__main__":
    main()

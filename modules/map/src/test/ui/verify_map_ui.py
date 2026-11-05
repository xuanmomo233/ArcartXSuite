"""Validate YAML and execute actual UI event scripts in a small contract harness (not a renderer)."""
import json
import pathlib
import subprocess
import yaml

ROOT = pathlib.Path(__file__).resolve().parents[3]

class UniqueLoader(yaml.SafeLoader):
    pass

def mapping(loader, node, deep=False):
    result = {}
    for key, value in node.value:
        name = loader.construct_object(key, deep=deep)
        if name in result:
            raise ValueError(f"Duplicate YAML key: {name} at line {key.start_mark.line + 1}")
        result[name] = loader.construct_object(value, deep=deep)
    return result

UniqueLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, mapping)
document = yaml.load((ROOT / 'src/main/resources/arcartx/ui/map_menu.yml').read_text(encoding='utf-8'), Loader=UniqueLoader)
result = subprocess.run(['node', str(pathlib.Path(__file__).with_suffix('.mjs'))],
    input=json.dumps(document, ensure_ascii=False), text=True, encoding='utf-8', capture_output=True)
print(result.stdout, end='')
print(result.stderr, end='')
raise SystemExit(result.returncode)

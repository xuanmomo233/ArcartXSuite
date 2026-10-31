"""Run the bundled Abyss UI contract scripts against a deferred control-tree harness.
Requires Python/PyYAML and Node.js. Does not claim to render the ArcartX client.
"""
import json, pathlib, subprocess, sys, re, yaml
root = pathlib.Path(__file__).resolve().parents[3]
ui = root / "src/main/resources/arcartx/ui"
class UniqueKeysLoader(yaml.SafeLoader): pass
def unique_mapping(loader, node, deep=False):
    mapping = {}
    for key_node, value_node in node.value:
        key = loader.construct_object(key_node, deep=deep)
        if key in mapping: raise ValueError(f"Duplicate YAML key {key!r} at line {key_node.start_mark.line + 1}")
        mapping[key] = loader.construct_object(value_node, deep=deep)
    return mapping
UniqueKeysLoader.add_constructor(yaml.resolver.BaseResolver.DEFAULT_MAPPING_TAG, unique_mapping)
documents = {p.stem.removeprefix("abyss_"): yaml.load(p.read_text(encoding="utf-8-sig"), Loader=UniqueKeysLoader)
             for p in sorted(ui.glob("abyss_*.yml"))}
assert len(documents) == 14, f"Expected all 14 bundled UIs, found {len(documents)}"
def validate_controls(name, document):
    nodes, aliases = [], {}
    def walk(children, parent=None, path=''):
        for key, definition in children.items():
            node = {'definition': definition, 'parent': parent, 'path': path + '/' + key}
            nodes.append(node)
            if 'val' in definition:
                assert definition['val'] not in aliases, f"{name}: duplicate val {definition['val']}"
                aliases[definition['val']] = node
            walk(definition.get('children', {}), node, node['path'])
    def strings(value):
        if isinstance(value, dict):
            for item in value.values(): yield from strings(item)
        elif isinstance(value, list):
            for item in value: yield from strings(item)
        elif isinstance(value, str): yield value
    walk(document['controls'])
    for node in nodes:
        definition = node['definition']
        kind = definition.get('type', '').lower()
        attributes = definition.get('attribute', {})
        assert attributes.get('limitControl') != 'self', f"{name}: self clipping recurses during native hit testing {node['path']}"
        if kind in ['hgrid', 'vgrid']:
            assert not {'hSpace', 'vSpace', 'spaceBetween'} & attributes.keys(), f"{name}: unsupported grid spacing {node['path']}"
        if kind == 'scroll':
            assert len(definition.get('children', {})) == 1, f"{name}: Scroll needs exactly one layout child {node['path']}"
        if kind in ['hstack', 'vstack'] and any('entry' in c.get('attribute', {}) for c in definition.get('children', {}).values()):
            assert 'maxSize' in attributes or node['path'].endswith('/行容器'), f"{name}: dynamic stack can discard its template above 64 entries {node['path']}"
        if 'clickLeft' in definition.get('action', {}):
            assert not definition.get('attribute', {}).get('through', False), f"{name}: click passes through {node['path']}"
        for code in strings({k: definition.get(k, {}) for k in ['attribute', 'action', 'effect']}):
            assert not re.search(r'[?:]\s*~', code), f"{name}: invalid color expression {code}"
            for alias in re.findall(r'\bval\.([\w\u4e00-\u9fff]+)', code):
                assert alias in aliases, f"{name}: missing val {alias}"
            for match in re.finditer(r'\b(self(?:\.parent)*)\.entry\b', code):
                target = node
                for _ in range(match.group(1).count('.parent')):
                    target = target['parent'] if target else None
                assert target and 'entry' in target['definition'].get('attribute', {}), f"{name}: incorrect entry path {node['path']}: {match.group()}"
    for code in strings(document['ui']):
        for alias in re.findall(r'\bval\.([\w\u4e00-\u9fff]+)', code):
            assert alias in aliases, f"{name}: missing handler val {alias}"
for name, document in documents.items(): validate_controls(name, document)
server = (root / 'src/main/java/xuanmo/arcartxsuite/abyss/ui/AbyssUiService.java').read_text(encoding='utf-8-sig')
routes = {}
for match in re.finditer(r'case "([a-z_]+)" -> \{(.*?)(?=\n            case|\n            default)', server, re.S):
    token = re.search(r'checkToken\(player, data, (\d+)\)', match.group(2))
    routes[match.group(1)] = int(token.group(1)) if token else None
result = subprocess.run(["node.exe", str(pathlib.Path(__file__).with_name("verify_abyss_ui.mjs"))],
    input=json.dumps({'documents': documents, 'routes': routes}, ensure_ascii=False), text=True, encoding="utf-8")
sys.exit(result.returncode)

import zipfile, os, re
from collections import defaultdict

LIB = os.path.join(os.path.dirname(__file__), '..', 'libs')
jars = {
    '1.1.8': os.path.join(LIB, 'Chemdah-1.1.8.jar'),
    '1.1.19-FREE': os.path.join(LIB, 'Chemdah-1.1.19-FREE.jar'),
    '1.1.33-FREE': os.path.join(LIB, 'Chemdah-1.1.33-FREE.jar'),
}

sets = {}
for name, path in jars.items():
    with zipfile.ZipFile(path) as z:
        sets[name] = set(e for e in z.namelist() if e.endswith('.class'))

only_18 = sets['1.1.8'] - sets['1.1.19-FREE'] - sets['1.1.33-FREE']

def pkg_summary(classes):
    pkgs = defaultdict(list)
    for c in classes:
        if 'ink/ptms/chemdah/' not in c:
            continue
        parts = c.replace('.class', '').split('/')
        pkg = '/'.join(parts[3:6]) if len(parts) >= 6 else '/'.join(parts[3:])
        pkgs[pkg].append(parts[-1])
    return pkgs

print('=== ONLY in 1.1.8 (premium candidates) ===')
for pkg, cls in sorted(pkg_summary(only_18).items(), key=lambda x: -len(x[1])):
    tops = [c for c in cls if '$' not in c]
    print(f'  {pkg}: {len(cls)} total, top-level: {tops}')

print('\n=== isFree references ===')
for name, path in jars.items():
    hits = []
    with zipfile.ZipFile(path) as z:
        for e in z.namelist():
            if not e.endswith('.class') or 'chemdah' not in e:
                continue
            data = z.read(e)
            if b'isFree' in data:
                hits.append(e.replace('ink/ptms/chemdah/', '').replace('.class', ''))
    print(f'{name}: {hits}')

print('\n=== Classes with 免费/付费 strings ===')
for name, path in jars.items():
    with zipfile.ZipFile(path) as z:
        for e in z.namelist():
            if not e.endswith('.class') or 'chemdah' not in e:
                continue
            data = z.read(e)
            if b'\xe5\x85\x8d\xe8\xb4\xb9' in data or b'\xe4\xbb\x98\xe8\xb4\xb9' in data:
                print(f'  {name}: {e.split("/")[-1].replace(".class","")}')

print('\n=== Database layer ===')
for name, path in jars.items():
    with zipfile.ZipFile(path) as z:
        db = sorted([e.split('/')[-1].replace('.class','') for e in z.namelist()
                     if 'core/database/' in e and e.endswith('.class') and '$' not in e.split('/')[-1]])
        print(f'{name}: {db}')

print('\n=== Flags classes diff ===')
for a, b in [('1.1.8', '1.1.33-FREE')]:
    flags_18 = {e for e in sets[a] if 'selector/Flags' in e}
    flags_33 = {e for e in sets[b] if 'selector/Flags' in e}
    print(f'Flags only in {a}:', sorted(flags_18 - flags_33))
    print(f'Flags only in {b}:', sorted(flags_33 - flags_18))

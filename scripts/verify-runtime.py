#!/usr/bin/env python3
"""Verify the exact runtime closure before a protected host can be published."""
import argparse
import hashlib
from pathlib import Path
import zipfile
from cryptography.hazmat.primitives import serialization

PUBLIC = ('com/mysql/', 'org/sqlite/', 'redis/clients/jedis/', 'org/apache/commons/pool2/',
          'com/zaxxer/hikari/', 'net/sourceforge/pinyin4j/', 'com/hp/hpl/', 'pinyindb/',
          'org/bouncycastle/', 'org/java_websocket/', 'org/json/')


def verify(jar_path, libraries, public_key):
    with zipfile.ZipFile(jar_path) as jar:
        descriptor = jar.read('META-INF/axs-libraries.properties')
        signature = jar.read('META-INF/axs-libraries.sig')
        public_key.verify(signature, hashlib.sha256(b'AXS-RUNTIME-1\0' + descriptor).digest())
        pairs = [line.split('=', 1) for line in descriptor.decode('ascii').splitlines() if line]
        props = dict(pairs)
        assert len(props) == len(pairs), 'Duplicate catalog fields'
        assert props['format'] == '1'
        count = int(props['artifact.count'])
        assert 1 <= count <= 32 and len(props) == 2 + 4 * count
        assert 'xuanmo/arcartxsuite/bootstrap/RuntimeBootstrap.class' in jar.namelist()
        assert hashlib.sha256(descriptor).hexdigest().encode('ascii') in jar.read('xuanmo/arcartxsuite/bootstrap/RuntimeBootstrap.class'), 'Catalog pin missing'
        assert 'native/axs-native.enc' in jar.namelist() or 'native/libaxs-native.enc' in jar.namelist()
        hashes = set()
        for index in range(count):
            prefix = f'artifact.{index}.'
            sha = props[prefix + 'sha256']
            assert sha not in hashes and len(sha) == 64
            hashes.add(sha)
            artifact = Path(libraries) / (sha + '.jar')
            data = artifact.read_bytes()
            assert len(data) == int(props[prefix + 'size']) and hashlib.sha256(data).hexdigest() == sha
            owned = props[prefix + 'prefixes'].split(',')
            assert all(name in PUBLIC for name in owned)
            with zipfile.ZipFile(artifact) as runtime:
                for name in runtime.namelist():
                    notice = name.startswith('META-INF/') and any(word in name.upper() for word in ('LICENSE', 'NOTICE', 'COPYING'))
                    service = name == 'META-INF/services/java.sql.Driver' and all(
                        (line.replace('.', '/') + '.class').startswith(tuple(owned))
                        for line in runtime.read(name).decode('utf-8').splitlines() if line)
                    assert '..' not in name and '\\' not in name and (notice or service or name.startswith(tuple(owned))), f'Non-public runtime entry: {name}'
                    assert notice or name not in jar.namelist(), f'Duplicate bundled/runtime entry: {name}'
            print(f'Runtime verified: {props[prefix + "id"]} {len(data)} bytes {sha}')
        assert {path.stem for path in Path(libraries).glob('*.jar')} == hashes, 'Stale runtime artifacts'
    print('Signed runtime catalog and complete public dependency closure verified')


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--input', required=True)
    parser.add_argument('--libraries', required=True)
    parser.add_argument('--public-key', required=True)
    args = parser.parse_args()
    public_key = serialization.load_pem_public_key(Path(args.public_key).read_bytes())
    verify(args.input, args.libraries, public_key)

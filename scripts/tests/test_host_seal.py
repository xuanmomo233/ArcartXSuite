import base64
import hashlib
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
import zipfile

from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey


SCRIPTS = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(SCRIPTS))
from host_seal import (
    HOST_MANIFEST, HOST_NOTICE, HOST_NOTICE_SIGNATURE, HOST_SIGNATURE, PLAIN_HOST_CLASSES,
    make_manifest, verify_host_seal,
)


COMMIT = "0123456789abcdef" * 2 + "01234567"
NATIVE = "native/axs-native.enc"


class HostSealTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.temp = tempfile.TemporaryDirectory()
        cls.addClassCleanup(cls.temp.cleanup)
        root = Path(cls.temp.name)
        cls.key = Ed25519PrivateKey.generate()
        cls.public_key = cls.key.public_key()
        (root / "root_seed.bin").write_bytes(bytes(range(32)))  # Fixture only.
        (root / "ed25519_private.pem").write_bytes(cls.key.private_bytes(
            serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()
        ))
        (root / "ed25519_public.pem").write_bytes(cls.public_key.public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
        ))
        cls.input = root / "fixture.jar"
        cls.output = root / "fixture-encrypted.jar"
        cls.source = {name.replace(".", "/") + ".class": b"fixture" for name in PLAIN_HOST_CLASSES}
        cls.source.update({
            "xuanmo/arcartxsuite/bootstrap/PrivateHelper.class": b"private fixture",
            "xuanmo/arcartxsuite/api/ApiContract.class": b"public fixture",
            "META-INF/axs-runtime.properties": b"format=1\ntarget=windows-x86_64\nos=windows\narch=x86_64\n",
            NATIVE: b"test-only native envelope" * 2,
            "plugin.yml": b"name: Suite-fixture\n",
            "assets/\u4e2d\u6587.txt": b"",
        })
        cls.write_archive(cls.input, cls.source)
        cls.encrypt_args = [sys.executable, "-X", "utf8", "-B", str(SCRIPTS / "encrypt-jar.py"),
                            "--input", str(cls.input), "--output", str(cls.output),
                            "--native-dir", str(root), "--no-native", "--keys-dir", str(root),
                            "--build-commit", COMMIT, "--protection-level", "standard"]
        cls.verify_args = [sys.executable, "-X", "utf8", "-B", str(SCRIPTS / "verify-protection.py"),
                           "--input", str(cls.output), "--keys-dir", str(root), "--strict"]
        process = subprocess.run(cls.encrypt_args, capture_output=True, check=False)
        if process.returncode:
            raise AssertionError(process.stdout.decode("utf-8", "replace") + process.stderr.decode("utf-8", "replace"))
        with zipfile.ZipFile(cls.output) as archive:
            cls.entries = {entry.filename: archive.read(entry) for entry in archive.infolist()}

    @staticmethod
    def write_archive(target, entries):
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
            for name, data in entries.items():
                archive.writestr(name, data)

    def test_packager_authenticates_release_metadata_and_encrypts_unlisted_bootstrap_class(self):
        self.assertEqual(json.loads(self.entries["axs-build.json"]), {"commit": COMMIT})
        self.assertIn("ENCRYPTED/xuanmo/arcartxsuite/bootstrap/PrivateHelper.enc", self.entries)
        self.assertNotIn("xuanmo/arcartxsuite/bootstrap/PrivateHelper.class", self.entries)
        self.assertIn("xuanmo/arcartxsuite/api/ApiContract.class", self.entries)
        process = subprocess.run(self.verify_args, capture_output=True, check=False)
        self.assertEqual(
            process.returncode, 0,
            process.stdout.decode("utf-8", "replace") + process.stderr.decode("utf-8", "replace"),
        )

    def test_signed_english_notice_is_discoverable_before_protected_payloads(self):
        self.assertEqual(list(self.entries)[:3], ["META-INF/MANIFEST.MF", HOST_NOTICE, HOST_NOTICE_SIGNATURE])
        expected = SCRIPTS.parent / "src" / "main" / "resources" / HOST_NOTICE
        self.assertEqual(self.entries[HOST_NOTICE], expected.read_bytes())
        self.assertIn(b"ARCARTX SUITE AUTHORIZATION AND SECURITY NOTICE", self.entries[HOST_NOTICE])
        self.assertTrue(self.entries[HOST_NOTICE].isascii())
        self.assertIn(f"AXS-Security-Notice: {HOST_NOTICE}\r\n".encode("ascii"), self.entries["META-INF/MANIFEST.MF"])
        self.assertEqual(len(self.entries[HOST_NOTICE_SIGNATURE]), 64)
        self.assertIn(HOST_NOTICE.encode("ascii"), self.entries[HOST_MANIFEST])
        self.assertIn(HOST_NOTICE_SIGNATURE.encode("ascii"), self.entries[HOST_MANIFEST])

    def test_signed_inventory_rejects_changed_missing_and_extra_files(self):
        for name, data in (
            (NATIVE, b"changed fixture"),
            ("plugin.yml", b"name: altered\n"),
            ("axs-build.json", b"{}"),
            ("META-INF/PROTECTION.MF", None),
            ("extra-resource.yml", b"a: 1"),
            (HOST_NOTICE_SIGNATURE, None),
        ):
            with self.subTest(name=name):
                entries = dict(self.entries)
                if data is None:
                    entries.pop(name)
                else:
                    entries[name] = data
                stream = io.BytesIO()
                self.write_archive(stream, entries)
                with zipfile.ZipFile(stream) as archive, self.assertRaises(ValueError):
                    verify_host_seal(archive, self.public_key)

    def test_module_packaging_keeps_its_outer_container_contract(self):
        root = Path(self.temp.name)
        source = root / "module-input.jar"
        output = root / "module-encrypted.jar"
        self.write_archive(source, {"Obfuscated.class": b"fixture module", "module.yml": b"id: fixture"})
        subprocess.run(
            [sys.executable, "-X", "utf8", "-B", str(SCRIPTS / "encrypt-jar.py"),
             "--input", str(source), "--output", str(output), "--native-dir", str(root),
             "--module-seed-b64", base64.b64encode(bytes(32)).decode("ascii"),
             "--encrypt-mode", "default-package", "--no-native", "--protection-level", "standard"],
            capture_output=True, check=True,
        )
        with zipfile.ZipFile(output) as archive:
            self.assertIn("ENCRYPTED/Obfuscated.enc", archive.namelist())
            self.assertIn("META-INF/PROTECTION.MF", archive.namelist())
            self.assertEqual(archive.read("module.yml"), b"id: fixture")
            self.assertNotIn(HOST_MANIFEST, archive.namelist())
            self.assertNotIn(HOST_SIGNATURE, archive.namelist())
            self.assertNotIn(HOST_NOTICE_SIGNATURE, archive.namelist())

    def test_valid_signature_cannot_legitimize_unexpected_plaintext_layout(self):
        releases = {k: v for k, v in self.entries.items() if k not in (HOST_MANIFEST, HOST_SIGNATURE)}
        for bad_name in (
            "xuanmo/arcartxsuite/bootstrap/Unexpected.class",
            "META-INF/versions/17/xuanmo/arcartxsuite/api/Unexpected.class",
            "native/axs-native.dll",
        ):
            with self.subTest(name=bad_name):
                bad = dict(releases, **{bad_name: b"fixture"})
                with self.assertRaises(ValueError):
                    make_manifest(bad.items())
                # Confirm the independent release verifier checks layout even after valid Ed25519 verification.
                lines = ["Bootstrap-Version: 2.0", f"Entry-Count: {len(bad)}", ""]
                lines.extend(f"{name} {hashlib.sha256(value).hexdigest()}" for name, value in sorted(bad.items()))
                manifest = ("\n".join(lines) + "\n").encode("utf-8")
                bad[HOST_MANIFEST] = manifest
                bad[HOST_SIGNATURE] = self.key.sign(hashlib.sha256(manifest).digest())
                stream = io.BytesIO()
                self.write_archive(stream, bad)
                with zipfile.ZipFile(stream) as archive, self.assertRaises(ValueError):
                    verify_host_seal(archive, self.public_key)


if __name__ == "__main__":
    unittest.main()

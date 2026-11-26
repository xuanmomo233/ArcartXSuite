import base64
import gzip
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch
import zipfile

from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

spec = importlib.util.spec_from_file_location("customer_export", Path(__file__).resolve().parents[1] / "export-customer-release.py")
customer_export = importlib.util.module_from_spec(spec)
spec.loader.exec_module(customer_export)


class CustomerExportTest(unittest.TestCase):
    def test_failed_second_platform_leaves_no_partial_delivery_and_can_retry(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            args = SimpleNamespace(commit="a" * 40, modules='["market"]',
                                   watermark_key=root / "watermark.pem", output=root / "delivery")
            from cryptography.hazmat.primitives import serialization
            args.watermark_key.write_bytes(Ed25519PrivateKey.generate().private_bytes(
                serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()))
            seed = base64.b64encode(os.urandom(32)).decode("ascii")
            values = {"AXS_EXPORT_ACCOUNT": "12345", "AXS_EXPORT_RECIPIENT": "customer-test",
                      "AXS_EXPORT_DELIVERY_ID": "delivery-test", "AXB_SIGN_SEED_B64": seed,
                      "AXS_VARIANT_REGISTRATION_KEY_B64": seed}

            def pack(output, platform, release, modules):
                (output / f"{platform}.zip").write_bytes(b"fixture")
                if platform == "linux-x86_64" and fail_platform:
                    raise ValueError("invalid Linux runtime")

            fail_platform = True
            with patch.dict(os.environ, values), patch.object(customer_export, "collect_modules", return_value=["fixture"]), \
                    patch.object(customer_export, "installer", side_effect=pack):
                with self.assertRaisesRegex(ValueError, "invalid Linux runtime"):
                    customer_export.export(args)
                self.assertFalse(args.output.exists())
                self.assertFalse(list(root.glob(".delivery-*")))

                fail_platform = False
                customer_export.export(args)
                self.assertEqual({path.name for path in args.output.iterdir()}, {
                    "windows-x86_64.zip", "linux-x86_64.zip", "register-module-variants.json",
                })
                with self.assertRaises(FileExistsError):
                    customer_export.export(args)

    def test_two_account_packages_share_release_but_have_distinct_keys_and_watermarks(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "gradle.properties").write_text("version=2.0.0\n", encoding="utf-8")
            commit = "1234567" + "9" * 33
            release = customer_export.release_name(commit, root / "gradle.properties")
            original = root / "market.jar"
            with zipfile.ZipFile(original, "w") as jar:
                jar.writestr("module.yml", "id: market\n")
                jar.writestr("ENCRYPTED/test.enc", b"test")
            watermark = customer_export.load_watermark()
            watermark_key = Ed25519PrivateKey.generate()
            signing_key = Ed25519PrivateKey.generate()
            registration_key = os.urandom(32)
            exported = []
            with patch.object(customer_export, "MODULES_DIR", root):
                for index in (1, 2):
                    account, recipient = f"12345678{index}", f"customer-{index}"
                    marked = root / f"{recipient}.jar"
                    stamp = watermark.stamp(original, marked, watermark_key, "market", release, recipient, recipient)
                    module_key = os.urandom(32)
                    iv = os.urandom(12)
                    axb = b"AXB!" + iv + AESGCM(module_key).encrypt(iv, gzip.compress(marked.read_bytes()), None)
                    (root / "market.axb").write_bytes(axb)
                    (root / "market.meta.json").write_text(json.dumps({
                        "moduleKey": base64.b64encode(module_key).decode("ascii"),
                    }), encoding="utf-8")
                    [envelope] = customer_export.collect_modules(
                        ["market"], release, account, recipient, recipient, watermark_key, signing_key, registration_key)
                    payload = base64.b64decode(envelope)
                    record = json.loads(AESGCM(registration_key).decrypt(payload[:12], payload[12:], customer_export.DOMAIN))
                    self.assertEqual(account, record["account"])
                    self.assertEqual(release, record["release"])
                    self.assertEqual(stamp["watermarkId"], record["watermarkId"])
                    self.assertEqual(hashlib.sha256(axb).hexdigest(), record["checksum"])
                    signing_key.public_key().verify(base64.b64decode(record["signature"]), axb)
                    self.assertEqual(module_key, base64.b64decode(record["moduleKey"]))
                    exported.append(record)
                with self.assertRaisesRegex(ValueError, "Watermark recipient mismatch"):
                    customer_export.collect_modules(
                        ["market"], release, "123456781", "customer-1", "customer-1", watermark_key, signing_key, registration_key)
            for field in ("checksum", "moduleKey", "watermarkId"):
                self.assertNotEqual(exported[0][field], exported[1][field])

    def test_installer_includes_exact_runtime_closure_and_no_registration_or_private_files(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            previous = Path.cwd()
            os.chdir(root)
            try:
                platform = "linux-x86_64"
                version = "2.0.0"
                host = Path("suite-release/hosts", f"axs-jar-encrypted-{platform}", "test-encrypted.jar")
                host.parent.mkdir(parents=True)
                data = b"test runtime"
                digest = hashlib.sha256(data).hexdigest()
                with zipfile.ZipFile(host, "w") as jar:
                    jar.writestr("axs-build.json", json.dumps({"release": version}))
                    jar.writestr("META-INF/axs-libraries.properties", f"artifact.count=1\nartifact.0.sha256={digest}\n")
                library = Path("suite-release/libraries", f"axs-runtime-libraries-{platform}", f"{digest}.jar")
                library.parent.mkdir(parents=True)
                library.write_bytes(data)
                Path("modules").mkdir()
                Path("modules/market.axb").write_bytes(b"module")
                Path("modules/market.meta.json").write_text("private data", encoding="utf-8")
                with patch.object(customer_export, "MODULES_DIR", Path("modules")):
                    customer_export.installer(root, platform, version, ["market"])
                with zipfile.ZipFile(root / f"ArcartX-Suite-{platform}.zip") as archive:
                    self.assertEqual(set(archive.namelist()), {
                        "plugins/ArcartX-Suite.jar", "plugins/ArcartX-Suite/modules/market.axb",
                        f"plugins/ArcartX-Suite/runtime-libraries/{digest}.jar", "INSTALL.txt",
                    })
            finally:
                os.chdir(previous)

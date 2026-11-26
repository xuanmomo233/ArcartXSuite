import gzip
import hashlib
import importlib.util
from pathlib import Path
import tempfile
import unittest
import zipfile

from cryptography.exceptions import InvalidSignature, InvalidTag
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey
from cryptography.hazmat.primitives.ciphers.aead import AESGCM

spec = importlib.util.spec_from_file_location("module_watermark", Path(__file__).resolve().parents[1] / "watermark-module.py")
watermark = importlib.util.module_from_spec(spec)
spec.loader.exec_module(watermark)


class WatermarkTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="axs-watermark-test-")
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.jar = self.root / "input.jar"
        with zipfile.ZipFile(self.jar, "w") as jar:
            jar.writestr("module.yml", "id: market\nmain: test.Main\n")
            jar.writestr("ENCRYPTED/a.enc", b"encrypted-class-fixture")
        self.key = Ed25519PrivateKey.generate()

    def stamp(self, recipient="customer-a", name="marked.jar"):
        output = self.root / name
        body = watermark.stamp(self.jar, output, self.key, "market", "test-release", recipient, "license-a")
        return output, body

    def rewrite(self, source, output, change):
        with zipfile.ZipFile(source) as jar:
            contents = {entry.filename: jar.read(entry) for entry in jar.infolist()}
        change(contents)
        with zipfile.ZipFile(output, "w") as jar:
            for name, value in contents.items():
                jar.writestr(name, value)

    def test_each_recipient_gets_a_distinct_verifiable_record_without_changing_the_input(self):
        original = hashlib.sha256(self.jar.read_bytes()).hexdigest()
        one, record_one = self.stamp()
        two, record_two = self.stamp("customer-b", "other.jar")
        self.assertEqual(watermark.inspect(one, self.key.public_key()), record_one)
        self.assertEqual(watermark.inspect(two, self.key.public_key()), record_two)
        self.assertNotEqual(record_one["watermarkId"], record_two["watermarkId"])
        self.assertNotEqual(one.read_bytes(), two.read_bytes())
        self.assertEqual(hashlib.sha256(self.jar.read_bytes()).hexdigest(), original)

    def test_wrong_issuer_and_modified_payload_or_recipient_are_rejected(self):
        source, _ = self.stamp()
        with self.assertRaises(InvalidSignature):
            watermark.inspect(source, Ed25519PrivateKey.generate().public_key())
        for name, edit, error in [
            ("payload.jar", lambda items: items.update({"ENCRYPTED/a.enc": b"tampered"}), ValueError),
            ("identity.jar", lambda items: items.update({watermark.MARK: items[watermark.MARK].replace(b"customer-a", b"customer-b")}), InvalidSignature),
            ("stripped.jar", lambda items: items.pop(watermark.SIGNATURE), ValueError),
        ]:
            output = self.root / name
            self.rewrite(source, output, edit)
            with self.assertRaises(error):
                watermark.inspect(output, self.key.public_key())

    def test_a_final_axb_can_be_inspected_in_memory_with_the_maintainers_module_key(self):
        source, record = self.stamp()
        key, iv = bytes(range(32)), bytes(range(12))
        axb = self.root / "market.axb"
        axb.write_bytes(b"TEST" + iv + AESGCM(key).encrypt(iv, gzip.compress(source.read_bytes()), None))
        self.assertEqual(watermark.inspect_axb(axb, key, self.key.public_key()), record)
        with self.assertRaises(InvalidTag):
            watermark.inspect_axb(axb, b"x" * 32, self.key.public_key())

    def test_refuses_double_stamping_overwriting_inputs_and_mismatched_module_ids(self):
        source, _ = self.stamp()
        with self.assertRaises(ValueError):
            watermark.stamp(source, self.root / "twice.jar", self.key, "market", "release", "recipient", "license")
        with self.assertRaises(ValueError):
            self.stamp()
        with self.assertRaises(ValueError):
            watermark.stamp(self.jar, self.jar, self.key, "market", "release", "recipient", "license", True)
        with self.assertRaises(ValueError):
            watermark.stamp(self.jar, self.root / "wrong.jar", self.key, "mail", "release", "recipient", "license")

    def test_key_generation_does_not_overwrite_existing_material(self):
        private, public = self.root / "private.pem", self.root / "public.pem"
        watermark.keygen(private, public)
        original = private.read_bytes()
        with self.assertRaises(ValueError):
            watermark.keygen(private, public)
        self.assertEqual(private.read_bytes(), original)


if __name__ == "__main__":
    unittest.main()

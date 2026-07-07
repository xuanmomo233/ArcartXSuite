import hashlib
import importlib.util
import tempfile
from pathlib import Path
import unittest


SCRIPT_PATH = Path(__file__).with_name("inject-self-hash.py")


def load_module():
    spec = importlib.util.spec_from_file_location("inject_self_hash", SCRIPT_PATH)
    module = importlib.util.module_from_spec(spec)
    assert spec.loader is not None
    spec.loader.exec_module(module)
    return module


class InjectSelfHashTest(unittest.TestCase):
    def setUp(self):
        self.mod = load_module()

    def make_blob(self, occurrences: int = 1) -> bytes:
        blob = bytearray(b"prefix-" + b"A" * 31)
        for _ in range(occurrences):
            blob.extend(self.mod.MAGIC)
            blob.extend(b"\xAA" * 32)
            blob.extend(b"-suffix-")
        return bytes(blob)

    def test_inject_is_idempotent_and_hashes_zeroed_slot(self):
        original = self.make_blob(occurrences=1)
        with tempfile.TemporaryDirectory() as tmpdir:
            path = Path(tmpdir) / "libaxs-native.so"
            path.write_bytes(original)

            patched1 = self.mod.inject_self_hash_file(path)
            patched2 = self.mod.inject_self_hash_file(path)

            self.assertEqual(patched1, patched2)
            self.assertEqual(path.read_bytes(), patched2)

            slot = self.mod.locate_slot(patched2)
            zeroed = bytearray(patched2)
            zeroed[slot:slot + self.mod.SLOT_SIZE] = b"\x00" * self.mod.SLOT_SIZE
            self.assertEqual(
                patched2[slot:slot + self.mod.SLOT_SIZE],
                hashlib.sha256(zeroed).digest(),
            )

    def test_missing_magic_errors(self):
        with self.assertRaises(ValueError):
            self.mod.locate_slot(b"no magic here")

    def test_duplicate_magic_errors(self):
        with self.assertRaises(ValueError):
            self.mod.locate_slot(self.make_blob(occurrences=2))


if __name__ == "__main__":
    unittest.main()

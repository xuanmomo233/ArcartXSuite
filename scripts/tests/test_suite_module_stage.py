import base64
import importlib.util
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

from cryptography.exceptions import InvalidTag


SCRIPT = Path(__file__).resolve().parents[1] / "suite-module-stage.py"
SPEC = importlib.util.spec_from_file_location("suite_module_stage", SCRIPT)
STAGING = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(STAGING)


class SuiteModuleStageTest(unittest.TestCase):
    def setUp(self):
        working = TemporaryDirectory()
        self.addCleanup(working.cleanup)
        root = Path(working.name)
        self.source = root / "source"
        self.transfer = root / "transfer"
        self.destination = root / "destination"
        self.source.mkdir()
        self.selection = ["market", "mail"]
        self.secret = "fixture-staging-secret"
        self.context = STAGING.staging_context("4321", "a" * 40, "customer")
        self.key = base64.b64encode(bytes(range(32))).decode("ascii")
        self.iv = base64.b64encode(bytes(range(12))).decode("ascii")
        for module in self.selection:
            data = f"fixture AXB {module}".encode("ascii")
            (self.source / f"{module}.axb").write_bytes(data)
            metadata = {"moduleKey": self.key, "moduleIv": self.iv, "size": len(data)}
            (self.source / f"{module}.meta.json").write_text(json.dumps(metadata), encoding="utf-8")

    def seal(self):
        STAGING.seal_modules(self.source, self.transfer, self.selection, self.secret, self.context)

    def open(self, selection=None, context=None):
        STAGING.open_modules(self.transfer, self.destination,
                             self.selection if selection is None else selection, self.secret,
                             self.context if context is None else context)

    def test_verified_module_inputs_cross_jobs_with_only_sealed_metadata(self):
        self.seal()
        self.assertEqual({"market.axb", "mail.axb", "metadata.enc"},
                         {path.name for path in self.transfer.iterdir()})
        self.assertNotIn(self.key.encode("ascii"), (self.transfer / "metadata.enc").read_bytes())
        self.open()
        for module in self.selection:
            name = f"{module}.axb"
            self.assertEqual((self.source / name).read_bytes(), (self.destination / name).read_bytes())
            name = f"{module}.meta.json"
            self.assertEqual(json.loads((self.source / name).read_text(encoding="utf-8")),
                             json.loads((self.destination / name).read_text(encoding="utf-8")))

    def test_rejects_tampered_module_without_restoring_partial_outputs(self):
        self.seal()
        axb = self.transfer / "mail.axb"
        axb.write_bytes(b"!" + axb.read_bytes()[1:])
        with self.assertRaisesRegex(ValueError, "Staged content changed"):
            self.open()
        self.assertFalse(self.destination.exists())

    def test_rejects_changed_release_scope(self):
        self.seal()
        with self.assertRaisesRegex(ValueError, "workflow selection"):
            self.open(selection=["market"])
        with self.assertRaises(InvalidTag):
            self.open(context=STAGING.staging_context("4322", "a" * 40, "customer"))
        with self.assertRaises(InvalidTag):
            self.open(context=STAGING.staging_context("4321", "a" * 40, "free"))
        self.assertFalse(self.destination.exists())

    def test_rejects_tampered_sealed_metadata(self):
        self.seal()
        seal = self.transfer / "metadata.enc"
        data = bytearray(seal.read_bytes())
        data[-1] ^= 1
        seal.write_bytes(data)
        with self.assertRaises(InvalidTag):
            self.open()
        self.assertFalse(self.destination.exists())


if __name__ == "__main__":
    unittest.main()

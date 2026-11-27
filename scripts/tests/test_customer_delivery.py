import base64
import importlib.util
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
from unittest.mock import patch
from zipfile import ZipFile


SCRIPTS = Path(__file__).resolve().parents[1]


def load_script(name, filename):
    spec = importlib.util.spec_from_file_location(name, SCRIPTS / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


bundle = load_script("bundle", "bundle-customer-delivery.py")
artifacts = load_script("artifacts", "customer-delivery-artifacts.py")


class CustomerDeliveryTest(unittest.TestCase):
    def setUp(self):
        folder = TemporaryDirectory()
        self.addCleanup(folder.cleanup)
        self.root = Path(folder.name)
        self.installers = self.root / "installers"
        self.installers.mkdir()
        for name in bundle.INSTALLERS:
            self.write_installer(name, "market")
        self.registration = self.root / bundle.REGISTRATION
        self.record = {
            "format": "axs-module-variants/v1",
            "records": [base64.b64encode(bytes(32)).decode("ascii")],
        }
        self.registration.write_text(json.dumps(self.record), encoding="utf-8")
        self.proxies = self.root / "proxy"
        for platform in ("Bungee", "Velocity"):
            path = self.proxies / platform / f"ArcartXSuite-Proxy-{platform}-3.3.7.jar"
            path.parent.mkdir(parents=True)
            with ZipFile(path, "w") as jar:
                jar.writestr("Plugin.class", b"proxy")
        self.output = self.root / "delivery"
        self.account = "123456789"

    def write_installer(self, name, module, extra=None):
        with ZipFile(self.installers / name, "w") as archive:
            archive.writestr("INSTALL.txt", b"instructions")
            archive.writestr("plugins/ArcartX-Suite.jar", b"signed core")
            archive.writestr(f"plugins/ArcartX-Suite/modules/{module}.axb", b"signed module")
            if extra:
                archive.writestr(extra, b"unexpected input")

    def test_delivers_unchanged_files_with_the_registration_separate(self):
        (self.installers / "release-private.pem").write_text("excluded key", encoding="utf-8")
        bundle.bundle(self.installers, self.registration, self.proxies, self.output, self.account)
        buyer_zip = self.output / f"{self.account}.zip"
        self.assertEqual(
            {buyer_zip, self.output / bundle.REGISTRATION},
            set(self.output.iterdir()),
        )
        files = [self.installers / name for name in bundle.INSTALLERS] + list(self.proxies.rglob("*.jar"))
        with ZipFile(buyer_zip) as archive:
            self.assertCountEqual(archive.namelist(), [file.name for file in files])
            for file in files:
                self.assertEqual(file.read_bytes(), archive.read(file.name))
        self.assertEqual(self.registration.read_bytes(),
                         (self.output / bundle.REGISTRATION).read_bytes())

    def test_requires_both_proxy_jars(self):
        with self.assertRaisesRegex(ValueError, "Expected one Velocity proxy JAR"):
            bundle.bundle(self.installers, self.registration, self.proxies / "Bungee", self.output, self.account)
        self.assertFalse(self.output.exists())

    def test_rejects_an_invalid_account_in_the_zip_name(self):
        for account in ("../12345", "1234", "12345\n", "１２３４５"):
            with self.subTest(account=account), self.assertRaisesRegex(ValueError, "numeric QQ account"):
                bundle.bundle(self.installers, self.registration, self.proxies, self.output, account)
        self.assertFalse(self.output.exists())

    def test_mismatched_installers_and_unexpected_files_are_rejected(self):
        self.write_installer(bundle.INSTALLERS[1], "chat")
        with self.assertRaisesRegex(ValueError, "different modules"):
            bundle.bundle(self.installers, self.registration, self.proxies, self.output, self.account)
        self.write_installer(bundle.INSTALLERS[1], "market", "plugins/ArcartX-Suite/config.yml")
        with self.assertRaisesRegex(ValueError, "Unexpected file"):
            bundle.bundle(self.installers, self.registration, self.proxies, self.output, self.account)
        self.assertFalse(self.output.exists())

    def test_registration_must_match_module_count_and_have_only_sealed_records(self):
        self.record["records"].append(self.record["records"][0])
        self.registration.write_text(json.dumps(self.record), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "module count"):
            bundle.bundle(self.installers, self.registration, self.proxies, self.output, self.account)
        self.record["records"] = ["not an encrypted record"]
        self.registration.write_text(json.dumps(self.record), encoding="utf-8")
        with self.assertRaises(ValueError):
            bundle.bundle(self.installers, self.registration, self.proxies, self.output, self.account)

    def test_prepare_forwards_the_workflow_account_to_the_bundler(self):
        candidates = CustomerArtifactSelectionTest.artifacts(
            "axs-customer-installers-attempt-1", "axs-customer-registration-attempt-1",
            "axs-customer-proxy-attempt-1",
        )
        with (
            patch.object(artifacts, "artifacts", return_value=candidates),
            patch.object(artifacts.subprocess, "run") as run,
            patch.dict("os.environ", {"GITHUB_OUTPUT": str(self.root / "github-output")}),
        ):
            artifacts.prepare("suite/repo", "1", self.output, self.account)
        self.assertEqual(run.call_args.args[0][-2:], ["--account", self.account])


class CustomerArtifactSelectionTest(unittest.TestCase):
    @staticmethod
    def artifacts(*names):
        return [{"id": index + 1, "name": name, "expired": False} for index, name in enumerate(names)]

    def test_pair_stays_on_one_attempt_and_a_successful_proxy_can_be_reused(self):
        candidates = self.artifacts(
            "axs-customer-installers-attempt-1", "axs-customer-registration-attempt-1",
            "axs-customer-installers-attempt-10", "axs-customer-registration-attempt-10",
            "axs-customer-installers-attempt-11", "axs-customer-proxy-attempt-1",
        )
        self.assertEqual(("axs-customer-delivery-attempt-10", (
            "axs-customer-installers-attempt-10", "axs-customer-registration-attempt-10",
            "axs-customer-proxy-attempt-1",
        )), artifacts.select_delivery(candidates))

    def test_resume_does_not_require_inputs_removed_by_partial_cleanup(self):
        candidates = self.artifacts(
            "axs-customer-installers-attempt-2", "axs-customer-registration-attempt-2",
            "axs-customer-delivery-attempt-3", "axs-customer-registration-attempt-3",
        )
        self.assertEqual(("axs-customer-delivery-attempt-3", ()), artifacts.select_delivery(candidates))

    def test_incomplete_pair_never_combines_separate_attempts(self):
        candidates = self.artifacts(
            "axs-customer-installers-attempt-2", "axs-customer-registration-attempt-3",
            "axs-customer-proxy-attempt-3",
        )
        with self.assertRaisesRegex(ValueError, "matching installers and registration"):
            artifacts.select_delivery(candidates)

    def test_prune_requires_final_upload_and_preserves_unrelated_artifacts(self):
        name = "axs-customer-delivery-attempt-2"
        candidates = self.artifacts(
            "axs-customer-installers-attempt-2", "native-linux-enc", "axs-runtime-libraries-windows-x86_64",
            "axs-customer-delivery-attempt-1", "suite-module-stage", "suite-published",
            "axs-customer-installers-unexpected", "native-public-keys", name,
        )
        candidates[-2]["expired"] = True
        self.assertEqual([1, 2, 3, 4, 5], list(artifacts.cleanup_ids(candidates, name)))
        with self.assertRaisesRegex(ValueError, "before removing"):
            list(artifacts.cleanup_ids(candidates[:-1], name))


if __name__ == "__main__":
    unittest.main()

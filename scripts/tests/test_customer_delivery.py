import base64
import importlib.util
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest
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

    def write_installer(self, name, module, extra=None):
        with ZipFile(self.installers / name, "w") as archive:
            archive.writestr("INSTALL.txt", b"instructions")
            archive.writestr("plugins/ArcartX-Suite.jar", b"signed core")
            archive.writestr(f"plugins/ArcartX-Suite/modules/{module}.axb", b"signed module")
            if extra:
                archive.writestr(extra, b"unexpected input")

    def test_delivers_unchanged_files_with_the_registration_separate(self):
        (self.installers / "release-private.pem").write_text("excluded key", encoding="utf-8")
        bundle.bundle(self.installers, self.registration, self.proxies, self.output)
        self.assertEqual(
            {"README.txt", "admin-only/register-module-variants.json",
             *{f"customer/installers/{name}" for name in bundle.INSTALLERS},
             *{f"customer/optional-proxy/{jar.name}" for jar in self.proxies.rglob("*.jar")}},
            {member.relative_to(self.output).as_posix() for member in self.output.rglob("*") if member.is_file()},
        )
        for name in bundle.INSTALLERS:
            self.assertEqual((self.installers / name).read_bytes(),
                             (self.output / "customer" / "installers" / name).read_bytes())
        self.assertEqual(self.registration.read_bytes(),
                         (self.output / "admin-only" / bundle.REGISTRATION).read_bytes())

    def test_proxy_is_optional_when_packaging_from_existing_files(self):
        bundle.bundle(self.installers, self.registration, None, self.output)
        self.assertFalse((self.output / "customer" / "optional-proxy").exists())

    def test_mismatched_installers_and_unexpected_files_are_rejected(self):
        self.write_installer(bundle.INSTALLERS[1], "chat")
        with self.assertRaisesRegex(ValueError, "different modules"):
            bundle.bundle(self.installers, self.registration, self.proxies, self.output)
        self.write_installer(bundle.INSTALLERS[1], "market", "plugins/ArcartX-Suite/config.yml")
        with self.assertRaisesRegex(ValueError, "Unexpected file"):
            bundle.bundle(self.installers, self.registration, self.proxies, self.output)
        self.assertFalse(self.output.exists())

    def test_registration_must_match_module_count_and_have_only_sealed_records(self):
        self.record["records"].append(self.record["records"][0])
        self.registration.write_text(json.dumps(self.record), encoding="utf-8")
        with self.assertRaisesRegex(ValueError, "module count"):
            bundle.bundle(self.installers, self.registration, self.proxies, self.output)
        self.record["records"] = ["not an encrypted record"]
        self.registration.write_text(json.dumps(self.record), encoding="utf-8")
        with self.assertRaises(ValueError):
            bundle.bundle(self.installers, self.registration, self.proxies, self.output)


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

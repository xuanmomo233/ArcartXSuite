import importlib.util
import io
from pathlib import Path
import unittest
import zipfile

spec = importlib.util.spec_from_file_location("verify_protection", Path(__file__).parents[1] / "verify-protection.py")
verifier = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verifier)


class RuntimeTargetTest(unittest.TestCase):
    def inspect(self, text):
        data = io.BytesIO()
        with zipfile.ZipFile(data, "w") as jar:
            if text is not None:
                jar.writestr("META-INF/axs-runtime.properties", text)
        data.seek(0)
        with zipfile.ZipFile(data) as jar:
            return verifier.runtime_target_native(jar)

    def test_universal_requires_both_native_platforms(self):
        self.assertEqual(len(self.inspect(None)[1]), 2)
        self.assertEqual(len(self.inspect("format=1\ntarget=universal\nos=windows,linux\narch=any\n")[1]), 2)

    def test_precise_targets_require_the_matching_platform(self):
        for os_name, library in (("windows", "axs-native.enc"), ("linux", "libaxs-native.enc")):
            target, native = self.inspect(f"format=1\ntarget={os_name}-x86_64\nos={os_name}\narch=x86_64\n")
            self.assertEqual(native, {"native/" + library})

    def test_rejects_unknown_malformed_and_duplicate_metadata(self):
        for text in ("", "format=1\ntarget=typo\n", "format=1\ntarget=linux-x86_64\nos=windows\narch=x86_64\n",
                     "format=1\ntarget=universal\nos=windows,linux\narch=any\narch=any\n"):
            with self.assertRaises(ValueError):
                self.inspect(text)


if __name__ == "__main__":
    unittest.main()

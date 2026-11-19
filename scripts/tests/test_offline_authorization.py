from datetime import datetime, timedelta, timezone
import hashlib
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
    HOST_MANIFEST, HOST_NOTICE, HOST_NOTICE_SIGNATURE, HOST_SIGNATURE,
    PLAIN_HOST_CLASSES, make_manifest, notice_digest,
)
from suite_authorization import encode_b64, grant_message


class OfflineAuthorizationTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        directory = tempfile.TemporaryDirectory()
        cls.addClassCleanup(directory.cleanup)
        cls.root = Path(directory.name)
        cls.release = cls.create_key("release")
        cls.maintainer = cls.create_key("maintainer")
        cls.create_key("reviewer")
        cls.other = cls.create_key("other")
        cls.jar = cls.root / "suite-signed-fixture.jar"
        cls.grant = cls.root / "authorization.json"
        cls.challenge = cls.root / "active-challenge.txt"
        cls.proof = cls.root / "proof.txt"
        cls.subject = "reviewer:测试"
        cls.action = "static-review"

        cls.payload = {name.replace(".", "/") + ".class": b"fixture" for name in PLAIN_HOST_CLASSES}
        cls.payload.update({
            "META-INF/MANIFEST.MF": b"Manifest-Version: 1.0\r\n\r\n",
            "META-INF/PROTECTION.MF": b"Protection-Version: fixture\n",
            "ENCRYPTED/private-fixture.enc": b"encrypted test bytes",
            "native/axs-native.enc": b"wrapped test bytes",
            "plugin.yml": b"name: Suite-fixture\n",
            HOST_NOTICE: (SCRIPTS.parent / "src" / "main" / "resources" / HOST_NOTICE).read_bytes(),
        })
        cls.payload[HOST_NOTICE_SIGNATURE] = cls.release.sign(notice_digest(cls.payload[HOST_NOTICE]))
        cls.write_release(cls.jar, cls.payload)

        expires = (datetime.now(timezone.utc) + timedelta(days=1)).strftime("%Y-%m-%dT%H:%M:%SZ")
        cls.run_ok(
            "issue", "--jar", cls.jar, "--release-public-key", cls.root / "release-public.pem",
            "--subject", cls.subject, "--subject-public-key", cls.root / "reviewer-public.pem",
            "--maintainer-private-key", cls.root / "maintainer-private.pem", "--allow", cls.action,
            "--allow", "verify-release", "--expires-at", expires, "--output", cls.grant,
        )
        cls.run_ok("challenge", "--output", cls.challenge)
        cls.run_ok(
            "prove", "--grant", cls.grant, "--challenge", cls.challenge, "--action", cls.action,
            "--subject-private-key", cls.root / "reviewer-private.pem", "--output", cls.proof,
        )

    @classmethod
    def create_key(cls, name):
        key = Ed25519PrivateKey.generate()
        (cls.root / f"{name}-private.pem").write_bytes(key.private_bytes(
            serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8, serialization.NoEncryption()
        ))
        (cls.root / f"{name}-public.pem").write_bytes(key.public_key().public_bytes(
            serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
        ))
        return key

    @classmethod
    def write_release(cls, target, payload):
        inventory = make_manifest(payload.items())
        signature = cls.release.sign(hashlib.sha256(inventory).digest())
        with zipfile.ZipFile(target, "w", zipfile.ZIP_DEFLATED) as archive:
            for name, data in payload.items():
                archive.writestr(name, data)
            archive.writestr(HOST_MANIFEST, inventory)
            archive.writestr(HOST_SIGNATURE, signature)

    @staticmethod
    def run_tool(*args):
        return subprocess.run(
            [sys.executable, "-X", "utf8", "-B", str(SCRIPTS / "suite_authorization.py"), *map(str, args)],
            capture_output=True, encoding="utf-8", check=False,
        )

    @classmethod
    def run_ok(cls, *args):
        result = cls.run_tool(*args)
        if result.returncode:
            raise AssertionError(result.stdout + result.stderr)
        return result

    def verify(self, **overrides):
        options = {
            "jar": self.jar,
            "release-public-key": self.root / "release-public.pem",
            "maintainer-public-key": self.root / "maintainer-public.pem",
            "subject": self.subject, "action": self.action, "grant": self.grant,
            "challenge": self.challenge, "proof": self.proof,
        }
        args = [item for key, value in (options | overrides).items() for item in (f"--{key}", value)]
        return self.run_tool("verify", "--json", *args)

    def assert_unconfirmed(self, *, reason=None, **overrides):
        result = self.verify(**overrides)
        self.assertEqual(result.returncode, 1, result.stdout + result.stderr)
        response = json.loads(result.stdout)
        self.assertEqual(response["status"], "unconfirmed")
        self.assertEqual(response["message"], "授权未确认")
        if reason is not None:
            self.assertIn(reason, response["reason"])

    def test_english_notice_preflight_and_authorization_round_trip(self):
        noticed = self.run_ok("notice", "--jar", self.jar, "--release-public-key", self.root / "release-public.pem", "--json")
        result = self.verify()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)
        report = json.loads(result.stdout)
        metadata = json.loads(noticed.stdout)
        self.assertEqual(metadata["status"], "verified")
        self.assertTrue(metadata["notice"].isascii())
        self.assertEqual(metadata["notice"], self.payload[HOST_NOTICE].decode("ascii"))
        self.assertEqual(report["status"], "confirmed")
        self.assertEqual(report["notice"], metadata["notice"])
        self.assertEqual(report["subject"], self.subject)
        self.assertEqual(report["action"], self.action)
        self.assertEqual(report["jar_sha256"], metadata["jar_sha256"])
        self.assertEqual(report["jar_sha256"], hashlib.sha256(self.jar.read_bytes()).hexdigest())

    def test_untrusted_publisher_or_maintainer_and_altered_grant_fail(self):
        self.assert_unconfirmed(**{"release-public-key": self.root / "other-public.pem"})
        self.assert_unconfirmed(reason="维护者签名", **{"maintainer-public-key": self.root / "other-public.pem"})
        altered = self.root / "altered.json"
        envelope = json.loads(self.grant.read_bytes())
        envelope["grant"]["subject"] = "another-reviewer"
        altered.write_text(json.dumps(envelope), encoding="utf-8")
        self.assert_unconfirmed(reason="维护者签名", grant=altered)

    def test_invalid_notice_signature_rejects_otherwise_valid_host_inventory(self):
        invalid = self.root / "invalid-notice-signature.jar"
        bad = dict(self.payload)
        bad[HOST_NOTICE_SIGNATURE] = self.other.sign(notice_digest(bad[HOST_NOTICE]))
        self.write_release(invalid, bad)
        self.assert_unconfirmed(reason="内置声明无法验证", jar=invalid)
        result = self.run_tool("notice", "--jar", invalid, "--release-public-key", self.root / "release-public.pem", "--json")
        self.assertEqual(result.returncode, 1)
        self.assertNotIn("notice", json.loads(result.stdout))

    def test_jar_hash_binds_exact_bytes_even_after_valid_repackaging(self):
        repacked = self.root / "repacked.jar"
        self.write_release(repacked, dict(reversed(self.payload.items())))
        self.run_ok("notice", "--jar", repacked, "--release-public-key", self.root / "release-public.pem")
        self.assert_unconfirmed(reason="SHA-256", jar=repacked)

    def test_requester_and_proof_are_bound_to_subject_action_and_fresh_challenge(self):
        self.assert_unconfirmed(reason="请求人", subject="another-reviewer")
        self.assert_unconfirmed(reason="授权范围", action="other-action")
        self.assert_unconfirmed(reason="身份挑战", action="verify-release")
        new_challenge = self.root / "next-challenge.txt"
        self.run_ok("challenge", "--output", new_challenge)
        self.assertNotEqual(new_challenge.read_bytes(), self.challenge.read_bytes())
        self.assert_unconfirmed(reason="身份挑战", challenge=new_challenge)
        self.assert_unconfirmed(proof=self.root / "missing-proof.txt")

        result = self.run_tool(
            "prove", "--grant", self.grant, "--challenge", self.challenge, "--action", self.action,
            "--subject-private-key", self.root / "other-private.pem", "--output", self.root / "wrong-proof.txt",
        )
        self.assertEqual(result.returncode, 1)
        self.assertFalse((self.root / "wrong-proof.txt").exists())

    def test_valid_signature_with_expired_or_future_grant_is_unconfirmed(self):
        grant = json.loads(self.grant.read_bytes())["grant"]
        for start, end in (("2001-01-01T00:00:00Z", "2001-01-02T00:00:00Z"),
                           ("2099-01-01T00:00:00Z", "2099-01-02T00:00:00Z")):
            with self.subTest(start=start):
                dated = dict(grant, valid_from=start, expires_at=end)
                path = self.root / f"authorization-{start[:4]}.json"
                path.write_text(json.dumps({
                    "grant": dated, "signature": encode_b64(self.maintainer.sign(grant_message(dated))),
                }), encoding="utf-8")
                self.assert_unconfirmed(reason="不在有效期", grant=path)


if __name__ == "__main__":
    unittest.main()

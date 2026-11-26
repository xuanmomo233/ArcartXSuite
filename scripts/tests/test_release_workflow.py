import base64
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
from types import SimpleNamespace
import unittest
from unittest.mock import patch

import yaml


WORKFLOW = Path(__file__).resolve().parents[2] / ".github/workflows/build-encrypted.yml"


class CloudActivationTest(unittest.TestCase):
    def run_publication(self, failure=None, rollback_failure=None, *, announce=False,
                        invalid_announcement=False, advance_main_after_activation=False, stale_origin=None):
        script = WORKFLOW.parents[2] / "scripts/publish-suite.py"
        spec = importlib.util.spec_from_file_location("suite_publisher", script)
        publisher = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(publisher)
        commit = "a" * 40
        mirror = "https://mirror.example.test"
        self.calls = []
        published_origins = set()

        def post(origin, path, _fields, *, payload):
            name = "primary" if origin == publisher.PRIMARY else "mirror"
            action = payload["action"]
            self.calls.append((name, action))
            if (name, action) in (failure, rollback_failure):
                raise RuntimeError("fixture request failed")
            if action == "activate":
                published_origins.add(name)
            if action == "withdraw":
                active = name in published_origins and name != stale_origin
                return {"ok": True, "state": "published" if active else "prepared", "version": payload["version"]}
            if action == "announce":
                self.assertEqual("/v1/ci/suite-release/current", path)
                self.assertEqual(["market"], payload["modules"])
                self.assertEqual(commit, payload["commit"])
                return {"ok": True, "state": "published" if invalid_announcement else "announced",
                        "version": payload["version"]}
            return {"ok": True}

        with tempfile.TemporaryDirectory() as tmp:
            release = Path(tmp) / "activation.json"
            release.write_text(json.dumps({
                "commit": commit, "version": "2.0.0-suite.42.1-ci.aaaaaaa",
                "modules": ["market"], "mirror": mirror,
            }), encoding="utf-8")
            environment = {"GITHUB_SHA": commit, "GITHUB_RUN_ID": "42", "FLARE_BASE_URL": mirror}
            git = SimpleNamespace(check_output=lambda *_args, **_kwargs: commit + "\trefs/heads/main\n")
            with patch.object(publisher, "os", SimpleNamespace(environ=environment)), \
                    patch.object(publisher, "ACTIVATION", release), \
                    patch.object(publisher, "post", side_effect=post), \
                    patch.object(publisher, "subprocess", git):
                publisher.activate_release()
                if advance_main_after_activation:
                    git.check_output = lambda *_args, **_kwargs: ("b" * 40) + "\trefs/heads/main\n"
                if announce:
                    publisher.announce_release()

    def test_later_activation_failure_rolls_back_both_origins(self):
        with self.assertRaisesRegex(RuntimeError, "both origins confirmed rollback"):
            self.run_publication(failure=("mirror", "activate"), announce=True)
        self.assertEqual([
            ("primary", "withdraw"), ("mirror", "withdraw"),
            ("primary", "activate"), ("mirror", "activate"),
            ("mirror", "rollback"), ("primary", "rollback"),
        ], self.calls)

    def test_failed_preflight_does_not_change_active_versions(self):
        with self.assertRaisesRegex(RuntimeError, "fixture request failed"):
            self.run_publication(failure=("mirror", "withdraw"))
        self.assertEqual([("primary", "withdraw"), ("mirror", "withdraw")], self.calls)

    def test_unconfirmed_rollback_still_attempts_the_other_origin(self):
        with self.assertRaisesRegex(RuntimeError, "rollback remains unconfirmed on mirror.example.test"):
            self.run_publication(failure=("mirror", "activate"), rollback_failure=("mirror", "rollback"))
        self.assertEqual([("mirror", "rollback"), ("primary", "rollback")], self.calls[-2:])

    def test_both_origins_activate_before_primary_announces(self):
        self.run_publication(announce=True)
        self.assertEqual([
            ("primary", "withdraw"), ("mirror", "withdraw"),
            ("primary", "activate"), ("mirror", "activate"),
            ("primary", "withdraw"), ("mirror", "withdraw"), ("primary", "announce"),
        ], self.calls)

    def test_announcement_requires_readback_from_both_origins(self):
        with self.assertRaisesRegex(RuntimeError, "mirror.example.test did not confirm the active Suite release"):
            self.run_publication(announce=True, stale_origin="mirror")
        self.assertEqual([("primary", "withdraw"), ("mirror", "withdraw")], self.calls[-2:])
        self.assertNotIn(("primary", "announce"), self.calls)

    def test_announcement_failure_does_not_roll_back_a_published_release(self):
        with self.assertRaisesRegex(RuntimeError, "did not confirm the bot announcement"):
            self.run_publication(announce=True, invalid_announcement=True)
        self.assertEqual(("primary", "announce"), self.calls[-1])
        self.assertNotIn("rollback", [action for _, action in self.calls])

    def test_primary_checks_the_active_release_after_source_main_advances(self):
        self.run_publication(announce=True, advance_main_after_activation=True)
        self.assertEqual(("primary", "announce"), self.calls[-1])


class BuildSeedsTest(unittest.TestCase):
    SCRIPT = WORKFLOW.parents[2] / "scripts/run-with-build-seeds.py"
    NAMES = ("AXS_SEED_PARTS_B64", "AXB_SIGN_SEED_B64", "AXB_RESP_SIGN_SEED_B64")
    FIXTURE_SEED = base64.b64encode(bytes(32)).decode("ascii")

    def invoke(self, event, ref, validation="true", attempt="1"):
        env = dict(os.environ, AXS_CI_VALIDATION=validation, GITHUB_EVENT_NAME=event,
                   GITHUB_REF=ref, GITHUB_REPOSITORY="fixture/test", GITHUB_RUN_ID="431",
                   GITHUB_RUN_ATTEMPT=attempt, GITHUB_SHA="a" * 40)
        env.update(dict.fromkeys(self.NAMES, self.FIXTURE_SEED))
        probe = f"import json, os; print(json.dumps([os.environ[name] for name in {self.NAMES!r}]))"
        return subprocess.run([sys.executable, str(self.SCRIPT), "-c", probe],
                              env=env, encoding="utf-8", capture_output=True, timeout=10)

    def seeds(self, result):
        self.assertEqual(0, result.returncode, result.stderr)
        return json.loads(result.stdout.splitlines()[-1])

    def test_paused_main_push_uses_validation_seeds_and_supports_retries(self):
        first = self.seeds(self.invoke("push", "refs/heads/main"))
        retry = self.seeds(self.invoke("push", "refs/heads/main", attempt="2"))
        self.assertEqual(first, retry)
        self.assertEqual(len(self.NAMES), len(set(first)))
        self.assertNotIn(self.FIXTURE_SEED, first)
        self.assertTrue(all(len(base64.b64decode(value, validate=True)) == 32 for value in first))

    def test_manual_validation_still_uses_validation_seeds(self):
        result = self.seeds(self.invoke("workflow_dispatch", "refs/heads/codex/verify"))
        self.assertNotIn(self.FIXTURE_SEED, result)

    def test_other_validation_triggers_are_rejected_and_production_seeds_are_preserved(self):
        for event, ref in (("push", "refs/heads/feature"), ("pull_request", "refs/pull/42/merge")):
            with self.subTest(event=event):
                self.assertNotEqual(0, self.invoke(event, ref).returncode)
        production = self.seeds(self.invoke("push", "refs/heads/main", validation="false"))
        self.assertEqual([self.FIXTURE_SEED] * len(self.NAMES), production)


class ReleaseWorkflowTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.workflow = yaml.load(WORKFLOW.read_text(encoding="utf-8"), Loader=yaml.BaseLoader)
        steps = cls.workflow["jobs"]["detect-changes"]["steps"]
        cls.detect = next(step["run"] for step in steps if step.get("id") == "changes")

    def setUp(self):
        workspace = tempfile.TemporaryDirectory()
        self.addCleanup(workspace.cleanup)
        self.repo = Path(workspace.name)
        self.git("init", "--quiet")
        self.git("config", "user.email", "test@example.invalid")
        self.git("config", "user.name", "Release Test")
        self.commit("modules/market/build.gradle.kts", "initial")
        self.baseline = self.git("rev-parse", "HEAD")

    def git(self, *args):
        return subprocess.run(["git", *args], cwd=self.repo, check=True, capture_output=True, text=True).stdout.strip()

    def commit(self, path, text):
        file = self.repo / path
        file.parent.mkdir(parents=True, exist_ok=True)
        file.write_text(text, encoding="utf-8")
        self.git("add", "--", path)
        self.git("commit", "--quiet", "-m", text)

    @staticmethod
    def artifact(sha, created, branch="main", expired=False):
        return {"created_at": created, "expired": expired, "workflow_run": {"head_branch": branch, "head_sha": sha}}

    def detect_scopes(self, suite, proxy, event="push", **inputs):
        def fixture(name, artifacts):
            (self.repo / name).write_text(json.dumps({"artifacts": artifacts}), encoding="utf-8")

        fixture("suite.json", suite)
        fixture("proxy.json", proxy)
        stub = """gh() {
          case "$2" in
            *'?name=suite-published&per_page=100') cat suite.json ;;
            *'?name=suite-proxy-published&per_page=100') cat proxy.json ;;
            *) return 2 ;;
          esac
        }
        """
        output = self.repo / "github-output"
        output.unlink(missing_ok=True)
        env = dict(os.environ, GITHUB_REPOSITORY="fixture/test", GITHUB_EVENT_NAME=event,
                   GITHUB_OUTPUT=output.as_posix(), RUNNER_TEMP=self.repo.as_posix(),
                   AXS_EXPORT_ONLY="false", VALIDATION_ONLY="false",
                   AXS_MANUAL_SUITE_PUBLISH="false", PUBLISH_SUITE_REQUESTED="false")
        env.update(inputs)
        run = subprocess.run([os.environ.get("AXS_TEST_BASH", "bash"), "-s"], cwd=self.repo,
                             env=env, input=stub + "\n" + self.detect, capture_output=True,
                             encoding="utf-8", timeout=20)
        self.assertEqual(0, run.returncode, run.stdout + run.stderr)
        return dict(line.split("=", 1) for line in output.read_text(encoding="utf-8").splitlines())

    def test_docs_push_recovers_product_changes_after_paused_runs(self):
        self.commit("src/main/code.txt", "paused product change")
        paused = self.git("rev-parse", "HEAD")
        self.commit("README.md", "docs only")
        baseline = self.artifact(self.baseline, "2026-11-20T00:00:00Z")
        newer = self.artifact(paused, "2026-11-21T00:00:00Z", branch="feature")
        expired = self.artifact(paused, "2026-11-21T00:00:00Z", expired=True)
        flags = self.detect_scopes([baseline, newer, expired], [baseline])
        self.assertEqual("true", flags["core_changed"])
        self.assertEqual(["market"], json.loads(flags["changed_modules"]))
        self.assertEqual("false", flags["proxy_changed"])

    def test_proxy_and_suite_publication_baselines_are_independent(self):
        self.commit("proxy/velocity/code.txt", "proxy change")
        published_proxy = self.artifact(self.git("rev-parse", "HEAD"), "2026-11-21T00:00:00Z")
        published_suite = self.artifact(self.baseline, "2026-11-20T00:00:00Z")
        flags = self.detect_scopes([published_suite], [published_proxy])
        self.assertEqual("false", flags["any_changed"])
        flags = self.detect_scopes([published_suite], [published_suite])
        self.assertEqual("true", flags["proxy_changed"])
        self.assertEqual("false", flags["core_changed"])

    def test_manual_dispatch_builds_all_and_uses_the_same_activation_step(self):
        baseline = self.artifact(self.baseline, "2026-11-20T00:00:00Z")
        flags = self.detect_scopes([baseline], [baseline], event="workflow_dispatch")
        self.assertEqual("true", flags["core_changed"])
        self.assertEqual("true", flags["proxy_changed"])
        steps = self.workflow["jobs"]["publish-suite"]["steps"]
        activate = next(i for i, step in enumerate(steps) if step.get("name") == "Activate the staged Suite release")
        record = next(i for i, step in enumerate(steps) if step.get("with", {}).get("name") == "suite-published")
        announce = next(i for i, step in enumerate(steps) if step.get("name") == "Queue the Suite release announcement")
        self.assertEqual("env.AXS_CLOUD_UPLOAD == 'true'", steps[activate]["if"])
        self.assertGreater(record, activate, "a staging run does not record a completed publication")
        self.assertGreater(announce, record, "a failed notification must not erase the publication record")
        self.assertEqual(steps[activate]["if"], steps[record]["if"])
        self.assertEqual(steps[record]["if"], steps[announce]["if"])


    def test_customer_export_dispatch_builds_only_the_requested_modules(self):
        self.commit("modules/mail/build.gradle.kts", "include a second available module")
        flags = self.detect_scopes([], [], event="workflow_dispatch",
                                   AXS_EXPORT_ONLY="true", VALIDATION_ONLY="false", GITHUB_REF="refs/heads/main",
                                   EXPORT_ACCOUNT="12345", EXPORT_RECIPIENT="customer-test",
                                   EXPORT_DELIVERY_ID="delivery-test", EXPORT_MODULES="market")
        self.assertEqual(["market"], json.loads(flags["changed_modules"]))
        self.assertEqual("true", flags["core_changed"])
        self.assertEqual("true", flags["proxy_changed"])

    def test_manual_suite_publication_builds_all_modules_without_publishing_proxies(self):
        self.commit("modules/mail/build.gradle.kts", "second public module")
        flags = self.detect_scopes([], [], event="workflow_dispatch",
                                   PUBLISH_SUITE_REQUESTED="true", AXS_MANUAL_SUITE_PUBLISH="true",
                                   GITHUB_REF="refs/heads/main")
        self.assertEqual(["mail", "market"], json.loads(flags["changed_modules"]))
        self.assertEqual("true", flags["core_changed"])
        self.assertEqual("false", flags["proxy_changed"])
        condition = self.workflow["env"]["AXS_CLOUD_UPLOAD"]
        self.assertIn("github.event_name == 'workflow_dispatch' && inputs.publish_suite", condition)

    def test_cloud_writes_require_main_and_exclude_export_only(self):
        condition = self.workflow["env"]["AXS_CLOUD_UPLOAD"]
        self.assertIn("github.ref == 'refs/heads/main'", condition)
        self.assertIn("!inputs.export_only", condition)
        self.assertIn("!inputs.skip_upload", condition)
        self.assertIn("vars.AXS_SKIP_CLOUD_UPLOAD != 'true'", condition)
        steps = self.workflow["jobs"]["publish-suite"]["steps"]
        for name in ("Stage the Suite release", "Activate the staged Suite release", "Record Suite publication"):
            self.assertIn("env.AXS_CLOUD_UPLOAD == 'true'", next(step["if"] for step in steps if step.get("name") == name))
        self.assertIn("github.ref != 'refs/heads/main'", self.workflow["env"]["AXS_CI_VALIDATION"])
        self.assertIn("!inputs.export_only", self.workflow["env"]["AXS_CI_VALIDATION"])

    def test_export_preflight_reports_missing_inputs_without_printing_values(self):
        steps = self.workflow["jobs"]["detect-changes"]["steps"]
        preflight = next(step for step in steps if step.get("name") == "Check customer export signing inputs")
        self.assertEqual("env.AXS_EXPORT_ONLY == 'true'", preflight["if"])
        fixture = "test-value-that-must-not-appear"
        env = dict(os.environ, **dict.fromkeys(preflight["env"], fixture))
        env["AXS_VARIANT_REGISTRATION_KEY_B64"] = ""
        run = subprocess.run([os.environ.get("AXS_TEST_BASH", "bash"), "-s"], input=preflight["run"],
                             env=env, encoding="utf-8", capture_output=True, timeout=20)
        self.assertNotEqual(0, run.returncode)
        self.assertIn("AXS_VARIANT_REGISTRATION_KEY_B64", run.stdout)
        self.assertNotIn(fixture, run.stdout + run.stderr)


if __name__ == "__main__":
    unittest.main()

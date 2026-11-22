import base64
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest

import yaml


WORKFLOW = Path(__file__).resolve().parents[2] / ".github/workflows/build-encrypted.yml"


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

    def detect_scopes(self, suite, proxy, event="push"):
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
                   GITHUB_OUTPUT=output.as_posix(), RUNNER_TEMP=self.repo.as_posix())
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
        self.assertEqual("env.AXS_CI_VALIDATION != 'true'", steps[activate]["if"])
        self.assertGreater(record, activate, "a staging run does not record a completed publication")
        self.assertEqual(steps[activate]["if"], steps[record]["if"])


if __name__ == "__main__":
    unittest.main()

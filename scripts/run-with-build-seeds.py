"""Run a key-material script with the seeds for this build."""

import base64
import hashlib
import os
import subprocess
import sys


def main():
    environment = os.environ.copy()
    if environment.get("AXS_CI_VALIDATION") == "true":
        if environment.get("GITHUB_EVENT_NAME") != "workflow_dispatch":
            sys.exit("CI validation seeds require a manual workflow run")
        # A retry can reuse artifacts from a successful job in the same run.
        run = ":".join(environment[key] for key in ("GITHUB_REPOSITORY", "GITHUB_RUN_ID", "GITHUB_SHA"))
        for name in ("AXS_SEED_PARTS_B64", "AXB_SIGN_SEED_B64", "AXB_RESP_SIGN_SEED_B64"):
            digest = hashlib.sha256(f"AXS validation only:{run}:{name}".encode("utf-8")).digest()
            environment[name] = base64.b64encode(digest).decode("ascii")
        print("Using signing and encryption seeds for this CI validation run", flush=True)

    return subprocess.call([sys.executable, *sys.argv[1:]], env=environment)


if __name__ == "__main__":
    sys.exit(main())

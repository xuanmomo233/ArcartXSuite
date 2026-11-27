"""Assemble and tidy the customer artifacts for the current GitHub Actions run."""

import argparse
import json
import os
from pathlib import Path
import re
import subprocess
import sys
from tempfile import TemporaryDirectory


ATTEMPT_ARTIFACT = re.compile(r"axs-customer-(installers|registration|proxy|delivery)-attempt-([1-9][0-9]*)")
INTERNAL_ARTIFACTS = {
    "abyss-regression-reports",
    "native-public-keys",
    "native-linux-enc",
    "native-windows-enc",
    "suite-module-stage",
    "axs-jar-encrypted-linux-x86_64",
    "axs-jar-encrypted-windows-x86_64",
    "axs-runtime-libraries-linux-x86_64",
    "axs-runtime-libraries-windows-x86_64",
}


def select_delivery(artifacts):
    """Use the latest complete matched pair, or resume cleanup after its upload."""
    attempts = {role: {} for role in ("installers", "registration", "proxy", "delivery")}
    for artifact in artifacts:
        match = ATTEMPT_ARTIFACT.fullmatch(artifact["name"])
        if match is None or artifact["expired"]:
            continue
        role, attempt = match[1], int(match[2])
        if attempt in attempts[role]:
            raise ValueError(f"Duplicate customer export artifact: {artifact['name']}")
        attempts[role][attempt] = artifact["name"]

    complete = attempts["installers"].keys() & attempts["registration"].keys()
    export = max(complete, default=0)
    uploaded = max(attempts["delivery"], default=0)
    if uploaded and uploaded >= export:
        return attempts["delivery"][uploaded], ()
    if not export or not attempts["proxy"]:
        raise ValueError("Expected matching installers and registration, plus the proxy JARs")

    proxy = max(attempts["proxy"])
    return f"axs-customer-delivery-attempt-{export}", (
        attempts["installers"][export], attempts["registration"][export], attempts["proxy"][proxy]
    )


def cleanup_ids(artifacts, delivery):
    if not any(item["name"] == delivery and not item["expired"] for item in artifacts):
        raise ValueError("The completed delivery must be available before removing build artifacts")
    for item in artifacts:
        name = item["name"]
        if not item["expired"] and name != delivery and (name in INTERNAL_ARTIFACTS or ATTEMPT_ARTIFACT.fullmatch(name)):
            yield int(item["id"])


def artifacts(repo, run):
    result = subprocess.run(
        ["gh", "api", "--paginate", "--slurp", f"repos/{repo}/actions/runs/{run}/artifacts?per_page=100"],
        check=True, capture_output=True, text=True, encoding="utf-8",
    )
    return [artifact for page in json.loads(result.stdout) for artifact in page["artifacts"]]


def prepare(repo, run, output):
    name, sources = select_delivery(artifacts(repo, run))
    if sources:
        with TemporaryDirectory() as work:
            directories = [Path(work) / source for source in sources]
            for source, directory in zip(sources, directories):
                subprocess.run(
                    ["gh", "run", "download", run, "--repo", repo, "--name", source, "--dir", str(directory)],
                    check=True,
                )
            builder = Path(__file__).with_name("bundle-customer-delivery.py")
            subprocess.run(
                [sys.executable, "-X", "utf8", str(builder), "--installers", str(directories[0]),
                 "--registration", str(directories[1] / "register-module-variants.json"),
                 "--proxies", str(directories[2]), "--output", str(output)],
                check=True,
            )
    with open(os.environ["GITHUB_OUTPUT"], "a", encoding="utf-8") as step_output:
        step_output.write(f"artifact_name={name}\nneeds_upload={'true' if sources else 'false'}\n")


def clean(repo, run, delivery):
    snapshot = artifacts(repo, run)
    selected, _ = select_delivery(snapshot)
    if delivery != selected:
        raise ValueError("The artifact to retain does not match the active customer delivery")
    for identifier in cleanup_ids(snapshot, delivery):
        subprocess.run(["gh", "api", "-X", "DELETE", f"repos/{repo}/actions/artifacts/{identifier}"], check=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    commands.add_parser("prepare").add_argument("--output", type=Path, required=True)
    commands.add_parser("cleanup").add_argument("--delivery", required=True)
    args = parser.parse_args()
    repo, run = os.environ["GITHUB_REPOSITORY"], os.environ["GITHUB_RUN_ID"]
    if not re.fullmatch(r"[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+", repo) or not re.fullmatch(r"[0-9]+", run):
        raise ValueError("Expected the current GitHub repository and run ID")
    if args.command == "prepare":
        prepare(repo, run, args.output)
    else:
        clean(repo, run, args.delivery)


if __name__ == "__main__":
    main()

#!/usr/bin/env python3
"""Publish one complete Suite release to both origins."""

import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import zipfile
from urllib.parse import urlsplit


PLATFORMS = ("windows-x86_64", "linux-x86_64")
PRIMARY = "https://cloud.021209.xyz"
ACTIVATION = Path("suite-release/activation.json")
PREPARED = Path("suite-release/staging.enc")
STAGED_MODULES = Path("suite-release/modules")


def post(origin, path, fields, *, payload=None):
    curl = ["curl", "--fail", "--silent", "--http1.1", "--connect-timeout", "10",
            "--max-time", "120", "--retry", "3", "--retry-delay", "2", "--retry-all-errors",
            "--header", "X-CI-Token: " + os.environ["CLOUD_CI_TOKEN"]]
    for name, value in fields.items():
        curl.extend(["--form" if isinstance(value, Path) else "--form-string",
                     name + "=" + ("@" + str(value) if isinstance(value, Path) else value)])
    if payload is not None:
        curl.extend(["--header", "Content-Type: application/json", "--data-raw", json.dumps(payload)])
    response = subprocess.run([*curl, origin + path], capture_output=True, check=False)
    try:
        result = json.loads(response.stdout) if response.returncode == 0 else {}
        accepted = result.get("ok") is True
    except (ValueError, AttributeError):
        accepted = False
    if not accepted:
        raise RuntimeError(f"{urlsplit(origin).hostname} rejected {path} (curl {response.returncode})")
    return result


def host_artifacts():
    hosts = {}
    for platform in PLATFORMS:
        jars = list(Path(f"suite-release/hosts/axs-jar-encrypted-{platform}").glob("*-encrypted.jar"))
        if len(jars) != 1:
            raise SystemExit(f"Expected one verified host JAR for {platform}")
        hosts[platform] = jars[0]
    return hosts


def module_artifacts(modules):
    modules_dir = Path("build/ArcartX-Suite/module-axb")
    packages = {}
    for module in modules:
        axb = modules_dir / f"{module}.axb"
        meta = modules_dir / f"{module}.meta.json"
        if not axb.is_file() or not meta.is_file():
            raise SystemExit(f"Missing AXB or metadata for {module}")
        packages[module] = (axb, json.loads(meta.read_text(encoding="utf-8")))
    return packages


def stage(origin, version, commit, commit_time, hosts, packages):
    common = {"version": version, "setCurrent": "false", "releaseNotes": "CI build " + commit}
    for platform, jar in hosts.items():
        result = post(origin, f"/v1/ci/app-jar/{platform}/versions",
                      {**common, "jar": jar, "commit": commit, "commitTime": commit_time})
        verify_staged(jar, version, result)
    for module, (axb, meta) in packages.items():
        result = post(origin, f"/v1/ci/modules/{module}/versions",
                      {**common, "axb": axb, "key": meta["moduleKey"], "iv": meta["moduleIv"]})
        verify_staged(axb, version, result)


def verify_staged(file, version, result):
    staged = result.get("version", {})
    with file.open("rb") as stream:
        checksum = hashlib.file_digest(stream, "sha256").hexdigest()
    if staged.get("version") != version or staged.get("checksum") != checksum:
        raise RuntimeError(f"Cloud staged a different version or checksum for {file.name}")


def upload_libraries(origin):
    result = subprocess.run([sys.executable, "scripts/upload-runtime.py", "--libraries", "suite-release/libraries",
                             "--primary", origin], check=False)
    if result.returncode:
        raise RuntimeError(f"{urlsplit(origin).hostname} did not accept the host libraries")


def validate_modules(modules):
    if not isinstance(modules, list) or not 1 <= len(modules) <= 48 or any(
            not isinstance(name, str) or not re.fullmatch(r"[a-z][a-z0-9-]{0,63}", name) for name in modules):
        raise SystemExit("Invalid release module list")
    if len(set(modules)) != len(modules):
        raise SystemExit("Duplicate release module")


def mirror_origin():
    mirror = (os.environ.get("FLARE_BASE_URL") or "https://flare.021209.xyz").rstrip("/")
    parsed = urlsplit(mirror)
    if parsed.scheme != "https" or not parsed.hostname or parsed.username or parsed.path or parsed.query or parsed.fragment:
        raise SystemExit("Invalid Flare HTTPS origin")
    return mirror


def release_modules():
    modules = json.loads(os.environ["CHANGED_MODULES"])
    validate_modules(modules)
    return modules


def staging_cipher():
    from cryptography.hazmat.primitives import hashes
    from cryptography.hazmat.primitives.ciphers.aead import AESGCM
    from cryptography.hazmat.primitives.kdf.hkdf import HKDF

    context = f"Suite CI staging v1:{os.environ['GITHUB_RUN_ID']}:{os.environ['GITHUB_SHA']}".encode("utf-8")
    token = os.environ["CLOUD_CI_TOKEN"].encode("utf-8")
    if not token:
        raise SystemExit("CLOUD_CI_TOKEN missing")
    key = HKDF(algorithm=hashes.SHA256(), length=32, salt=None, info=context).derive(token)
    return AESGCM(key), context


def artifact_hashes(hosts, modules):
    libraries = sorted(Path("suite-release/libraries").glob("*.jar"))
    if not libraries:
        raise SystemExit("Missing runtime libraries in prepared release")
    files = [*hosts.values(), *libraries, *(STAGED_MODULES / f"{module}.axb" for module in modules)]
    hashes = {}
    for artifact in files:
        with artifact.open("rb") as stream:
            hashes[artifact.as_posix()] = hashlib.file_digest(stream, "sha256").hexdigest()
    return hashes


def prepare_release():
    modules = release_modules()
    hosts, packages = host_artifacts(), module_artifacts(modules)
    commit = os.environ["GITHUB_SHA"]
    base = next(line.split("=", 1)[1].strip() for line in Path("gradle.properties").read_text(encoding="utf-8").splitlines()
                if line.startswith("version="))
    version = f"{base}-suite.{os.environ['GITHUB_RUN_ID']}.1-ci.{commit[:7]}"
    for jar in hosts.values():
        with zipfile.ZipFile(jar) as archive:
            build = json.loads(archive.read("axs-build.json"))
        if build.get("commit") != commit or build.get("release") != version:
            raise SystemExit("Host artifacts do not match this release")
    STAGED_MODULES.mkdir(parents=True, exist_ok=True)
    metadata = {}
    for module, (axb, meta) in packages.items():
        shutil.copyfile(axb, STAGED_MODULES / f"{module}.axb")
        metadata[module] = {"moduleKey": meta["moduleKey"], "moduleIv": meta["moduleIv"]}
    prepared = {"version": version, "commit": commit, "modules": modules, "mirror": mirror_origin(),
                "metadata": metadata, "hashes": artifact_hashes(hosts, modules)}
    cipher, context = staging_cipher()
    nonce = os.urandom(12)
    PREPARED.write_bytes(nonce + cipher.encrypt(nonce, json.dumps(prepared).encode("utf-8"), context))
    print(f"Prepared immutable Suite staging input for {version}", flush=True)


def stage_release():
    cipher, context = staging_cipher()
    encoded = PREPARED.read_bytes()
    prepared = json.loads(cipher.decrypt(encoded[:12], encoded[12:], context))
    commit, version, modules = prepared["commit"], prepared["version"], prepared["modules"]
    mirror = mirror_origin()
    if commit != os.environ["GITHUB_SHA"] or modules != release_modules() or mirror != prepared["mirror"]:
        raise SystemExit("Prepared release does not match this workflow")
    hosts = host_artifacts()
    if artifact_hashes(hosts, modules) != prepared["hashes"]:
        raise SystemExit("Prepared release artifacts no longer match their original checksums")
    packages = {module: (STAGED_MODULES / f"{module}.axb", prepared["metadata"][module]) for module in modules}
    commit_time = subprocess.check_output(["git", "show", "-s", "--format=%ct", commit], text=True).strip()
    for origin in (PRIMARY, mirror):
        upload_libraries(origin)
        stage(origin, version, commit, commit_time, hosts, packages)
    ACTIVATION.parent.mkdir(parents=True, exist_ok=True)
    ACTIVATION.write_text(json.dumps({"version": version, "commit": commit, "modules": modules, "mirror": mirror}),
                          encoding="utf-8")
    print(f"Staged Suite release {version} on both origins", flush=True)


def activate_release():
    release = json.loads(ACTIVATION.read_text(encoding="utf-8"))
    commit, version, modules = release["commit"], release["version"], release["modules"]
    run = re.escape(os.environ["GITHUB_RUN_ID"])
    if commit != os.environ["GITHUB_SHA"] or not re.fullmatch(r"[0-9a-f]{40}", commit) or not re.fullmatch(
            rf"[a-zA-Z0-9._-]+-suite\.{run}\.\d+-ci\.{commit[:7]}", version):
        raise SystemExit("Activation manifest does not match this workflow run")
    validate_modules(modules)
    mirror = mirror_origin()
    if release["mirror"] != mirror:
        raise SystemExit("Flare origin changed since staging")
    head = subprocess.check_output(["git", "ls-remote", "--exit-code", "origin", "refs/heads/main"], text=True)
    if head.split()[0] != commit:
        raise SystemExit("Only the current main commit may activate a Suite release")
    for action in ("withdraw", "activate"):
        for origin in (PRIMARY, mirror):
            post(origin, "/v1/ci/suite-release/current", {},
                 payload={"version": version, "commit": commit, "modules": modules, "action": action})
    print(f"Activated Suite release {version} on both origins", flush=True)


if __name__ == "__main__":
    if sys.argv[1:] == ["prepare"]:
        prepare_release()
    elif sys.argv[1:] == ["stage"]:
        stage_release()
    elif sys.argv[1:] == ["activate"]:
        activate_release()
    else:
        raise SystemExit("Usage: publish-suite.py prepare|stage|activate")

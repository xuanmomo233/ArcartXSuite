#!/usr/bin/env python3
"""Publish and check scoped Suite maintainer authorizations without network access."""

import argparse
import base64
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import stat
import sys
import zipfile
import zlib

from cryptography.exceptions import InvalidSignature, UnsupportedAlgorithm
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric.ed25519 import Ed25519PrivateKey, Ed25519PublicKey

from host_seal import HOST_NOTICE, MAX_EXPANDED_BYTES, verify_host_seal


GRANT_TYPE = "arcartx-suite-authorization-v1"
GRANT_DOMAIN = b"ArcartX-Suite/authorization-grant/v1\0"
PROOF_DOMAIN = b"ArcartX-Suite/authorization-subject-proof/v1\0"
GRANT_FIELDS = {
    "type", "subject", "subject_public_key", "jar_sha256", "actions", "valid_from", "expires_at",
}
MAX_GRANT_BYTES = 16 * 1024
ACTION = re.compile(r"[a-z][a-z0-9._:-]{0,63}")
HEX_SHA256 = re.compile(r"[0-9a-f]{64}")
UTC_TIMESTAMP = re.compile(r"[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}Z")


def read_bounded(path, limit):
    with path.open("rb") as source:
        if not stat.S_ISREG(os.fstat(source.fileno()).st_mode):
            raise ValueError("输入必须是本地普通文件")
        data = source.read(limit + 1)
    if len(data) > limit:
        raise ValueError("输入文件超出大小限制")
    return data


def write_output(path, data):
    # Authorization and proof files are small. An interrupted write fails verification.
    with path.open("xb") as output:
        output.write(data)


def public_key(path):
    key = serialization.load_pem_public_key(read_bounded(path, 8192))
    if not isinstance(key, Ed25519PublicKey):
        raise ValueError("需要 Ed25519 公钥")
    return key


def private_key(path):
    key = serialization.load_pem_private_key(read_bounded(path, 8192), password=None)
    if not isinstance(key, Ed25519PrivateKey):
        raise ValueError("需要 Ed25519 私钥")
    return key


def raw_public_key(key):
    return key.public_bytes(serialization.Encoding.Raw, serialization.PublicFormat.Raw)


def decode_b64(value, size, label):
    if not isinstance(value, str) or len(value) != 4 * ((size + 2) // 3):
        raise ValueError(f"{label}格式无效")
    try:
        data = base64.b64decode(value, validate=True)
    except ValueError as exc:
        raise ValueError(f"{label}格式无效") from exc
    if len(data) != size or base64.b64encode(data).decode("ascii") != value:
        raise ValueError(f"{label}格式无效")
    return data


def encode_b64(data):
    return base64.b64encode(data).decode("ascii")


def canonical_json(value):
    return json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=True).encode("ascii")


def unique_object(pairs):
    entries = dict(pairs)
    if len(entries) != len(pairs):
        raise ValueError("授权凭证含重复字段")
    return entries


def parse_time(value):
    if not isinstance(value, str) or UTC_TIMESTAMP.fullmatch(value) is None:
        raise ValueError("有效期必须使用 UTC 格式 YYYY-MM-DDTHH:MM:SSZ")
    try:
        return datetime.fromisoformat(value[:-1] + "+00:00")
    except ValueError as exc:
        raise ValueError("授权有效期包含无效的日期或时间") from exc


def valid_subject(value):
    return isinstance(value, str) and 1 <= len(value) <= 128 and value.isprintable() and value == value.strip()


def valid_action(value):
    return isinstance(value, str) and ACTION.fullmatch(value) is not None


def validate_grant(grant):
    if not isinstance(grant, dict) or grant.keys() != GRANT_FIELDS or grant["type"] != GRANT_TYPE:
        raise ValueError("授权凭证字段或版本无效")
    if not valid_subject(grant["subject"]):
        raise ValueError("被授权人标识无效")
    decode_b64(grant["subject_public_key"], 32, "被授权人公钥")
    digest = grant["jar_sha256"]
    if not isinstance(digest, str) or HEX_SHA256.fullmatch(digest) is None:
        raise ValueError("JAR 的 SHA-256 格式无效")
    actions = grant["actions"]
    if (not isinstance(actions, list) or not 1 <= len(actions) <= 32
            or not all(valid_action(action) for action in actions) or len(set(actions)) != len(actions)):
        raise ValueError("允许的操作必须是 1 至 32 个不同的操作标识")
    if parse_time(grant["valid_from"]) >= parse_time(grant["expires_at"]):
        raise ValueError("授权结束时间必须晚于开始时间")


def grant_message(grant):
    return GRANT_DOMAIN + canonical_json(grant)


def read_grant(path):
    try:
        envelope = json.loads(read_bounded(path, MAX_GRANT_BYTES).decode("utf-8"), object_pairs_hook=unique_object)
    except (UnicodeError, json.JSONDecodeError, RecursionError) as exc:
        raise ValueError("无法读取授权凭证 JSON") from exc
    if not isinstance(envelope, dict) or envelope.keys() != {"grant", "signature"}:
        raise ValueError("授权凭证封装无效")
    grant = envelope["grant"]
    validate_grant(grant)
    return grant, decode_b64(envelope["signature"], 64, "维护者签名")


def inspect_release(path, release_public_key_path):
    key = public_key(release_public_key_path)
    try:
        with path.open("rb") as source:
            before = os.fstat(source.fileno())
            if not stat.S_ISREG(before.st_mode) or not 0 < before.st_size <= MAX_EXPANDED_BYTES:
                raise ValueError("发行 JAR 文件类型或大小无效")
            with zipfile.ZipFile(source) as archive:
                verify_host_seal(archive, key)
                notice = archive.read(HOST_NOTICE).decode("ascii")
            source.seek(0)
            digest = hashlib.file_digest(source, "sha256").hexdigest()
            after = os.fstat(source.fileno())
            if (before.st_size, before.st_mtime_ns, before.st_ctime_ns) != (
                after.st_size, after.st_mtime_ns, after.st_ctime_ns
            ):
                raise ValueError("发行 JAR 在核验过程中发生变化")
    except (InvalidSignature, KeyError, ValueError, zipfile.BadZipFile, zlib.error, RuntimeError) as exc:
        raise ValueError("JAR 的发行签名或内置声明无法验证") from exc
    return digest, notice


def proof_message(grant, signature, challenge_path, action):
    if not valid_action(action) or action not in grant["actions"]:
        raise ValueError("请求的操作不在授权范围内")
    challenge = read_bounded(challenge_path, 80).decode("ascii").strip()
    if HEX_SHA256.fullmatch(challenge) is None:
        raise ValueError("身份挑战必须是验证方本次生成的 32 字节随机值")
    fingerprint = hashlib.sha256(grant_message(grant) + signature).digest()
    return PROOF_DOMAIN + bytes.fromhex(challenge) + fingerprint + action.encode("ascii")


def issue(args):
    digest, _ = inspect_release(args.jar, args.release_public_key)
    grant = {
        "type": GRANT_TYPE,
        "subject": args.subject,
        "subject_public_key": encode_b64(raw_public_key(public_key(args.subject_public_key))),
        "jar_sha256": digest,
        "actions": sorted(set(args.allow)),
        "valid_from": datetime.now(timezone.utc).strftime("%Y-%m-%dT%H:%M:%SZ"),
        "expires_at": args.expires_at,
    }
    validate_grant(grant)
    signature = private_key(args.maintainer_private_key).sign(grant_message(grant))
    write_output(args.output, canonical_json({"grant": grant, "signature": encode_b64(signature)}) + b"\n")


def prove(args):
    grant, signature = read_grant(args.grant)
    key = private_key(args.subject_private_key)
    if encode_b64(raw_public_key(key.public_key())) != grant["subject_public_key"]:
        raise ValueError("私钥与凭证记录的被授权人公钥不匹配")
    proof = key.sign(proof_message(grant, signature, args.challenge, args.action))
    write_output(args.output, encode_b64(proof).encode("ascii") + b"\n")


def verify_grant(args, digest):
    grant, signature = read_grant(args.grant)
    try:
        public_key(args.maintainer_public_key).verify(signature, grant_message(grant))
    except InvalidSignature as exc:
        raise ValueError("维护者签名校验失败") from exc
    if digest != grant["jar_sha256"]:
        raise ValueError("授权凭证绑定的 JAR SHA-256 不匹配")
    if not valid_subject(args.subject) or args.subject != grant["subject"]:
        raise ValueError("请求人与被授权人不匹配")
    now = datetime.now(timezone.utc)
    if not parse_time(grant["valid_from"]) <= now < parse_time(grant["expires_at"]):
        raise ValueError("授权不在有效期内")

    message = proof_message(grant, signature, args.challenge, args.action)
    proof = decode_b64(read_bounded(args.proof, 96).decode("ascii").strip(), 64, "身份证明")
    subject_key = Ed25519PublicKey.from_public_bytes(decode_b64(grant["subject_public_key"], 32, "被授权人公钥"))
    try:
        subject_key.verify(proof, message)
    except InvalidSignature as exc:
        raise ValueError("被授权人的身份挑战校验失败") from exc
    return {
        "status": "confirmed",
        "message": "授权已确认",
        "notice_verified": True,
        "subject": args.subject,
        "jar_sha256": digest,
        "action": args.action,
        "expires_at": grant["expires_at"],
    }


def argument_parser():
    parser = argparse.ArgumentParser(description="ArcartX Suite 离线授权工具")
    commands = parser.add_subparsers(dest="command", required=True)
    notice = commands.add_parser("notice", help="核验发行签名并读取英文声明")
    issuing = commands.add_parser("issue", help="为已核验的 JAR 签发授权凭证")
    checking = commands.add_parser("verify", help="核验请求人的授权范围和身份")
    challenging = commands.add_parser("challenge", help="验证方生成本次身份挑战")
    proving = commands.add_parser("prove", help="被授权人用自己的私钥应答")
    for command in (notice, issuing, checking):
        command.add_argument("--jar", type=Path, required=True)
        command.add_argument("--release-public-key", type=Path, required=True)
    for command in (issuing, checking):
        command.add_argument("--subject", required=True)
    for command in (proving, checking):
        command.add_argument("--grant", type=Path, required=True)
        command.add_argument("--challenge", type=Path, required=True)
        command.add_argument("--action", required=True)
    for command in (notice, checking):
        command.add_argument("--json", action="store_true", help="输出可供工具读取的 JSON")
    for command in (issuing, challenging, proving):
        command.add_argument("--output", type=Path, required=True)

    issuing.add_argument("--maintainer-private-key", type=Path, required=True)
    issuing.add_argument("--subject-public-key", type=Path, required=True)
    issuing.add_argument("--allow", action="append", required=True, help="允许的操作，可以重复指定")
    issuing.add_argument("--expires-at", required=True, help="UTC，例如 2026-12-01T12:00:00Z")
    proving.add_argument("--subject-private-key", type=Path, required=True)
    checking.add_argument("--maintainer-public-key", type=Path, required=True)
    checking.add_argument("--proof", type=Path, required=True)
    return parser


def main():
    args = argument_parser().parse_args()
    json_output = getattr(args, "json", False)
    notice = None
    try:
        if args.command in ("notice", "verify"):
            digest, notice = inspect_release(args.jar, args.release_public_key)
        if args.command == "notice":
            if json_output:
                print(json.dumps({"status": "verified", "jar_sha256": digest, "notice": notice}))
            else:
                print(notice.rstrip())
                print(f"\nVerified JAR SHA-256: {digest}")
        elif args.command == "issue":
            issue(args)
            print("授权凭证已签发")
        elif args.command == "challenge":
            write_output(args.output, secrets.token_hex(32).encode("ascii") + b"\n")
            print("本次身份挑战已生成")
        elif args.command == "prove":
            prove(args)
            print("身份证明已生成")
        elif args.command == "verify":
            result = verify_grant(args, digest)
            if json_output:
                result["notice"] = notice
                print(json.dumps(result))
            else:
                print(notice.rstrip())
                print(f"\n{result['message']}")
        return 0
    except (ValueError, OSError, TypeError, UnsupportedAlgorithm) as exc:
        if isinstance(exc, UnicodeError):
            reason = "输入文件编码无效"
        elif isinstance(exc, (OSError, TypeError, UnsupportedAlgorithm)):
            reason = "无法读取或写入文件，请检查文件路径、公钥格式和输出文件状态"
        else:
            reason = str(exc)
        verification = args.command in ("notice", "verify")
        message = "授权未确认" if verification else "操作失败"
        if json_output:
            report = {"status": "unconfirmed", "message": message, "reason": reason}
            if notice is not None:
                report["notice"] = notice
            print(json.dumps(report))
        else:
            if notice is not None:
                print(notice.rstrip())
            print(f"{message}：{reason}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    raise SystemExit(main())

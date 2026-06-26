#!/usr/bin/env python3
"""CI 上传：把逐类加密的模块 .axb 与核心 payload .axb 上传到 CloudPlatform。

- 模块： build/ArcartX-Suite/module-axb/<name>.axb (+ <name>.meta.json)
         → POST {api}/v1/ci/modules/<name>/versions   （X-CI-Token）
- 核心： --core-axb 指定的 __core__.axb (+ .meta.json)
         → POST {api}/v1/ci/core/versions             （X-CI-Token）

每个 .axb 旁必须有同名 .meta.json（由 EncryptModuleAxbTask / wrap-axb.py 产出），
内含 {"moduleKey": <base64 32B>, ...}，作为该版本的解密 key 一并上传。
CI Token 从环境变量 CI_UPLOAD_TOKEN 读取（不作为命令行参数，避免泄漏到日志）。
"""
import argparse
import glob
import json
import mimetypes
import os
import sys
import urllib.request
import uuid


def _multipart(fields, file_field, file_path):
    boundary = "----axsci" + uuid.uuid4().hex
    crlf = b"\r\n"
    body = bytearray()
    for k, v in fields.items():
        body += b"--" + boundary.encode() + crlf
        body += ('Content-Disposition: form-data; name="%s"' % k).encode() + crlf + crlf
        body += str(v).encode() + crlf
    fname = os.path.basename(file_path)
    ctype = mimetypes.guess_type(fname)[0] or "application/octet-stream"
    with open(file_path, "rb") as f:
        data = f.read()
    body += b"--" + boundary.encode() + crlf
    body += ('Content-Disposition: form-data; name="%s"; filename="%s"' % (file_field, fname)).encode() + crlf
    body += ("Content-Type: %s" % ctype).encode() + crlf + crlf
    body += data + crlf
    body += b"--" + boundary.encode() + b"--" + crlf
    return bytes(body), "multipart/form-data; boundary=" + boundary


def _post(url, token, fields, axb_path):
    body, ctype = _multipart(fields, "axb", axb_path)
    req = urllib.request.Request(url, data=body, method="POST")
    req.add_header("Content-Type", ctype)
    req.add_header("X-CI-Token", token)
    try:
        with urllib.request.urlopen(req, timeout=120) as resp:
            return resp.status, resp.read().decode("utf-8", "replace")
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")


def _key_from_meta(axb_path):
    meta_path = axb_path[:-4] + ".meta.json" if axb_path.endswith(".axb") else axb_path + ".meta.json"
    if not os.path.exists(meta_path):
        sys.exit("缺少 meta： %s" % meta_path)
    with open(meta_path, "r", encoding="utf-8") as f:
        meta = json.load(f)
    key = meta.get("moduleKey")
    if not key:
        sys.exit("meta 缺少 moduleKey： %s" % meta_path)
    return key


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--api-base", required=True, help="如 https://cloud.example.com")
    ap.add_argument("--version", required=True)
    ap.add_argument("--set-current", default="false")
    ap.add_argument("--modules-dir", default=None, help="含 module-axb/*.axb 的根（递归查找）")
    ap.add_argument("--core-axb", default=None)
    ap.add_argument("--release-notes", default="")
    args = ap.parse_args()

    token = os.environ.get("CI_UPLOAD_TOKEN", "")
    if not token:
        sys.exit("环境变量 CI_UPLOAD_TOKEN 未设置")
    api = args.api_base.rstrip("/")
    set_current = "true" if str(args.set_current).lower() in ("1", "true", "yes") else "false"

    failures = 0

    if args.modules_dir:
        axbs = sorted(glob.glob(os.path.join(args.modules_dir, "**", "module-axb", "*.axb"), recursive=True))
        print("[ci-upload] 发现 %d 个模块 .axb" % len(axbs))
        for axb in axbs:
            mod_id = os.path.splitext(os.path.basename(axb))[0]
            key = _key_from_meta(axb)
            url = "%s/v1/ci/modules/%s/versions" % (api, mod_id)
            fields = {"version": args.version, "key": key, "setCurrent": set_current}
            if args.release_notes:
                fields["releaseNotes"] = args.release_notes
            status, text = _post(url, token, fields, axb)
            ok = status == 200
            print("[module] %-24s -> %s %s" % (mod_id, status, "OK" if ok else text[:300]))
            if not ok:
                failures += 1

    if args.core_axb:
        key = _key_from_meta(args.core_axb)
        url = "%s/v1/ci/core/versions" % api
        fields = {"version": args.version, "key": key, "setCurrent": set_current}
        if args.release_notes:
            fields["releaseNotes"] = args.release_notes
        status, text = _post(url, token, fields, args.core_axb)
        ok = status == 200
        print("[core]   __core__                 -> %s %s" % (status, "OK" if ok else text[:300]))
        if not ok:
            failures += 1

    if failures:
        sys.exit("[ci-upload] %d 个上传失败" % failures)
    print("[ci-upload] 全部上传成功")


if __name__ == "__main__":
    main()

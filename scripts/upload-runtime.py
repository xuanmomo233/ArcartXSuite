#!/usr/bin/env python3
"""Publish immutable public libraries to both backends before publishing their host JAR."""
import argparse
import hashlib
import os
from pathlib import Path
import time
import urllib.parse
import urllib.request
import uuid


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def upload(directory, origins):
    token = os.environ.get('CLOUD_CI_TOKEN')
    if not token:
        raise SystemExit('CLOUD_CI_TOKEN missing')
    artifacts = sorted(Path(directory).glob('*.jar'))
    if not artifacts:
        raise SystemExit('No runtime libraries found')
    opener = urllib.request.build_opener(NoRedirect())
    for origin in origins:
        parsed = urllib.parse.urlsplit(origin)
        if parsed.scheme != 'https' or not parsed.hostname or parsed.username or parsed.query or parsed.fragment:
            raise SystemExit('Invalid HTTPS runtime origin')
        for artifact in artifacts:
            content = artifact.read_bytes()
            sha = hashlib.sha256(content).hexdigest()
            if sha != artifact.stem:
                raise SystemExit('Runtime filename/checksum mismatch')
            boundary = 'axs-runtime-' + uuid.uuid4().hex
            body = (f'--{boundary}\r\nContent-Disposition: form-data; name="jar"; filename="{sha}.jar"\r\n'
                    'Content-Type: application/java-archive\r\n\r\n').encode('ascii') + content + f'\r\n--{boundary}--\r\n'.encode('ascii')
            request = urllib.request.Request(origin.rstrip('/') + '/v1/ci/runtime-libraries/' + sha, data=body,
                headers={'X-CI-Token': token, 'Content-Type': 'multipart/form-data; boundary=' + boundary,
                         'User-Agent': 'ArcartX-Suite-CI/1'}, method='POST')
            for attempt in range(3):
                try:
                    with opener.open(request, timeout=120) as response:
                        if response.status != 200:
                            raise OSError('Runtime upload rejected')
                    print(f'Runtime published: {parsed.hostname} {sha} ({len(content)} bytes)', flush=True)
                    break
                except OSError as error:
                    if attempt == 2:
                        status = getattr(error, 'code', type(error).__name__)
                        raise SystemExit(f'Runtime upload failed: {parsed.hostname} {sha} ({status}); host publication stopped') from None
                    time.sleep(2 ** attempt)


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--libraries', required=True)
    parser.add_argument('--primary', required=True)
    parser.add_argument('--mirror')
    args = parser.parse_args()
    upload(args.libraries, [args.primary, args.mirror] if args.mirror else [args.primary])

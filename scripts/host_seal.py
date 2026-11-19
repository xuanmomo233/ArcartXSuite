"""Generate and verify the signed inventory of a protected Suite host JAR."""
import hashlib
from zipfile import ZIP_DEFLATED, ZIP_STORED


HOST_MANIFEST = "META-INF/BOOTSTRAP.MF"
HOST_SIGNATURE = "META-INF/BOOTSTRAP.SIG"
HOST_NOTICE = "META-INF/AXS-SECURITY-NOTICE.txt"
HOST_NOTICE_SIGNATURE = "META-INF/AXS-SECURITY-NOTICE.sig"
NOTICE_DOMAIN = b"ArcartX-Suite/authorization-notice/v1\0"
MAX_NOTICE_BYTES = 8192
SEAL_FILES = frozenset({HOST_MANIFEST, HOST_SIGNATURE})
MAX_ENTRY_BYTES = 128 * 1024 * 1024
MAX_EXPANDED_BYTES = 512 * 1024 * 1024
WRAPPED_NATIVE = {"native/axs-native.enc", "native/libaxs-native.enc"}

PLAIN_HOST_CLASSES = (
    "xuanmo.arcartxsuite.ArcartXSuitePlugin",
    "xuanmo.arcartxsuite.bootstrap.SuiteCore",
    "xuanmo.arcartxsuite.bootstrap.HostShutdown",
    "xuanmo.arcartxsuite.bootstrap.RuntimeBootstrap",
    "xuanmo.arcartxsuite.security.NativeBridge",
    "xuanmo.arcartxsuite.security.protection.ProtectionInit",
    "xuanmo.arcartxsuite.security.protection.ProtectedClassLoader",
    "xuanmo.arcartxsuite.security.protection.JvmAntiDebug",
    "xuanmo.arcartxsuite.security.protection.ProtectionEnvironment",
    "xuanmo.arcartxsuite.util.HashUtils",
)
PLAIN_HOST_PREFIXES = ("xuanmo/arcartxsuite/api/", "xuanmo/arcartxsuite/auth/minecraft/")
_PLAIN_CLASS_PATHS = tuple(name.replace(".", "/") for name in PLAIN_HOST_CLASSES)


def check_host_layout(names):
    """Reject an unintended plaintext Suite class even in a correctly signed build."""
    names = set(names)
    missing = {
        "META-INF/MANIFEST.MF", "META-INF/PROTECTION.MF", "plugin.yml", HOST_NOTICE, HOST_NOTICE_SIGNATURE,
        *[name + ".class" for name in _PLAIN_CLASS_PATHS],
    } - names
    if missing:
        raise ValueError("Missing host entries: " + ", ".join(sorted(missing)))

    unexpected = []
    for name in names:
        class_path = name
        if class_path.startswith("META-INF/versions/"):
            parts = class_path.split("/", 3)
            if len(parts) != 4:
                continue
            class_path = parts[3]
        if not class_path.startswith("xuanmo/") or not class_path.endswith(".class"):
            continue
        base = class_path.removesuffix(".class")
        if name == class_path and (
            class_path.startswith(PLAIN_HOST_PREFIXES)
            or any(base == allowed or base.startswith(allowed + "$") for allowed in _PLAIN_CLASS_PATHS)
        ):
            continue
        unexpected.append(name)
    if unexpected:
        raise ValueError("Unexpected plaintext Suite classes: " + ", ".join(sorted(unexpected)))
    if not any(name.startswith("ENCRYPTED/") and name.endswith(".enc") for name in names):
        raise ValueError("Host contains no encrypted classes")
    native = {name for name in names if name.startswith("native/")}
    if not native or not native.issubset(WRAPPED_NATIVE):
        raise ValueError("Host native resources must be AES-GCM envelopes for Windows or Linux")


def check_entry_name(name):
    if (not name or "\\" in name or any(ord(char) < 32 or ord(char) == 127 for char in name)
            or any(part in ("", ".", "..") for part in name.split("/"))):
        raise ValueError(f"Invalid host entry name: {name!r}")


def notice_digest(data):
    if not 1 <= len(data) <= MAX_NOTICE_BYTES:
        raise ValueError("Invalid authorization notice size")
    if any((c < 32 and c not in (9, 10, 13)) or c >= 127 for c in data):
        raise ValueError("Authorization notice must be printable English text")
    return hashlib.sha256(NOTICE_DOMAIN + data).digest()


def verify_host_notice(jar, release_public_key):
    try:
        notice = jar.getinfo(HOST_NOTICE)
        signature = jar.getinfo(HOST_NOTICE_SIGNATURE)
    except KeyError as exc:
        raise ValueError("Missing signed authorization notice") from exc
    if not 1 <= notice.file_size <= MAX_NOTICE_BYTES or signature.file_size != 64:
        raise ValueError("Invalid authorization notice or signature size")
    release_public_key.verify(jar.read(signature), notice_digest(jar.read(notice)))


def make_manifest(entries):
    """Sign names and uncompressed bytes, independently of ZIP order and compression."""
    hashes = {}
    expanded_bytes = 0
    for name, data in entries:
        check_entry_name(name)
        if name in SEAL_FILES or name in hashes or len(data) > MAX_ENTRY_BYTES:
            raise ValueError(f"Invalid or duplicate host entry: {name}")
        expanded_bytes += len(data)
        if expanded_bytes > MAX_EXPANDED_BYTES:
            raise ValueError("Host entries exceed the native expanded size limit")
        hashes[name] = hashlib.sha256(data).hexdigest()
    check_host_layout(hashes)
    lines = ["Bootstrap-Version: 2.0", f"Entry-Count: {len(hashes)}", ""]
    lines.extend(f"{name} {hashes[name]}" for name in sorted(hashes))
    return ("\n".join(lines) + "\n").encode("utf-8")


def verify_host_seal(jar, public_key):
    names = set()
    expanded_bytes = 0
    for entry in jar.infolist():
        check_entry_name(entry.filename.removesuffix("/") if entry.is_dir() else entry.filename)
        if entry.filename in names:
            raise ValueError(f"Duplicate ZIP entry: {entry.filename}")
        if entry.file_size > MAX_ENTRY_BYTES:
            raise ValueError(f"Oversized ZIP entry: {entry.filename}")
        if entry.compress_type not in (ZIP_STORED, ZIP_DEFLATED) or entry.flag_bits & 1:
            raise ValueError(f"Unsupported ZIP encoding: {entry.filename}")
        if entry.is_dir() and entry.file_size:
            raise ValueError(f"Directory entry contains data: {entry.filename}")
        expanded_bytes += entry.file_size
        if expanded_bytes > MAX_EXPANDED_BYTES:
            raise ValueError("Host entries exceed the native expanded size limit")
        names.add(entry.filename)

    verify_host_notice(jar, public_key)
    data = jar.read(HOST_MANIFEST)
    signature = jar.read(HOST_SIGNATURE)
    if len(signature) != 64:
        raise ValueError("Invalid host seal signature size")
    public_key.verify(signature, hashlib.sha256(data).digest())
    payload = [entry for entry in jar.infolist() if not entry.is_dir() and entry.filename not in SEAL_FILES]
    if data != make_manifest((entry.filename, jar.read(entry)) for entry in payload):
        raise ValueError("Signed host inventory does not match JAR contents")
    return len(payload)

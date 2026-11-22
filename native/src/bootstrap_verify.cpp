// n5 从 NativeBridge 的 JVM CodeSource 定位宿主 JAR，并在派生解密密钥前验签。
// BOOTSTRAP.MF v2 覆盖所有文件条目的名称和原始内容，仅排除清单及其签名本身。
// 发布库必须验证签名清单，明文开发库仍可从类目录或普通 JAR 加载。

#include "axs_native.h"
#include <openssl/sha.h>
#include <openssl/evp.h>
#include <zlib.h>
#include <algorithm>
#include <cstring>
#include <exception>
#include <fstream>
#include <string>
#include <unordered_set>
#include <vector>
#ifdef _WIN32
#include <windows.h>
#endif

namespace {

// ─── 小工具 ─────────────────────────────────────────────────────

uint16_t rd16(const uint8_t *p) { return (uint16_t)(p[0] | (p[1] << 8)); }
uint32_t rd32(const uint8_t *p) {
    return (uint32_t)p[0] | ((uint32_t)p[1] << 8) | ((uint32_t)p[2] << 16) | ((uint32_t)p[3] << 24);
}

std::string hex_of(const uint8_t *d, size_t n) {
    static const char *H = "0123456789abcdef";
    std::string s;
    s.reserve(n * 2);
    for (size_t i = 0; i < n; i++) {
        s.push_back(H[d[i] >> 4]);
        s.push_back(H[d[i] & 15]);
    }
    return s;
}

constexpr const char *HOST_MANIFEST = "META-INF/BOOTSTRAP.MF";
constexpr const char *HOST_SIGNATURE = "META-INF/BOOTSTRAP.SIG";
constexpr size_t MAX_ENTRY_BYTES = 128ULL * 1024 * 1024;
constexpr size_t MAX_EXPANDED_BYTES = 512ULL * 1024 * 1024;
#ifdef AXS_REQUIRE_HOST_SEAL
constexpr bool REQUIRE_HOST_SEAL = true;
#else
constexpr bool REQUIRE_HOST_SEAL = false;
#endif

bool valid_zip_name(const std::string &name) {
    if (name.empty() || name.find('\\') != std::string::npos) return false;
    for (unsigned char c : name) {
        if (c < 32 || c == 127) return false;
    }
    size_t start = 0;
    while (start < name.size()) {
        size_t end = name.find('/', start);
        if (end == std::string::npos) end = name.size();
        auto segment = name.substr(start, end - start);
        if (segment.empty() || segment == "." || segment == "..") return false;
        start = end + 1;
    }
    return true;
}

// ─── ZIP 只读解析（EOCD→中央目录→本地头；STORED/DEFLATE） ──────

struct ZipEntry {
    std::string name;
    uint16_t method = 0;
    uint32_t compSize = 0;
    uint32_t uncompSize = 0;
    uint32_t localOff = 0;
};

// 读取全部中央目录条目（失败返回 false）
bool zip_read_central(const std::vector<uint8_t> &jar, std::vector<ZipEntry> &out) {
    constexpr uint32_t EOCD_SIG = 0x06054b50;
    if (jar.size() < 22) return false;
    // EOCD 最多在文件末尾 22+65535 字节内
    size_t lo = jar.size() > 22 + 65535 ? jar.size() - 22 - 65535 : 0;
    size_t eocd = SIZE_MAX;
    for (size_t i = jar.size() - 22; ; i--) {
        if (rd32(jar.data() + i) == EOCD_SIG && i + 22 + rd16(jar.data() + i + 20) == jar.size()) {
            eocd = i;
            break;
        }
        if (i == lo) break;
    }
    if (eocd == SIZE_MAX) return false;
    size_t cdOff = rd32(jar.data() + eocd + 16);
    size_t cdSize = rd32(jar.data() + eocd + 12);
    uint16_t total = rd16(jar.data() + eocd + 10);
    const auto *header = jar.data() + eocd;
    if (rd16(header + 4) != 0 || rd16(header + 6) != 0 || rd16(header + 8) != total
        || cdOff > eocd || cdSize != eocd - cdOff) return false;
    size_t pos = cdOff;
    size_t expanded = 0;
    std::unordered_set<std::string> names;
    for (uint16_t k = 0; k < total; k++) {
        if (pos + 46 > eocd || rd32(jar.data() + pos) != 0x02014b50) return false;
        if ((rd16(jar.data() + pos + 8) & 1) != 0 || rd16(jar.data() + pos + 34) != 0) return false;
        ZipEntry e;
        e.method = rd16(jar.data() + pos + 10);
        e.compSize = rd32(jar.data() + pos + 20);
        e.uncompSize = rd32(jar.data() + pos + 24);
        uint16_t nl = rd16(jar.data() + pos + 28);
        uint16_t el = rd16(jar.data() + pos + 30);
        uint16_t cl = rd16(jar.data() + pos + 32);
        e.localOff = rd32(jar.data() + pos + 42);
        if (pos + 46 + nl + el + cl > eocd || e.localOff >= cdOff
            || e.uncompSize > MAX_ENTRY_BYTES) return false;
        e.name.assign(reinterpret_cast<const char *>(jar.data() + pos + 46), nl);
        if (!valid_zip_name(e.name) || !names.insert(e.name).second) return false;
        if (e.name.back() == '/' && e.uncompSize != 0) return false;
        expanded += e.uncompSize;
        if (expanded > MAX_EXPANDED_BYTES) return false;
        out.push_back(std::move(e));
        pos += 46 + nl + el + cl;
    }
    return pos == eocd;
}

// 提取条目数据（STORED 直接拷贝，DEFLATE 用 zlib raw inflate）
bool zip_extract(const std::vector<uint8_t> &jar, const ZipEntry &e, std::vector<uint8_t> &out) {
    size_t offset = e.localOff;
    if (offset + 30 > jar.size() || rd32(jar.data() + offset) != 0x04034b50) return false;
    if (e.method != rd16(jar.data() + offset + 8) || (rd16(jar.data() + offset + 6) & 1) != 0) return false;
    uint16_t nl = rd16(jar.data() + offset + 26);
    uint16_t el = rd16(jar.data() + offset + 28);
    size_t dataOff = offset + 30 + nl + el;
    if (dataOff + e.compSize > jar.size()) return false;
    if (nl != e.name.size() || memcmp(jar.data() + offset + 30, e.name.data(), nl) != 0) return false;
    const uint8_t *src = jar.data() + dataOff;

    if (e.method == 0) {
        if (e.compSize != e.uncompSize) return false;
        out.assign(src, src + e.compSize);
        return true;
    }
    if (e.method != 8) return false;

    out.resize(e.uncompSize);
    uint8_t empty = 0;
    z_stream strm{};
    strm.next_in = const_cast<uint8_t *>(src);
    strm.avail_in = e.compSize;
    strm.next_out = out.empty() ? &empty : out.data();
    strm.avail_out = out.empty() ? 1 : e.uncompSize;
    if (inflateInit2(&strm, -MAX_WBITS) != Z_OK) return false;
    int ret = inflate(&strm, Z_FINISH);
    inflateEnd(&strm);
    return ret == Z_STREAM_END && strm.total_out == e.uncompSize && strm.total_in == e.compSize;
}

// ─── 通过 JVM CodeSource 定位插件 JAR（不可被 jar 内 patch 伪造） ──

// 把 URL 字符串（file:/path、file:///path）转为本地文件路径；非 file: 返回空
std::string url_to_path(const std::string &url) {
    std::string u = url;
    if (u.rfind("jar:", 0) == 0) {
        u = u.substr(4);
        auto bang = u.find("!/");
        if (bang != std::string::npos) u = u.substr(0, bang);
    }
    if (u.rfind("file:", 0) != 0) return "";
    u = u.substr(5);
    // 规范化前导斜杠：file:/D:/x、file:///D:/x 等形态统一处理。
    // 去掉全部前导 / 后，若余下是 "X:/..." 则直接作为 Windows 盘符路径；
    // 否则是 Unix 绝对路径，补回单个 /。
    size_t slashes = 0;
    while (slashes < u.size() && u[slashes] == '/') slashes++;
    std::string rest = u.substr(slashes);
    if (rest.size() > 2 && rest[1] == ':' &&
        ((rest[0] >= 'A' && rest[0] <= 'Z') || (rest[0] >= 'a' && rest[0] <= 'z'))) {
        u = rest;
#ifdef _WIN32
    } else if (slashes == 2) {
        u = "//" + rest; // Windows UNC path.
#endif
    } else if (slashes > 0) {
        u = "/" + rest;
    }
    // %XX 解码
    std::string out;
    out.reserve(u.size());
    for (size_t i = 0; i < u.size(); i++) {
        if (u[i] == '%' && i + 2 < u.size()) {
            int hi = 0, lo = 0;
            auto v = [](char c) -> int {
                if (c >= '0' && c <= '9') return c - '0';
                if (c >= 'a' && c <= 'f') return c - 'a' + 10;
                if (c >= 'A' && c <= 'F') return c - 'A' + 10;
                return -1;
            };
            hi = v(u[i + 1]); lo = v(u[i + 2]);
            if (hi >= 0 && lo >= 0) {
                if (hi == 0 && lo == 0) return "";
                out.push_back((char)((hi << 4) | lo));
                i += 2;
                continue;
            }
        }
        out.push_back(u[i]);
    }
    return out;
}

std::string plugin_jar_path(JNIEnv *env) {
    jclass bridge = env->FindClass("xuanmo/arcartxsuite/security/NativeBridge");
    if (!bridge) { if (env->ExceptionCheck()) env->ExceptionClear(); return ""; }

    jclass classCls = env->FindClass("java/lang/Class");
    if (!classCls) { if (env->ExceptionCheck()) env->ExceptionClear(); return ""; }
    jmethodID getPD = env->GetMethodID(classCls, "getProtectionDomain",
                                     "()Ljava/security/ProtectionDomain;");
    if (!getPD) { if (env->ExceptionCheck()) env->ExceptionClear(); return ""; }
    jobject pd = env->CallObjectMethod(bridge, getPD);
    if (env->ExceptionCheck() || !pd) { env->ExceptionClear(); return ""; }

    jclass pdCls = env->GetObjectClass(pd);
    jmethodID getCS = env->GetMethodID(pdCls, "getCodeSource", "()Ljava/security/CodeSource;");
    if (!getCS) { if (env->ExceptionCheck()) env->ExceptionClear(); return ""; }
    jobject cs = env->CallObjectMethod(pd, getCS);
    if (env->ExceptionCheck() || !cs) { env->ExceptionClear(); return ""; }

    jclass csCls = env->GetObjectClass(cs);
    jmethodID getLoc = env->GetMethodID(csCls, "getLocation", "()Ljava/net/URL;");
    if (!getLoc) { if (env->ExceptionCheck()) env->ExceptionClear(); return ""; }
    jobject url = env->CallObjectMethod(cs, getLoc);
    if (env->ExceptionCheck() || !url) { env->ExceptionClear(); return ""; }

    jclass urlCls = env->GetObjectClass(url);
    jmethodID toUri = env->GetMethodID(urlCls, "toURI", "()Ljava/net/URI;");
    if (!toUri) { if (env->ExceptionCheck()) env->ExceptionClear(); return ""; }
    jobject uri = env->CallObjectMethod(url, toUri);
    if (env->ExceptionCheck() || !uri) { env->ExceptionClear(); return ""; }
    jclass uriCls = env->GetObjectClass(uri);
    jmethodID toAscii = env->GetMethodID(uriCls, "toASCIIString", "()Ljava/lang/String;");
    if (!toAscii) { if (env->ExceptionCheck()) env->ExceptionClear(); return ""; }
    auto pathStr = static_cast<jstring>(env->CallObjectMethod(uri, toAscii));
    if (env->ExceptionCheck() || !pathStr) { env->ExceptionClear(); return ""; }

    const char *utf = env->GetStringUTFChars(pathStr, nullptr);
    std::string urlText(utf ? utf : "");
    if (utf) env->ReleaseStringUTFChars(pathStr, utf);
    return url_to_path(urlText);
}

void sha256_of(const uint8_t *d, size_t n, uint8_t out[32]) { SHA256(d, n, out); }

bool verify_inventory(const std::vector<uint8_t> &jar, const std::vector<ZipEntry> &entries,
                      const std::vector<uint8_t> &manifest) {
    std::vector<const ZipEntry *> files;
    files.reserve(entries.size());
    for (const auto &e : entries) {
        if (e.name.back() != '/' && e.name != HOST_MANIFEST && e.name != HOST_SIGNATURE) {
            files.push_back(&e);
        }
    }
    std::sort(files.begin(), files.end(), [](const ZipEntry *a, const ZipEntry *b) { return a->name < b->name; });
    size_t position = 0;
    auto consume = [&](const std::string &expected) {
        if (expected.size() > manifest.size() - position
            || memcmp(manifest.data() + position, expected.data(), expected.size()) != 0) return false;
        position += expected.size();
        return true;
    };
    if (!consume("Bootstrap-Version: 2.0\nEntry-Count: " + std::to_string(files.size()) + "\n\n")) return false;

    std::vector<uint8_t> data;
    for (const auto *entry : files) {
        if (!zip_extract(jar, *entry, data)) return false;
        uint8_t hash[32];
        sha256_of(data.data(), data.size(), hash);
        if (!consume(entry->name + " " + hex_of(hash, sizeof(hash)) + "\n")) return false;
    }
    return position == manifest.size();
}

const ZipEntry *find_entry(const std::vector<ZipEntry> &es, const std::string &name) {
    for (const auto &e : es) if (e.name == name) return &e;
    return nullptr;
}

// 按 UTF-8 路径打开文件。Windows 的 ifstream(const char*) 走 ANSI 代码页，
// 中文/非 ASCII 路径会打不开 → 转换为 UTF-16 宽路径用 _wfopen（标准 CRT，
// MinGW/MSVC 均支持；wifstream(wstring) 只是 MSVC 扩展，MinGW 不可用）。
bool open_file_utf8(const std::string &path, std::vector<uint8_t> &out) {
#ifdef _WIN32
    int wlen = MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, path.c_str(), -1, nullptr, 0);
    if (wlen <= 1) return false;
    std::wstring wpath(static_cast<size_t>(wlen), L'\0');
    if (!MultiByteToWideChar(CP_UTF8, MB_ERR_INVALID_CHARS, path.c_str(), -1, wpath.data(), wlen)) return false;
    FILE *fp = _wfopen(wpath.c_str(), L"rb");
    if (!fp) return false;
    fseek(fp, 0, SEEK_END);
    long sz = ftell(fp);
    if (sz <= 0 || sz > 512L * 1024 * 1024) { fclose(fp); return false; }
    out.resize(static_cast<size_t>(sz));
    fseek(fp, 0, SEEK_SET);
    bool ok = fread(out.data(), 1, static_cast<size_t>(sz), fp) == static_cast<size_t>(sz);
    fclose(fp);
    return ok;
#else
    std::ifstream f(path, std::ios::binary | std::ios::ate);
    if (!f) return false;
    std::streamsize sz = f.tellg();
    if (sz <= 0 || sz > 512LL * 1024 * 1024) return false;
    out.resize(static_cast<size_t>(sz));
    f.seekg(0);
    return static_cast<bool>(f.read(reinterpret_cast<char *>(out.data()), sz));
#endif
}

bool verify_sig_32(const uint8_t hash[32], const uint8_t sig[64]) {
    const uint8_t *pub = axb_sign_pubkey();
    EVP_PKEY *pkey = EVP_PKEY_new_raw_public_key(EVP_PKEY_ED25519, nullptr, pub, 32);
    if (!pkey) return false;
    bool ok = false;
    EVP_MD_CTX *ctx = EVP_MD_CTX_new();
    if (ctx) {
        if (EVP_DigestVerifyInit(ctx, nullptr, nullptr, nullptr, pkey) == 1) {
            ok = EVP_DigestVerify(ctx, sig, 64, hash, 32) == 1;
        }
        EVP_MD_CTX_free(ctx);
    }
    EVP_PKEY_free(pkey);
    return ok;
}

} // namespace

bool verify_bootstrap_classes(JNIEnv *env) {
    try {
        std::string path = plugin_jar_path(env);
        if (path.empty()) return !REQUIRE_HOST_SEAL;
        std::vector<uint8_t> jar;
        if (!open_file_utf8(path, jar)) return !REQUIRE_HOST_SEAL;

        std::vector<ZipEntry> entries;
        if (!zip_read_central(jar, entries)) return !REQUIRE_HOST_SEAL;
        const auto *pmf = find_entry(entries, "META-INF/PROTECTION.MF");
        const auto *bmf = find_entry(entries, HOST_MANIFEST);
        const auto *bsig = find_entry(entries, HOST_SIGNATURE);
        bool hasEncryptedClass = std::any_of(entries.begin(), entries.end(), [](const ZipEntry &entry) {
            const auto &name = entry.name;
            return name.rfind("ENCRYPTED/", 0) == 0 && name.compare(name.size() - 4, 4, ".enc") == 0;
        });
        bool hasWrappedNative = find_entry(entries, "native/axs-native.enc") || find_entry(entries, "native/libaxs-native.enc");
        if (!REQUIRE_HOST_SEAL && !pmf && !bmf && !bsig && !hasEncryptedClass && !hasWrappedNative) return true;
        if (!pmf || !bmf || !bsig || !hasEncryptedClass || !hasWrappedNative || bsig->uncompSize != 64) return false;

        std::vector<uint8_t> manifest, signature;
        if (!zip_extract(jar, *bmf, manifest) || !zip_extract(jar, *bsig, signature)) return false;
        uint8_t manifestHash[32];
        sha256_of(manifest.data(), manifest.size(), manifestHash);
        return verify_sig_32(manifestHash, signature.data()) && verify_inventory(jar, entries, manifest);
    } catch (const std::exception &) {
        return false;
    }
}

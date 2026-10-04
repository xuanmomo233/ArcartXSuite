// bootstrap_verify.cpp —— JAR 明文层完整性校验（native 侧信任锚点）
//
// 背景：PROTECTION.MF 的 Merkle+Ed25519 校验与 Java 层 verifyJarIntegrity 均只能
// 覆盖 ENCRYPTED/*.enc 条目；约十来个必须保持明文的引导类（ProtectedClassLoader、
// NativeBridge、JvmAntiDebug、api/auth 包等）可被任意字节码 patch 而不被发现——
// 攻击者插桩 defineEncrypted 即可旁路全部逐类加密。
//
// 本文件把校验推进 native：构建期 encrypt-jar.py 生成
//   META-INF/BOOTSTRAP.MF —— 全部明文 .class 条目的 name+SHA-256 清单（排序、逐行）
//   META-INF/BOOTSTRAP.SIG —— 对 SHA256(BOOTSTRAP.MF 原文) 的 Ed25519 签名（64B）
// n5(initProtection) 时 native 端：
//   1) 通过 JNI 反射 NativeBridge.class 的 ProtectionDomain.CodeSource 定位插件 JAR
//      （来源是 JVM 类加载元数据，jar 内被 patch 的类无法伪造）；
//   2) 自行解析 ZIP（EOCD→中央目录→本地头），不依赖任何可被 patch 的 Java 代码；
//   3) 验签 BOOTSTRAP.MF（公钥 = integrity_check.cpp 内嵌的同一 ed25519_public_key）；
//   4) 逐一比对 jar 内全部非 ENCRYPTED/ 的 .class 条目哈希与清单（含集合相等性，
//      多余/缺失/不符均为篡改）；
//   5) 顺带在 native 侧复核 .enc Merkle 根 + Ed25519 签名，使 Java 侧
//      verifyJarIntegrity 即使被 patch 也不影响判决。
//
// 放行规则（兼容与可用性约束）：
//   - jar 内无 META-INF/PROTECTION.MF → 明文 dev jar → 跳过（返回 true）；
//   - CodeSource 无法定位到 file: 路径（exotic 类加载环境）→ 跳过（fail-open）；
//   - 其余缺失/验签失败/哈希不符 → 拒绝（fail-closed，n5 返回非 0）。

#include "axs_native.h"
#include <openssl/sha.h>
#include <openssl/evp.h>
#include <zlib.h>
#include <algorithm>
#include <cstring>
#include <fstream>
#include <string>
#include <unordered_map>
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

bool hex_to_bytes(const std::string &hex, uint8_t *out, size_t n) {
    if (hex.size() != n * 2) return false;
    auto hv = [](char c) -> int {
        if (c >= '0' && c <= '9') return c - '0';
        if (c >= 'a' && c <= 'f') return c - 'a' + 10;
        if (c >= 'A' && c <= 'F') return c - 'A' + 10;
        return -1;
    };
    for (size_t i = 0; i < n; i++) {
        int hi = hv(hex[i * 2]), lo = hv(hex[i * 2 + 1]);
        if (hi < 0 || lo < 0) return false;
        out[i] = (uint8_t)((hi << 4) | lo);
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
        if (rd32(jar.data() + i) == EOCD_SIG) { eocd = i; break; }
        if (i == lo) break;
    }
    if (eocd == SIZE_MAX) return false;
    uint32_t cdOff = rd32(jar.data() + eocd + 16);
    uint16_t total = rd16(jar.data() + eocd + 10);
    size_t pos = cdOff;
    for (uint16_t k = 0; k < total; k++) {
        if (pos + 46 > jar.size() || rd32(jar.data() + pos) != 0x02014b50) return false;
        ZipEntry e;
        e.method = rd16(jar.data() + pos + 10);
        e.compSize = rd32(jar.data() + pos + 20);
        e.uncompSize = rd32(jar.data() + pos + 24);
        uint16_t nl = rd16(jar.data() + pos + 28);
        uint16_t el = rd16(jar.data() + pos + 30);
        uint16_t cl = rd16(jar.data() + pos + 32);
        e.localOff = rd32(jar.data() + pos + 42);
        if (pos + 46 + nl > jar.size()) return false;
        e.name.assign(reinterpret_cast<const char *>(jar.data() + pos + 46), nl);
        out.push_back(std::move(e));
        pos += 46 + nl + el + cl;
    }
    return true;
}

// 提取条目数据（STORED 直接拷贝，DEFLATE 用 zlib raw inflate）
bool zip_extract(const std::vector<uint8_t> &jar, const ZipEntry &e, std::vector<uint8_t> &out) {
    if (e.localOff + 30 > jar.size() || rd32(jar.data() + e.localOff) != 0x04034b50) return false;
    uint16_t nl = rd16(jar.data() + e.localOff + 26);
    uint16_t el = rd16(jar.data() + e.localOff + 28);
    size_t dataOff = e.localOff + 30 + nl + el;
    if (dataOff + e.compSize > jar.size()) return false;
    const uint8_t *src = jar.data() + dataOff;

    if (e.method == 0) {
        out.assign(src, src + e.compSize);
        return true;
    }
    if (e.method != 8) return false;

    out.resize(e.uncompSize);
    z_stream strm{};
    strm.next_in = const_cast<uint8_t *>(src);
    strm.avail_in = e.compSize;
    strm.next_out = out.data();
    strm.avail_out = e.uncompSize;
    if (inflateInit2(&strm, -MAX_WBITS) != Z_OK) return false;
    int ret = inflate(&strm, Z_FINISH);
    inflateEnd(&strm);
    if (ret != Z_STREAM_END) { out.clear(); return false; }
    return true;
}

// ─── 通过 JVM CodeSource 定位插件 JAR（不可被 jar 内 patch 伪造） ──

// 把 URL 字符串（file:/path、file:///path）转为本地文件路径；非 file: 返回空
std::string url_to_path(const std::string &url) {
    std::string u = url;
    if (u.rfind("jar:", 0) == 0) u = u.substr(4);
    if (u.rfind("file:", 0) != 0) return "";
    u = u.substr(5);
    // 去掉尾部 !/...（如有）
    auto bang = u.find('!');
    if (bang != std::string::npos) u = u.substr(0, bang);
    // 规范化前导斜杠：file:/D:/x、file:///D:/x 等形态统一处理。
    // 去掉全部前导 / 后，若余下是 "X:/..." 则直接作为 Windows 盘符路径；
    // 否则是 Unix 绝对路径，补回单个 /。
    size_t slashes = 0;
    while (slashes < u.size() && u[slashes] == '/') slashes++;
    std::string rest = u.substr(slashes);
    if (rest.size() > 2 && rest[1] == ':' &&
        ((rest[0] >= 'A' && rest[0] <= 'Z') || (rest[0] >= 'a' && rest[0] <= 'z'))) {
        u = rest;
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
            if (hi >= 0 && lo >= 0) { out.push_back((char)((hi << 4) | lo)); i += 2; continue; }
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
    jmethodID toStr = env->GetMethodID(urlCls, "toString", "()Ljava/lang/String;");
    if (!toStr) { if (env->ExceptionCheck()) env->ExceptionClear(); return ""; }
    auto pathStr = static_cast<jstring>(env->CallObjectMethod(url, toStr));
    if (env->ExceptionCheck() || !pathStr) { env->ExceptionClear(); return ""; }

    const char *utf = env->GetStringUTFChars(pathStr, nullptr);
    std::string urlText(utf ? utf : "");
    if (utf) env->ReleaseStringUTFChars(pathStr, utf);
    return url_to_path(urlText);
}

// ─── BOOTSTRAP.MF 解析：头部行 + 空行 + "name sha256hex" 逐行 ────

bool parse_manifest(const std::vector<uint8_t> &mf,
                    std::unordered_map<std::string, std::string> &entries) {
    std::string text(reinterpret_cast<const char *>(mf.data()), mf.size());
    size_t pos = 0;
    bool inEntries = false;
    while (pos <= text.size()) {
        size_t eol = text.find('\n', pos);
        if (eol == std::string::npos) eol = text.size();
        std::string line = text.substr(pos, eol - pos);
        if (!line.empty() && line.back() == '\r') line.pop_back();
        pos = eol + 1;

        if (!inEntries) {
            if (line.empty()) inEntries = true;
            continue;
        }
        if (line.empty()) continue;
        size_t sp = line.rfind(' ');
        if (sp == std::string::npos) return false;
        std::string name = line.substr(0, sp);
        std::string hex = line.substr(sp + 1);
        if (name.size() < 7 || name.compare(name.size() - 6, 6, ".class") != 0) return false;
        if (hex.size() != 64) return false;
        entries[name] = hex;
    }
    return inEntries && !entries.empty();
}

// ─── .enc Merkle 根（与 encrypt-jar.py / ProtectionInit 同构） ────

void sha256_of(const uint8_t *d, size_t n, uint8_t out[32]) { SHA256(d, n, out); }

bool merkle_over_enc(const std::vector<uint8_t> &jar,
                     const std::vector<ZipEntry> &entries,
                     uint8_t root[32]) {
    std::vector<std::string> encNames;
    for (const auto &e : entries) {
        if (e.name.rfind("ENCRYPTED/", 0) == 0 && e.name.size() > 4
            && e.name.compare(e.name.size() - 4, 4, ".enc") == 0) {
            encNames.push_back(e.name);
        }
    }
    std::sort(encNames.begin(), encNames.end());
    // 空集：与 encrypt-jar.py（sha256(b"")）和 ProtectionInit（md.digest(空)）保持一致，
    // 即 SHA-256("")，而非全零——否则含 PROTECTION.MF 但无 .enc 的 jar 会被误拒。
    if (encNames.empty()) { SHA256(nullptr, 0, root); return true; }

    std::vector<std::vector<uint8_t>> level;
    level.reserve(encNames.size());
    for (const auto &name : encNames) {
        auto it = std::find_if(entries.begin(), entries.end(),
                               [&](const ZipEntry &e) { return e.name == name; });
        std::vector<uint8_t> data;
        if (!zip_extract(jar, *it, data)) return false;
        std::vector<uint8_t> h(32);
        sha256_of(data.data(), data.size(), h.data());
        level.push_back(std::move(h));
    }
    while (level.size() > 1) {
        std::vector<std::vector<uint8_t>> next;
        for (size_t i = 0; i < level.size(); i += 2) {
            uint8_t buf[64];
            memcpy(buf, level[i].data(), 32);
            if (i + 1 < level.size()) memcpy(buf + 32, level[i + 1].data(), 32);
            else memcpy(buf + 32, level[i].data(), 32);
            std::vector<uint8_t> h(32);
            sha256_of(buf, 64, h.data());
            next.push_back(std::move(h));
        }
        level = std::move(next);
    }
    memcpy(root, level[0].data(), 32);
    return true;
}

// 从 PROTECTION.MF 文本中取 "Key: value" 属性
std::string mf_prop(const std::vector<uint8_t> &mf, const std::string &key) {
    std::string text(reinterpret_cast<const char *>(mf.data()), mf.size());
    std::string prefix = key + ": ";
    size_t p = 0;
    while (p < text.size()) {
        size_t eol = text.find('\n', p);
        if (eol == std::string::npos) eol = text.size();
        if (text.compare(p, prefix.size(), prefix) == 0) {
            std::string v = text.substr(p + prefix.size(), eol - p - prefix.size());
            while (!v.empty() && (v.back() == '\r' || v.back() == ' ')) v.pop_back();
            return v;
        }
        p = eol + 1;
    }
    return "";
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
    int wlen = MultiByteToWideChar(CP_UTF8, 0, path.c_str(), -1, nullptr, 0);
    if (wlen <= 1) return false;
    std::wstring wpath(static_cast<size_t>(wlen - 1), L'\0');
    MultiByteToWideChar(CP_UTF8, 0, path.c_str(), -1, wpath.data(), wlen);
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

// ─── 入口：n5 调用。true = 通过/无需校验；false = 篡改（fail-closed） ──

bool verify_bootstrap_classes(JNIEnv *env) {
    std::string path = plugin_jar_path(env);
    if (path.empty()) {
        // exotic 环境无法定位 jar → fail-open，不阻塞启动
        return true;
    }

    std::vector<uint8_t> jar;
    if (!open_file_utf8(path, jar)) return true; // jar 不可读 → fail-open

    std::vector<ZipEntry> entries;
    if (!zip_read_central(jar, entries)) return true; // 非 zip 结构 → 不判

    // 明文 dev jar：无 PROTECTION.MF → 跳过全部校验
    const ZipEntry *pmf = find_entry(entries, "META-INF/PROTECTION.MF");
    if (!pmf) return true;

    // 加密 JAR：BOOTSTRAP 清单必须存在（新一代构建必有；剥离即拒绝）
    const ZipEntry *bmf = find_entry(entries, "META-INF/BOOTSTRAP.MF");
    const ZipEntry *bsig = find_entry(entries, "META-INF/BOOTSTRAP.SIG");
    if (!bmf || !bsig) return false;

    std::vector<uint8_t> mfData, sigData;
    if (!zip_extract(jar, *bmf, mfData)) return false;
    if (!zip_extract(jar, *bsig, sigData)) return false;
    if (sigData.size() != 64) return false;

    // 验签 BOOTSTRAP.MF（Ed25519，消息 = SHA256(清单原文)）
    uint8_t mfHash[32];
    sha256_of(mfData.data(), mfData.size(), mfHash);
    if (!verify_sig_32(mfHash, sigData.data())) return false;

    // 比对全部明文 .class 条目与清单（集合相等 + 哈希一致）
    std::unordered_map<std::string, std::string> manifest;
    if (!parse_manifest(mfData, manifest)) return false;

    size_t plainCount = 0;
    for (const auto &e : entries) {
        bool isClass = e.name.size() > 6
            && e.name.compare(e.name.size() - 6, 6, ".class") == 0;
        if (!isClass || e.name.rfind("ENCRYPTED/", 0) == 0) continue;
        auto it = manifest.find(e.name);
        if (it == manifest.end()) return false; // 清单外的明文类 = 注入
        std::vector<uint8_t> data;
        if (!zip_extract(jar, e, data)) return false;
        uint8_t h[32];
        sha256_of(data.data(), data.size(), h);
        if (hex_of(h, 32) != it->second) return false;
        plainCount++;
    }
    if (plainCount != manifest.size()) return false; // 清单内条目缺失

    // 顺带复核 .enc Merkle 根 + 签名（Java 侧同校验的 native 锚定版本）
    std::vector<uint8_t> pmfData;
    if (!zip_extract(jar, *pmf, pmfData)) return false;
    std::string rootHex = mf_prop(pmfData, "Integrity-Hash");
    std::string sigHex = mf_prop(pmfData, "Signature");
    if (rootHex.empty() || sigHex.empty()) return false;
    uint8_t expectRoot[32], sig[64];
    if (!hex_to_bytes(rootHex, expectRoot, 32) || !hex_to_bytes(sigHex, sig, 64)) return false;
    uint8_t computed[32];
    if (!merkle_over_enc(jar, entries, computed)) return false;
    if (memcmp(computed, expectRoot, 32) != 0) return false;
    if (!verify_sig_32(computed, sig)) return false;

    return true;
}

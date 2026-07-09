#include "axs_native.h"
#include <openssl/evp.h>
#include <openssl/sha.h>
#include <algorithm>
#include <cstring>
#include <fstream>
#include <string>
#include <vector>

static constexpr bool kHardenedBuild = (AXS_HARDENED != 0);

// Ed25519 公钥（构建时由 generate-keys.py 生成并嵌入）
// !!! 以下占位值必须在构建前替换为真实公钥 !!!
static const uint8_t ed25519_public_key[32] = {
    0xC6, 0xEA, 0xDC, 0x02, 0x0B, 0xAF, 0x2A, 0x21,
    0x31, 0x59, 0x27, 0x04, 0xE6, 0x46, 0xCF, 0x7F,
    0x43, 0x60, 0xE3, 0x8E, 0xA4, 0x48, 0x10, 0x8B,
    0x08, 0x11, 0x5C, 0x96, 0xA2, 0x4C, 0x65, 0x69
};

// 响应签名公钥（构建时由 generate-keys.py 生成并嵌入）
// !!! 以下占位值必须在构建前替换为真实公钥 !!!
static const uint8_t ed25519_response_public_key[32] = {
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
};

static bool is_all_zero_key(const uint8_t *key) {
    for (int i = 0; i < 32; ++i) {
        if (key[i] != 0x00) {
            return false;
        }
    }
    return true;
}

static bool ed25519_verify_with_key(
    const uint8_t *pubkey,
    const uint8_t *message,
    size_t message_len,
    const uint8_t *signature,
    size_t signature_len
) {
    EVP_PKEY *pkey = EVP_PKEY_new_raw_public_key(EVP_PKEY_ED25519, nullptr, pubkey, 32);
    if (!pkey) {
        return false;
    }

    bool ok = false;
    EVP_MD_CTX *mdctx = EVP_MD_CTX_new();
    if (mdctx) {
        if (EVP_DigestVerifyInit(mdctx, nullptr, nullptr, nullptr, pkey) == 1) {
            int ret = EVP_DigestVerify(mdctx, signature, signature_len, message, message_len);
            ok = (ret == 1);
        }
        EVP_MD_CTX_free(mdctx);
    }
    EVP_PKEY_free(pkey);
    return ok;
}

// 供 module_decrypt.cpp 复用同一 Ed25519 公钥验证 .axb 签名（单一来源，避免重复嵌入）。
const uint8_t* axb_sign_pubkey() { return ed25519_public_key; }

const uint8_t* axb_response_sign_pubkey() { return ed25519_response_public_key; }

/**
 * 验证 Merkle 根哈希的 Ed25519 签名。
 * rootHash: 32 字节 SHA-256
 * signature: 64 字节 Ed25519 签名
 * 返回 true = 签名有效
 */
jboolean verifyIntegrity(JNIEnv *env, jclass clazz, jbyteArray rootHash, jbyteArray signature) {
    if (!rootHash || !signature) return JNI_FALSE;
    jsize hash_len = env->GetArrayLength(rootHash);
    jsize sig_len = env->GetArrayLength(signature);
    if (hash_len != 32 || sig_len != 64) return JNI_FALSE;

    jbyte *hash_raw = env->GetByteArrayElements(rootHash, nullptr);
    jbyte *sig_raw = env->GetByteArrayElements(signature, nullptr);

    jboolean result = JNI_FALSE;
    if (hash_raw && sig_raw) {
        result = ed25519_verify_with_key(
            ed25519_public_key,
            reinterpret_cast<const uint8_t *>(hash_raw),
            32,
            reinterpret_cast<const uint8_t *>(sig_raw),
            64
        ) ? JNI_TRUE : JNI_FALSE;
    }

    if (hash_raw) env->ReleaseByteArrayElements(rootHash, hash_raw, JNI_ABORT);
    if (sig_raw) env->ReleaseByteArrayElements(signature, sig_raw, JNI_ABORT);
    return result;
}

jboolean verifyResponseSig(JNIEnv *env, jclass clazz, jlong timestamp, jbyteArray body, jbyteArray signature) {
    if (!body || !signature) return JNI_FALSE;
    if (kHardenedBuild && is_all_zero_key(ed25519_response_public_key)) return JNI_FALSE;

    jsize body_len = env->GetArrayLength(body);
    jsize sig_len = env->GetArrayLength(signature);
    if (body_len < 0 || sig_len != 64) return JNI_FALSE;

    jbyte *body_raw = env->GetByteArrayElements(body, nullptr);
    jbyte *sig_raw = env->GetByteArrayElements(signature, nullptr);
    if (!body_raw || !sig_raw) {
        if (body_raw) env->ReleaseByteArrayElements(body, body_raw, JNI_ABORT);
        if (sig_raw) env->ReleaseByteArrayElements(signature, sig_raw, JNI_ABORT);
        return JNI_FALSE;
    }

    std::string ts_text = std::to_string(static_cast<long long>(timestamp));
    std::string message;
    message.reserve(ts_text.size() + 1 + static_cast<size_t>(body_len));
    message.append(ts_text);
    message.push_back('\n');
    message.append(reinterpret_cast<char *>(body_raw), static_cast<size_t>(body_len));

    bool ok = ed25519_verify_with_key(
        ed25519_response_public_key,
        reinterpret_cast<const uint8_t *>(message.data()),
        message.size(),
        reinterpret_cast<const uint8_t *>(sig_raw),
        64
    );

    env->ReleaseByteArrayElements(body, body_raw, JNI_ABORT);
    env->ReleaseByteArrayElements(signature, sig_raw, JNI_ABORT);
    return ok ? JNI_TRUE : JNI_FALSE;
}

jboolean responseVerifyActive(JNIEnv *env, jclass clazz) {
    (void)env;
    (void)clazz;
    return (kHardenedBuild && !is_all_zero_key(ed25519_response_public_key)) ? JNI_TRUE : JNI_FALSE;
}

// ─── Native 自校验 ──────────────────────────────
// 编译后由 post-build 脚本（inject-self-hash.py）计算并嵌入实际哈希。
// 槽位布局：16 字节 MAGIC 紧跟 32 字节 expected_self_hash 槽，二者在二进制中必须连续，
// 故合并为单一数组——分成两个变量时 const MAGIC 落在 .rodata、可变槽落在 .data，
// 不再相邻，注入脚本按“MAGIC+16”定位到的将不是真正的比较槽位。
// used 保证即使 dev 构建（AXS_PRODUCTION=OFF）未引用也会写入二进制供注入脚本定位；
// volatile 防止 -O2 把占位初值常量折叠进比较逻辑。
#if defined(__GNUC__) || defined(__clang__)
#define AXS_USED __attribute__((used))
#else
#define AXS_USED
#endif

static volatile AXS_USED uint8_t g_self_hash_block[48] = {
    // [0..15] MAGIC
    0xA3, 0x51, 0x7D, 0x9C, 0xE4, 0x2B, 0x18, 0xF0,
    0x6D, 0xC7, 0x91, 0x34, 0xBA, 0x5E, 0xD2, 0x88,
    // [16..47] expected_self_hash 槽（占位全 0xFF，注入后为真实哈希）
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF
};

#ifdef AXS_PRODUCTION
namespace {
constexpr size_t kSelfHashMagicSize = 16;
constexpr size_t kSelfHashSlotSize = 32;

// 从 volatile 存储快照出 MAGIC 与 expected_self_hash，避免 volatile 直接参与 memcmp。
void load_self_hash_block(uint8_t magic_out[kSelfHashMagicSize],
                          uint8_t hash_out[kSelfHashSlotSize]) {
    for (size_t i = 0; i < kSelfHashMagicSize; ++i) {
        magic_out[i] = g_self_hash_block[i];
    }
    for (size_t i = 0; i < kSelfHashSlotSize; ++i) {
        hash_out[i] = g_self_hash_block[kSelfHashMagicSize + i];
    }
}
bool is_all_ff_placeholder(const uint8_t hash[kSelfHashSlotSize]) {
    for (size_t i = 0; i < kSelfHashSlotSize; ++i) {
        if (hash[i] != 0xFF) {
            return false;
        }
    }
    return true;
}
bool read_file_bytes(const std::string& path, std::vector<uint8_t>& out) {
    std::ifstream file(path, std::ios::binary | std::ios::ate);
    if (!file) {
        return false;
    }
    std::streamsize size = file.tellg();
    if (size <= 0) {
        return false;
    }
    out.resize(static_cast<size_t>(size));
    file.seekg(0, std::ios::beg);
    if (!file.read(reinterpret_cast<char*>(out.data()), size)) {
        return false;
    }
    return true;
}
bool compute_slot_excluded_hash(const std::vector<uint8_t>& file_bytes,
                                const uint8_t magic[kSelfHashMagicSize],
                                uint8_t out[kSelfHashSlotSize]) {
    if (file_bytes.size() < kSelfHashMagicSize + kSelfHashSlotSize) {
        return false;
    }
    size_t magic_offset = static_cast<size_t>(-1);
    for (size_t i = 0; i + kSelfHashMagicSize + kSelfHashSlotSize <= file_bytes.size(); ++i) {
        if (std::memcmp(file_bytes.data() + i, magic, kSelfHashMagicSize) == 0) {
            if (magic_offset != static_cast<size_t>(-1)) {
                return false;
            }
            magic_offset = i;
        }
    }
    if (magic_offset == static_cast<size_t>(-1)) {
        return false;
    }
    std::vector<uint8_t> hashed(file_bytes);
    size_t slot_offset = magic_offset + kSelfHashMagicSize;
    std::fill_n(hashed.data() + slot_offset, kSelfHashSlotSize, static_cast<uint8_t>(0));
    SHA256(hashed.data(), hashed.size(), out);
    return true;
}
bool verify_self_integrity_file(const std::string& path) {
    uint8_t magic[kSelfHashMagicSize];
    uint8_t expected[kSelfHashSlotSize];
    load_self_hash_block(magic, expected);
    if (is_all_ff_placeholder(expected)) {
        return false;
    }
    std::vector<uint8_t> file_bytes;
    if (!read_file_bytes(path, file_bytes)) {
        return false;
    }
    uint8_t hash[kSelfHashSlotSize];
    if (!compute_slot_excluded_hash(file_bytes, magic, hash)) {
        return false;
    }
    return std::memcmp(hash, expected, kSelfHashSlotSize) == 0;
}
} // namespace
#endif // AXS_PRODUCTION
#ifdef _WIN32
#include <windows.h>
bool verify_native_self_integrity() {
#ifndef AXS_PRODUCTION
    return true;
#else
    HMODULE hSelf = nullptr;
    if (!GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,
                            reinterpret_cast<LPCSTR>(&verify_native_self_integrity),
                            &hSelf)) {
        return false;
    }
    char path[MAX_PATH * 4] = {0};
    DWORD len = GetModuleFileNameA(hSelf, path, static_cast<DWORD>(sizeof(path)));
    if (len == 0 || len >= sizeof(path)) {
        return false;
    }
    return verify_self_integrity_file(path);
#endif
}

#else
#include <dlfcn.h>

bool verify_native_self_integrity() {
#ifndef AXS_PRODUCTION
    return true;
#else
    Dl_info info;
    if (!dladdr((void*)verify_native_self_integrity, &info) || info.dli_fname == nullptr) {
        return false;
    }
    return verify_self_integrity_file(info.dli_fname);
#endif
}
#endif

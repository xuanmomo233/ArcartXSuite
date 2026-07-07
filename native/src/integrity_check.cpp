#include "axs_native.h"
#include <openssl/evp.h>
#include <openssl/sha.h>
#include <algorithm>
#include <cstring>
#include <fstream>
#include <string>
#include <vector>

// Ed25519 公钥（构建时由 generate-keys.py 生成并嵌入）
// !!! 以下占位值必须在构建前替换为真实公钥 !!!
static const uint8_t ed25519_public_key[32] = {
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
};

// 供 module_decrypt.cpp 复用同一 Ed25519 公钥验证 .axb 签名（单一来源，避免重复嵌入）。
const uint8_t* axb_sign_pubkey() { return ed25519_public_key; }

/**
 * 验证 Merkle 根哈希的 Ed25519 签名。
 * rootHash: 32 字节 SHA-256
 * signature: 64 字节 Ed25519 签名
 * 返回 true = 签名有效
 */
jboolean verifyIntegrity(JNIEnv *env, jclass clazz, jbyteArray rootHash, jbyteArray signature) {
    jsize hash_len = env->GetArrayLength(rootHash);
    jsize sig_len = env->GetArrayLength(signature);
    if (hash_len != 32 || sig_len != 64) return JNI_FALSE;

    jbyte *hash_raw = env->GetByteArrayElements(rootHash, nullptr);
    jbyte *sig_raw = env->GetByteArrayElements(signature, nullptr);

    // 使用 OpenSSL EVP 验证 Ed25519 签名
    EVP_PKEY *pkey = EVP_PKEY_new_raw_public_key(EVP_PKEY_ED25519, nullptr,
                                                  ed25519_public_key, 32);
    jboolean result = JNI_FALSE;

    if (pkey) {
        EVP_MD_CTX *mdctx = EVP_MD_CTX_new();
        if (EVP_DigestVerifyInit(mdctx, nullptr, nullptr, nullptr, pkey) == 1) {
            int ret = EVP_DigestVerify(mdctx, reinterpret_cast<uint8_t*>(sig_raw), 64,
                                       reinterpret_cast<uint8_t*>(hash_raw), 32);
            result = (ret == 1) ? JNI_TRUE : JNI_FALSE;
        }
        EVP_MD_CTX_free(mdctx);
        EVP_PKEY_free(pkey);
    }

    env->ReleaseByteArrayElements(rootHash, hash_raw, JNI_ABORT);
    env->ReleaseByteArrayElements(signature, sig_raw, JNI_ABORT);
    return result;
}

// ─── Native 自校验 ──────────────────────────────
// 编译后由 post-build 脚本计算并嵌入实际哈希
// 槽位布局：16 字节 MAGIC 紧跟 32 字节 expected_self_hash 槽
static const uint8_t AXS_SELFHASH_MAGIC[16] = {
    0xA3, 0x51, 0x7D, 0x9C, 0xE4, 0x2B, 0x18, 0xF0,
    0x6D, 0xC7, 0x91, 0x34, 0xBA, 0x5E, 0xD2, 0x88
};
static uint8_t expected_self_hash[32] = {
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF
};
namespace {
constexpr size_t kSelfHashMagicSize = sizeof(AXS_SELFHASH_MAGIC);
constexpr size_t kSelfHashSlotSize = 32;
bool is_all_ff_placeholder() {
    for (int i = 0; i < 32; i++) {
        if (expected_self_hash[i] != 0xFF) {
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
bool compute_slot_excluded_hash(const std::vector<uint8_t>& file_bytes, uint8_t out[32]) {
    if (file_bytes.size() < kSelfHashMagicSize + kSelfHashSlotSize) {
        return false;
    }
    size_t magic_offset = static_cast<size_t>(-1);
    for (size_t i = 0; i + kSelfHashMagicSize + kSelfHashSlotSize <= file_bytes.size(); ++i) {
        if (std::memcmp(file_bytes.data() + i, AXS_SELFHASH_MAGIC, kSelfHashMagicSize) == 0) {
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
    std::vector<uint8_t> file_bytes;
    if (!read_file_bytes(path, file_bytes)) {
        return false;
    }
    uint8_t hash[32];
    if (!compute_slot_excluded_hash(file_bytes, hash)) {
        return false;
    }
    return std::memcmp(hash, expected_self_hash, 32) == 0;
}
} // namespace
#ifdef _WIN32
#include <windows.h>
bool verify_native_self_integrity() {
#ifndef AXS_PRODUCTION
    return true;
#else
    if (is_all_ff_placeholder()) return false;
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
    if (is_all_ff_placeholder()) return false;
    Dl_info info;
    if (!dladdr((void*)verify_native_self_integrity, &info) || info.dli_fname == nullptr) {
        return false;
    }
    return verify_self_integrity_file(info.dli_fname);
#endif
}
#endif

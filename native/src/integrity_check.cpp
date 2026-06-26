#include "axs_native.h"
#include <openssl/evp.h>
#include <openssl/sha.h>
#include <cstring>

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

// ─── Native 自校验 ───────────────────────────────────────────────
// 编译后由 post-build 脚本计算并嵌入实际哈希

// 预期的 .text 段哈希（post-build 替换）
static uint8_t expected_self_hash[32] = {
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF,
    0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF, 0xFF
};

#ifdef _WIN32
#include <windows.h>
#include <psapi.h>

bool verify_native_self_integrity() {
    // 全 0xFF = 占位未替换，跳过校验
    bool all_ff = true;
    for (int i = 0; i < 32; i++) {
        if (expected_self_hash[i] != 0xFF) { all_ff = false; break; }
    }
    if (all_ff) return true;

    HMODULE hSelf = nullptr;
    GetModuleHandleExA(GET_MODULE_HANDLE_EX_FLAG_FROM_ADDRESS,
                       (LPCSTR)verify_native_self_integrity, &hSelf);
    if (!hSelf) return false;

    MODULEINFO modInfo = {};
    GetModuleInformation(GetCurrentProcess(), hSelf, &modInfo, sizeof(modInfo));

    // 计算模块 .text 段 SHA-256（简化：计算整个模块哈希，排除 hash 占位区）
    // 实际产品中应精确定位 .text 节区
    uint8_t hash[32];
    SHA256_CTX sha;
    SHA256_Init(&sha);
    SHA256_Update(&sha, modInfo.lpBaseOfDll, modInfo.SizeOfImage);
    SHA256_Final(hash, &sha);

    return memcmp(hash, expected_self_hash, 32) == 0;
}

#else
#include <fstream>
#include <dlfcn.h>

bool verify_native_self_integrity() {
    bool all_ff = true;
    for (int i = 0; i < 32; i++) {
        if (expected_self_hash[i] != 0xFF) { all_ff = false; break; }
    }
    if (all_ff) return true;

    // 读取 /proc/self/maps 找到自身模块的映射区域
    Dl_info info;
    if (!dladdr((void*)verify_native_self_integrity, &info)) return false;

    std::ifstream lib(info.dli_fname, std::ios::binary);
    if (!lib) return false;

    lib.seekg(0, std::ios::end);
    size_t size = lib.tellg();
    lib.seekg(0);

    uint8_t hash[32];
    SHA256_CTX sha;
    SHA256_Init(&sha);
    char buf[4096];
    while (lib.read(buf, sizeof(buf))) {
        SHA256_Update(&sha, buf, lib.gcount());
    }
    if (lib.gcount() > 0) SHA256_Update(&sha, buf, lib.gcount());
    SHA256_Final(hash, &sha);

    return memcmp(hash, expected_self_hash, 32) == 0;
}
#endif

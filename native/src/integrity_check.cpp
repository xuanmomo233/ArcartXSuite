#include "axs_native.h"
#include <openssl/evp.h>
#include <openssl/sha.h>
#include <cstring>
#include <string>

static constexpr bool kHardenedBuild = (AXS_HARDENED != 0);

// Ed25519 公钥（构建时由 generate-keys.py 生成并嵌入）
// !!! 以下占位值必须在构建前替换为真实公钥 !!!
static const uint8_t ed25519_public_key[32] = {
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00
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
    // TODO: expected_self_hash 注入管道未落地前，保持占位放行；硬化 fail-closed 需待后续注入。
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

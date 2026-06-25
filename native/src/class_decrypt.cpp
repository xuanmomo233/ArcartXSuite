#include "axs_native.h"
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <openssl/sha.h>
#include <cstring>
#include <cstdlib>

// ═══ 密钥分层架构 ═══════════════════════════════════════════════
// Layer 0: Root Seed (编译时嵌入，分散存储)
// Layer 1: Master Key = HKDF(root_seed, hardware_fingerprint)
// Layer 2: Session Key = HKDF(master_key, time_window)
// Layer 3: Per-Class Key = HKDF(session_key, class_name_hash)

// root seed 分散存储（构建时由 generate-keys.py 生成并替换）
// !!!  以下占位值必须在构建前替换为真实密钥  !!!
static volatile uint8_t seed_part_a[] = { 0xDE, 0xAD, 0xBE, 0xEF, 0xCA, 0xFE, 0xBA, 0xBE };
static volatile uint8_t seed_part_b[] = { 0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF };
static volatile uint8_t seed_part_c[] = { 0xFE, 0xDC, 0xBA, 0x98, 0x76, 0x54, 0x32, 0x10 };
static volatile uint8_t seed_part_d[] = { 0x42, 0x5A, 0x7E, 0x3C, 0x9D, 0x1F, 0x8A, 0x6B };

// 运行时派生的密钥（仅存在于内存中）
static uint8_t master_key[32] = {0};
static uint8_t session_key[32] = {0};
static volatile bool keys_initialized = false;

// 算法标志
static constexpr uint8_t ALG_AES_256_GCM = 0x01;
static constexpr uint8_t ALG_CHACHA20_POLY1305 = 0x02;

// ─── HKDF-SHA256 ─────────────────────────────────────────────────

static void hkdf_extract(const uint8_t *salt, size_t salt_len,
                          const uint8_t *ikm, size_t ikm_len,
                          uint8_t prk[32]) {
    unsigned int prk_len = 32;
    HMAC(EVP_sha256(), salt, (int)salt_len, ikm, ikm_len, prk, &prk_len);
}

static void hkdf_expand(const uint8_t prk[32],
                         const uint8_t *info, size_t info_len,
                         uint8_t *okm, size_t okm_len) {
    uint8_t t[32] = {0};
    size_t t_len = 0;
    size_t pos = 0;
    uint8_t counter = 1;

    while (pos < okm_len) {
        unsigned int out_len = 32;
        HMAC_CTX *ctx = HMAC_CTX_new();
        HMAC_Init_ex(ctx, prk, 32, EVP_sha256(), nullptr);
        if (t_len > 0) HMAC_Update(ctx, t, t_len);
        HMAC_Update(ctx, info, info_len);
        HMAC_Update(ctx, &counter, 1);
        HMAC_Final(ctx, t, &out_len);
        HMAC_CTX_free(ctx);
        t_len = 32;

        size_t n = (okm_len - pos < 32) ? (okm_len - pos) : 32;
        memcpy(okm + pos, t, n);
        pos += n;
        counter++;
    }
    memset(t, 0, sizeof(t));
}

// ─── Root Seed 组装（XOR 混淆） ─────────────────────────────────

static void assemble_root_seed(uint8_t out[32]) {
    for (int i = 0; i < 8; i++) {
        out[i]      = seed_part_a[i] ^ seed_part_c[7 - i];
        out[8 + i]  = seed_part_b[i] ^ seed_part_d[7 - i];
        out[16 + i] = seed_part_c[i] ^ seed_part_a[7 - i];
        out[24 + i] = seed_part_d[i] ^ seed_part_b[7 - i];
    }
}

// ─── 硬件指纹（声明，实现在 env_check.cpp 或单独文件中） ──────────
extern void get_hardware_fingerprint(uint8_t out[32]);

// ─── 密钥初始化 ─────────────────────────────────────────────────

bool protection_init_keys() {
    if (keys_initialized) return true;

    uint8_t root_seed[32];
    assemble_root_seed(root_seed);

    // 获取硬件指纹作为 salt
    uint8_t hw_fp[32];
    get_hardware_fingerprint(hw_fp);

    // Layer 1: master_key = HKDF(root_seed, salt=hw_fingerprint)
    uint8_t prk[32];
    hkdf_extract(hw_fp, 32, root_seed, 32, prk);
    static const uint8_t info_master[] = "master_key_v1";
    hkdf_expand(prk, info_master, sizeof(info_master) - 1, master_key, 32);

    // Layer 2: session_key = HKDF(master_key, salt=time_window)
    // 时间窗口 = 当前小时戳（±1 小时容差在解密时处理）
    uint64_t time_window = (uint64_t)time(nullptr) / 3600;
    uint8_t tw_bytes[8];
    for (int i = 7; i >= 0; i--) { tw_bytes[i] = time_window & 0xFF; time_window >>= 8; }

    hkdf_extract(tw_bytes, 8, master_key, 32, prk);
    static const uint8_t info_session[] = "session_key_v1";
    hkdf_expand(prk, info_session, sizeof(info_session) - 1, session_key, 32);

    // 清零中间值
    memset(root_seed, 0, sizeof(root_seed));
    memset(hw_fp, 0, sizeof(hw_fp));
    memset(prk, 0, sizeof(prk));

    keys_initialized = true;
    return true;
}

// ─── Per-Class 密钥派生 ──────────────────────────────────────────

static void derive_class_key(const uint8_t class_name_hash[32], uint8_t class_key[32]) {
    uint8_t prk[32];
    hkdf_extract(class_name_hash, 32, session_key, 32, prk);
    static const uint8_t info_class[] = "class_key_v1";
    hkdf_expand(prk, info_class, sizeof(info_class) - 1, class_key, 32);
    memset(prk, 0, sizeof(prk));
}

// ─── 解密单个类 ─────────────────────────────────────────────────
// .enc 格式:
// [1 byte]  algorithm flag
// [12 bytes] IV/Nonce
// [4 bytes]  ciphertext length (big-endian)
// [N bytes]  ciphertext
// [16 bytes] auth tag
// [32 bytes] class name hash

jbyteArray decryptClass(JNIEnv *env, jclass clazz, jbyteArray classNameHash, jbyteArray encData) {
    if (!keys_initialized) return nullptr;

    jsize hash_len = env->GetArrayLength(classNameHash);
    jsize enc_len = env->GetArrayLength(encData);
    if (hash_len != 32 || enc_len < 1 + 12 + 4 + 16 + 32) return nullptr;

    jbyte *hash_raw = env->GetByteArrayElements(classNameHash, nullptr);
    jbyte *enc_raw = env->GetByteArrayElements(encData, nullptr);

    uint8_t *hash_bytes = reinterpret_cast<uint8_t*>(hash_raw);
    uint8_t *enc_bytes = reinterpret_cast<uint8_t*>(enc_raw);

    // 解析 .enc 格式
    uint8_t alg_flag = enc_bytes[0];
    uint8_t iv[12];
    memcpy(iv, enc_bytes + 1, 12);
    uint32_t ct_len = ((uint32_t)enc_bytes[13] << 24) | ((uint32_t)enc_bytes[14] << 16)
                    | ((uint32_t)enc_bytes[15] << 8) | (uint32_t)enc_bytes[16];

    // 验证长度
    if ((jsize)(1 + 12 + 4 + ct_len + 16 + 32) != enc_len) {
        env->ReleaseByteArrayElements(classNameHash, hash_raw, JNI_ABORT);
        env->ReleaseByteArrayElements(encData, enc_raw, JNI_ABORT);
        return nullptr;
    }

    uint8_t *ciphertext = enc_bytes + 17;
    uint8_t *tag = ciphertext + ct_len;
    uint8_t *embedded_hash = tag + 16;

    // 验证嵌入的类名哈希
    if (memcmp(hash_bytes, embedded_hash, 32) != 0) {
        env->ReleaseByteArrayElements(classNameHash, hash_raw, JNI_ABORT);
        env->ReleaseByteArrayElements(encData, enc_raw, JNI_ABORT);
        return nullptr;
    }

    // 派生 per-class 密钥
    uint8_t class_key[32];
    derive_class_key(hash_bytes, class_key);

    // AAD = 类名哈希前 16 字节
    uint8_t aad[16];
    memcpy(aad, hash_bytes, 16);

    // 解密
    uint8_t *plaintext = (uint8_t*)malloc(ct_len);
    int plain_len = 0;
    bool success = false;

    if (alg_flag == ALG_AES_256_GCM) {
        EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
        EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr);
        EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, 12, nullptr);
        EVP_DecryptInit_ex(ctx, nullptr, nullptr, class_key, iv);
        EVP_DecryptUpdate(ctx, nullptr, &plain_len, aad, 16);
        EVP_DecryptUpdate(ctx, plaintext, &plain_len, ciphertext, (int)ct_len);
        EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, 16, (void*)tag);
        int ret = EVP_DecryptFinal_ex(ctx, plaintext + plain_len, &plain_len);
        success = (ret > 0);
        plain_len = success ? (int)ct_len : 0;
        EVP_CIPHER_CTX_free(ctx);
    } else if (alg_flag == ALG_CHACHA20_POLY1305) {
        EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
        EVP_DecryptInit_ex(ctx, EVP_chacha20_poly1305(), nullptr, nullptr, nullptr);
        EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_AEAD_SET_IVLEN, 12, nullptr);
        EVP_DecryptInit_ex(ctx, nullptr, nullptr, class_key, iv);
        EVP_DecryptUpdate(ctx, nullptr, &plain_len, aad, 16);
        EVP_DecryptUpdate(ctx, plaintext, &plain_len, ciphertext, (int)ct_len);
        EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_AEAD_SET_TAG, 16, (void*)tag);
        int ret = EVP_DecryptFinal_ex(ctx, plaintext + plain_len, &plain_len);
        success = (ret > 0);
        plain_len = success ? (int)ct_len : 0;
        EVP_CIPHER_CTX_free(ctx);
    }

    // 清零密钥
    memset(class_key, 0, sizeof(class_key));

    jbyteArray result = nullptr;
    if (success && plain_len > 0) {
        result = env->NewByteArray(plain_len);
        env->SetByteArrayRegion(result, 0, plain_len, reinterpret_cast<jbyte*>(plaintext));
    }

    memset(plaintext, 0, ct_len);
    free(plaintext);
    env->ReleaseByteArrayElements(classNameHash, hash_raw, JNI_ABORT);
    env->ReleaseByteArrayElements(encData, enc_raw, JNI_ABORT);

    return result;
}

// ─── 密钥清零（关闭时调用） ──────────────────────────────────────

void protection_clear_keys() {
    memset(master_key, 0, sizeof(master_key));
    memset(session_key, 0, sizeof(session_key));
    keys_initialized = false;
}

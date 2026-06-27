#include "axs_native.h"
#include <openssl/evp.h>
#include <openssl/hmac.h>
#include <openssl/sha.h>
#include <cstring>
#include <cstdlib>

// ═══ 密钥分层架构（方案 B：字节码加密 key 与机器/时间解耦） ═══════
// Layer 0: Root Seed (编译时嵌入，分散存储)
// Layer 1: Master Key = HKDF(root_seed, salt=build_salt_l1)
// Layer 2: Session Key = HKDF(master_key, salt=build_salt_l2)
// Layer 3: Per-Class Key = HKDF(session_key, salt=class_name_hash)
//
// 说明：类解密 key 仅由 root_seed + 固定 build salt 派生，
//       不再混入硬件指纹/时间窗，保证"构建期加密一次、任意机器任意时刻解密一致"。
//       机器绑定由云端 moduleKey 的指纹 HKDF 绑定层负责（见后端 crypto.ts）。
//       这两个常量必须与 scripts/encrypt-jar.py 中的同名常量逐字节一致。

// root seed 分散存储（构建时由 embed-keys.py 生成并替换）
// !!!  以下为占位值；embed-keys.py 会替换为【HKDF-keystream 包裹后的密文分片】  !!!
static volatile uint8_t seed_part_a[] = { 0xDE, 0xAD, 0xBE, 0xEF, 0xCA, 0xFE, 0xBA, 0xBE };
static volatile uint8_t seed_part_b[] = { 0x01, 0x23, 0x45, 0x67, 0x89, 0xAB, 0xCD, 0xEF };
static volatile uint8_t seed_part_c[] = { 0xFE, 0xDC, 0xBA, 0x98, 0x76, 0x54, 0x32, 0x10 };
static volatile uint8_t seed_part_d[] = { 0x42, 0x5A, 0x7E, 0x3C, 0x9D, 0x1F, 0x8A, 0x6B };

// 固定 build salt（方案 B）。必须与 scripts/encrypt-jar.py / embed-keys.py 同名常量逐字节一致。
static const uint8_t build_salt_l1[] = "AXS-class-enc-master-salt-v1";
static const uint8_t build_salt_l2[] = "AXS-class-enc-session-salt-v1";

// seed 分片 at-rest 解包常量：seed_part_* 存的是 plaintext XOR HKDF-keystream 的密文。
// keystream 由两个 build salt 在代码中拼接后 SHA-256 派生，不以连续可识别形态存在于
// 二进制；运行时重算 keystream 解包后再做 XOR 重组。威胁模型：抵御 strings/静态字节
// 数组提取与朴素内存扫描；对完整逆向二进制者非密码学级保密（自包含离线解密的固有
// 上限），机器级强绑定由云端指纹 HKDF 层负责。须与 embed-keys.py SEED_WRAP_* 一致。
static const uint8_t seed_wrap_salt[] = "AXS-seed-wrap-salt-v1";
static const uint8_t seed_wrap_info[] = "axs-seed-unwrap-v1";

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

// 由 build salt 在代码中重算 seed 解包 keystream（不存储连续密钥）。
static void compute_seed_keystream(uint8_t ks[32]) {
    uint8_t ikm_buf[64];
    size_t n = 0;
    memcpy(ikm_buf + n, build_salt_l1, sizeof(build_salt_l1) - 1); n += sizeof(build_salt_l1) - 1;
    ikm_buf[n++] = '|';
    memcpy(ikm_buf + n, build_salt_l2, sizeof(build_salt_l2) - 1); n += sizeof(build_salt_l2) - 1;
    uint8_t wrap_ikm[32];
    SHA256(ikm_buf, n, wrap_ikm);
    uint8_t prk[32];
    hkdf_extract(seed_wrap_salt, sizeof(seed_wrap_salt) - 1, wrap_ikm, 32, prk);
    hkdf_expand(prk, seed_wrap_info, sizeof(seed_wrap_info) - 1, ks, 32);
    memset(ikm_buf, 0, sizeof(ikm_buf));
    memset(wrap_ikm, 0, sizeof(wrap_ikm));
    memset(prk, 0, sizeof(prk));
}

static void assemble_root_seed(uint8_t out[32]) {
    // 1) 解包密文分片 -> 明文分片（仅存在于栈上）
    uint8_t ks[32];
    compute_seed_keystream(ks);
    uint8_t a[8], b[8], c[8], d[8];
    for (int i = 0; i < 8; i++) {
        a[i] = seed_part_a[i] ^ ks[i];
        b[i] = seed_part_b[i] ^ ks[8 + i];
        c[i] = seed_part_c[i] ^ ks[16 + i];
        d[i] = seed_part_d[i] ^ ks[24 + i];
    }
    // 2) 原 XOR 重组公式（在解包后的明文分片上进行）
    for (int i = 0; i < 8; i++) {
        out[i]      = a[i] ^ c[7 - i];
        out[8 + i]  = b[i] ^ d[7 - i];
        out[16 + i] = c[i] ^ a[7 - i];
        out[24 + i] = d[i] ^ b[7 - i];
    }
    memset(ks, 0, sizeof(ks));
    memset(a, 0, sizeof(a)); memset(b, 0, sizeof(b));
    memset(c, 0, sizeof(c)); memset(d, 0, sizeof(d));
}

// ─── 密钥初始化 ─────────────────────────────────────────────────

// build_salt_l1 / build_salt_l2 已上移至文件顶部（seed 分片附近）。

bool protection_init_keys() {
    if (keys_initialized) return true;

    uint8_t root_seed[32];
    assemble_root_seed(root_seed);

    // Layer 1: master_key = HKDF(root_seed, salt=build_salt_l1)
    uint8_t prk[32];
    hkdf_extract(build_salt_l1, sizeof(build_salt_l1) - 1, root_seed, 32, prk);
    static const uint8_t info_master[] = "master_key_v1";
    hkdf_expand(prk, info_master, sizeof(info_master) - 1, master_key, 32);

    // Layer 2: session_key = HKDF(master_key, salt=build_salt_l2)
    hkdf_extract(build_salt_l2, sizeof(build_salt_l2) - 1, master_key, 32, prk);
    static const uint8_t info_session[] = "session_key_v1";
    hkdf_expand(prk, info_session, sizeof(info_session) - 1, session_key, 32);

    // 清零中间值
    memset(root_seed, 0, sizeof(root_seed));
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

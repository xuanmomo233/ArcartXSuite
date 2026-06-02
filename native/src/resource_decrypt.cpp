#include "axs_native.h"
#include <openssl/evp.h>
#include <openssl/sha.h>
#include <cstring>
#include <vector>
#include <zlib.h>

// AXB magic: AXR1 (free) / AXL1 (license-bound)
static constexpr unsigned char MAGIC_FREE[]    = {0x41, 0x58, 0x52, 0x31};
static constexpr unsigned char MAGIC_LICENSE[] = {0x41, 0x58, 0x4C, 0x31};
static constexpr int MAGIC_LEN   = 4;
static constexpr int GCM_IV_LEN  = 12;
static constexpr int GCM_TAG_LEN = 16;

// ─── 内部密钥派生（与 ProtectYamlResourcesTask.kt 对应） ─────────

static const int SEED_LEFT[] = {
    175, 119, 140, 14, 90, 244, 58, 154,
    22, 234, 29, 141, 21, 229, 61, 74,
    181, 98, 76, 164, 206, 128, 205, 200
};
static const int SEED_RIGHT[] = {
    142, 91, 87, 25, 42, 3, 205, 96,
    67, 146, 110, 46, 153, 21, 165, 229,
    114, 152, 36, 68, 209, 105, 189, 51
};
static const unsigned char DIGEST_SALT[] = {19, 51, 87, 123, 9, 44, 62, 108};
static constexpr int SEED_LEN = 24;

static void derive_resource_key(unsigned char out[32]) {
    unsigned char seed[SEED_LEN];
    for (int i = 0; i < SEED_LEN; i++) {
        int offset = (i * 29 + 17) & 0xFF;
        seed[i] = (unsigned char)(SEED_LEFT[i] ^ SEED_RIGHT[i] ^ offset);
    }

    SHA256_CTX ctx;
    SHA256_Init(&ctx);
    SHA256_Update(&ctx, seed, SEED_LEN);
    SHA256_Update(&ctx, DIGEST_SALT, sizeof(DIGEST_SALT));
    SHA256_Final(out, &ctx);

    // 擦除中间材料
    memset(seed, 0, sizeof(seed));
}

// ─── AES-256-GCM 解密 ──────────────────────────────────────────

static std::vector<unsigned char> aes_gcm_decrypt(
    const unsigned char *ciphertext, int ciphertext_len,
    const unsigned char *iv, int iv_len,
    const unsigned char *key) {

    // GCM tag 在密文尾部
    if (ciphertext_len < GCM_TAG_LEN) return {};

    int actual_ct_len = ciphertext_len - GCM_TAG_LEN;
    const unsigned char *tag = ciphertext + actual_ct_len;

    EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
    if (!ctx) return {};

    std::vector<unsigned char> plaintext(actual_ct_len);
    int len = 0, plaintext_len = 0;

    EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr);
    EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, iv_len, nullptr);
    EVP_DecryptInit_ex(ctx, nullptr, nullptr, key, iv);
    EVP_DecryptUpdate(ctx, plaintext.data(), &len, ciphertext, actual_ct_len);
    plaintext_len = len;
    EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, GCM_TAG_LEN, (void *)tag);

    int ret = EVP_DecryptFinal_ex(ctx, plaintext.data() + len, &len);
    EVP_CIPHER_CTX_free(ctx);

    if (ret <= 0) return {};  // 认证失败
    plaintext_len += len;
    plaintext.resize(plaintext_len);
    return plaintext;
}

// ─── GZIP 解压 ──────────────────────────────────────────────────

static std::vector<unsigned char> ungzip(const unsigned char *data, int data_len) {
    z_stream strm{};
    strm.next_in = const_cast<unsigned char *>(data);
    strm.avail_in = data_len;

    if (inflateInit2(&strm, 15 + 16) != Z_OK) return {};

    std::vector<unsigned char> result;
    unsigned char buf[8192];
    int ret;
    do {
        strm.next_out = buf;
        strm.avail_out = sizeof(buf);
        ret = inflate(&strm, Z_NO_FLUSH);
        result.insert(result.end(), buf, buf + sizeof(buf) - strm.avail_out);
    } while (ret == Z_OK);

    inflateEnd(&strm);
    return (ret == Z_STREAM_END) ? result : std::vector<unsigned char>{};
}

// ─── JNI 导出 ───────────────────────────────────────────────────

JNIEXPORT jbyteArray JNICALL
Java_xuanmo_arcartxsuite_security_NativeBridge_decryptResource(
    JNIEnv *env, jclass clazz, jbyteArray encrypted, jbyteArray keyMaterial) {

    if (!encrypted) return nullptr;

    jsize enc_len = env->GetArrayLength(encrypted);
    if (enc_len <= MAGIC_LEN + GCM_IV_LEN) return nullptr;

    auto *enc_data = (unsigned char *)env->GetByteArrayElements(encrypted, nullptr);

    // 验证 magic
    bool is_free    = memcmp(enc_data, MAGIC_FREE, MAGIC_LEN) == 0;
    bool is_license = memcmp(enc_data, MAGIC_LICENSE, MAGIC_LEN) == 0;
    if (!is_free && !is_license) {
        env->ReleaseByteArrayElements(encrypted, (jbyte *)enc_data, JNI_ABORT);
        return nullptr;
    }

    const unsigned char *iv      = enc_data + MAGIC_LEN;
    const unsigned char *payload = enc_data + MAGIC_LEN + GCM_IV_LEN;
    int payload_len              = enc_len - MAGIC_LEN - GCM_IV_LEN;

    // 派生密钥
    unsigned char key[32];
    if (keyMaterial && env->GetArrayLength(keyMaterial) > 0) {
        // 使用调用方提供的密钥材料（license-bound 场景）
        auto *km = (unsigned char *)env->GetByteArrayElements(keyMaterial, nullptr);
        SHA256_CTX sha;
        SHA256_Init(&sha);
        SHA256_Update(&sha, km, env->GetArrayLength(keyMaterial));
        SHA256_Final(key, &sha);
        env->ReleaseByteArrayElements(keyMaterial, (jbyte *)km, JNI_ABORT);
    } else {
        derive_resource_key(key);
    }

    auto decrypted = aes_gcm_decrypt(payload, payload_len, iv, GCM_IV_LEN, key);
    memset(key, 0, sizeof(key));
    env->ReleaseByteArrayElements(encrypted, (jbyte *)enc_data, JNI_ABORT);

    if (decrypted.empty()) return nullptr;

    // GZIP 解压
    auto plain = ungzip(decrypted.data(), (int)decrypted.size());
    if (plain.empty()) return nullptr;

    jbyteArray result = env->NewByteArray((jsize)plain.size());
    env->SetByteArrayRegion(result, 0, (jsize)plain.size(), (jbyte *)plain.data());
    return result;
}

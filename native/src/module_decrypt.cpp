#include "axs_native.h"
#include <openssl/evp.h>
#include <cstring>
#include <vector>
#include <zlib.h>

static constexpr int GCM_IV_LEN  = 12;
static constexpr int GCM_TAG_LEN = 16;

// ─── AES-256-GCM 解密（复用 resource_decrypt 中的逻辑） ─────────

static std::vector<unsigned char> aes_gcm_decrypt(
    const unsigned char *ciphertext, int ciphertext_len,
    const unsigned char *iv, int iv_len,
    const unsigned char *key) {

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

    if (ret <= 0) return {};
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

// ─── JNI 导出：解密云端模块 .axb ───────────────────────────────
//
// axb 文件自包含格式：IV(12 字节) + 密文 + GCM 认证标签(16 字节)。
// 故 IV 直接从 axb 前 12 字节读取，无需单独传入。

jbyteArray decryptModule(
    JNIEnv *env, jclass clazz, jbyteArray encryptedAxb, jbyteArray key) {

    if (!encryptedAxb || !key) return nullptr;

    jsize enc_len = env->GetArrayLength(encryptedAxb);
    jsize key_len = env->GetArrayLength(key);

    static constexpr int MAGIC_LEN = 4;
    // 新格式：magic(4) + IV(12) + ciphertext + TAG(16)
    if (enc_len <= MAGIC_LEN + GCM_IV_LEN + GCM_TAG_LEN || key_len != 32) return nullptr;

    auto *enc_data = (unsigned char *)env->GetByteArrayElements(encryptedAxb, nullptr);
    auto *key_data = (unsigned char *)env->GetByteArrayElements(key, nullptr);

    // 新格式：magic(4) + IV(12) + 密文+认证标签
    const unsigned char *iv_data = enc_data + MAGIC_LEN;
    const unsigned char *cipher_data = enc_data + MAGIC_LEN + GCM_IV_LEN;
    int cipher_len = (int)enc_len - MAGIC_LEN - GCM_IV_LEN;

    auto decrypted = aes_gcm_decrypt(cipher_data, cipher_len, iv_data, GCM_IV_LEN, key_data);

    env->ReleaseByteArrayElements(encryptedAxb, (jbyte *)enc_data, JNI_ABORT);
    env->ReleaseByteArrayElements(key, (jbyte *)key_data, JNI_ABORT);

    if (decrypted.empty()) return nullptr;

    auto plain = ungzip(decrypted.data(), (int)decrypted.size());
    if (plain.empty()) return nullptr;

    jbyteArray result = env->NewByteArray((jsize)plain.size());
    env->SetByteArrayRegion(result, 0, (jsize)plain.size(), (jbyte *)plain.data());
    return result;
}

// ─── V6：先验 .axb 的 Ed25519 平台签名，再解密 ───────────────────
//
// 平台用私钥对整条 .axb 文件签名（services/crypto.ts signAxb），native 用
// integrity_check.cpp 内嵌的同一密钥对公钥验签。验签通过才解密，阻断被篡改/
// 伪造的 .axb 注入。
//
// 公钥占位（全 0，generate-keys/embed-keys 尚未注入真实公钥）时跳过验签，兼容
// 未启用平台签名的构建；一旦嵌入真实公钥即强制验签：签名缺失/长度不符/无效一律拒绝。

static bool axb_signature_valid(
    const unsigned char *axb, size_t axb_len,
    const unsigned char *sig, size_t sig_len) {

    const uint8_t *pub = axb_sign_pubkey();
    bool placeholder = true;
    for (int i = 0; i < 32; i++) {
        if (pub[i] != 0) { placeholder = false; break; }
    }
    if (placeholder) return true; // 未启用平台签名，放行（与后端空签名策略一致）

    if (!sig || sig_len != 64) return false;

    EVP_PKEY *pkey = EVP_PKEY_new_raw_public_key(EVP_PKEY_ED25519, nullptr, pub, 32);
    if (!pkey) return false;

    bool ok = false;
    EVP_MD_CTX *mdctx = EVP_MD_CTX_new();
    if (mdctx && EVP_DigestVerifyInit(mdctx, nullptr, nullptr, nullptr, pkey) == 1) {
        ok = (EVP_DigestVerify(mdctx, sig, sig_len, axb, axb_len) == 1);
    }
    if (mdctx) EVP_MD_CTX_free(mdctx);
    EVP_PKEY_free(pkey);
    return ok;
}

jbyteArray decryptModuleVerified(
    JNIEnv *env, jclass clazz,
    jbyteArray encryptedAxb, jbyteArray key, jbyteArray signature) {

    if (!encryptedAxb || !key) return nullptr;

    jsize axb_len = env->GetArrayLength(encryptedAxb);
    jsize sig_len = signature ? env->GetArrayLength(signature) : 0;

    auto *axb_data = (unsigned char *)env->GetByteArrayElements(encryptedAxb, nullptr);
    unsigned char *sig_data = signature
        ? (unsigned char *)env->GetByteArrayElements(signature, nullptr)
        : nullptr;

    bool ok = axb_signature_valid(axb_data, (size_t)axb_len, sig_data, (size_t)sig_len);

    env->ReleaseByteArrayElements(encryptedAxb, (jbyte *)axb_data, JNI_ABORT);
    if (sig_data) env->ReleaseByteArrayElements(signature, (jbyte *)sig_data, JNI_ABORT);

    if (!ok) return nullptr; // 平台签名校验未通过，拒绝解密

    // 验签通过：复用既有 AES-256-GCM + GZIP 解密路径
    return decryptModule(env, clazz, encryptedAxb, key);
}

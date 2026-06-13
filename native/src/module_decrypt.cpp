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

JNIEXPORT jbyteArray JNICALL
Java_xuanmo_arcartxsuite_security_NativeBridge_decryptModule(
    JNIEnv *env, jclass clazz, jbyteArray encryptedAxb, jbyteArray key, jbyteArray iv) {

    if (!encryptedAxb || !key || !iv) return nullptr;

    jsize enc_len = env->GetArrayLength(encryptedAxb);
    jsize key_len = env->GetArrayLength(key);
    jsize iv_len  = env->GetArrayLength(iv);

    if (enc_len <= 0 || key_len != 32 || iv_len != GCM_IV_LEN) return nullptr;

    auto *enc_data = (unsigned char *)env->GetByteArrayElements(encryptedAxb, nullptr);
    auto *key_data = (unsigned char *)env->GetByteArrayElements(key, nullptr);
    auto *iv_data  = (unsigned char *)env->GetByteArrayElements(iv, nullptr);

    auto decrypted = aes_gcm_decrypt(enc_data, enc_len, iv_data, iv_len, key_data);

    env->ReleaseByteArrayElements(encryptedAxb, (jbyte *)enc_data, JNI_ABORT);
    env->ReleaseByteArrayElements(key, (jbyte *)key_data, JNI_ABORT);
    env->ReleaseByteArrayElements(iv, (jbyte *)iv_data, JNI_ABORT);

    if (decrypted.empty()) return nullptr;

    auto plain = ungzip(decrypted.data(), (int)decrypted.size());
    if (plain.empty()) return nullptr;

    jbyteArray result = env->NewByteArray((jsize)plain.size());
    env->SetByteArrayRegion(result, 0, (jsize)plain.size(), (jbyte *)plain.data());
    return result;
}

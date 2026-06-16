#include "axs_native.h"
#include <openssl/evp.h>
#include <openssl/sha.h>
#include <cstring>
#include <vector>

jbyteArray unwrapResourceKey(
    JNIEnv *env, jclass clazz, jbyteArray wrappedKey, jbyteArray iv, jbyteArray material) {

    if (!wrappedKey || !iv || !material) return nullptr;

    jsize wk_len  = env->GetArrayLength(wrappedKey);
    jsize iv_len  = env->GetArrayLength(iv);
    jsize mat_len = env->GetArrayLength(material);

    auto *wk  = (unsigned char *)env->GetByteArrayElements(wrappedKey, nullptr);
    auto *iv_data  = (unsigned char *)env->GetByteArrayElements(iv, nullptr);
    auto *mat = (unsigned char *)env->GetByteArrayElements(material, nullptr);

    // 从 material 派生 AES-256 wrapping key (SHA-256)
    unsigned char wrapping_key[32];
    SHA256(mat, mat_len, wrapping_key);

    // AES-256-GCM 解密（tag 在密文尾部）
    int tag_len = 16;
    if (wk_len < tag_len) {
        env->ReleaseByteArrayElements(wrappedKey, (jbyte *)wk, JNI_ABORT);
        env->ReleaseByteArrayElements(iv, (jbyte *)iv_data, JNI_ABORT);
        env->ReleaseByteArrayElements(material, (jbyte *)mat, JNI_ABORT);
        return nullptr;
    }

    int ct_len = wk_len - tag_len;
    const unsigned char *tag = wk + ct_len;

    EVP_CIPHER_CTX *ctx = EVP_CIPHER_CTX_new();
    std::vector<unsigned char> plaintext(ct_len);
    int len = 0, pt_len = 0;

    EVP_DecryptInit_ex(ctx, EVP_aes_256_gcm(), nullptr, nullptr, nullptr);
    EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_IVLEN, iv_len, nullptr);
    EVP_DecryptInit_ex(ctx, nullptr, nullptr, wrapping_key, iv_data);
    EVP_DecryptUpdate(ctx, plaintext.data(), &len, wk, ct_len);
    pt_len = len;
    EVP_CIPHER_CTX_ctrl(ctx, EVP_CTRL_GCM_SET_TAG, tag_len, (void *)tag);
    int ret = EVP_DecryptFinal_ex(ctx, plaintext.data() + len, &len);
    EVP_CIPHER_CTX_free(ctx);

    memset(wrapping_key, 0, sizeof(wrapping_key));
    env->ReleaseByteArrayElements(wrappedKey, (jbyte *)wk, JNI_ABORT);
    env->ReleaseByteArrayElements(iv, (jbyte *)iv_data, JNI_ABORT);
    env->ReleaseByteArrayElements(material, (jbyte *)mat, JNI_ABORT);

    if (ret <= 0) return nullptr;
    pt_len += len;

    jbyteArray result = env->NewByteArray(pt_len);
    env->SetByteArrayRegion(result, 0, pt_len, (jbyte *)plaintext.data());
    return result;
}

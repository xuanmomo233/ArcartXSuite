#include "axs_native.h"
#include <openssl/evp.h>
#include <openssl/ec.h>
#include <openssl/pem.h>
#include <openssl/err.h>
#include <cstring>

// 内嵌公钥（构建时由脚本注入，此处为占位）
// 实际部署时替换为你的 ECDSA P-256 公钥 PEM
static const char *EMBEDDED_PUBLIC_KEY = R"(
-----BEGIN PUBLIC KEY-----
MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAE_PLACEHOLDER_REPLACE_AT_BUILD_TIME_
-----END PUBLIC KEY-----
)";

JNIEXPORT jboolean JNICALL
Java_xuanmo_arcartxsuite_security_NativeBridge_verifyTicketSignature(
    JNIEnv *env, jclass clazz, jbyteArray ticketJson, jbyteArray signature) {

    if (!ticketJson || !signature) return JNI_FALSE;

    // 读取 Java 数组
    jsize msg_len = env->GetArrayLength(ticketJson);
    jsize sig_len = env->GetArrayLength(signature);
    auto *msg = (unsigned char *)env->GetByteArrayElements(ticketJson, nullptr);
    auto *sig = (unsigned char *)env->GetByteArrayElements(signature, nullptr);

    jboolean result = JNI_FALSE;

    // 加载公钥
    BIO *bio = BIO_new_mem_buf(EMBEDDED_PUBLIC_KEY, -1);
    EVP_PKEY *pkey = PEM_read_bio_PUBKEY(bio, nullptr, nullptr, nullptr);
    BIO_free(bio);

    if (pkey) {
        EVP_MD_CTX *md_ctx = EVP_MD_CTX_new();
        if (EVP_DigestVerifyInit(md_ctx, nullptr, EVP_sha256(), nullptr, pkey) == 1 &&
            EVP_DigestVerifyUpdate(md_ctx, msg, msg_len) == 1 &&
            EVP_DigestVerifyFinal(md_ctx, sig, sig_len) == 1) {
            result = JNI_TRUE;
        }
        EVP_MD_CTX_free(md_ctx);
        EVP_PKEY_free(pkey);
    }

    env->ReleaseByteArrayElements(ticketJson, (jbyte *)msg, JNI_ABORT);
    env->ReleaseByteArrayElements(signature, (jbyte *)sig, JNI_ABORT);
    return result;
}

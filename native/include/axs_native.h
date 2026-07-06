#pragma once
#include <jni.h>
#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

// ═══ 原有 NativeBridge 方法（云端模块解密） ═══════════════════════
jint nativeVersion(JNIEnv *env, jclass clazz);
jbyteArray decryptResource(JNIEnv *env, jclass clazz, jbyteArray encrypted, jbyteArray keyMaterial);
jbyteArray unwrapResourceKey(JNIEnv *env, jclass clazz, jbyteArray wrappedKey, jbyteArray iv, jbyteArray material);
jint environmentCheck(JNIEnv *env, jclass clazz);
jbyteArray decryptModule(JNIEnv *env, jclass clazz, jbyteArray encryptedAxb, jbyteArray key);
jbyteArray decryptModuleVerified(JNIEnv *env, jclass clazz, jbyteArray encryptedAxb, jbyteArray key, jbyteArray signature);

// ═══ JAR 保护层新增方法 ═══════════════════════════════════════════
jint initProtection(JNIEnv *env, jclass clazz);
jbyteArray decryptClass(JNIEnv *env, jclass clazz, jbyteArray classNameHash, jbyteArray encData);
// n11：方案 B 自包含模块逐类解密——session 由外部传入的 32 字节模块种子（云端 moduleKey）派生。
jbyteArray decryptModuleClass(JNIEnv *env, jclass clazz, jbyteArray classNameHash, jbyteArray encData, jbyteArray moduleSeed);
jboolean verifyIntegrity(JNIEnv *env, jclass clazz, jbyteArray rootHash, jbyteArray signature);
jint enhancedEnvironmentCheck(JNIEnv *env, jclass clazz);
jbyteArray getHardwareFingerprint(JNIEnv *env, jclass clazz);

// ═══ 内部函数 ═══════════════════════════════════════════════════
// 密钥初始化（class_decrypt.cpp）
bool protection_init_keys();
void protection_clear_keys();

// 硬件指纹（hw_fingerprint.cpp）
void get_hardware_fingerprint(uint8_t out[32]);

// Native 自校验（integrity_check.cpp）
bool verify_native_self_integrity();
bool native_hard_reject_signal(JNIEnv *env);
// .axb 验签复用的 Ed25519 公钥（integrity_check.cpp 内嵌的同一密钥）
const uint8_t* axb_sign_pubkey();

// JNI_OnLoad
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved);

#ifdef __cplusplus
}
#endif

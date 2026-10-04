#pragma once
#include <jni.h>
#include <cstdint>

#ifdef __cplusplus
extern "C" {
#endif

// ═══ 原有 NativeBridge 方法（云端模块解密） ═══════════════════════
jint nativeVersion(JNIEnv *env, jclass clazz);
jint environmentCheck(JNIEnv *env, jclass clazz);
jbyteArray decryptModule(JNIEnv *env, jclass clazz, jbyteArray encryptedAxb, jbyteArray key);
jbyteArray decryptModuleVerified(JNIEnv *env, jclass clazz, jbyteArray encryptedAxb, jbyteArray key, jbyteArray signature);

// ═══ JAR 保护层新增方法 ═══════════════════════════════════════════
jint initProtection(JNIEnv *env, jclass clazz);
jbyteArray decryptClass(JNIEnv *env, jclass clazz, jbyteArray classNameHash, jbyteArray encData);
// n11：方案 B 自包含模块逐类解密——session 由外部传入的 32 字节模块种子（云端 moduleKey）派生。
jbyteArray decryptModuleClass(JNIEnv *env, jclass clazz, jbyteArray classNameHash, jbyteArray encData, jbyteArray moduleSeed);
jboolean verifyIntegrity(JNIEnv *env, jclass clazz, jbyteArray rootHash, jbyteArray signature);
jboolean verifyResponseSig(JNIEnv *env, jclass clazz, jlong timestamp, jbyteArray body, jbyteArray signature);
jboolean responseVerifyActive(JNIEnv *env, jclass clazz);
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

// 解密入口调用方校验（axs_native.cpp）
// 回调 Java 侧 JvmAntiDebug.isTrustedDecryptCaller()，用 StackWalker 核对调用栈中
// 是否存在 ProtectedClassLoader 自身或其定义的类（即加密类）的帧。
// 保护层未就位（pcl == null：明文 dev jar / 测试 / CI harness）或检测基础设施异常时放行。
bool trusted_decrypt_caller(JNIEnv *env);

// JAR 明文层完整性校验（bootstrap_verify.cpp）
// 通过 JVM CodeSource 定位插件 JAR，自行解析 ZIP，验签 BOOTSTRAP.MF 清单，
// 比对全部非 ENCRYPTED/ .class 条目哈希，并复核 .enc Merkle+Ed25519。
// 明文 jar（无 PROTECTION.MF）或 jar 无法定位时跳过；加密 jar 校验失败返回 false。
bool verify_bootstrap_classes(JNIEnv *env);
// .axb 验签复用的 Ed25519 公钥（integrity_check.cpp 内嵌的同一密钥）
const uint8_t* axb_sign_pubkey();

// JNI_OnLoad
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved);

#ifdef __cplusplus
}
#endif

#pragma once
#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

// ═══ NativeBridge 内部函数（由 JNI_OnLoad 通过 RegisterNatives 动态注册）════
// 这种设计消除了静态符号表中的自解释 JNI 方法名，增加逆向分析难度。

jint nativeVersion(JNIEnv *env, jclass clazz);

jbyteArray decryptResource(JNIEnv *env, jclass clazz, jbyteArray encrypted, jbyteArray keyMaterial);

jbyteArray unwrapResourceKey(JNIEnv *env, jclass clazz, jbyteArray wrappedKey, jbyteArray iv, jbyteArray material);

jint environmentCheck(JNIEnv *env, jclass clazz);

jbyteArray decryptModule(JNIEnv *env, jclass clazz, jbyteArray encryptedAxb, jbyteArray key);

// JNI_OnLoad：动态注册 natives，消除静态符号暴露
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved);

#ifdef __cplusplus
}
#endif

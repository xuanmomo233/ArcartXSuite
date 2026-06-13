#pragma once
#include <jni.h>

#ifdef __cplusplus
extern "C" {
#endif

// ═══ NativeBridge JNI 导出 ══════════════════════════════════════

JNIEXPORT jint JNICALL
Java_xuanmo_arcartxsuite_security_NativeBridge_nativeVersion(JNIEnv *env, jclass clazz);

JNIEXPORT jbyteArray JNICALL
Java_xuanmo_arcartxsuite_security_NativeBridge_decryptResource(
    JNIEnv *env, jclass clazz, jbyteArray encrypted, jbyteArray keyMaterial);

JNIEXPORT jboolean JNICALL
Java_xuanmo_arcartxsuite_security_NativeBridge_verifyTicketSignature(
    JNIEnv *env, jclass clazz, jbyteArray ticketJson, jbyteArray signature);

JNIEXPORT jbyteArray JNICALL
Java_xuanmo_arcartxsuite_security_NativeBridge_unwrapResourceKey(
    JNIEnv *env, jclass clazz, jbyteArray wrappedKey, jbyteArray iv, jbyteArray material);

JNIEXPORT jint JNICALL
Java_xuanmo_arcartxsuite_security_NativeBridge_environmentCheck(JNIEnv *env, jclass clazz);

JNIEXPORT jbyteArray JNICALL
Java_xuanmo_arcartxsuite_security_NativeBridge_decryptModule(
    JNIEnv *env, jclass clazz, jbyteArray encryptedAxb, jbyteArray key, jbyteArray iv);

#ifdef __cplusplus
}
#endif

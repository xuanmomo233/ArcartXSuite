#include "axs_native.h"
#include <cstring>

// AXS Native Bridge - 版本号
static constexpr int AXS_NATIVE_VERSION = 1;

jint nativeVersion(JNIEnv *env, jclass clazz) {
    return AXS_NATIVE_VERSION;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_6) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass("xuanmo/arcartxsuite/security/NativeBridge");
    if (!clazz) {
        return JNI_ERR;
    }

    JNINativeMethod methods[] = {
        {"n0", "()I", reinterpret_cast<void *>(nativeVersion)},
        {"n1", "([B[B)[B", reinterpret_cast<void *>(decryptResource)},
        {"n2", "([B[B[B)[B", reinterpret_cast<void *>(unwrapResourceKey)},
        {"n3", "()I", reinterpret_cast<void *>(environmentCheck)},
        {"n4", "([B[B)[B", reinterpret_cast<void *>(decryptModule)},
    };

    if (env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0])) != 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_6;
}

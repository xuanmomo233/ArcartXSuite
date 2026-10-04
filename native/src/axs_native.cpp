#include "axs_native.h"
#include <cstring>

static constexpr int AXS_NATIVE_VERSION = 3; // v3: 模块逐类自包含解密 n11（方案 B）

jint nativeVersion(JNIEnv *env, jclass clazz) {
    return AXS_NATIVE_VERSION;
}

/**
 * 初始化保护子系统：
 * 1. 派生密钥链（root_seed → master_key → session_key）
 * 2. 验证 native 自身完整性
 * 3. 返回 0=成功，非 0=错误码
 */
jint initProtection(JNIEnv *env, jclass clazz) {
    // 验证 native 自身完整性
    if (!verify_native_self_integrity()) {
        return -1; // native 库被篡改
    }

    // 验证 JAR 明文层完整性（引导类哈希清单 + .enc Merkle，native 侧锚定）
    if (!verify_bootstrap_classes(env)) {
        return -3; // 插件 JAR 明文类被篡改
    }

    // 初始化密钥链
    if (!protection_init_keys()) {
        return -2; // 密钥初始化失败
    }

    return 0;
}

// 解密入口调用方校验：回调 JvmAntiDebug.isTrustedDecryptCaller()。
// 阻断同服恶意插件/脚本直接反射 n6/n10/n11 脱壳：反射得到的栈帧里不存在
// ProtectedClassLoader 或其定义的类，校验必返回 false。
// fail-open 说明：检测类缺失 / 检测过程异常时放行——生产 JAR 中 JvmAntiDebug 为
// bootstrap 明文类且受 Merkle+Ed25519 完整性门保护，缺失只可能发生在 harness/测试环境；
// 若因基础设施异常误拒合法解密，整个插件将无法启动。
bool trusted_decrypt_caller(JNIEnv *env) {
    if (!env) return true;
    jclass antiDebug = env->FindClass("xuanmo/arcartxsuite/security/protection/JvmAntiDebug");
    if (!antiDebug) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return true;
    }
    jmethodID m = env->GetStaticMethodID(antiDebug, "isTrustedDecryptCaller", "()Z");
    if (!m) {
        if (env->ExceptionCheck()) env->ExceptionClear();
        return true;
    }
    jboolean ok = env->CallStaticBooleanMethod(antiDebug, m);
    if (env->ExceptionCheck()) {
        env->ExceptionClear();
        return true;
    }
    return ok == JNI_TRUE;
}

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM *vm, void *reserved) {
    JNIEnv *env;
    if (vm->GetEnv(reinterpret_cast<void **>(&env), JNI_VERSION_1_8) != JNI_OK) {
        return JNI_ERR;
    }

    jclass clazz = env->FindClass("xuanmo/arcartxsuite/security/NativeBridge");
    if (!clazz) {
        return JNI_ERR;
    }

    // 注册所有 native 方法（混淆后的名称 n0-n9）
    JNINativeMethod methods[] = {
        // 原有方法
        {"n0", "()I",         reinterpret_cast<void *>(nativeVersion)},
        {"n3", "()I",         reinterpret_cast<void *>(environmentCheck)},
        {"n4", "([B[B)[B",   reinterpret_cast<void *>(decryptModule)},
        {"n10", "([B[B[B)[B", reinterpret_cast<void *>(decryptModuleVerified)},
        // JAR 保护层新增
        {"n5", "()I",         reinterpret_cast<void *>(initProtection)},
        {"n6", "([B[B)[B",   reinterpret_cast<void *>(decryptClass)},
        {"n11", "([B[B[B)[B", reinterpret_cast<void *>(decryptModuleClass)},
        {"n7", "([B[B)Z",    reinterpret_cast<void *>(verifyIntegrity)},
        {"verifyResponseSig", "(J[B[B)Z", reinterpret_cast<void *>(verifyResponseSig)},
        {"responseVerifyActive", "()Z", reinterpret_cast<void *>(responseVerifyActive)},
        {"n8", "()I",         reinterpret_cast<void *>(enhancedEnvironmentCheck)},
        {"n9", "()[B",        reinterpret_cast<void *>(getHardwareFingerprint)},
    };

    if (env->RegisterNatives(clazz, methods, sizeof(methods) / sizeof(methods[0])) != 0) {
        return JNI_ERR;
    }

    return JNI_VERSION_1_8;
}

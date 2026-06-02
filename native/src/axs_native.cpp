#include "axs_native.h"

// AXS Native Bridge - 版本号
static constexpr int AXS_NATIVE_VERSION = 1;

JNIEXPORT jint JNICALL
Java_xuanmo_arcartxsuite_security_NativeBridge_nativeVersion(JNIEnv *env, jclass clazz) {
    return AXS_NATIVE_VERSION;
}

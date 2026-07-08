package xuanmo.arcartxsuite.security;

/**
 * 仅供 CI 运行时自校验用的最小桩类（NOT for production）。
 *
 * JNI_OnLoad 会 FindClass("xuanmo/arcartxsuite/security/NativeBridge") 并
 * RegisterNatives 绑定 n0..n11——若类里缺少对应 native 方法声明，RegisterNatives
 * 失败会导致 System.load 抛异常。故此处按 axs_native.cpp 的 methods[] 逐一声明
 * 相同的名字/签名。测试只调用 n5()（initProtection → verify_native_self_integrity）。
 *
 * 与生产 NativeBridge 不同：本桩不在 static 块里 loadLibrary，加载时机由 harness 控制。
 */
public class NativeBridge {
    public static native int n0();                              // nativeVersion            ()I
    public static native byte[] n1(byte[] a, byte[] b);         // decryptResource          ([B[B)[B
    public static native byte[] n2(byte[] a, byte[] b, byte[] c); // unwrapResourceKey      ([B[B[B)[B
    public static native int n3();                              // environmentCheck         ()I
    public static native byte[] n4(byte[] a, byte[] b);         // decryptModule            ([B[B)[B
    public static native byte[] n10(byte[] a, byte[] b, byte[] c); // decryptModuleVerified ([B[B[B)[B
    public static native int n5();                              // initProtection           ()I
    public static native byte[] n6(byte[] a, byte[] b);         // decryptClass             ([B[B)[B
    public static native byte[] n11(byte[] a, byte[] b, byte[] c); // decryptModuleClass    ([B[B[B)[B
    public static native boolean n7(byte[] a, byte[] b);        // verifyIntegrity          ([B[B)Z
    public static native int n8();                              // enhancedEnvironmentCheck ()I
    public static native byte[] n9();                           // getHardwareFingerprint   ()[B
}

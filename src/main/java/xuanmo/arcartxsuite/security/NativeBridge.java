package xuanmo.arcartxsuite.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Native JNI 桥接类，提供加密/解密、签名验证、反调试等安全操作。
 * 所有 native 方法名已随机化，实际映射由 JNI_OnLoad 通过 RegisterNatives 动态注册。
 */
public final class NativeBridge {

    private static volatile boolean available = false;
    private static volatile String loadError = null;

    static {
        try {
            loadNativeFromClasspath();
            available = true;
        } catch (Exception e) {
            loadError = e.getMessage();
        }
    }

    public static boolean isAvailable() {
        return available;
    }

    public static String getLoadError() {
        return loadError;
    }

    private static void loadNativeFromClasspath() throws IOException {
        String osName = System.getProperty("os.name").toLowerCase();
        String libName;
        if (osName.contains("win")) {
            libName = "axs-native.dll";
        } else if (osName.contains("linux")) {
            libName = "libaxs-native.so";
        } else if (osName.contains("mac")) {
            libName = "libaxs-native.dylib";
        } else {
            throw new UnsupportedOperationException("Unsupported OS: " + osName);
        }

        String resourcePath = "/native/" + libName;
        try (InputStream in = NativeBridge.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Native library not found: " + resourcePath);
            }

            Path tempDir = Files.createTempDirectory("axs_native");
            tempDir.toFile().deleteOnExit();
            Path tempFile = tempDir.resolve(libName);
            tempFile.toFile().deleteOnExit();

            Files.copy(in, tempFile, StandardCopyOption.REPLACE_EXISTING);
            System.load(tempFile.toAbsolutePath().toString());
        }
    }

    private NativeBridge() {}

    // Native 层双向校验入口
    @SuppressWarnings("unused")
    static boolean t0() { return true; }

    // ═══ 原有 native 方法 ════════════════════════════════════════

    /** native 库版本号 */
    public static native int n0();

    /** 解密受保护的资源（AES-256-GCM + GZIP） */
    public static native byte[] n1(byte[] encrypted, byte[] keyMaterial);

    /** 解包资源密钥（AES-256-GCM unwrap） */
    public static native byte[] n2(byte[] wrappedKey, byte[] iv, byte[] material);

    /** 环境安全检查（反调试/反篡改） */
    public static native int n3();

    /** 解密云端模块 .axb 文件（AES-256-GCM + GZIP） */
    public static native byte[] n4(byte[] encryptedAxb, byte[] key);

    // ═══ JAR 保护层新增 native 方法 ══════════════════════════════

    /**
     * 初始化保护子系统（派生 master key、启动反调试监控、执行初始完整性校验）。
     * 返回 0 = 成功，非 0 = 错误码。
     */
    public static native int n5();

    /**
     * 解密加密的 .class 字节码。
     * @param classNameHash 类名的 SHA-256 哈希（32 字节）
     * @param encData       .enc 格式的加密数据
     * @return 解密后的原始字节码，失败返回 null
     */
    public static native byte[] n6(byte[] classNameHash, byte[] encData);

    /**
     * 验证 JAR 完整性（Merkle 树 + Ed25519 签名）。
     * @param rootHash   计算得到的 Merkle 根哈希（32 字节）
     * @param signature  PROTECTION.MF 中的 Ed25519 签名（64 字节）
     * @return true = 完整性通过
     */
    public static native boolean n7(byte[] rootHash, byte[] signature);

    /**
     * 增强型调试环境检测（Frida、硬件断点、IAT/PLT hook）。
     * 返回威胁等级 bitmap（0 = 安全）。
     */
    public static native int n8();

    /**
     * 获取当前硬件指纹哈希（32 字节 SHA-256）。
     */
    public static native byte[] n9();
}

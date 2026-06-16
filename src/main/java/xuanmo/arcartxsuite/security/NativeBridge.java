package xuanmo.arcartxsuite.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Native JNI 桥接类，提供加密/解密、签名验证等安全操作。
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

    /** 是否成功加载了原生库。 */
    public static boolean isAvailable() {
        return available;
    }

    /** 获取加载失败的错误信息（null 表示加载成功）。 */
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
            throw new UnsupportedOperationException("不支持的操作系统: " + osName);
        }

        String resourcePath = "/native/" + libName;
        try (InputStream in = NativeBridge.class.getResourceAsStream(resourcePath)) {
            if (in == null) {
                throw new IOException("Classpath 中未找到原生库: " + resourcePath);
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

    // Native 层双向校验入口（方法名必须保留）。
    // environmentCheck 在 native 侧会回调此方法来验证 Java 层未被篡改/剥离。
    @SuppressWarnings("unused")
    static boolean t0() { return true; }

    // 以下 native 方法名已随机化，实际映射由 JNI_OnLoad 通过 RegisterNatives 动态注册。
    // 这种设计消除了静态符号表中的自解释 JNI 方法名，增加逆向分析难度。

    /** native 库版本号 */
    public static native int n0();

    /**
     * 解密受保护的资源（AES-256-GCM + GZIP）。
     * 对应原 decryptResource。
     */
    public static native byte[] n1(byte[] encrypted, byte[] keyMaterial);

    /**
     * 解包资源密钥（AES-256-GCM unwrap）。
     * 对应原 unwrapResourceKey。
     */
    public static native byte[] n2(byte[] wrappedKey, byte[] iv, byte[] material);

    /**
     * 环境安全检查（反调试/反篡改）。
     * 对应原 environmentCheck。
     */
    public static native int n3();

    /**
     * 解密云端模块 .axb 文件（AES-256-GCM + GZIP）。
     * 对应原 decryptModule。
     */
    public static native byte[] n4(byte[] encryptedAxb, byte[] key);
}

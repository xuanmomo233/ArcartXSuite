package xuanmo.arcartxsuite.security;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Step 2：将 AXB 解密 + License 签名校验下沉到 C++ 编写的本地库。
 * <p>
 * Java 侧只暴露 native 方法签名，核心密钥派生 / 解密 / 签名验证
 * 全部在 native 代码中完成，反编译 Java 字节码无法获取这些逻辑。
 * <p>
 * 本地库在运行时按平台自动选择：
 * <ul>
 *   <li>Windows x64 → {@code axs-native.dll}</li>
 *   <li>Linux x64   → {@code libaxs-native.so}</li>
 *   <li>Linux aarch64→ {@code libaxs-native-aarch64.so}</li>
 * </ul>
 */
public final class NativeBridge {

    private static final Logger LOGGER = Logger.getLogger("AXS-NativeBridge");
    private static boolean loaded = false;
    private static boolean available = false;

    private NativeBridge() {}

    // ═══ 生命周期 ═══════════════════════════════════════════════

    /**
     * 尝试加载本地库。无法加载时回退到纯 Java 实现。
     *
     * @param dataFolder 插件数据目录（存放解压的 .dll/.so）
     */
    public static synchronized void tryLoad(File dataFolder) {
        if (loaded) return;
        loaded = true;
        try {
            String libName = platformLibraryName();
            Path nativeDir = dataFolder.toPath().resolve("native");
            Files.createDirectories(nativeDir);
            Path libFile = nativeDir.resolve(libName);

            // 从 jar 解压到数据目录（兼容 Mohist 混合端类加载器）
            try (InputStream input = MohistCompat.getResourceSafe(
                    "native/" + libName, NativeBridge.class.getClassLoader())) {
                if (input == null) {
                    LOGGER.fine("未找到 native 库 " + libName + "，使用纯 Java 回退");
                    return;
                }
                Files.copy(input, libFile, StandardCopyOption.REPLACE_EXISTING);
            }

            System.load(libFile.toAbsolutePath().toString());
            int version = nativeVersion();
            if (version < 1) {
                LOGGER.warning("native 库版本不兼容: " + version);
                return;
            }
            available = true;
            LOGGER.info("AXS native 库已加载 (v" + version + "): " + libName);
        } catch (UnsatisfiedLinkError | IOException exception) {
            LOGGER.log(Level.FINE, "native 库加载失败，回退到纯 Java", exception);
        }
    }

    /** native 库是否可用 */
    public static boolean isAvailable() {
        return available;
    }

    // ═══ Native 方法声明 ════════════════════════════════════════

    /**
     * native 库版本号。用于兼容性检查。
     */
    static native int nativeVersion();

    /**
     * AXB 资源解密（替代 ProtectedResourceStore / EncryptedResourceLoader 中的 Java AES-GCM）。
     *
     * @param encrypted   加密后的字节数组（含 magic + IV + payload）
     * @param keyMaterial 密钥派生材料
     * @return 解密并解压后的明文字节，失败返回 null
     */
    public static native byte[] decryptResource(byte[] encrypted, byte[] keyMaterial);

    /**
     * License ticket 签名验证（替代 LicenseVerifier 中的 Java ECDSA/RSA 验签）。
     *
     * @param ticketJson  ticket JSON 字节
     * @param signature   服务端签名
     * @return 验证通过返回 true
     */
    public static native boolean verifyTicketSignature(byte[] ticketJson, byte[] signature);

    /**
     * 在 native 侧派生模块资源解密密钥（替代 ResourceKeyManager.unwrap）。
     *
     * @param wrappedKey  被包装的密钥密文
     * @param iv          GCM IV
     * @param material    包装密钥派生材料
     * @return 解包后的原始密钥，失败返回 null
     */
    public static native byte[] unwrapResourceKey(byte[] wrappedKey, byte[] iv, byte[] material);

    /**
     * 运行时环境完整性检查（native 侧检测调试器 / Agent / 内存篡改）。
     *
     * @return 状态标志位：0=正常, bit0=agent, bit1=debugger, bit2=tamper
     */
    public static native int environmentCheck();

    // ═══ 平台检测 ═══════════════════════════════════════════════

    private static String platformLibraryName() {
        String os = System.getProperty("os.name", "").toLowerCase();
        String arch = System.getProperty("os.arch", "").toLowerCase();
        if (os.contains("win")) {
            return "axs-native.dll";
        } else if (os.contains("linux")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                return "libaxs-native-aarch64.so";
            }
            return "libaxs-native.so";
        }
        return "libaxs-native.so";
    }
}

package xuanmo.arcartxsuite.security;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import xuanmo.arcartxsuite.security.protection.JvmAntiDebug;

/**
 * Native JNI 桥接类，提供加密/解密、签名验证、反调试等安全操作。
 * 所有 native 方法名已随机化，实际映射由 JNI_OnLoad 通过 RegisterNatives 动态注册。
 */
public final class NativeBridge {

    private static volatile boolean available = false;
    private static volatile String loadError = null;

    // V2 DLL 加密密钥片段：占位 0，由 scripts/encrypt-native.py gen-key 在 CI 构建时 patch。
    // ═══ V2_KEY_FRAGMENTS_START ═══
    private static final long _KF0 = 0x0000000000000000L;
    private static final long _KM0 = 0x0000000000000000L;
    private static final long _KF1 = 0x0000000000000000L;
    private static final long _KM1 = 0x0000000000000000L;
    private static final long _KF2 = 0x0000000000000000L;
    private static final long _KM2 = 0x0000000000000000L;
    private static final long _KF3 = 0x0000000000000000L;
    private static final long _KM3 = 0x0000000000000000L;
    // ═══ V2_KEY_FRAGMENTS_END ═══

    private static final byte[] _ENC_MAGIC = { 'A', 'X', 'N', 'E' };
    private static final byte[] _ENC_SALT =
            "axs-native-enc-v1".getBytes(java.nio.charset.StandardCharsets.UTF_8);

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

        String encName = libName.substring(0, libName.lastIndexOf('.')) + ".enc";
        byte[] libBytes = null;

        // V2: 优先加载 at-rest 加密库（AES-256-GCM .enc），运行时解密后再 System.load。
        // JAR 内不再含明文 DLL/SO，可击溃 unzip + strings/IDA 直接提取 native 二进制。
        try (InputStream encIn = NativeBridge.class.getResourceAsStream("/native/" + encName)) {
            if (encIn != null) {
                libBytes = decryptNativeLib(readAll(encIn));
            }
        } catch (Exception e) {
            throw new IOException("Native library decrypt failed: " + e.getMessage());
        }

        // 回退：开发构建可能直接打包明文库（无 .enc 时）。
        if (libBytes == null) {
            try (InputStream in = NativeBridge.class.getResourceAsStream("/native/" + libName)) {
                if (in == null) {
                    throw new IOException("Native library not found: /native/" + encName
                            + " or /native/" + libName);
                }
                libBytes = readAll(in);
            }
        }

        Path tempDir = Files.createTempDirectory("axs_native");
        tempDir.toFile().deleteOnExit();
        Path tempFile = tempDir.resolve(libName);
        tempFile.toFile().deleteOnExit();
        Files.write(tempFile, libBytes);
        System.load(tempFile.toAbsolutePath().toString());
        java.util.Arrays.fill(libBytes, (byte) 0);
    }

    private static byte[] readAll(InputStream in) throws IOException {
        java.io.ByteArrayOutputStream bos = new java.io.ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int r;
        while ((r = in.read(buf)) != -1) {
            bos.write(buf, 0, r);
        }
        return bos.toByteArray();
    }

    /** V2: 由 4 段被掩码的 8 字节片段 XOR 还原 32 字节原料，再 SHA-256 派生 AES-256 密钥。 */
    private static byte[] reconstructKey() throws Exception {
        long[] frags = { _KF0, _KF1, _KF2, _KF3 };
        long[] masks = { _KM0, _KM1, _KM2, _KM3 };
        byte[] raw = new byte[32];
        for (int i = 0; i < 4; i++) {
            long val = frags[i] ^ masks[i];
            for (int j = 7; j >= 0; j--) {
                raw[i * 8 + (7 - j)] = (byte) ((val >>> (j * 8)) & 0xFF);
            }
        }
        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
        md.update(raw);
        md.update(_ENC_SALT);
        byte[] aesKey = md.digest();
        java.util.Arrays.fill(raw, (byte) 0);
        return aesKey;
    }

    /** V2: 解密 .enc 格式 [AXNE][ver:4][iv:12][ct+tag]（AES-256-GCM，128-bit tag）。 */
    private static byte[] decryptNativeLib(byte[] encData) throws Exception {
        if (encData.length < 4 + 4 + 12 + 16) {
            throw new IOException("encrypted native too short");
        }
        for (int i = 0; i < 4; i++) {
            if (encData[i] != _ENC_MAGIC[i]) {
                throw new IOException("bad native enc magic");
            }
        }
        int ver = ((encData[4] & 0xFF) << 24) | ((encData[5] & 0xFF) << 16)
                | ((encData[6] & 0xFF) << 8) | (encData[7] & 0xFF);
        if (ver != 1) {
            throw new IOException("unsupported native enc version: " + ver);
        }
        byte[] iv = java.util.Arrays.copyOfRange(encData, 8, 20);
        byte[] ct = java.util.Arrays.copyOfRange(encData, 20, encData.length);
        byte[] aesKey = reconstructKey();
        try {
            javax.crypto.Cipher cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding");
            javax.crypto.spec.SecretKeySpec ks = new javax.crypto.spec.SecretKeySpec(aesKey, "AES");
            javax.crypto.spec.GCMParameterSpec gcm = new javax.crypto.spec.GCMParameterSpec(128, iv);
            cipher.init(javax.crypto.Cipher.DECRYPT_MODE, ks, gcm);
            return cipher.doFinal(ct);
        } finally {
            java.util.Arrays.fill(aesKey, (byte) 0);
        }
    }

    private NativeBridge() {}

    // Native 层双向校验入口
    @SuppressWarnings("unused")
    static boolean t0() { return JvmAntiDebug.hasKnownTamperSignal(); }

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

    /** 先 Ed25519 验签再解密云端模块 .axb（签名为空/未配置时回退为仅解密）。 */
    public static native byte[] n10(byte[] encryptedAxb, byte[] key, byte[] signature);

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
     * 方案 B：自包含模块逐类解密。session 由 moduleSeed（云端下发的 32 字节 moduleKey）派生，
     * 与本体内嵌 root_seed 完全解耦，因此模块单独重建/任意已装 native 的本体均可解密。
     * @param classNameHash 类名的 SHA-256 哈希（32 字节）
     * @param encData       .enc 格式的加密数据
     * @param moduleSeed    模块种子（32 字节，= 云端下发的 moduleKey）
     * @return 解密后的原始字节码，失败返回 null
     */
    public static native byte[] n11(byte[] classNameHash, byte[] encData, byte[] moduleSeed);

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

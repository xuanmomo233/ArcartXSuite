package xuanmo.arcartxsuite.security.protection;

import xuanmo.arcartxsuite.security.NativeBridge;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Properties;
import java.util.TreeMap;
import java.util.jar.JarFile;
import java.util.logging.Logger;

/**
 * 保护子系统初始化器。
 * 在插件最早生命周期（onLoad 或 static block）调用，负责：
 * 1. 初始化 native 保护引擎（密钥派生）
 * 2. 启动 JVM + native 双层反调试监控
 * 3. 执行 JAR 完整性校验（Merkle 树 + Ed25519 签名）
 * 4. 设置 ProtectedClassLoader（如果 JAR 包含 ENCRYPTED/ 目录）
 */
public final class ProtectionInit {

    private static volatile boolean initialized = false;
    private static volatile ProtectedClassLoader protectedLoader = null;
    private static final Logger LOGGER = Logger.getLogger("AXS-Protection");

    // 检测间隔（秒），加随机偏移防止时序分析
    private static final int CHECK_INTERVAL_SECONDS = 60;

    private ProtectionInit() {}

    /**
     * 初始化保护系统。应在插件 onLoad() 中调用。
     * @param pluginJarPath 插件 JAR 文件的绝对路径
     * @return true = 初始化成功
     */
    public static boolean initialize(String pluginJarPath) {
        if (initialized) return true;

        // 1. 确认 native 库可用
        if (!NativeBridge.isAvailable()) {
            LOGGER.severe("[Protection] Native library unavailable: " + NativeBridge.getLoadError());
            return false;
        }

        // 2. 初始化 native 保护引擎
        int initResult = NativeBridge.n5();
        if (initResult != 0) {
            LOGGER.severe("[Protection] Native init failed: code " + initResult);
            return false;
        }

        // 3. 启动双层反调试监控
        JvmAntiDebug.startMonitoring(ProtectionInit::onThreatDetected, CHECK_INTERVAL_SECONDS);

        // 4. 完整性校验
        try {
            if (!verifyJarIntegrity(pluginJarPath)) {
                LOGGER.severe("[Protection] JAR integrity check failed");
                triggerTamperResponse();
                return false;
            }
        } catch (Exception e) {
            LOGGER.severe("[Protection] Integrity verification error");
            return false;
        }

        // 5. 设置 ProtectedClassLoader（如果存在加密类）
        try {
            JarFile jar = new JarFile(pluginJarPath);
            if (jar.getJarEntry("ENCRYPTED/") != null) {
                protectedLoader = new ProtectedClassLoader(
                        ProtectionInit.class.getClassLoader(), jar);
                LOGGER.info("[Protection] ProtectedClassLoader active");
            } else {
                jar.close();
            }
        } catch (Exception e) {
            LOGGER.warning("[Protection] ClassLoader setup failed: " + e.getMessage());
        }

        initialized = true;
        return true;
    }

    /**
     * 获取保护 ClassLoader（加载加密类时使用）。
     */
    public static ProtectedClassLoader getProtectedLoader() {
        return protectedLoader;
    }

    public static boolean isInitialized() {
        return initialized;
    }

    /**
     * 关闭保护系统。
     */
    public static void shutdown() {
        JvmAntiDebug.stopMonitoring();
        if (protectedLoader != null) {
            protectedLoader.deactivate();
        }
        initialized = false;
    }

    // ─── 完整性校验 ─────────────────────────────────────────────

    private static boolean verifyJarIntegrity(String jarPath) throws Exception {
        JarFile jar = new JarFile(jarPath);
        try {
            // 读取 PROTECTION.MF
            var pmfEntry = jar.getJarEntry("META-INF/PROTECTION.MF");
            if (pmfEntry == null) {
                // 没有保护元数据 = 未加密 JAR，跳过校验
                return true;
            }

            Properties pmf = new Properties();
            try (InputStream is = jar.getInputStream(pmfEntry)) {
                pmf.load(is);
            }

            String integrityHashHex = pmf.getProperty("Integrity-Hash");
            String signatureHex = pmf.getProperty("Signature");
            if (integrityHashHex == null || signatureHex == null) return false;

            // 计算所有加密文件的 Merkle 根哈希
            byte[] computedRoot = computeMerkleRoot(jar);
            byte[] expectedRoot = hexToBytes(integrityHashHex);
            byte[] signature = hexToBytes(signatureHex);

            // 比对根哈希
            if (!MessageDigest.isEqual(computedRoot, expectedRoot)) {
                return false;
            }

            // 调用 native 验证 Ed25519 签名（公钥编译时嵌入 native）
            return NativeBridge.n7(computedRoot, signature);
        } finally {
            jar.close();
        }
    }

    private static byte[] computeMerkleRoot(JarFile jar) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        TreeMap<String, byte[]> hashes = new TreeMap<>();

        var entries = jar.entries();
        while (entries.hasMoreElements()) {
            var entry = entries.nextElement();
            String name = entry.getName();
            if (name.startsWith("ENCRYPTED/") && name.endsWith(".enc")) {
                try (InputStream is = jar.getInputStream(entry)) {
                    byte[] data = readFully(is);
                    hashes.put(name, md.digest(data));
                    md.reset();
                }
            }
        }

        if (hashes.isEmpty()) return md.digest(new byte[0]);

        // 构建 Merkle 树
        byte[][] leaves = hashes.values().toArray(new byte[0][]);
        while (leaves.length > 1) {
            int newLen = (leaves.length + 1) / 2;
            byte[][] next = new byte[newLen][];
            for (int i = 0; i < leaves.length; i += 2) {
                if (i + 1 < leaves.length) {
                    md.update(leaves[i]);
                    md.update(leaves[i + 1]);
                } else {
                    md.update(leaves[i]);
                    md.update(leaves[i]);
                }
                next[i / 2] = md.digest();
                md.reset();
            }
            leaves = next;
        }
        return leaves[0];
    }

    // ─── 威胁响应 ───────────────────────────────────────────────

    private static void onThreatDetected(int threatLevel) {
        // 静默降级：不崩溃，但禁用保护功能（使后续类加载失败）
        LOGGER.warning("[Protection] Threat detected: 0x" + Integer.toHexString(threatLevel));
        triggerTamperResponse();
    }

    private static void triggerTamperResponse() {
        // 延迟 5-30 秒后降级（避免二分法定位检测点）
        int delay = 5 + (int)(Math.random() * 25);
        new Thread(() -> {
            try { Thread.sleep(delay * 1000L); } catch (InterruptedException ignored) {}
            if (protectedLoader != null) {
                protectedLoader.deactivate();
            }
        }, "axs-gc-finalize").start();
    }

    // ─── 工具方法 ───────────────────────────────────────────────

    private static byte[] readFully(InputStream is) throws Exception {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        while ((n = is.read(buf)) != -1) bos.write(buf, 0, n);
        return bos.toByteArray();
    }

    private static byte[] hexToBytes(String hex) {
        int len = hex.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return data;
    }
}

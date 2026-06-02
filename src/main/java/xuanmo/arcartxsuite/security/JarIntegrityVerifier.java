package xuanmo.arcartxsuite.security;

import java.io.IOException;
import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.jar.JarFile;
import java.util.logging.Logger;

/**
 * Step 4：运行时环境封锁 + Jar 完整性校验。
 * <p>
 * 检查维度：
 * <ol>
 *   <li>JVM 参数级 —— 检测 -javaagent / -agentlib / -Xdebug / jdwp</li>
 *   <li>Attach API 级 —— 检测 DisableAttachMechanism 是否启用</li>
 *   <li>Native 级 —— 若 NativeBridge 可用，调用 C++ 侧环境检测</li>
 *   <li>Jar 完整性 —— SHA-256 摘要校验（由 EmbedIntegrityTask 写入）</li>
 * </ol>
 */
public final class JarIntegrityVerifier {

    private static final String INTEGRITY_RESOURCE = "META-INF/axs-integrity.bin";
    public static final int FLAG_TAMPERED        = 1;
    public static final int FLAG_AGENT_DETECTED  = 2;
    public static final int FLAG_DEBUG_ATTACHED  = 4;
    public static final int FLAG_ATTACH_OPEN     = 8;   // Attach API 未关闭
    public static final int FLAG_NATIVE_ALERT    = 16;  // Native 侧告警

    private final Logger logger;
    private int flags = 0;

    public JarIntegrityVerifier(Logger logger) {
        this.logger = logger;
    }

    /**
     * 执行全部环境与完整性检查。
     *
     * @param pluginClass 插件主类（定位 jar 路径）
     * @return 标志位组合（0 = 全部通过）
     */
    public int verify(Class<?> pluginClass) {
        flags = 0;
        checkAgentInjection();
        checkDebugger();
        checkAttachMechanism();
        checkNativeEnvironment();
        checkJarIntegrity(pluginClass);
        return flags;
    }

    public boolean isTampered()        { return (flags & FLAG_TAMPERED) != 0; }
    public boolean isAgentDetected()   { return (flags & FLAG_AGENT_DETECTED) != 0; }
    public boolean isDebuggerAttached(){ return (flags & FLAG_DEBUG_ATTACHED) != 0; }
    public boolean isAttachOpen()      { return (flags & FLAG_ATTACH_OPEN) != 0; }
    public boolean isNativeAlert()     { return (flags & FLAG_NATIVE_ALERT) != 0; }
    public boolean isClean()           { return flags == 0; }
    public int flags()                 { return flags; }

    /**
     * 生成人类可读的安全报告摘要。
     */
    public String summary() {
        if (flags == 0) return "环境安全检查通过";
        StringBuilder sb = new StringBuilder("安全警告:");
        if (isTampered())         sb.append(" [Jar篡改]");
        if (isAgentDetected())    sb.append(" [Agent注入]");
        if (isDebuggerAttached()) sb.append(" [调试器]");
        if (isAttachOpen())       sb.append(" [Attach未封锁]");
        if (isNativeAlert())      sb.append(" [Native告警]");
        return sb.toString();
    }

    // ─── 1. Agent 注入检测 ──────────────────────────────────────

    private void checkAgentInjection() {
        List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String arg : args) {
            String lower = arg.toLowerCase();
            if (lower.startsWith("-javaagent") || lower.startsWith("-agentlib")
                || lower.startsWith("-agentpath")) {
                // 白名单：ClassFinal agent 是我们自己的
                if (lower.contains("classfinal")) continue;
                flags |= FLAG_AGENT_DETECTED;
                logger.warning("[AXS-Security] 检测到非预期的 Java Agent: " + arg);
                return;
            }
        }

        // 动态 attach 检测
        if (System.getProperty("sun.jvm.hotspot.debugger.serverID") != null) {
            flags |= FLAG_AGENT_DETECTED;
        }
    }

    // ─── 2. 调试器检测 ──────────────────────────────────────────

    private void checkDebugger() {
        List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        for (String arg : args) {
            if (arg.contains("-Xdebug") || arg.contains("-Xrunjdwp")
                || arg.contains("jdwp") || arg.contains("-agentlib:jdwp")) {
                flags |= FLAG_DEBUG_ATTACHED;
                logger.warning("[AXS-Security] 检测到调试器连接参数: " + arg);
                return;
            }
        }
    }

    // ─── 3. Attach API 封锁检测 ─────────────────────────────────

    private void checkAttachMechanism() {
        List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
        boolean disabled = false;
        for (String arg : args) {
            if (arg.contains("DisableAttachMechanism")) {
                disabled = true;
                break;
            }
        }
        if (!disabled) {
            flags |= FLAG_ATTACH_OPEN;
            // 不发 warning，在 summary 中统一提示
        }
    }

    // ─── 4. Native 侧环境检测 ───────────────────────────────────

    private void checkNativeEnvironment() {
        if (!NativeBridge.isAvailable()) return;
        try {
            int nativeFlags = NativeBridge.environmentCheck();
            if (nativeFlags != 0) {
                flags |= FLAG_NATIVE_ALERT;
                if ((nativeFlags & 1) != 0) flags |= FLAG_AGENT_DETECTED;
                if ((nativeFlags & 2) != 0) flags |= FLAG_DEBUG_ATTACHED;
                logger.warning("[AXS-Security] Native 环境检测告警: 0x"
                    + Integer.toHexString(nativeFlags));
            }
        } catch (UnsatisfiedLinkError ignored) {
            // native 不可用，静默跳过
        }
    }

    // ─── 5. Jar 文件完整性校验 ──────────────────────────────────

    private void checkJarIntegrity(Class<?> pluginClass) {
        Path jarPath = resolveJarPath(pluginClass);
        if (jarPath == null || !Files.exists(jarPath)) return;

        try (JarFile jar = new JarFile(jarPath.toFile())) {
            var integrityEntry = jar.getJarEntry(INTEGRITY_RESOURCE);
            if (integrityEntry == null) return;

            byte[] expectedDigest;
            try (InputStream input = jar.getInputStream(integrityEntry)) {
                expectedDigest = input.readAllBytes();
            }

            byte[] actualDigest = computeClassDigest(jar);
            if (!MessageDigest.isEqual(expectedDigest, actualDigest)) {
                flags |= FLAG_TAMPERED;
                logger.severe("[AXS-Security] Jar 完整性校验失败！文件可能已被篡改。");
            }
        } catch (IOException exception) {
            logger.warning("[AXS-Security] 完整性校验 IO 异常: " + exception.getMessage());
        }
    }

    private byte[] computeClassDigest(JarFile jar) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            jar.stream()
                .filter(entry -> entry.getName().endsWith(".class"))
                .sorted((a, b) -> a.getName().compareTo(b.getName()))
                .forEach(entry -> {
                    try (InputStream input = jar.getInputStream(entry)) {
                        digest.update(entry.getName().getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        digest.update(input.readAllBytes());
                    } catch (IOException e) {
                        throw new RuntimeException(e);
                    }
                });
            return digest.digest();
        } catch (NoSuchAlgorithmException exception) {
            throw new IOException("SHA-256 不可用", exception);
        }
    }

    private Path resolveJarPath(Class<?> pluginClass) {
        try {
            var location = pluginClass.getProtectionDomain().getCodeSource();
            if (location == null) return null;
            var uri = location.getLocation().toURI();
            Path path = Path.of(uri);
            return Files.isRegularFile(path) ? path : null;
        } catch (URISyntaxException | SecurityException exception) {
            return null;
        }
    }
}

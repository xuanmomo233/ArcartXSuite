package xuanmo.arcartxsuite.security.protection;

import xuanmo.arcartxsuite.security.NativeBridge;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ThreadLocalRandom;

/**
 * JVM 层反调试/反 Hook 检测。
 * 与 native 层 (n8) 互为补充：任一层检测到威胁均触发响应。
 */
public final class JvmAntiDebug {

    // 威胁标志位
    public static final int THREAT_JDWP       = 0x01;
    public static final int THREAT_AGENT      = 0x02;
    public static final int THREAT_FRIDA      = 0x04;
    public static final int THREAT_ATTACH     = 0x08;
    public static final int THREAT_SUSPICIOUS = 0x10;

    private static volatile int lastThreatLevel = 0;
    private static volatile String lastTrigger = "";
    private static volatile ThreatResponseHandler responseHandler;
    private static volatile long lastHardRejectCheckMillis = 0L;
    private static volatile boolean lastHardRejectResult = false;
    private static ScheduledExecutorService scheduler;

    private JvmAntiDebug() {}

    /**
     * 启动定期检测。
     * @param handler 威胁响应处理器
     * @param intervalSeconds 检测间隔秒数（加入随机偏移）
     */
    public static void startMonitoring(ThreatResponseHandler handler, int intervalSeconds) {
        responseHandler = handler;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "axs-gc-worker"); // 伪装线程名
            t.setDaemon(true);
            return t;
        });

        // 立即执行一次
        int initialDelay = ThreadLocalRandom.current().nextInt(2, 5);
        scheduler.scheduleAtFixedRate(JvmAntiDebug::performCheck,
                initialDelay, intervalSeconds, TimeUnit.SECONDS);
    }

    public static void stopMonitoring() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            scheduler = null;
        }
    }

    public static int getLastThreatLevel() {
        return lastThreatLevel;
    }

    public static String getLastTrigger() {
        return lastTrigger;
    }

    public static String describeThreat(int t) {
        StringBuilder sb = new StringBuilder();
        if ((t & THREAT_JDWP) != 0) sb.append("JDWP ");
        if ((t & THREAT_AGENT) != 0) sb.append("AGENT ");
        if ((t & THREAT_FRIDA) != 0) sb.append("FRIDA ");
        if ((t & THREAT_ATTACH) != 0) sb.append("ATTACH ");
        if ((t & THREAT_SUSPICIOUS) != 0) sb.append("SUSPICIOUS ");
        if (lastTrigger != null && !lastTrigger.isEmpty()) sb.append("thread='").append(lastTrigger).append("'");
        return sb.toString().trim();
    }

    private static void performCheck() {
        try {
            int javaThreats = checkAll();
            int nativeThreats = 0;
            boolean nativeAvailable = NativeBridge.isAvailable();

            // 合并 native 层检测结果（Frida/硬件断点/IAT-Hook 等）
            if (nativeAvailable) {
                nativeThreats = NativeBridge.n8();
            }

            int threats = javaThreats | nativeThreats;
            lastThreatLevel = threats;
            ProtectionEnvironment.observeThreats(javaThreats, nativeThreats, nativeAvailable);
            // AGENT(instrumentation present) alone must NOT trigger downgrade:
            // legit profilers/monitors (Spark/async-profiler) load as javaagent.
            if (nativeAvailable && ProtectionEnvironment.isHardThreatSignal(javaThreats, nativeThreats)
                    && responseHandler != null) {
                responseHandler.onThreatDetected(threats);
            }
        } catch (Exception ignored) {
            // 检测过程中发生异常时静默忽略，避免检测自身崩溃导致保护层误降级
        }
    }

    public static int checkAll() {
        int threats = 0;
        if (isJdwpAttached()) threats |= THREAT_JDWP;
        if (isAgentPresent()) threats |= THREAT_AGENT;
        if (hasSuspiciousThreads()) threats |= THREAT_FRIDA | THREAT_SUSPICIOUS;
        if (isAttachApiActive()) threats |= THREAT_ATTACH;
        return threats;
    }

    public static boolean hasKnownTamperSignal() {
        return checkAll() != 0;
    }

    public static boolean hasHardRejectSignal() {
        long now = System.currentTimeMillis();
        long lastCheck = lastHardRejectCheckMillis;
        if (now - lastCheck <= 2000L) {
            return lastHardRejectResult;
        }
        boolean result = isHardJdwpAttached() || hasHardFridaSignal();
        lastHardRejectResult = result;
        lastHardRejectCheckMillis = now;
        return result;
    }

    private static boolean isHardJdwpAttached() {
        try {
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : args) {
                if (arg.contains("-agentlib:jdwp") || arg.contains("-Xrunjdwp")) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean hasHardFridaSignal() {
        try {
            Set<Thread> threads = Thread.getAllStackTraces().keySet();
            for (Thread t : threads) {
                String name = t.getName().toLowerCase();
                if (name.contains("frida") || name.contains("gum-js-loop")
                        || name.contains("linjector")) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ─── 检测方法 ─────────────────────────────────────────────────

    private static boolean isJdwpAttached() {
        try {
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : args) {
                if (arg.contains("-agentlib:jdwp") || arg.contains("-Xrunjdwp")) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean isAgentPresent() {
        try {
            // 检查 sun.instrument.InstrumentationImpl 是否已加载
            Class.forName("sun.instrument.InstrumentationImpl", false,
                    ClassLoader.getSystemClassLoader());
            return true;
        } catch (ClassNotFoundException e) {
            return hasTransformerAgentSignals();
        }
    }

    private static boolean hasTransformerAgentSignals() {
        try {
            for (StackTraceElement[] stack : Thread.getAllStackTraces().values()) {
                for (StackTraceElement element : stack) {
                    String className = element.getClassName().toLowerCase();
                    if (className.contains("sun.instrument")
                            || className.contains("java.lang.instrument")) {
                        return true;
                    }
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean hasSuspiciousThreads() {
        try {
            Set<Thread> threads = Thread.getAllStackTraces().keySet();
            for (Thread t : threads) {
                String name = t.getName().toLowerCase();
                // Strong signal: Frida-family injection/hook threads
                if (name.contains("frida") || name.contains("gum-js-loop")
                        || name.contains("linjector")) {
                    lastTrigger = t.getName();
                    return true;
                }
                // Strong signal: runtime debugger attach (JDWP/JDI/debugger threads)
                if (name.contains("jdwp") || name.contains("debugger")
                        || name.contains("jdi-")) {
                    lastTrigger = t.getName();
                    return true;
                }
                // Strong signal: JVMTI bytecode-injection frameworks
                if (name.contains("jvmti") || name.contains("byteman")) {
                    lastTrigger = t.getName();
                    return true;
                }
                // Removed broad substrings "interceptor"/"gmain"/"attach listener":
                // legit profilers (Spark/async-profiler attach via the Attach API,
                // creating the standard "Attach Listener" thread) false-positived,
                // wrongly downgrading protection (the 0x16 false alarm).
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean isAttachApiActive() {
        try {
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            Path attachFile = Paths.get(System.getProperty("java.io.tmpdir"), ".attach_pid" + pid);
            if (!Files.exists(attachFile)) {
                return false;
            }
            // Spark/async-profiler 等 legit profiler 通过 Attach API attach 后，
            // JVM 会在 attach 完成后删除 .attach_pid 文件。仅在文件存在且创建时间在最近 10 秒内
            // 才视为活跃 attach（正在 attach 中），避免误杀已完成 attach 的 legit profiler。
            try {
                long lastModified = Files.getLastModifiedTime(attachFile).toMillis();
                long ageMs = System.currentTimeMillis() - lastModified;
                if (ageMs > 10_000L) {
                    // 文件残留但已过期（attach 早已完成），不视为威胁
                    return false;
                }
            } catch (Exception ignored) {
                // 无法读取时间戳时保守判断为活跃
            }
            return true;
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * 解密入口调用方校验（由 native 层在每次类解密前回调）。
     *
     * <p>放行条件（任一）：
     * <ul>
     *   <li>保护层未就位（{@code pcl == null}，如明文 dev jar、单元测试、CI harness）——
     *       此时不存在受保护边界，放行不影响安全态势；</li>
     *   <li>调用栈中存在 {@link ProtectedClassLoader} 自身帧（本体 .enc 解密路径），
     *       或存在由 ProtectedClassLoader 定义的类（即全部加密类，如
     *       ByteArrayModuleClassLoader / CloudModuleService）的帧。</li>
     * </ul>
     * 反射调用也逃不过：StackWalker 返回的是真实调用帧，攻击者类由其自身
     * PluginClassLoader 定义，不可能出现在受保护加载链上。
     *
     * <p>注意：检测自身异常时放行（fail-open），避免 JVM 差异导致合法解密被拒、
     * 插件无法启动。常见攻击形态（无任何受保护帧）仍会返回 false。
     */
    public static boolean isTrustedDecryptCaller() {
        try {
            ClassLoader pcl = ProtectionInit.getProtectedLoader();
            if (pcl == null) {
                return true;
            }
            return StackWalker.getInstance(StackWalker.Option.RETAIN_CLASS_REFERENCE)
                    .walk(frames -> frames.anyMatch(f -> {
                        Class<?> dc = f.getDeclaringClass();
                        return dc == ProtectedClassLoader.class || dc.getClassLoader() == pcl;
                    }));
        } catch (Throwable t) {
            return true;
        }
    }

    /**
     * 威胁响应回调接口
     */
    @FunctionalInterface
    public interface ThreatResponseHandler {
        void onThreatDetected(int threatLevel);
    }
}

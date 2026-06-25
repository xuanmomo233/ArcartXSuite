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
    private static volatile ThreatResponseHandler responseHandler;
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

    private static void performCheck() {
        try {
            int threats = checkAll();

            // 同时检查 native 层（如果可用）
            if (NativeBridge.isAvailable()) {
                threats |= NativeBridge.n8();
            }

            lastThreatLevel = threats;
            if (threats != 0 && responseHandler != null) {
                responseHandler.onThreatDetected(threats);
            }
        } catch (Exception ignored) {
            // 检测本身不应抛出异常影响宿主
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

    // ─── 检测方法 ─────────────────────────────────────────────────

    private static boolean isJdwpAttached() {
        try {
            List<String> args = ManagementFactory.getRuntimeMXBean().getInputArguments();
            for (String arg : args) {
                if (arg.contains("-agentlib:jdwp") || arg.contains("-Xrunjdwp")
                        || arg.contains("-agentpath:") || arg.contains("-javaagent:")) {
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
            return false;
        }
    }

    private static boolean hasSuspiciousThreads() {
        try {
            Set<Thread> threads = Thread.getAllStackTraces().keySet();
            for (Thread t : threads) {
                String name = t.getName().toLowerCase();
                if (name.contains("frida") || name.contains("gum-js-loop")
                        || name.contains("interceptor") || name.contains("gmain")
                        || name.contains("linjector")) {
                    return true;
                }
                if (name.contains("jdwp") || name.contains("debugger")
                        || name.contains("jdi-") || name.contains("attach listener")) {
                    return true;
                }
                if (name.contains("jvmti") || name.contains("byteman")) {
                    return true;
                }
            }
        } catch (Exception ignored) {}
        return false;
    }

    private static boolean isAttachApiActive() {
        try {
            String pid = ManagementFactory.getRuntimeMXBean().getName().split("@")[0];
            Path attachFile = Paths.get(System.getProperty("java.io.tmpdir"), ".attach_pid" + pid);
            return Files.exists(attachFile);
        } catch (Exception ignored) {}
        return false;
    }

    /**
     * 威胁响应回调接口
     */
    @FunctionalInterface
    public interface ThreatResponseHandler {
        void onThreatDetected(int threatLevel);
    }
}

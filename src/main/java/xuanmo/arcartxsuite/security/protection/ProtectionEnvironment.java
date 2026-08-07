package xuanmo.arcartxsuite.security.protection;

import java.util.logging.Logger;

import xuanmo.arcartxsuite.security.NativeBridge;

/**
 * 安全防护环境，聚合 Java 层反调试检测与 native 层完整性检测，
 * 判定服务端是否处于被调试/篡改环境。
 * <p>
 * 一旦判定为"硬威胁"（agent/debugger/tamper 或 JDWP/Frida/attach），
 * 标记为 compromised 并拒绝后续敏感操作。
 */
public final class ProtectionEnvironment {

    /** native 检测位：Java agent 注入 */
    public static final int NATIVE_FLAG_AGENT = 0x01;
    /** native 检测位：调试器附加 */
    public static final int NATIVE_FLAG_DEBUGGER = 0x02;
    /** native 检测位：二进制篡改 */
    public static final int NATIVE_FLAG_TAMPER = 0x04;

    private static final Logger LOGGER = Logger.getLogger("AXS-Protection");
    private static final int JAVA_HARD_MASK = JvmAntiDebug.THREAT_JDWP
            | JvmAntiDebug.THREAT_FRIDA
            | JvmAntiDebug.THREAT_ATTACH
            | JvmAntiDebug.THREAT_SUSPICIOUS;
    private static final int NATIVE_HARD_MASK = NATIVE_FLAG_AGENT
            | NATIVE_FLAG_DEBUGGER
            | NATIVE_FLAG_TAMPER;

    private static volatile boolean compromised = false;

    private ProtectionEnvironment() {}

    public static void reset() {
        compromised = false;
    }

    public static void resetForTests() {
        reset();
    }

    public static boolean isCompromised() {
        return compromised;
    }

    public static boolean ensureCleanEnvironment() {
        if (compromised) {
            return false;
        }
        if (!NativeBridge.isAvailable()) {
            return true;
        }
        try {
            int javaThreats = JvmAntiDebug.checkAll();
            int nativeThreats = NativeBridge.n8();
            observeThreats(javaThreats, nativeThreats, true);
            return !compromised;
        } catch (Throwable ignored) {
            return true;
        }
    }

    public static void observeThreats(int javaThreats, int nativeThreats, boolean nativeAvailable) {
        if (!nativeAvailable) {
            return;
        }
        if (!isHardThreatSignal(javaThreats, nativeThreats)) {
            return;
        }
        if (!compromised) {
            LOGGER.warning("[Protection] Hard threat observed: java=0x"
                    + Integer.toHexString(javaThreats)
                    + " native=0x" + Integer.toHexString(nativeThreats));
        }
        compromised = true;
    }

    public static boolean isHardThreatSignal(int javaThreats, int nativeThreats) {
        return (javaThreats & JAVA_HARD_MASK) != 0
                || (nativeThreats & NATIVE_HARD_MASK) != 0;
    }
}

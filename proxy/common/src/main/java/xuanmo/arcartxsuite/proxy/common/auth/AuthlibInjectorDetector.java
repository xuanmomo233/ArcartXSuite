package xuanmo.arcartxsuite.proxy.common.auth;

import java.lang.management.ManagementFactory;

/**
 * authlib-injector 加载状态检测工具，供 BungeeCord / Velocity 代理端共用。
 * <p>
 * 检测顺序：
 * <ol>
 *   <li>JVM 启动参数中包含 {@code -javaagent:...authlib-injector...}（最可靠）</li>
 *   <li>类路径中存在 {@code moe.yushi.authlibinjector.AuthlibInjector}（fallback）</li>
 * </ol>
 */
public final class AuthlibInjectorDetector {

    private static final String AUTHLIB_AGENT_CLASS = "moe.yushi.authlibinjector.AuthlibInjector";

    private AuthlibInjectorDetector() {
    }

    /**
     * 检测当前 JVM 是否以 authlib-injector 启动。
     *
     * @return {@code true} 表示已加载 authlib-injector
     */
    public static boolean detect() {
        try {
            Class.forName(AUTHLIB_AGENT_CLASS);
            return true;
        } catch (ClassNotFoundException ignored) {
        }
        try {
            for (String arg : ManagementFactory.getRuntimeMXBean().getInputArguments()) {
                if (arg.contains("-javaagent") && arg.toLowerCase().contains("authlib-injector")) {
                    return true;
                }
            }
        } catch (Exception ignored) {
        }
        return false;
    }
}

package xuanmo.arcartxsuite.afkreward.service;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;

/**
 * 服务器 TPS（每秒刻数）读取工具。
 * <p>
 * 关联模块：afkreward。用于在低 TPS 时暂停挂机结算，避免在服务器卡顿时继续发放奖励。
 * 通过反射调用 {@code Bukkit.getServer().getTPS()}（Paper/Spigot 提供），调用失败时按满 TPS（20.0）返回。
 */
public final class TpsProvider {
    private static volatile Method getTpsMethod;
    private static volatile boolean resolved;

    private TpsProvider() {}

    /**
     * 获取当前服务器 TPS。
     *
     * @return 1 分钟平均 TPS；当前服务端不支持 {@code getTPS()} 时返回 {@code 20.0}
     */
    public static double currentTps() {
        try {
            if (!resolved) {
                synchronized (TpsProvider.class) {
                    if (!resolved) {
                        try {
                            getTpsMethod = Bukkit.getServer().getClass().getMethod("getTPS");
                        } catch (ReflectiveOperationException ignored) {
                            getTpsMethod = null;
                        }
                        resolved = true;
                    }
                }
            }
            if (getTpsMethod == null) return 20.0;
            Object value = getTpsMethod.invoke(Bukkit.getServer());
            if (value instanceof double[] tps && tps.length > 0
                && Double.isFinite(tps[0])) {
                return tps[0];
            }
        } catch (ReflectiveOperationException | RuntimeException ignored) {
            // Spigot 或兼容实现没有 getTPS 时按满 TPS 处理。
        }
        return 20.0;
    }
}

package xuanmo.arcartxsuite.afkreward.service;

import java.lang.reflect.Method;
import org.bukkit.Bukkit;

public final class TpsProvider {
    private static volatile Method getTpsMethod;
    private static volatile boolean resolved;

    private TpsProvider() {}

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

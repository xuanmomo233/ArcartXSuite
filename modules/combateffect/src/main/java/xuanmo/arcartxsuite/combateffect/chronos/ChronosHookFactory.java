package xuanmo.arcartxsuite.combateffect.chronos;

import java.util.logging.Logger;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * ChronosHook 的延迟加载工厂。
 * <p>
 * 调用方通过本工厂获取 ChronosHook 实例，本工厂用 {@code Class.forName} 延迟加载
 * {@link ChronosHook}，确保只在 Chronos 可用时才触发 ChronosHook 的类加载
 * （ChronosHook 直接 import 了 ChronosAPI，若 Chronos 未装则其类加载会失败）。
 * <p>
 * 本类自身不引用任何 Chronos 类，可安全地在任何上下文中加载。
 */
public final class ChronosHookFactory {

    private ChronosHookFactory() {}

    /**
     * 尝试创建 ChronosHook。Chronos 未安装时返回 null（不触发 ChronosHook 类加载）。
     *
     * @return ChronosHook 实例或 null
     */
    public static ChronosHook tryCreate(JavaPlugin plugin, Logger logger, boolean debug) {
        // 先检查插件是否存在，避免不必要的类加载
        if (org.bukkit.Bukkit.getPluginManager().getPlugin("ArcartX_Chronos_Plugin") == null
            && org.bukkit.Bukkit.getPluginManager().getPlugin("Chronos") == null) {
            return null;
        }
        try {
            // 延迟加载 ChronosHook——只有此时才触发对 ChronosAPI 的引用
            Class<?> hookClass = Class.forName(
                "xuanmo.arcartxsuite.combateffect.chronos.ChronosHook",
                true,
                ChronosHookFactory.class.getClassLoader()
            );
            java.lang.reflect.Method tryCreate = hookClass.getMethod("tryCreate",
                JavaPlugin.class, Logger.class, boolean.class);
            return (ChronosHook) tryCreate.invoke(null, plugin, logger, debug);
        } catch (Exception | LinkageError e) {
            if (debug) {
                logger.warning("ChronosHook 延迟加载失败（Chronos 未安装?）: " + e.getMessage());
            }
            return null;
        }
    }
}

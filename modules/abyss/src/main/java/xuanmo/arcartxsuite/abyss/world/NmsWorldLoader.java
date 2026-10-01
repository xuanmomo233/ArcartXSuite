package xuanmo.arcartxsuite.abyss.world;

import java.lang.reflect.Method;
import java.util.logging.Logger;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.jetbrains.annotations.Nullable;

/**
 * NMS 快速世界加载（可选快速通道）。
 * <p>
 * Bukkit {@code WorldCreator.createWorld()} 会做出生点搜索/出生区块预热，
 * 对"用完即焚"的副本世界浪费可观时间，可用 NMS 直建 {@code ServerLevel}
 * 跳过这段。本类按 axs-api {@code RawNbtAccess} 的模式做版本自适应反射：
 * 探测当前服务端 NMS/CraftBukkit 包结构，能快则快、探测失败一律回退 API。
 *
 * <h3>探测策略</h3>
 * <ol>
 *   <li>CraftServer#createWorld(WorldCreator) 是 Paper/Spigot/Mohist 都有的内部钩子——
 *       直接调它等价于 Bukkit API 路径但跳过部分校验，优先尝试。</li>
 *   <li>找不到内部入口就回退 {@code WorldCreator.createWorld()}（API 兜底，永不为 null）。</li>
 * </ol>
 *
 * 结论：本类实际提供的是"预解析 + 单反射调用 + API 兜底"的薄封装，
 * 不做深度 NMS 世界构造（那需要逐版本适配 20+ 个签名，收益/维护比差）。
 */
public final class NmsWorldLoader {

    private static volatile NmsWorldLoader instance;

    private final Method craftCreateWorld;   // CraftServer#createWorld(WorldCreator)

    private NmsWorldLoader(Method craftCreateWorld) {
        this.craftCreateWorld = craftCreateWorld;
    }

    /**
     * 探测当前服务端是否支持快速通道。
     *
     * @return 支持的适配器；不支持返回 null（调用方走纯 API）
     */
    public static @Nullable NmsWorldLoader resolve(Logger logger) {
        NmsWorldLoader cached = instance;
        if (cached != null) return cached;
        synchronized (NmsWorldLoader.class) {
            cached = instance;
            if (cached != null) return cached;
            try {
                Class<?> craftServer = Bukkit.getServer().getClass();
                Method m = findCreateWorld(craftServer);
                if (m == null) return null;
                m.setAccessible(true);
                cached = new NmsWorldLoader(m);
                instance = cached;
                logger.fine("[abyss] NMS 快速世界加载通道就绪: " + m.getName());
                return cached;
            } catch (Throwable t) {
                logger.fine("[abyss] NMS 快速通道不可用，回退 Bukkit API: " + t.getMessage());
                return null;
            }
        }
    }

    private static Method findCreateWorld(Class<?> craftServer) {
        for (Class<?> c = craftServer; c != null && c != Object.class; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (!m.getName().equals("createWorld")) continue;
                if (m.getParameterCount() == 1
                    && m.getParameterTypes()[0] == WorldCreator.class
                    && World.class.isAssignableFrom(m.getReturnType())) {
                    return m;
                }
            }
        }
        return null;
    }

    /**
     * 加载世界：优先内部 createWorld 钩子，异常时回退 API。
     */
    public World loadWorld(WorldCreator creator) {
        try {
            Object w = craftCreateWorld.invoke(Bukkit.getServer(), creator);
            if (w instanceof World world) return world;
        } catch (Throwable ignored) {
            // 回退
        }
        return creator.createWorld();
    }
}

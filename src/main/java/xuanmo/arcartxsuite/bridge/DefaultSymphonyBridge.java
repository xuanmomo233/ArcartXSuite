package xuanmo.arcartxsuite.bridge;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.symphony.api.SymphonyAPI;
import priv.seventeen.artist.symphony.api.attribute.Operation;
import xuanmo.arcartxsuite.api.attribute.SymphonyBridge;

/**
 * {@link SymphonyBridge} 的默认实现，桥接 Symphony 属性插件。
 * <p>
 * Symphony 为可选依赖，运行时通过插件管理器检测可用性。
 */
public final class DefaultSymphonyBridge implements SymphonyBridge {

    private final JavaPlugin plugin;

    private boolean available;
    private SymphonyAPI symphonyApi;

    public DefaultSymphonyBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void initialize() {
        reset();

        Plugin symphony = Bukkit.getPluginManager().getPlugin("Symphony");
        if (symphony == null || !symphony.isEnabled()) {
            return;
        }

        try {
            symphonyApi = SymphonyAPI.Companion.getInstance();
            if (symphonyApi == null) {
                return;
            }
            available = true;
            xuanmo.arcartxsuite.module.AxsLog.logger().fine("[Symphony] 桥接初始化成功");
        } catch (Exception | LinkageError exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[Symphony] 桥接初始化失败: " + exception.getMessage());
            reset();
        }
    }

    @Override public boolean available() { return available; }

    @Override
    public void setAttribute(Player player, String attributeId, boolean percent, double value, String sourceKey) {
        if (!available || player == null || attributeId == null || sourceKey == null) return;
        try {
            Operation operation = percent ? Operation.PERCENT : Operation.FLAT;
            symphonyApi.setAttribute(player, attributeId, operation, value, sourceKey);
        } catch (Exception | LinkageError e) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[Symphony] setAttribute 失败: " + e.getMessage());
        }
    }

    @Override
    public void removeAttribute(Player player, String sourceKey) {
        if (!available || player == null || sourceKey == null) return;
        try {
            symphonyApi.removeAttribute(player, sourceKey);
        } catch (Exception | LinkageError ignored) {
        }
    }

    @Override
    public void recalculate(Player player) {
        if (!available || player == null) return;
        try {
            symphonyApi.recalculate(player);
        } catch (Exception | LinkageError ignored) {
        }
    }

    private void reset() {
        available = false;
        symphonyApi = null;
    }
}

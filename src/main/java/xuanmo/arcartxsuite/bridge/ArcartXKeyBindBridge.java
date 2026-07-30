package xuanmo.arcartxsuite.bridge;

import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.arcartx.api.ArcartXAPI;
import priv.seventeen.artist.arcartx.core.keybind.ArcartXKeyBindRegistry;

public final class ArcartXKeyBindBridge {

    private final JavaPlugin plugin;
    private boolean available;
    private ArcartXKeyBindRegistry keyBindRegistry;

    public ArcartXKeyBindBridge(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean initialize() {
        available = false;
        keyBindRegistry = null;

        if (plugin.getServer().getPluginManager().getPlugin("ArcartX") == null) {
            return false;
        }
        try {
            keyBindRegistry = ArcartXAPI.getKeyBindRegistry();
            if (keyBindRegistry == null) {
                throw new IllegalStateException("ArcartX KeyBindRegistry 不可用。");
            }
            available = true;
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("初始化 ArcartX KeyBind 桥接失败: " + exception.getMessage());
            return false;
        }
    }

    public void shutdown() {
        available = false;
        keyBindRegistry = null;
    }

    public boolean isAvailable() {
        return available;
    }

    public boolean registerClientKeyBind(String keyName, String category, String defaultKey) {
        if (!available || keyBindRegistry == null) return false;
        try {
            keyBindRegistry.registerClientKeyBind(keyName, category, defaultKey);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX KeyBind 注册失败: " + exception.getMessage());
            return false;
        }
    }

    public boolean unregisterClientKeyBind(String keyName) {
        if (!available || keyBindRegistry == null) return false;
        try {
            keyBindRegistry.unregisterClientKeyBind(keyName);
            return true;
        } catch (RuntimeException exception) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("ArcartX KeyBind 注销失败: " + exception.getMessage());
            return false;
        }
    }
}

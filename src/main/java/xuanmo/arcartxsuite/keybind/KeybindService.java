package xuanmo.arcartxsuite.keybind;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.api.util.AxsScheduler;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.arcartx.event.client.ClientKeyPressEvent;
import xuanmo.arcartxsuite.api.KeybindHandler;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI;
import xuanmo.arcartxsuite.bridge.ArcartXKeyBindBridge;

public final class KeybindService implements Listener {

    private final JavaPlugin plugin;
    private final Map<String, CopyOnWriteArrayList<PrioritizedHandler>> handlers = new ConcurrentHashMap<>();
    private final List<String> registeredKeys = new ArrayList<>();
    private final ArcartXKeyBindBridge keyBindBridge;
    private boolean listenerRegistered;

    public KeybindService(JavaPlugin plugin, PropBridgeAPI propBridge) {
        this.plugin = plugin;
        this.keyBindBridge = new ArcartXKeyBindBridge(plugin);
    }

    public void initialize(FileConfiguration config) {
        ConfigurationSection keybindsSection = config.getConfigurationSection("keybinds");
        if (keybindsSection == null) {
            xuanmo.arcartxsuite.module.AxsLog.logger().info("[Keybind] config.yml 未配置 keybinds 节，跳过按键注册。");
            return;
        }
        if (!keyBindBridge.initialize()) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[Keybind] ArcartX KeyBind 桥接不可用，按键注册已跳过。");
            return;
        }

        for (String key : keybindsSection.getKeys(false)) {
            ConfigurationSection entry = keybindsSection.getConfigurationSection(key);
            if (entry == null) {
                continue;
            }
            String name = entry.getString("name", "");
            String defaultKey = entry.getString("default-key", "");
            String category = entry.getString("category", "ArcartXSuite");
            if (name.isBlank() || defaultKey.isBlank()) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("[Keybind] 跳过无效按键配置: keybinds." + key);
                continue;
            }
            boolean success = keyBindBridge.registerClientKeyBind(name, category, defaultKey);
            if (success) {
                registeredKeys.add(name);
                xuanmo.arcartxsuite.module.AxsLog.logger().info("[Keybind] 已注册客户端按键: " + name + " (默认 " + defaultKey + ")");
            } else {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("[Keybind] 注册客户端按键失败: " + name);
            }
        }

        registerClientKeyPressListener();
    }

    public void shutdown() {
        unregisterClientKeyPressListener();
        for (String keyName : registeredKeys) {
            keyBindBridge.unregisterClientKeyBind(keyName);
        }
        registeredKeys.clear();
        handlers.clear();
        keyBindBridge.shutdown();
    }

    private void registerClientKeyPressListener() {
        unregisterClientKeyPressListener();
        if (Bukkit.getPluginManager().getPlugin("ArcartX") == null) {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[Keybind] ArcartX 未加载，ClientKeyPressEvent 监听未注册。");
            return;
        }
        Bukkit.getPluginManager().registerEvents(this, plugin);
        listenerRegistered = true;
        xuanmo.arcartxsuite.module.AxsLog.logger().info("[Keybind] ClientKeyPressEvent 监听注册成功。");
    }

    private void unregisterClientKeyPressListener() {
        if (listenerRegistered) {
            HandlerList.unregisterAll(this);
            listenerRegistered = false;
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClientKeyPress(ClientKeyPressEvent event) {
        Player player = event.getPlayer();
        String keyName = event.getKeyName();
        if (player != null && keyName != null) {
            dispatch(player, keyName);
        }
    }

    public void registerHandler(String keyName, int priority, KeybindHandler handler) {
        if (keyName == null || keyName.isBlank() || handler == null) {
            return;
        }
        CopyOnWriteArrayList<PrioritizedHandler> list = handlers.computeIfAbsent(
            keyName.toUpperCase(), ignored -> new CopyOnWriteArrayList<>()
        );
        list.add(new PrioritizedHandler(priority, handler));
        list.sort(null);
    }

    public void unregisterHandler(KeybindHandler handler) {
        if (handler == null) {
            return;
        }
        for (CopyOnWriteArrayList<PrioritizedHandler> list : handlers.values()) {
            list.removeIf(entry -> entry.handler == handler);
        }
    }

    private void dispatch(Player player, String keyName) {
        if (Bukkit.isPrimaryThread()) {
            doDispatch(player, keyName);
        } else {
            AxsScheduler.runTask(plugin, () -> doDispatch(player, keyName));
        }
    }

    private void doDispatch(Player player, String keyName) {
        CopyOnWriteArrayList<PrioritizedHandler> list = handlers.get(keyName.toUpperCase());
        if (list == null || list.isEmpty()) {
            return;
        }
        for (PrioritizedHandler entry : list) {
            try {
                if (entry.handler.handleKeyPress(player, keyName)) {
                    return;
                }
            } catch (Exception exception) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning("[Keybind] 处理器异常 (" + keyName + "): " + exception.getMessage());
            }
        }
    }

    public List<String> registeredKeyNames() {
        return List.copyOf(registeredKeys);
    }

    private record PrioritizedHandler(int priority, KeybindHandler handler) implements Comparable<PrioritizedHandler> {
        @Override
        public int compareTo(PrioritizedHandler other) {
            return Integer.compare(this.priority, other.priority);
        }
    }
}

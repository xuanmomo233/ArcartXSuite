package xuanmo.arcartxsuite.keybind;

import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import xuanmo.arcartxsuite.api.util.AxsScheduler;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import priv.seventeen.artist.arcartx.event.client.ClientKeyPressEvent;
import xuanmo.arcartxsuite.api.KeybindHandler;
import xuanmo.arcartxsuite.api.bridge.PropBridgeAPI;
import xuanmo.arcartxsuite.bridge.ArcartXKeyBindBridge;

public final class KeybindService implements Listener {

    private final JavaPlugin plugin;
    private final Map<String, CopyOnWriteArrayList<PrioritizedHandler>> handlers = new ConcurrentHashMap<>();
    private final List<String> registeredKeys = new CopyOnWriteArrayList<>();
    private final ArcartXKeyBindBridge keyBindBridge;
    private boolean listenerRegistered;
    /** 被合并的按键 name → 实际注册到客户端的 name（键位重复时后注册的并入先注册的）。 */
    private final Map<String, String> aliasByKeyName = new ConcurrentHashMap<>();
    /** 已实际注册到客户端的按键 name（大写）。 */
    private final Set<String> registeredNames = ConcurrentHashMap.newKeySet();
    /** 已注册的默认键位（大写）→ 注册名，用于同键位合并判定。 */
    private final Map<String, String> registeredNameByKey = new ConcurrentHashMap<>();
    /** 声明了归属模块（module 字段）、延迟到模块真实启用时才注册的按键，key = name 大写。仅在 initialize 写入。 */
    private final Map<String, DeferredKeybind> deferredByName = new LinkedHashMap<>();
    /** config.yml keybinds 节声明过的全部按键 name（大写）。仅在 initialize 写入。 */
    private final Set<String> declaredNames = new HashSet<>();

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

        // 按 config.yml 声明顺序处理；若某条目的 default-key 与已注册条目相同，
        // 则跳过本次注册（客户端只注册一个按键），并建立 alias：后注册的 name → 先注册的 name。
        // 模块用自己配置里的 name 订阅，KeybindService 自动路由到实际注册的 name。
        // 声明了 module 归属的条目不立即注册：延迟到归属模块真实启用（onEnable 成功）
        // 时由 onModuleEnabled 注册；归属模块禁用/加载失败则该键不会下发到客户端。
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
            declaredNames.add(name.toUpperCase(Locale.ROOT));
            Set<String> owners = parseModuleOwners(entry);
            if (!owners.isEmpty()) {
                deferredByName.put(name.toUpperCase(Locale.ROOT), new DeferredKeybind(name, category, defaultKey, owners));
                continue;
            }
            registerDeclaredKey(name, category, defaultKey);
        }

        registerClientKeyPressListener();
    }

    /** 解析条目的 module 归属字段（单值或列表），返回小写模块 id 集合。 */
    private static Set<String> parseModuleOwners(ConfigurationSection entry) {
        List<String> raw = entry.isList("module")
            ? entry.getStringList("module")
            : List.of(entry.getString("module", ""));
        Set<String> owners = new LinkedHashSet<>();
        for (String value : raw) {
            if (value != null && !value.isBlank()) {
                owners.add(value.trim().toLowerCase(Locale.ROOT));
            }
        }
        return owners;
    }

    /** 实际注册一个按键到客户端（含同默认键位合并 alias 判定），供启动注册与延迟注册共用。 */
    private void registerDeclaredKey(String name, String category, String defaultKey) {
        String upper = name.toUpperCase(Locale.ROOT);
        if (registeredNames.contains(upper) || aliasByKeyName.containsKey(upper)) {
            return;
        }
        String normalizedKey = defaultKey.toUpperCase(Locale.ROOT);
        String existingName = registeredNameByKey.get(normalizedKey);
        if (existingName != null) {
            aliasByKeyName.put(upper, existingName.toUpperCase(Locale.ROOT));
            xuanmo.arcartxsuite.module.AxsLog.logger().info(
                "[Keybind] 按键 " + name + " 默认键位 " + defaultKey
                    + " 与 " + existingName + " 相同，合并注册为 " + existingName
            );
            return;
        }
        boolean success = keyBindBridge.registerClientKeyBind(name, category, defaultKey);
        if (success) {
            registeredKeys.add(name);
            registeredNames.add(upper);
            registeredNameByKey.put(normalizedKey, name);
            xuanmo.arcartxsuite.module.AxsLog.logger().info("[Keybind] 已注册客户端按键: " + name + " (默认 " + defaultKey + ")");
        } else {
            xuanmo.arcartxsuite.module.AxsLog.logger().warning("[Keybind] 注册客户端按键失败: " + name);
        }
    }

    /**
     * 模块真实启用（onEnable 成功）后由 ModuleRegistry 调用，
     * 注册所有声明归属该模块的按键（keybinds.&lt;entry&gt;.module 包含此 id）。
     * 本地加载与云端下发都汇聚到这一入口。
     */
    public void onModuleEnabled(String moduleId) {
        if (moduleId == null || deferredByName.isEmpty()) {
            return;
        }
        String normalizedId = moduleId.toLowerCase(Locale.ROOT);
        for (DeferredKeybind bind : deferredByName.values()) {
            if (bind.modules().contains(normalizedId)) {
                registerDeclaredKey(bind.name(), bind.category(), bind.defaultKey());
            }
        }
    }

    /**
     * 模块加载完成后调用，汇总仍未注册的归属按键（归属模块未启用或加载失败）。
     * 云端模块稍后下发启用时仍可触发注册，此处仅作诊断提示。
     */
    public void reportDeferred() {
        for (DeferredKeybind bind : deferredByName.values()) {
            String upper = bind.name().toUpperCase(Locale.ROOT);
            if (registeredNames.contains(upper) || aliasByKeyName.containsKey(upper)) {
                continue;
            }
            xuanmo.arcartxsuite.module.AxsLog.logger().info(
                "[Keybind] 按键 " + bind.name() + " 未注册：归属模块 " + bind.modules() + " 未启用。"
            );
        }
    }

    public void shutdown() {
        unregisterClientKeyPressListener();
        for (String keyName : registeredKeys) {
            keyBindBridge.unregisterClientKeyBind(keyName);
        }
        registeredKeys.clear();
        registeredNames.clear();
        registeredNameByKey.clear();
        handlers.clear();
        aliasByKeyName.clear();
        deferredByName.clear();
        declaredNames.clear();
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
            xuanmo.arcartxsuite.util.ListenerUnregister.safeUnregisterAll(this, "KeybindService");
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
        registerHandler(null, keyName, priority, handler);
    }

    public void registerHandler(String moduleId, String keyName, int priority, KeybindHandler handler) {
        if (keyName == null || keyName.isBlank() || handler == null) {
            return;
        }
        String upper = keyName.toUpperCase(Locale.ROOT);
        if (!registeredNames.contains(upper) && !aliasByKeyName.containsKey(upper)) {
            DeferredKeybind bind = deferredByName.get(upper);
            if (bind != null) {
                // 归属模块自己在 onEnable 中订阅时按键尚未注册（onModuleEnabled 在 onEnable 成功后触发），
                // 属正常时序，不告警；非归属模块订阅未注册的归属键才提示。
                String subscriber = moduleId == null ? "" : moduleId.toLowerCase(Locale.ROOT);
                if (!bind.modules().contains(subscriber)) {
                    xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                        "[Keybind] 模块 " + (moduleId == null ? "<unknown>" : moduleId)
                            + " 订阅了按键 " + keyName + "，其归属模块 " + bind.modules() + " 尚未启用，按键不会生效。"
                    );
                }
            } else if (!declaredNames.contains(upper)) {
                xuanmo.arcartxsuite.module.AxsLog.logger().warning(
                    "[Keybind] 按键 " + keyName + " 未在 config.yml keybinds 节声明，客户端不会收到该键。"
                );
            }
        }
        // 若该 name 因键位重复被合并到其他 name，则把 handler 挂到实际注册的 name 上。
        String resolved = aliasByKeyName.getOrDefault(upper, upper);
        CopyOnWriteArrayList<PrioritizedHandler> list = handlers.computeIfAbsent(
            resolved, ignored -> new CopyOnWriteArrayList<>()
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

    /** 声明了归属模块、延迟到模块真实启用时才注册的按键定义（modules 为小写模块 id）。 */
    private record DeferredKeybind(String name, String category, String defaultKey, Set<String> modules) {
    }
}

package xuanmo.arcartxsuite.tab;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import org.bukkit.command.TabExecutor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.tab.command.TabPlayerCommand;
import xuanmo.arcartxsuite.tab.config.TabDefinition;
import xuanmo.arcartxsuite.tab.config.TabModuleConfiguration;
import xuanmo.arcartxsuite.tab.debug.TabSnapshotStore;
import xuanmo.arcartxsuite.tab.listener.TabPvpListener;
import xuanmo.arcartxsuite.tab.placeholder.TabPlaceholderExpansion;
import xuanmo.arcartxsuite.tab.placeholder.TabPlayerFallbackExpansion;
import xuanmo.arcartxsuite.tab.placeholder.TabServerFallbackExpansion;
import xuanmo.arcartxsuite.tab.sync.TabSyncService;

/**
 * Tab 列表同步独立模块。
 */
public final class TabModule extends AbstractAXSModule {

    private static final String TAB_UI_RESOURCE_PATH = "arcartx/ui/tab.yml";
    private static final String TAB_UI_FILE_PATH = "ui/tab.yml";
    private static final String TAB_RICH_UI_RESOURCE_PATH = "arcartx/ui/tab-rich.yml";
    private static final String TAB_RICH_UI_FILE_PATH = "ui/tab-rich.yml";
    private static final String TAB_ARENA_UI_RESOURCE_PATH = "arcartx/ui/tab-arena.yml";
    private static final String TAB_ARENA_UI_FILE_PATH = "ui/tab-arena.yml";

    private TabModuleConfiguration configuration;
    private TabSyncService service;
    private TabSnapshotStore snapshotStore;

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("tab")
            .name("Tab")
            .version("1.0.2-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXTab.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        // tabs 定义已外部化到 tabs-directory 目录文件；
        // 若用户旧配置仍有 tabs: 段，保留为 dynamicSection 避免被引擎清除
        return SyncPolicy.builder()
            .dynamicSection("tabs")
            .build();
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            // 服务端ID不能为空
            ValidationRule.required("settings.server-id", ValueType.STRING),
            ValidationRule.of("settings.stale-snapshot-ms", ValueType.LONG)
                .withRange(1000, null),
            ValidationRule.of("cross-server.enabled", ValueType.BOOLEAN)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        return Map.of(
            TAB_UI_RESOURCE_PATH, TAB_UI_FILE_PATH,
            TAB_RICH_UI_RESOURCE_PATH, TAB_RICH_UI_FILE_PATH,
            TAB_ARENA_UI_RESOURCE_PATH, TAB_ARENA_UI_FILE_PATH
        );
    }

    @Override
    protected boolean overwriteUiFiles() {
        return configuration != null && configuration.overwriteUiFile();
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXTab.yml 配置文件缺失");
        }
        var yaml = YamlConfiguration.loadConfiguration(configFile);
        String tabsDirRelative = yaml.getString("tabs-directory", "tabs");
        File tabsDirectory = new File(context.dataFolder(), tabsDirRelative);
        ensureTabDefaults(tabsDirRelative);
        configuration = TabModuleConfiguration.load(yaml, tabsDirectory, context.logger());
    }

    private void ensureTabDefaults(String tabsRelative) {
        File tabsDir = new File(context.dataFolder(), tabsRelative);
        if (!tabsDir.exists()) {
            tabsDir.mkdirs();
        }
        File[] existing = tabsDir.listFiles((dir, name) -> name.endsWith(".yml") || name.endsWith(".yaml"));
        if (existing != null && existing.length > 0) {
            return;
        }
        for (String tab : new String[]{"online-tab.yml", "demo.yml", "arena.yml"}) {
            File target = new File(tabsDir, tab);
            if (!target.exists()) {
                context.exportResource("tabs/" + tab, target, false);
            }
        }
    }

    @Override
    protected void startService() throws Exception {
        Map<String, UiBinding> tabUiBindings = registerTabUis();
        PacketBridgeAPI packetBridge = context.packetBridge();
        PacketGuardAPI packetGuard = context.packetGuard();
        service = new TabSyncService(context.plugin(), configuration, packetBridge, packetGuard, context.crossServer(), context.placeholderResolver());
        service.start();

        // Snapshot 调试存储：plugins/ArcartXSuite/data/tab/snapshots/
        snapshotStore = new TabSnapshotStore(
            new File(context.pluginDataFolder(), "data/tab/snapshots").toPath()
        );

        // 注册 PVP 监听器（始终注册，是否生效由 settings.style.pvp-highlight.enabled 控制）
        context.plugin().getServer().getPluginManager()
            .registerEvents(new TabPvpListener(service), context.plugin());

        // 注册 TabRefreshable capability，供其他模块刷新 Tab
        context.registerCapability(xuanmo.arcartxsuite.api.capability.TabRefreshable.class, service);

        // 检测并注册 fallback PAPI 扩展（若原生 player/server 扩展缺失）
        registerFallbackPapiExpansionsIfNeeded();

        context.logger().fine(
            "Tab 模块已载入，定义数量: " + configuration.definitions().size()
                + " | UI: " + tabUiBindings.keySet()
                + " | 轮询间隔: " + configuration.refreshIntervalTicks() + " ticks"
        );
    }

    @Override
    protected void stopService() {
        if (service != null) {
            service.shutdown();
            service = null;
        }
        configuration = null;
    }

    @Override
    protected @NotNull Map<String, TabExecutor> commandBindings() {
        TabPlayerCommand cmd = new TabPlayerCommand(() -> service, () -> snapshotStore, messages());
        return Map.of("axstab", (TabExecutor) cmd);
    }

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) ->
            service != null && service.handleClientRefreshPacket(player, packetId, data);
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        if (service == null || configuration == null) {
            return null;
        }
        return new TabPlaceholderExpansion(
            context.plugin(),
            service,
            configuration.style(),
            configuration.privacy()
        );
    }

    public TabSyncService getService() {
        return service;
    }

    private final AtomicBoolean fallbackChecked = new AtomicBoolean(false);

    private void registerFallbackPapiExpansionsIfNeeded() {
        if (!context.hasPlugin("PlaceholderAPI")) {
            return;
        }
        // 先立即检测一次（PAPI 内部扩展可能已经注册）
        if (checkAndRegisterFallbacks()) {
            return;
        }
        // 方案 1：反射注册 PAPI 官方的 ExpansionsLoadedEvent（最精确，所有外部扩展注册完成后触发）
        if (registerExpansionLoadedListener()) {
            return;
        }
        // 方案 2：回退到 Paper 的 ServerLoadEvent（PAPI 外部扩展加载时机）
        if (registerServerLoadListener()) {
            return;
        }
        // 方案 3：最终回退到固定延迟
        context.plugin().getServer().getScheduler().runTaskLater(context.plugin(), this::checkAndRegisterFallbacks, 40L);
        context.logger().warning("Tab 无法注册 PAPI ExpansionsLoadedEvent 或 ServerLoadEvent，将使用延迟检测 PAPI 扩展。");
    }

    /**
     * 尝试反射注册 PAPI 的 ExpansionsLoadedEvent 监听器。
     * @return true 如果注册成功
     */
    private boolean registerExpansionLoadedListener() {
        try {
            Class<?> eventClass = Class.forName("me.clip.placeholderapi.events.ExpansionsLoadedEvent");
            context.plugin().getServer().getPluginManager().registerEvent(
                (Class<? extends org.bukkit.event.Event>) eventClass,
                new org.bukkit.event.Listener() {},
                org.bukkit.event.EventPriority.HIGHEST,
                (listener, event) -> {
                    context.logger().fine("收到 PAPI ExpansionsLoadedEvent，开始检测 player/server 扩展。");
                    checkAndRegisterFallbacks();
                },
                context.plugin()
            );
            context.logger().fine("Tab 已注册 PAPI ExpansionsLoadedEvent 监听器。");
            return true;
        } catch (ClassNotFoundException e) {
            context.logger().fine("当前 PAPI 版本不支持 ExpansionsLoadedEvent，将尝试其他方案。");
            return false;
        }
    }

    /**
     * 尝试反射注册 Paper 的 ServerLoadEvent 监听器。
     * @return true 如果注册成功
     */
    private boolean registerServerLoadListener() {
        try {
            Class<?> eventClass = Class.forName("com.destroystokyo.paper.event.server.ServerLoadEvent");
            context.plugin().getServer().getPluginManager().registerEvent(
                (Class<? extends org.bukkit.event.Event>) eventClass,
                new org.bukkit.event.Listener() {},
                org.bukkit.event.EventPriority.HIGHEST,
                (listener, event) -> {
                    context.logger().fine("收到 Paper ServerLoadEvent，开始检测 player/server 扩展。");
                    checkAndRegisterFallbacks();
                },
                context.plugin()
            );
            context.logger().fine("Tab 已注册 ServerLoadEvent 监听器。");
            return true;
        } catch (ClassNotFoundException e) {
            return false;
        }
    }

    private boolean checkAndRegisterFallbacks() {
        if (!fallbackChecked.compareAndSet(false, true)) {
            return true; // 已经执行过
        }
        if (!isPapiExpansionRegistered("player")) {
            context.logger().info("PlaceholderAPI 未检测到 player 扩展，Tab 模块将注入内置 player 占位符。");
            context.expansionRegistry().register(new TabPlayerFallbackExpansion(context.plugin()));
        }
        if (!isPapiExpansionRegistered("server")) {
            context.logger().info("PlaceholderAPI 未检测到 server 扩展，Tab 模块将注入内置 server 占位符。");
            context.expansionRegistry().register(new TabServerFallbackExpansion(context.plugin()));
        }
        return true;
    }

    private boolean isPapiExpansionRegistered(String identifier) {
        org.bukkit.plugin.Plugin papi = org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
        if (papi == null) {
            return false;
        }

        // 方案 A：直接反射调用 PlaceholderAPI 静态方法 getRegisteredPlaceholderIdentifiers()
        try {
            Class<?> papiClass = Class.forName("me.clip.placeholderapi.PlaceholderAPI");
            java.lang.reflect.Method getIdentifiers = papiClass.getMethod("getRegisteredPlaceholderIdentifiers");
            java.util.Set<String> identifiers = (java.util.Set<String>) getIdentifiers.invoke(null);
            boolean found = identifiers != null && identifiers.contains(identifier.toLowerCase(java.util.Locale.ROOT));
            context.logger().fine("PAPI 检测 [" + identifier + "] via identifiers: " + found);
            return found;
        } catch (ReflectiveOperationException | LinkageError e) {
            context.logger().fine("PAPI identifiers 检测 [" + identifier + "] 失败: " + e.getClass().getSimpleName());
        }

        // 方案 B：通过 PlaceholderAPIPlugin 的 LocalExpansionManager 检测
        try {
            Object manager;
            try {
                java.lang.reflect.Method getManager = papi.getClass().getMethod("getLocalExpansionManager");
                manager = getManager.invoke(papi);
            } catch (NoSuchMethodException e) {
                java.lang.reflect.Method getManager = papi.getClass().getMethod("getExpansionManager");
                manager = getManager.invoke(papi);
            }

            if (manager != null) {
                java.lang.reflect.Method find = manager.getClass().getMethod("findExpansionByIdentifier", String.class);
                Object result = find.invoke(manager, identifier);

                if (result instanceof java.util.Optional<?> optional) {
                    boolean present = optional.isPresent();
                    context.logger().fine("PAPI 检测 [" + identifier + "] via Optional: " + present);
                    return present;
                }
                boolean found = result != null;
                context.logger().fine("PAPI 检测 [" + identifier + "] via direct: " + found);
                return found;
            }
        } catch (ReflectiveOperationException | LinkageError e) {
            context.logger().fine("PAPI API 检测 [" + identifier + "] 失败: " + e.getClass().getSimpleName() + " - " + e.getMessage());
        }

        // 方案 C（兜底）：检查 expansions/ 目录下是否存在对应 jar
        try {
            java.io.File expansionsDir = new java.io.File(papi.getDataFolder(), "expansions");
            if (expansionsDir.exists() && expansionsDir.isDirectory()) {
                java.io.File[] jars = expansionsDir.listFiles((dir, name) ->
                    name.toLowerCase().startsWith("expansion-" + identifier.toLowerCase()) && name.endsWith(".jar")
                );
                boolean exists = jars != null && jars.length > 0;
                context.logger().fine("PAPI 检测 [" + identifier + "] via jar: " + exists);
                return exists;
            }
        } catch (Exception e) {
            context.logger().fine("PAPI jar 检测 [" + identifier + "] 失败: " + e.getMessage());
        }

        return false;
    }

    private Map<String, UiBinding> registerTabUis() {
        Map<String, UiBinding> bindings = new LinkedHashMap<>();
        for (TabDefinition definition : configuration.definitions()) {
            if (!definition.enabled()) {
                continue;
            }
            // 按每个 target 的 uiId 注册（同 uiId 只注册一次）。
            // 内置资源 tab.yml / tab-rich.yml / tab-arena.yml 在 startService 前已被 uiResourceMappings 复制到 plugins 目录。
            for (String uiId : definition.distinctUiIds()) {
                if (uiId.isBlank() || bindings.containsKey(uiId)) {
                    continue;
                }
                String fileRelative = "ui/" + uiId + ".yml";
                File uiFile = new File(context.pluginDataFolder(), fileRelative);
                UiBinding binding = context.prepareUiBinding(
                    "Tab", uiId, configuration.registerUiOnEnable(), uiFile
                );
                if (binding == null) {
                    throw new IllegalStateException("Tab UI 注册失败: " + uiId + " (file=" + fileRelative + ")");
                }
                bindings.put(uiId, binding);
                recordUiBinding(fileRelative + "#" + uiId, binding);
            }
        }
        return bindings;
    }
}


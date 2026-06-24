package xuanmo.arcartxsuite.tab;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

        // 注册 PAPI 扩展加载事件监听器
        if (context.hasPlugin("PlaceholderAPI")) {
            if (xuanmo.arcartxsuite.ArcartXSuitePlugin.isInitialLoadComplete()) {
                // 核心首次加载已完成（服务器运行中重载/后加载 tab），PAPI 早已加载完，直接检测
                org.bukkit.Bukkit.getScheduler().runTaskLater(context.plugin(),
                    () -> detectPapiExpansions(0, true), 1L);
            } else {
                // 服务器启动中，注册事件监听器等待 PAPI 全部加载完成
                org.bukkit.Bukkit.getPluginManager().registerEvents(
                    new PapiExpansionListener(), context.plugin());
            }
        }

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

    private final java.util.concurrent.atomic.AtomicBoolean papiDetected = new java.util.concurrent.atomic.AtomicBoolean(false);

    private class PapiExpansionListener implements org.bukkit.event.Listener {
        @org.bukkit.event.EventHandler
        public void onExpansionsLoaded(me.clip.placeholderapi.events.ExpansionsLoadedEvent event) {
            if (papiDetected.get()) return;
            context.logger().info("[tab] 监听到 PAPI 全部扩展加载完成，开始检测");
            detectPapiExpansions(0, true);
        }
    }

    private void detectPapiExpansions(int attempt, boolean finalCheck) {
        if (!context.hasPlugin("PlaceholderAPI")) {
            return;
        }
        if (!papiDetected.compareAndSet(false, true)) {
            return; // 已检测过
        }
        try {
            me.clip.placeholderapi.PlaceholderAPIPlugin papi =
                (me.clip.placeholderapi.PlaceholderAPIPlugin) org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            if (papi == null) {
                return;
            }
            java.util.Collection<me.clip.placeholderapi.expansion.PlaceholderExpansion> expansions =
                papi.getLocalExpansionManager().getExpansions();
            boolean hasPlayer = false;
            boolean hasServer = false;
            for (me.clip.placeholderapi.expansion.PlaceholderExpansion expansion : expansions) {
                String id = expansion.getIdentifier().toLowerCase();
                if ("player".equals(id)) {
                    hasPlayer = true;
                }
                if ("server".equals(id)) {
                    hasServer = true;
                }
            }
            context.logger().info("[tab] PAPI 扩展状态 — player: " + (hasPlayer ? "已安装" : "未安装") + "，server: " + (hasServer ? "已安装" : "未安装"));

            if (!hasPlayer || !hasServer) {
                if (!finalCheck) {
                    // 预检发现缺失，可能是时机问题，重置标记等待 ExpansionsLoadedEvent
                    context.logger().info("[tab] 预检发现扩展缺失，等待 PAPI 全部加载完成后再检测");
                    papiDetected.set(false);
                    return;
                }
                if (attempt < 2) {
                    papiDetected.set(false); // 允许重试
                    org.bukkit.Bukkit.getScheduler().runTaskLater(context.plugin(),
                        () -> detectPapiExpansions(attempt + 1, true), 60L);
                    return;
                }
                // 终检且重试结束仍未找到，注册 fallback
                if (!hasPlayer) {
                    context.logger().info("[tab] PAPI player 扩展未安装，已注册内置 fallback");
                    context.expansionRegistry().register(new TabPlayerFallbackExpansion(context.plugin()));
                }
                if (!hasServer) {
                    context.logger().info("[tab] PAPI server 扩展未安装，已注册内置 fallback");
                    context.expansionRegistry().register(new TabServerFallbackExpansion(context.plugin()));
                }
            }
        } catch (Exception e) {
            context.logger().warning("[tab] PAPI 扩展检测失败: " + e.getMessage());
        }
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


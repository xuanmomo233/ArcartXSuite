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

    private final java.util.concurrent.atomic.AtomicBoolean fallbackRegistered = new java.util.concurrent.atomic.AtomicBoolean(false);

    private void registerFallbackPapiExpansionsIfNeeded() {
        if (!context.hasPlugin("PlaceholderAPI")) {
            return;
        }
        // 先立即检测一次
        if (checkAndRegisterFallbacks()) {
            return;
        }
        // 没查到则启动轮询：每秒检测一次，最多 60 秒
        // PAPI 的 ecloud 扩展可能在服务器启动后才异步加载完成
        startFallbackPolling(0);
    }

    private void startFallbackPolling(int attempt) {
        if (attempt >= 60) {
            context.logger().warning("[tab] PAPI 扩展检测已达最大重试次数(60秒)，按当前结果注册缺失的 fallback 扩展。");
            checkAndRegisterFallbacks();
            return;
        }
        context.plugin().getServer().getScheduler().runTaskLater(context.plugin(), () -> {
            if (fallbackRegistered.get()) {
                return; // 已处理
            }
            boolean found = checkAndRegisterFallbacks();
            if (!found && attempt < 59) {
                startFallbackPolling(attempt + 1);
            }
        }, attempt == 0 ? 1L : 20L);
    }

    private boolean checkAndRegisterFallbacks() {
        if (!fallbackRegistered.compareAndSet(false, true)) {
            return true;
        }
        try {
            me.clip.placeholderapi.PlaceholderAPIPlugin papi =
                (me.clip.placeholderapi.PlaceholderAPIPlugin) org.bukkit.Bukkit.getPluginManager().getPlugin("PlaceholderAPI");
            if (papi == null) {
                return false;
            }

            boolean hasPlayer = detectExpansion(papi, "player");
            boolean hasServer = detectExpansion(papi, "server");

            context.logger().info("[tab] PAPI 扩展检测: player=" + hasPlayer + ", server=" + hasServer);

            if (!hasPlayer) {
                context.logger().info("[tab] PAPI player 扩展未找到，注册内置 fallback。");
                context.expansionRegistry().register(new TabPlayerFallbackExpansion(context.plugin()));
            }
            if (!hasServer) {
                context.logger().info("[tab] PAPI server 扩展未找到，注册内置 fallback。");
                context.expansionRegistry().register(new TabServerFallbackExpansion(context.plugin()));
            }
            return true;
        } catch (Exception e) {
            context.logger().warning("[tab] PAPI 扩展检测失败: " + e.getMessage());
            return false;
        }
    }

    private boolean detectExpansion(me.clip.placeholderapi.PlaceholderAPIPlugin papi, String identifier) {
        // 方式1: 通过 LocalExpansionManager 检测（标准扩展、expansions 文件夹加载的扩展）
        java.util.Collection<me.clip.placeholderapi.expansion.PlaceholderExpansion> expansions =
            papi.getLocalExpansionManager().getExpansions();
        for (me.clip.placeholderapi.expansion.PlaceholderExpansion expansion : expansions) {
            if (identifier.equalsIgnoreCase(expansion.getIdentifier())) {
                return true;
            }
        }

        // 方式2: 占位符解析测试（PAPI 2.11+ ecloud 扩展可能不显示在注册表中，但仍能解析占位符）
        try {
            if ("player".equals(identifier)) {
                if (org.bukkit.Bukkit.getOnlinePlayers().isEmpty()) {
                    return false; // 无玩家时无法测试 player 占位符
                }
                org.bukkit.entity.Player testPlayer = org.bukkit.Bukkit.getOnlinePlayers().iterator().next();
                // %player_ping% 是 player 扩展特有的，PAPI 本身不提供
                String result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(testPlayer, "%player_ping%");
                if (!result.equals("%player_ping%") && !result.isEmpty()) {
                    return true;
                }
            } else if ("server".equals(identifier)) {
                // %server_tps_1% 是 server 扩展特有的
                String result = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(null, "%server_tps_1%");
                if (!result.equals("%server_tps_1%") && !result.isEmpty()) {
                    return true;
                }
            }
        } catch (Exception e) {
            context.logger().warning("[tab] 占位符解析检测 " + identifier + " 失败: " + e.getMessage());
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


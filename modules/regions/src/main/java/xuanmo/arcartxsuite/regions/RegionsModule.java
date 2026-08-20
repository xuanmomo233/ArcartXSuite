package xuanmo.arcartxsuite.regions;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.regions.command.RegionCommandHandler;
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
import xuanmo.arcartxsuite.regions.listener.RegionProtectionListener;
import xuanmo.arcartxsuite.regions.listener.SelectionListener;
import xuanmo.arcartxsuite.regions.packet.RegionsAdminPacketHandler;
import xuanmo.arcartxsuite.regions.packet.RegionsMenuPacketHandler;
import xuanmo.arcartxsuite.regions.service.RegionManager;
import xuanmo.arcartxsuite.regions.service.WorldRulesService;
import xuanmo.arcartxsuite.regions.storage.RegionsRepository;

/**
 * Regions 模块主类，负责区域保护、选区、命令、UI 与数据存储的整体生命周期管理。
 * <p>
 * 继承 {@link AbstractAXSModule}，实现 {@link ModuleCommandHandler}，
 * 在启动时初始化 {@link RegionsRepository}、{@link RegionManager}、保护/选区监听器、
 * 命令处理器、世界规则服务以及 UI Packet Handler，并注册数据库迁移与玩家数据清理能力。
 * 关联模块：{@code regions}。
 */
public final class RegionsModule extends AbstractAXSModule implements ModuleCommandHandler {

    private RegionsConfiguration configuration;
    private YamlConfiguration rawYaml;
    private RegionsRepository repository;
    private RegionManager regionManager;
    private RegionProtectionListener protectionListener;
    private SelectionListener selectionListener;
    private RegionCommandHandler commandHandler;
    private WorldRulesService worldRulesService;
    private RegionsMenuPacketHandler menuPacketHandler;
    private RegionsAdminPacketHandler adminPacketHandler;

    /**
     * 返回模块描述符，包含模块 ID、名称、版本与主类信息。
     *
     * @return 模块描述符
     */
    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("regions")
            .name("Regions")
            .version("1.0.0-beta")
            .mainClass(getClass().getName())
            .build();
    }

    /**
     * 返回主配置文件名。
     *
     * @return 主配置文件名
     */
    @Override
    protected String configFileName() {
        return "ArcartXRegions.yml";
    }

    /**
     * 返回消息配置文件名。
     *
     * @return 消息配置文件名
     */
    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    /**
     * 返回主配置的校验规则列表，覆盖存储方言与选区体积限制。
     *
     * @return 校验规则列表
     */
    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            ValidationRule.required("storage.shared", ValueType.BOOLEAN),
            ValidationRule.of("selection.max-volume", ValueType.INT).withRange(1, 100000000)
        );
    }

    /**
     * 返回默认配置同步策略，允许 {@code world-rules.disabled-interactions} 按世界名动态新增。
     *
     * @return 同步策略
     */
    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        // world-rules.disabled-interactions 允许用户按世界名新增交互限制
        return SyncPolicy.builder()
            .dynamicSection("world-rules.disabled-interactions")
            .build();
    }

    /**
     * 返回 UI 资源路径到目标文件路径的映射，用于菜单与管理面板 UI 的释放与加载。
     *
     * @return UI 资源映射
     */
    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(RegionsMenuPacketHandler.UI_RESOURCE_PATH, RegionsMenuPacketHandler.UI_FILE_PATH);
        mappings.put(RegionsAdminPacketHandler.UI_RESOURCE_PATH, RegionsAdminPacketHandler.UI_FILE_PATH);
        return mappings;
    }

    /**
     * 加载主配置与消息配置，解析为 {@link RegionsConfiguration}。
     *
     * @param configFile 主配置文件，为 {@code null} 时抛出异常
     * @throws IllegalStateException 配置文件缺失时抛出
     * @throws Exception             加载或解析失败时抛出
     */
    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXRegions.yml 配置文件缺失");
        }
        rawYaml = YamlConfiguration.loadConfiguration(configFile);
        configuration = RegionsConfiguration.load(
            rawYaml,
            MessageProvider.loadYamlWithBundledDefaults(
                new File(configFile.getParentFile(), messagesFileName()),
                messagesFileName(),
                moduleClassLoader(),
                logger,
                resourcePath -> openResource(resourcePath, moduleClassLoader())),
            logger);
    }

    /**
     * 启动核心服务：初始化存储、加载区域、注册监听器与命令、启动世界规则服务、
     * 绑定 UI Packet Handler，并注册数据库迁移与玩家数据清理能力。
     *
     * @throws Exception 启动过程中发生错误时抛出
     */
    @Override
    protected List<String> publishedTopics() {
        return List.of("axs.regions.region_change");
    }

    @Override
    protected void startService() throws Exception {
        // 共享模式：使用本体统一数据源，模块只需提供表前缀
        // 若模块配置了独立 storage 节（mode=mysql 等），则走自建模式（向后兼容）
        if (configuration.storage().hasOverride()) {
            // 旧路径：模块自建连接池
            repository = new RegionsRepository(dataFolder, configuration.storage(), logger);
        } else {
            // 新路径：共享本体数据源
            repository = new RegionsRepository(
                storageManager, "regions",
                configuration.storage().tablePrefix(),
                configuration.storage().sqliteFileName(), logger);
        }
        repository.initialize();
        regionManager = new RegionManager(repository, configuration, logger);
        regionManager.loadAll();

        protectionListener = new RegionProtectionListener(regionManager, configuration);
        protectionListener.setEventBusProvider(() -> getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        selectionListener = new SelectionListener(regionManager, configuration);
        Bukkit.getPluginManager().registerEvents(protectionListener, plugin);
        Bukkit.getPluginManager().registerEvents(selectionListener, plugin);

        commandHandler = new RegionCommandHandler(regionManager, configuration, messages());

        // 世界规则服务
        var worldRulesSection = rawYaml.getConfigurationSection("world-rules");
        if (worldRulesSection != null) {
            worldRulesService = new WorldRulesService(plugin, logger, worldRulesSection);
            worldRulesService.start();
        }

        // UI 绑定与 Packet Handler 初始化
        if (packetBridge != null && packetBridge.isAvailable()) {
            UiBinding menuBinding = registerModuleUi(
                RegionsMenuPacketHandler.UI_FILE_PATH, null, true
            );
            UiBinding adminBinding = registerModuleUi(
                RegionsAdminPacketHandler.UI_FILE_PATH, null, true
            );

            if (menuBinding.registeredUiId() != null) {
                menuPacketHandler = new RegionsMenuPacketHandler(
                    plugin, packetBridge, packetGuard,
                    regionManager, menuBinding.runtimeUiId());
            }
            if (adminBinding.registeredUiId() != null) {
                adminPacketHandler = new RegionsAdminPacketHandler(
                    plugin, packetBridge, packetGuard,
                    regionManager, adminBinding.runtimeUiId());
            }
        }

        // 注册 /rg 独立缩写命令，转发给 regions 子命令处理
        registerCommand("rg", new org.bukkit.command.TabExecutor() {
            @Override
            public boolean onCommand(@NotNull CommandSender sender,
                    @NotNull org.bukkit.command.Command command,
                    @NotNull String label, @NotNull String[] args) {
                String[] forwarded = new String[args.length + 1];
                forwarded[0] = "regions";
                System.arraycopy(args, 0, forwarded, 1, args.length);
                return RegionsModule.this.onCommand(sender, label, forwarded);
            }
            @Override
            public java.util.List<String> onTabComplete(@NotNull CommandSender sender,
                    @NotNull org.bukkit.command.Command command,
                    @NotNull String label, @NotNull String[] args) {
                String[] forwarded = new String[args.length + 1];
                forwarded[0] = "regions";
                System.arraycopy(args, 0, forwarded, 1, args.length);
                java.util.List<String> result = RegionsModule.this.onTabComplete(sender, forwarded);
                return result != null ? result : List.of();
            }
        });

        registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "regions"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    return repository.migrateData(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return repository.getDescriptor();
                }
            });

        registerCapability(PlayerDataPurgeable.class, new PlayerDataPurgeable() {
            @Override public @NotNull String moduleId() { return "regions"; }
            @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                try { return repository.deletePlayerData(playerUuid); }
                catch (Exception e) { logger.warning("Regions purge 失败: " + e.getMessage()); return -1; }
            }
            @Override public int purgeAllPlayerData() {
                try { return repository.deleteAllPlayerData(); }
                catch (Exception e) { logger.warning("Regions purgeAll 失败: " + e.getMessage()); return -1; }
            }
        });

        logger.info("Regions 模块已启动 (已加载 " + regionManager.getAllRegions().size() + " 个区域)。");
    }

    /**
     * 停止并释放所有服务资源，包括监听器注销、存储关闭与配置清理。
     */
    @Override
    protected void stopService() {
        menuPacketHandler = null;
        adminPacketHandler = null;
        if (worldRulesService != null) { worldRulesService.shutdown(); worldRulesService = null; }
        if (protectionListener != null) { HandlerList.unregisterAll(protectionListener); protectionListener = null; }
        if (selectionListener != null) { HandlerList.unregisterAll(selectionListener); selectionListener = null; }
        if (repository != null) { repository.close(); repository = null; }
        regionManager = null;
        commandHandler = null;
        configuration = null;
        rawYaml = null;
    }

    /**
     * 创建客户端 Packet 处理器，将数据包分发给菜单与管理面板 Packet Handler。
     *
     * @return Packet 处理器，无可用 UI 时仍返回一个空实现
     */
    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) -> {
            if (menuPacketHandler != null && menuPacketHandler.handleClientPacket(player, packetId, data)) return true;
            if (adminPacketHandler != null && adminPacketHandler.handleClientPacket(player, packetId, data)) return true;
            return false;
        };
    }

    /**
     * 返回模块命令 ID。
     *
     * @return 命令 ID {@code "regions"}
     */
    @Override
    public String commandId() { return "regions"; }

    /**
     * 返回命令别名列表。
     *
     * @return 别名列表
     */
    @Override
    public List<String> commandAliases() { return List.of("rg"); }

    /**
     * 返回可用的子命令动作列表。
     *
     * @return 动作列表
     */
    @Override
    public List<String> actions() {
        return commandHandler != null ? commandHandler.actions() : List.of("help", "status");
    }

    /**
     * 处理 {@code /regions} 命令，支持 {@code menu}、{@code admin} 等动作并转发其余动作给命令处理器。
     *
     * @param sender 命令发送者
     * @param label  命令标签
     * @param args   参数数组，{@code args[0]} 为命令 ID，{@code args[1]} 为动作
     * @return {@code true} 表示已处理
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (args.length >= 2) {
            String action = args[1].toLowerCase(java.util.Locale.ROOT);
            String prefix = messages() != null ? messages().get("prefix") : "";
            if ("menu".equals(action)) {
                if (sender instanceof Player p) {
                    if (menuPacketHandler != null) menuPacketHandler.openMenu(p);
                    else p.sendMessage(prefix + (messages() != null ? messages().get("player.ui.offline") : "UI 功能不可用（ArcartX 未加载）"));
                } else {
                    sender.sendMessage(prefix + (messages() != null ? messages().get("common.only-player") : "只有玩家可以执行此命令。"));
                }
                return true;
            }
            if ("admin".equals(action)) {
                if (sender instanceof Player p) {
                    if (!p.hasPermission("axs.regions.admin")) {
                        p.sendMessage(prefix + (messages() != null ? messages().get("common.no-permission") : "权限不足。"));
                        return true;
                    }
                    if (adminPacketHandler != null) adminPacketHandler.openMenu(p);
                    else p.sendMessage(prefix + (messages() != null ? messages().get("player.ui.offline") : "UI 功能不可用（ArcartX 未加载）"));
                } else {
                    sender.sendMessage(prefix + (messages() != null ? messages().get("common.only-player") : "只有玩家可以执行此命令。"));
                }
                return true;
            }
        }
        return commandHandler != null && commandHandler.onCommand(sender, label, args);
    }

    /**
     * 提供命令 Tab 补全建议。
     *
     * @param sender 命令发送者
     * @param args   参数数组
     * @return 补全建议列表，无可用建议时返回 {@code null}
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return commandHandler != null ? commandHandler.onTabComplete(sender, args) : null;
    }

    /**
     * 返回区域管理器实例，供外部模块查询区域数据。
     *
     * @return 区域管理器，模块未启动时为 {@code null}
     */
    public RegionManager getRegionManager() {
        return regionManager;
    }
}



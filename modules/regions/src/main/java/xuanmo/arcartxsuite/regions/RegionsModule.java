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
import java.util.Set;
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

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("regions")
            .name("Regions")
            .version("1.0.0-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected String configFileName() {
        return "ArcartXRegions.yml";
    }

    @Override
    protected String messagesFileName() {
        return "messages.yml";
    }

    @Override
    protected @NotNull List<ValidationRule> mainConfigValidations() {
        return List.of(
            ValidationRule.required("storage.dialect", ValueType.STRING)
                .withEnum(Set.of("sqlite", "mysql")),
            ValidationRule.of("selection.max-volume", ValueType.INT).withRange(1, 100000000)
        );
    }

    @Override
    protected @NotNull SyncPolicy defaultSyncPolicy() {
        // world-rules.disabled-interactions 允许用户按世界名新增交互限制
        return SyncPolicy.builder()
            .dynamicSection("world-rules.disabled-interactions")
            .build();
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(RegionsMenuPacketHandler.UI_RESOURCE_PATH, RegionsMenuPacketHandler.UI_FILE_PATH);
        mappings.put(RegionsAdminPacketHandler.UI_RESOURCE_PATH, RegionsAdminPacketHandler.UI_FILE_PATH);
        return mappings;
    }

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
                resourcePath -> openProtectedResource(resourcePath, moduleClassLoader())),
            logger);
    }

    @Override
    protected void startService() throws Exception {
        repository = new RegionsRepository(dataFolder, configuration.storage(), logger);
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

    @Override
    protected @Nullable ClientPacketHandler createPacketHandler() {
        return (player, packetId, data) -> {
            if (menuPacketHandler != null && menuPacketHandler.handleClientPacket(player, packetId, data)) return true;
            if (adminPacketHandler != null && adminPacketHandler.handleClientPacket(player, packetId, data)) return true;
            return false;
        };
    }

    @Override
    public String commandId() { return "regions"; }

    @Override
    public List<String> commandAliases() { return List.of("rg"); }

    @Override
    public List<String> actions() {
        return commandHandler != null ? commandHandler.actions() : List.of("help", "status", "reload");
    }

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

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        return commandHandler != null ? commandHandler.onTabComplete(sender, args) : null;
    }

    public RegionManager getRegionManager() {
        return regionManager;
    }
}



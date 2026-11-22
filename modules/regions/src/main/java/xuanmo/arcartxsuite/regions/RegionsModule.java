package xuanmo.arcartxsuite.regions;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ExecutorService;
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
import xuanmo.arcartxsuite.api.capability.RegionProtectionQueryable;
import xuanmo.arcartxsuite.api.config.SyncPolicy;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.message.MessageProvider;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.regions.command.RegionCommandHandler;
import xuanmo.arcartxsuite.regions.config.RegionsConfiguration;
import xuanmo.arcartxsuite.regions.config.RegionTemplates;
import xuanmo.arcartxsuite.regions.listener.RegionProtectionListener;
import xuanmo.arcartxsuite.regions.listener.RegionTransitions;
import xuanmo.arcartxsuite.regions.listener.RegionChatGuard;
import xuanmo.arcartxsuite.regions.listener.SelectionListener;
import xuanmo.arcartxsuite.regions.packet.RegionsAdminPacketHandler;
import xuanmo.arcartxsuite.regions.packet.RegionsMenuPacketHandler;
import xuanmo.arcartxsuite.regions.service.RegionManager;
import xuanmo.arcartxsuite.regions.service.RegionBackups;
import xuanmo.arcartxsuite.regions.service.SelectionParticleTask;
import xuanmo.arcartxsuite.regions.storage.RegionsRepository;

/**
 * Regions 模块主类，负责区域保护、选区、命令、UI 与数据存储的整体生命周期管理。
 * <p>
 * 继承 {@link AbstractAXSModule}，实现 {@link ModuleCommandHandler}，
 * 在启动时加载 YAML、注册保护监听器、选区、命令与管理界面。
 * 关联模块：{@code regions}。
 */
public final class RegionsModule extends AbstractAXSModule implements ModuleCommandHandler {

    private RegionsConfiguration configuration;
    private YamlConfiguration rawYaml;
    private ExecutorService writer;
    private RegionManager regionManager;
    private RegionProtectionListener protectionListener;
    private RegionTransitions transitions;
    private RegionChatGuard chatGuard;
    private org.bukkit.event.Listener packetCleanup;
    private SelectionListener selectionListener;
    private RegionCommandHandler commandHandler;
    private RegionsMenuPacketHandler menuPacketHandler;
    private RegionsAdminPacketHandler adminPacketHandler;
    private SelectionParticleTask particleTask;

    /**
     * 返回模块描述符，包含模块 ID、名称、版本与主类信息。
     *
     * @return 模块描述符
     */
    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("regions")
            .name("Regions")
            .version("3.0.0")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXRegions.yml")
            .messagesFileName("messages.yml")
            .syncPolicy(SyncPolicy.builder()
                    .obsoletePath("world-rules")
                    .obsoletePath("storage")
                    .opaqueSection("templates")
                .build())
            .currentVersion(4)
            .migrationFolder("")
            .validations(List.of(
                    ValidationRule.of("selection.max-volume", ValueType.INT).withRange(1, 100000000),
                    ValidationRule.of("defaults.global-region-id", ValueType.STRING),
                    ValidationRule.of("templates", ValueType.SECTION)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(RegionsMenuPacketHandler.UI_RESOURCE_PATH, RegionsMenuPacketHandler.UI_FILE_PATH);
        mappings.put(RegionsAdminPacketHandler.UI_RESOURCE_PATH, RegionsAdminPacketHandler.UI_FILE_PATH);
        return new ModuleUiSpec(mappings, false);
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of((player, packetId, data) -> {
            if (menuPacketHandler != null && menuPacketHandler.handleClientPacket(player, packetId, data)) return true;
            if (adminPacketHandler != null && adminPacketHandler.handleClientPacket(player, packetId, data)) return true;
            return false;
        }, 0, RegionsMenuPacketHandler.PACKET_ID, "regions", RegionsAdminPacketHandler.PACKET_ID);
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
        rawYaml = new YamlConfiguration();
        rawYaml.load(configFile);
        configuration = RegionsConfiguration.load(
            rawYaml,
            MessageProvider.loadYamlWithBundledDefaults(
                new File(configFile.getParentFile(), configSpec().messagesFileName()),
                configSpec().messagesFileName(),
                moduleClassLoader(),
                logger,
                resourcePath -> openResource(resourcePath, moduleClassLoader())),
            logger);
    }

    /**
     * 启动保护监听器、命令与界面，并注册玩家数据清理能力。
     *
     * @throws Exception 启动过程中发生错误时抛出
     */
    @Override
    protected List<String> publishedTopics() {
        return List.of(RegionProtectionQueryable.REGION_CHANGE_TOPIC);
    }

    @Override
    protected void startService() throws Exception {
        var templateSection = rawYaml.getConfigurationSection("templates");
        if (templateSection == null) {
            try (var resource = openResource("ArcartXRegions.yml", moduleClassLoader())) {
                if (resource != null) {
                    var reader = new java.io.InputStreamReader(resource, java.nio.charset.StandardCharsets.UTF_8);
                    templateSection = YamlConfiguration.loadConfiguration(reader).getConfigurationSection("templates");
                }
            }
        }
        var templates = new RegionTemplates(templateSection);
        var repository = new RegionsRepository(dataFolder.toPath().resolve("regions.yml"));
        writer = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "AXS-Regions-YAML");
            thread.setDaemon(true);
            return thread;
        });
        regionManager = new RegionManager(repository, configuration, logger,
            task -> Bukkit.getScheduler().runTask(plugin, task), writer);
        regionManager.loadAll();

        protectionListener = new RegionProtectionListener(regionManager, configuration);
        transitions = new RegionTransitions(plugin, regionManager, configuration,
            () -> getCapability(xuanmo.arcartxsuite.api.capability.EventBusCapability.class));
        chatGuard = new RegionChatGuard(plugin, regionManager, configuration);
        selectionListener = new SelectionListener(regionManager, configuration);
        Bukkit.getPluginManager().registerEvents(protectionListener, plugin);
        Bukkit.getPluginManager().registerEvents(transitions, plugin);
        regionManager.onPublication(worlds -> {
            for (String name : worlds) {
                org.bukkit.World world = Bukkit.getWorld(name);
                if (world != null) world.getPlayers().forEach(protectionListener::enforceFlight);
            }
        });
        Bukkit.getOnlinePlayers().forEach(protectionListener::enforceFlight);
        chatGuard.register();
        Bukkit.getPluginManager().registerEvents(selectionListener, plugin);
        // 玩家退出时清理 packet handler 的动作令牌与在途标记
        packetCleanup = new org.bukkit.event.Listener() {
            @org.bukkit.event.EventHandler
            public void onPlayerQuit(org.bukkit.event.player.PlayerQuitEvent event) {
                if (menuPacketHandler != null) {
                    menuPacketHandler.clearPlayer(event.getPlayer().getUniqueId());
                }
                if (adminPacketHandler != null) {
                    adminPacketHandler.clearPlayer(event.getPlayer().getUniqueId());
                }
            }
        };
        Bukkit.getPluginManager().registerEvents(packetCleanup, plugin);

        // 选区与区域边框粒子可视化
        particleTask = new SelectionParticleTask(plugin, regionManager, configuration);
        particleTask.start();

        commandHandler = new RegionCommandHandler(regionManager, configuration, messages(), particleTask, templates,
            new RegionBackups(dataFolder.toPath()), plugin);

        // UI 绑定与 Packet Handler 初始化
        if (packetBridge != null && packetBridge.isAvailable()) {
            UiBinding menuBinding = registerModuleUi(
                RegionsMenuPacketHandler.UI_FILE_PATH, "AXS:regions_menu", true
            );
            UiBinding adminBinding = registerModuleUi(
                RegionsAdminPacketHandler.UI_FILE_PATH, "AXS:regions_admin", true
            );

            if (menuBinding.registeredUiId() != null) {
                menuPacketHandler = new RegionsMenuPacketHandler(
                    plugin, packetBridge, regionManager, configuration, menuBinding.runtimeUiId());
            }
            if (adminBinding.registeredUiId() != null) {
                adminPacketHandler = new RegionsAdminPacketHandler(
                    plugin, packetBridge, regionManager, adminBinding.runtimeUiId());
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

        RegionManager manager = regionManager;
        registerCapability(PlayerDataPurgeable.class, new PlayerDataPurgeable() {
            @Override public @NotNull String moduleId() { return "regions"; }
            @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) { return purge(playerUuid); }
            @Override public int purgeAllPlayerData() { return purge(null); }

            private int purge(java.util.UUID playerUuid) {
                if (Bukkit.isPrimaryThread()) return -1;
                try {
                    return Bukkit.getScheduler().callSyncMethod(plugin, () -> manager.purgePlayers(playerUuid)).get().get();
                } catch (InterruptedException error) {
                    Thread.currentThread().interrupt();
                    return -1;
                } catch (Exception error) {
                    logger.log(java.util.logging.Level.WARNING, "区域玩家数据清理失败。", error);
                    return -1;
                }
            }
        });

        registerCapability(xuanmo.arcartxsuite.api.capability.RegionQueryable.class,
            new xuanmo.arcartxsuite.api.capability.RegionQueryable() {
                private RegionInfo toInfo(xuanmo.arcartxsuite.regions.model.Region region) {
                    return new RegionInfo(region.id(), region.world(),
                        region.minX(), region.minY(), region.minZ(),
                        region.maxX(), region.maxY(), region.maxZ(),
                        region.priority(), region.parentId(), region.owners(), region.members());
                }
                @Override public @NotNull List<RegionInfo> regionsAt(@NotNull String world,
                        int x, int y, int z) {
                    if (regionManager == null) return List.of();
                    return regionManager.getRegionsAt(world, x, y, z).stream()
                        .sorted(java.util.Comparator.comparingInt(xuanmo.arcartxsuite.regions.model.Region::priority).reversed())
                        .map(this::toInfo).toList();
                }
                @Override public @NotNull List<RegionInfo> regionsInWorld(@NotNull String world) {
                    if (regionManager == null) return List.of();
                    return regionManager.getRegionsInWorld(world).stream()
                        .filter(region -> region.kind() != xuanmo.arcartxsuite.regions.model.Region.Kind.GLOBAL)
                        .map(this::toInfo).toList();
                }
                @Override public @NotNull List<RegionInfo> allRegions() {
                    if (regionManager == null) return List.of();
                    return regionManager.getAllRegions().stream()
                        .filter(region -> region.kind() != xuanmo.arcartxsuite.regions.model.Region.Kind.GLOBAL)
                        .map(this::toInfo).toList();
                }
                @Override public int countRegionsByOwner(@NotNull java.util.UUID owner) {
                    return regionManager != null ? regionManager.countRegionsByOwner(owner) : 0;
                }
            });

        RegionManager rules = regionManager;
        registerCapability(xuanmo.arcartxsuite.api.capability.RegionChatAccess.class, chatGuard);
        registerCapability(RegionProtectionQueryable.class, new RegionProtectionQueryable() {
            @Override
            public @NotNull Decision decide(@NotNull String world, int x, int y, int z, @NotNull String flag,
                                             @Nullable Player player) {
                var key = xuanmo.arcartxsuite.regions.model.RegionFlag.fromKey(flag);
                if (key == null) throw new IllegalArgumentException("Unknown region flag " + flag);
                var result = rules.decide(world, x, y, z, key, player);
                return new Decision(State.valueOf(result.state().name()), result.reason(),
                    identity(result.matched()), identity(result.source()));
            }

            @Override
            public @NotNull List<Boundary> boundaries(@NotNull String world) {
                return rules.getRegionsInWorld(world).stream()
                    .filter(region -> region.kind() != xuanmo.arcartxsuite.regions.model.Region.Kind.GLOBAL)
                    .map(region -> new Boundary(identity(region), region.minX(), region.minY(), region.minZ(),
                        region.maxX(), region.maxY(), region.maxZ(), region.priority()))
                    .toList();
            }

            private Identity identity(xuanmo.arcartxsuite.regions.model.Region region) {
                return region == null ? null : new Identity(region.uid(), region.world(), region.id(),
                    Kind.valueOf(region.kind().name()));
            }
        });

        logger.info("Regions 模块已启动 (已加载 " + regionManager.getAllRegions().size() + " 个区域)。");
    }

    /**
     * 停止并释放所有服务资源，包括监听器注销、存储关闭与配置清理。
     */
    @Override
    protected void stopService() {
        if (adminPacketHandler != null) adminPacketHandler.close();
        if (menuPacketHandler != null) menuPacketHandler.close();
        menuPacketHandler = null;
        adminPacketHandler = null;
        if (regionManager != null) {
            regionManager.onPublication(worlds -> {});
            regionManager.close();
        }
        if (writer != null) { writer.shutdown(); writer = null; }
        if (particleTask != null) { particleTask.shutdown(); particleTask = null; }
        if (protectionListener != null) { HandlerList.unregisterAll(protectionListener); protectionListener = null; }
        if (transitions != null) {
            HandlerList.unregisterAll(transitions);
            transitions.stop();
            transitions = null;
        }
        if (chatGuard != null) { HandlerList.unregisterAll(chatGuard); chatGuard = null; }
        if (packetCleanup != null) { HandlerList.unregisterAll(packetCleanup); packetCleanup = null; }
        if (selectionListener != null) { HandlerList.unregisterAll(selectionListener); selectionListener = null; }
        regionManager = null;
        commandHandler = null;
        configuration = null;
        rawYaml = null;
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


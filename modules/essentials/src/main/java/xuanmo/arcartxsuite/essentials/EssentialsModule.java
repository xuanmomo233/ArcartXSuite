package xuanmo.arcartxsuite.essentials;

import java.io.File;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import xuanmo.arcartxsuite.api.AbstractAXSModule;
import xuanmo.arcartxsuite.api.ClientPacketHandler;
import xuanmo.arcartxsuite.api.config.ValidationRule;
import xuanmo.arcartxsuite.api.config.ValueType;
import xuanmo.arcartxsuite.api.ModuleCommandHandler;
import xuanmo.arcartxsuite.api.ModuleDescriptor;
import xuanmo.arcartxsuite.api.UiBinding;
import xuanmo.arcartxsuite.api.bridge.PacketBridgeAPI;
import xuanmo.arcartxsuite.api.capability.ChatMutable;
import xuanmo.arcartxsuite.api.capability.EssentialsQueryable;
import xuanmo.arcartxsuite.api.capability.TabRefreshable;
import xuanmo.arcartxsuite.api.security.PacketGuardAPI;
import xuanmo.arcartxsuite.api.config.ModuleConfig;
import xuanmo.arcartxsuite.api.ModuleUiSpec;
import xuanmo.arcartxsuite.api.PacketHandlerSpec;
import xuanmo.arcartxsuite.essentials.command.EssentialsCommandDelegate;
import xuanmo.arcartxsuite.essentials.config.EssentialsConfiguration;
import xuanmo.arcartxsuite.essentials.packet.EssentialsAdminPacketHandler;
import xuanmo.arcartxsuite.essentials.packet.EssentialsMenuPacketHandler;
import xuanmo.arcartxsuite.essentials.placeholder.EssentialsPlaceholderExpansion;
import xuanmo.arcartxsuite.essentials.service.InventoryActionsService;
import xuanmo.arcartxsuite.essentials.service.PlayerManagementService;
import xuanmo.arcartxsuite.essentials.service.TeleportService;
import xuanmo.arcartxsuite.essentials.service.TreeCapitatorService;
import xuanmo.arcartxsuite.essentials.storage.EssentialsRepository;

/**
 * Essentials 模块主类。
 * <p>
 * 继承 {@link AbstractAXSModule}，实现 {@link ModuleCommandHandler}，
 * 是 Essentials 模块的生命周期入口与命令注册中心。
 * 负责加载配置、初始化仓储与各服务层、注册 UI 绑定与 Packet Handler、
 * 注册跨模块能力（{@code EssentialsQueryable}、{@code PlayerDataPurgeable}、
 * {@code DatabaseMigratable}）以及注册 PAPI 占位符扩展。
 */
public final class EssentialsModule extends AbstractAXSModule implements ModuleCommandHandler {

    private static final List<String> ACTIONS = List.of(
        "help", "status", "reload",
        // UI
        "menu", "admin",
        // 玩家管理
        "fly", "god", "heal", "feed", "gamemode", "speed", "vanish", "afk", "back",
        "repair", "hat", "enderchest", "workbench", "anvil", "trash", "nick", "seen",
        // 传送
        "home", "sethome", "delhome", "warp", "setwarp", "delwarp",
        "spawn", "setspawn", "tpa", "tpahere", "tpaccept", "tpdeny", "tp", "top", "tppos",
        // 世界
        "time", "weather",
        // 安全管理
        "ban", "tempban", "unban", "mute", "tempmute", "unmute",
        "kick", "warn", "sudo", "inv",
        // 交互
        "sit", "lay",
        // 工具
        "sort", "replant", "autotool"
    );

    private EssentialsConfiguration configuration;
    private EssentialsRepository repository;
    private PlayerManagementService playerService;
    private TeleportService teleportService;
    private TreeCapitatorService treeCapitatorService;
    private InventoryActionsService inventoryActionsService;
    private YamlConfiguration rawYaml;
    private Supplier<ChatMutable> chatMutableSupplier;
    private EssentialsMenuPacketHandler menuPacketHandler;
    private EssentialsAdminPacketHandler adminPacketHandler;
    private EssentialsCommandDelegate commandDelegate;
    private final Map<UUID, BukkitTask> poseMonitorTasks = new ConcurrentHashMap<>();

    /**
     * 返回模块描述符（id=essentials，名称=Essentials，版本=1.0.0-beta）。
     *
     * @return 模块描述符
     */
    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("essentials")
            .name("Essentials")
            .version("1.4.4")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXEssentials.yml")
            .messagesFileName("messages.yml")
            .currentVersion(3)
            .validations(List.of(
                    ValidationRule.required("storage.shared", ValueType.BOOLEAN)
            ))
            .build();
    }

    @Override
    protected @NotNull ModuleUiSpec uiSpec() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(EssentialsMenuPacketHandler.UI_RESOURCE_PATH, EssentialsMenuPacketHandler.UI_FILE_PATH);
        mappings.put(EssentialsAdminPacketHandler.UI_RESOURCE_PATH, EssentialsAdminPacketHandler.UI_FILE_PATH);
        return new ModuleUiSpec(mappings, false);
    }

    @Override
    protected @NotNull PacketHandlerSpec createPacketHandlerSpec() {
        return PacketHandlerSpec.of((player, packetId, data) -> {
            if (menuPacketHandler != null && menuPacketHandler.handleClientPacket(player, packetId, data)) return true;
            if (adminPacketHandler != null && adminPacketHandler.handleClientPacket(player, packetId, data)) return true;
            return false;
        }, 0, null, null);
    }

    /**
     * 返回命令标识符。
     *
     * @return {@code "essentials"}
     */
    @Override
    public String commandId() {
        return "essentials";
    }

    /**
     * 返回命令别名列表。
     *
     * @return 包含 {@code "ess"} 的别名列表
     */
    @Override
    public List<String> commandAliases() {
        return List.of("ess");
    }

    /**
     * 返回模块支持的全部子动作列表，用于命令分发与 Tab 补全。
     *
     * @return 不可变动作列表
     */
    @Override
    public List<String> actions() {
        return ACTIONS;
    }

    /**
     * 加载并解析主配置文件为 {@link EssentialsConfiguration}。
     *
     * @param configFile 配置文件，为 null 时抛出异常
     * @throws IllegalStateException 配置文件缺失时抛出
     * @throws Exception             配置解析失败时抛出
     */
    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXEssentials.yml 配置文件缺失");
        }
        rawYaml = YamlConfiguration.loadConfiguration(configFile);
        configuration = EssentialsConfiguration.load(rawYaml, logger);
    }

    /**
     * 启动全部服务层：仓储、玩家管理、传送、砍树、背包操作，
     * 注册能力、UI 绑定、命令与 PAPI 扩展。
     *
     * @throws Exception 启动失败时抛出
     */
    @Override
    protected void startService() throws Exception {
        // 共享模式：使用本体统一数据源，模块只需提供表前缀
        // 若模块配置了独立 storage 节（mode=mysql 等），则走自建模式（向后兼容）
        xuanmo.arcartxsuite.api.storage.StorageDescriptor essDesc;
        javax.sql.DataSource essDs;
        if (configuration.storage().hasOverride()) {
            essDesc = configuration.storage().toDescriptor();
            essDs = storageManager.resolveModuleDataSource("essentials", null, dataFolder, essDesc);
        } else {
            essDesc = storageManager.getDescriptor()
                .withTablePrefix(configuration.storage().tablePrefix());
            essDs = storageManager.resolveModuleDataSource("essentials",
                configuration.storage().sqliteFileName(), dataFolder, null);
        }
        repository = new EssentialsRepository(essDs, essDesc, dataFolder, logger);
        repository.initialize();

        playerService = new PlayerManagementService(plugin, logger, configuration,
            () -> getCapability(TabRefreshable.class));
        playerService.start();

        teleportService = new TeleportService(
            plugin, configuration, repository, playerService, logger);
        teleportService.start();

        // 一键砍树服务
        var treeSection = rawYaml.getConfigurationSection("tree-capitator");
        if (treeSection != null) {
            treeCapitatorService = new TreeCapitatorService(plugin, logger, treeSection,
                configuration.messages().treeFelled());
            treeCapitatorService.start();
        }

        // 背包操作服务
        var invSection = rawYaml.getConfigurationSection("inv-actions");
        if (invSection != null) {
            inventoryActionsService = new InventoryActionsService(plugin, logger, invSection);
            inventoryActionsService.start();
        }

        // 延迟获取 ChatMutable capability（Chat 模块可能后加载）
        chatMutableSupplier = () -> getCapability(ChatMutable.class);

        // 注册 EssentialsQueryable capability 供 Tab/Chat 等模块查询
        registerCapability(EssentialsQueryable.class, new EssentialsQueryableImpl());

        // UI 绑定与 Packet Handler 初始化
        if (packetBridge != null && packetBridge.isAvailable()) {
            UiBinding menuBinding = registerModuleUi(
                EssentialsMenuPacketHandler.UI_FILE_PATH, "AXS:essentials_menu", true
            );
            UiBinding adminBinding = registerModuleUi(
                EssentialsAdminPacketHandler.UI_FILE_PATH, "AXS:essentials_admin", true
            );

            if (menuBinding.registeredUiId() != null) {
                menuPacketHandler = new EssentialsMenuPacketHandler(
                    plugin, packetBridge, packetGuard,
                    playerService, teleportService, repository,
                    inventoryActionsService, menuBinding.runtimeUiId());
            }
            if (adminBinding.registeredUiId() != null) {
                adminPacketHandler = new EssentialsAdminPacketHandler(
                    plugin, packetBridge, packetGuard,
                    playerService, teleportService, repository,
                    chatMutableSupplier, adminBinding.runtimeUiId());
            }
        }

        // 注册 /ess 独立缩写命令，转发给 essentials 子命令处理
        registerCommand("ess", new org.bukkit.command.TabExecutor() {
            @Override
            public boolean onCommand(@NotNull org.bukkit.command.CommandSender sender,
                    @NotNull org.bukkit.command.Command command,
                    @NotNull String label, @NotNull String[] args) {
                String[] forwarded = new String[args.length + 1];
                forwarded[0] = "essentials";
                System.arraycopy(args, 0, forwarded, 1, args.length);
                return EssentialsModule.this.onCommand(sender, label, forwarded);
            }
            @Override
            public java.util.List<String> onTabComplete(@NotNull org.bukkit.command.CommandSender sender,
                    @NotNull org.bukkit.command.Command command,
                    @NotNull String label, @NotNull String[] args) {
                String[] forwarded = new String[args.length + 1];
                forwarded[0] = "essentials";
                System.arraycopy(args, 0, forwarded, 1, args.length);
                java.util.List<String> result = EssentialsModule.this.onTabComplete(sender, forwarded);
                return result != null ? result : List.of();
            }
        });

        // 注册配置中定义的命令简写别名（如 /fly /god /gm 等），转发到 /essentials <action>
        if (configuration != null && configuration.commandAliases() != null) {
            for (var entry : configuration.commandAliases().entrySet()) {
                String alias = entry.getKey();
                String action = entry.getValue();
                registerCommand(alias, new org.bukkit.command.TabExecutor() {
                    @Override
                    public boolean onCommand(@NotNull org.bukkit.command.CommandSender sender,
                            @NotNull org.bukkit.command.Command command,
                            @NotNull String label, @NotNull String[] args) {
                        // 转发为 /essentials <action> [args...]
                        String[] forwarded = new String[args.length + 2];
                        forwarded[0] = "essentials";
                        forwarded[1] = action;
                        System.arraycopy(args, 0, forwarded, 2, args.length);
                        return EssentialsModule.this.onCommand(sender, label, forwarded);
                    }
                    @Override
                    public java.util.List<String> onTabComplete(@NotNull org.bukkit.command.CommandSender sender,
                            @NotNull org.bukkit.command.Command command,
                            @NotNull String label, @NotNull String[] args) {
                        // 转发 tab 补全，但补全位置要减去 action 参数
                        String[] forwarded = new String[args.length + 2];
                        forwarded[0] = "essentials";
                        forwarded[1] = action;
                        System.arraycopy(args, 0, forwarded, 2, args.length);
                        java.util.List<String> result = EssentialsModule.this.onTabComplete(sender, forwarded);
                        return result != null ? result : List.of();
                    }
                });
            }
            logger.info("已注册 " + configuration.commandAliases().size() + " 个命令简写别名");
        }

        EssentialsRepository essRepo = repository;
        registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "essentials"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    try { return essRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { logger.warning("Essentials purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    try { return essRepo.deleteAllPlayerData(); }
                    catch (Exception e) { logger.warning("Essentials purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "essentials"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    return essRepo.migrateData(target, overwrite);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return essRepo.getDescriptor();
                }
            });

        commandDelegate = new EssentialsCommandDelegate(
            configuration, playerService, teleportService, repository,
            inventoryActionsService, chatMutableSupplier,
            menuPacketHandler, adminPacketHandler,
            plugin, this::messages, poseMonitorTasks
        );

        logger.info("Essentials 模块已启动。");
    }

    /**
     * 停止并释放全部服务层资源，按反向顺序关闭。
     */
    @Override
    protected void stopService() {
        commandDelegate = null;
        poseMonitorTasks.values().forEach(BukkitTask::cancel);
        poseMonitorTasks.clear();
        menuPacketHandler = null;
        adminPacketHandler = null;
        if (inventoryActionsService != null) { inventoryActionsService.shutdown(); inventoryActionsService = null; }
        if (treeCapitatorService != null) { treeCapitatorService.shutdown(); treeCapitatorService = null; }
        if (teleportService != null) { teleportService.shutdown(); teleportService = null; }
        if (playerService != null) { playerService.shutdown(); playerService = null; }
        if (repository != null) { repository.close(); repository = null; }
        storageManager.closeModuleDataSource("essentials");
        configuration = null;
        rawYaml = null;
    }

    /**
     * 处理 {@code /essentials} 命令，委派给 {@link EssentialsCommandDelegate}。
     *
     * @param sender 命令发送者
     * @param label  命令标签
     * @param args   命令参数
     * @return {@code true} 表示命令已处理
     */
    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (commandDelegate == null) {
            sender.sendMessage(messages() != null ? messages().get("common.command-handler-down") : "§cEssentials 命令处理器尚未初始化。");
            return true;
        }
        return commandDelegate.onCommand(sender, label, args);
    }

    /**
     * 提供 {@code /essentials} 命令的 Tab 补全，委派给 {@link EssentialsCommandDelegate}。
     *
     * @param sender 命令发送者
     * @param args   当前参数数组
     * @return 补全候选列表，无补全时返回空列表
     */
    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (commandDelegate == null) return List.of();
        return commandDelegate.onTabComplete(sender, args);
    }

    // ─── Sit / Lay ───

    /**
     * 创建模块事件监听器列表，当前包含玩家退出时取消姿势监控任务的监听器。
     *
     * @return 监听器列表
     */
    @Override
    protected List<Listener> createListeners() {
        return List.of(new Listener() {
            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent event) {
                cancelPoseTask(event.getPlayer().getUniqueId());
            }
        });
    }

    private void cancelPoseTask(UUID playerId) {
        BukkitTask task = poseMonitorTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
    }

    // ─── Utilities ───

    /**
     * 返回模块数据仓储实例。
     *
     * @return 仓储实例，模块未启动时可能为 null
     */
    public EssentialsRepository getRepository() {
        return repository;
    }

    /**
     * 返回玩家管理服务实例。
     *
     * @return 玩家管理服务，模块未启动时可能为 null
     */
    public PlayerManagementService getPlayerService() {
        return playerService;
    }

    /**
     * 创建 PlaceholderAPI 占位符扩展实例。
     *
     * @return {@link EssentialsPlaceholderExpansion} 实例
     */
    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new EssentialsPlaceholderExpansion(plugin, () -> playerService);
    }

    // ─── EssentialsQueryable 实现 ───

    /**
     * {@link EssentialsQueryable} 能力的内部实现，
     * 供 Tab、Chat 等外部模块查询玩家的 AFK、隐身、禁言、昵称、飞行与无敌状态。
     */
    private final class EssentialsQueryableImpl implements EssentialsQueryable {

        @Override
        public boolean isAfk(@NotNull UUID playerUuid) {
            return playerService != null && playerService.isAfk(playerUuid);
        }

        @Override
        public boolean isVanished(@NotNull UUID playerUuid) {
            return playerService != null && playerService.isVanished(playerUuid);
        }

        @Override
        public boolean isMuted(@NotNull UUID playerUuid) {
            ChatMutable chatMutable = chatMutableSupplier != null ? chatMutableSupplier.get() : null;
            if (chatMutable != null) {
                return chatMutable.isMuted(playerUuid);
            }
            return false;
        }

        @Override
        public @Nullable String getNickname(@NotNull UUID playerUuid) {
            if (repository == null) return null;
            try {
                return repository.getNickname(playerUuid);
            } catch (SQLException e) {
                return null;
            }
        }

        @Override
        public boolean isFlying(@NotNull UUID playerUuid) {
            Player p = Bukkit.getPlayer(playerUuid);
            return p != null && p.isFlying();
        }

        @Override
        public boolean isGodMode(@NotNull UUID playerUuid) {
            Player p = Bukkit.getPlayer(playerUuid);
            return p != null && p.isInvulnerable();
        }
    }
}


package xuanmo.arcartxsuite.essentials;

import java.io.File;
import java.nio.file.Path;
import java.sql.SQLException;
import org.bukkit.Location;
import org.bukkit.World;
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
import xuanmo.arcartxsuite.essentials.service.InventoryInspection;
import xuanmo.arcartxsuite.essentials.service.ModerationService;
import xuanmo.arcartxsuite.essentials.service.WarpDirectory;
import xuanmo.arcartxsuite.essentials.service.EssentialsWork;
import xuanmo.arcartxsuite.api.storage.ModuleStorage;

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


    private EssentialsConfiguration configuration;
    private EssentialsRepository repository;
    private ModuleStorage storageScope;
    private EssentialsWork work;
    private ModerationService moderation;
    private InventoryInspection inventoryInspection;
    private PlayerManagementService playerService;
    private TeleportService teleportService;
    private TreeCapitatorService treeCapitatorService;
    private InventoryActionsService inventoryActionsService;
    private YamlConfiguration rawYaml;
    private Path configPath;
    private Supplier<ChatMutable> chatMutableSupplier;
    private EssentialsMenuPacketHandler menuPacketHandler;
    private EssentialsAdminPacketHandler adminPacketHandler;
    private EssentialsCommandDelegate commandDelegate;
    private BukkitTask commandRegistrationTask;
    private final Map<UUID, BukkitTask> poseMonitorTasks = new ConcurrentHashMap<>();
    /** 坐下姿势的坐垫实体映射（玩家 UUID → ArmorStand），与命令委派器共享以便统一清理 */
    private final Map<UUID, org.bukkit.entity.Entity> seatEntities = new ConcurrentHashMap<>();

    /**
     * 返回模块描述符（id=essentials，名称=Essentials，版本=1.0.0-beta）。
     *
     * @return 模块描述符
     */
    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("essentials")
            .name("Essentials")
            .version("3.3.7")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    protected @NotNull ModuleConfig configSpec() {
        return ModuleConfig.builder()
            .configFileName("ArcartXEssentials.yml")
            .messagesFileName("messages.yml")
            .currentVersion(3)
            .syncPolicy(xuanmo.arcartxsuite.api.config.SyncPolicy.builder()
                .dynamicSection("warps").opaqueSection("command-aliases").build())
            .validations(List.of(
                    ValidationRule.required("storage.shared", ValueType.BOOLEAN),
                    ValidationRule.of("command-conflict-protection", ValueType.BOOLEAN),
                    ValidationRule.of("disabled-actions", ValueType.STRING_LIST),
                    ValidationRule.of("warps", ValueType.SECTION),
                    ValidationRule.of("tpa.enabled", ValueType.BOOLEAN),
                    ValidationRule.of("tpa.cooldown-seconds", ValueType.INT).withRange(0, 3600),
                    ValidationRule.of("tpa.max-incoming", ValueType.INT).withRange(1, 50),
                    ValidationRule.of("tpa.max-outgoing", ValueType.INT).withRange(1, 50),
                    ValidationRule.of("unstuck.enabled", ValueType.BOOLEAN),
                    ValidationRule.of("unstuck.cooldown-seconds", ValueType.INT).withRange(0, 86400),
                    ValidationRule.of("unstuck.warmup-seconds", ValueType.INT).withRange(0, 60),
                    ValidationRule.of("unstuck.combat-seconds", ValueType.INT).withRange(0, 600),
                    ValidationRule.of("unstuck.search-radius", ValueType.INT).withRange(1, 8),
                    ValidationRule.of("unstuck.vertical-range", ValueType.INT).withRange(0, 8),
                    ValidationRule.of("unstuck.only-when-stuck", ValueType.BOOLEAN),
                    ValidationRule.of("unstuck.allowed-worlds", ValueType.STRING_LIST),
                    ValidationRule.of("unstuck.blocked-worlds", ValueType.STRING_LIST),
                    ValidationRule.of("moderation.allow-offline-bans", ValueType.BOOLEAN),
                    ValidationRule.of("moderation.history-page-size", ValueType.INT).withRange(1, 25)
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
        var runtime=work;
        var menu=menuPacketHandler;
        var admin=adminPacketHandler;
        return PacketHandlerSpec.of((player, packetId, data) -> {
            return runtime.call(()->{
                if (menu != null && menu.handleClientPacket(player, packetId, data)) return true;
                if (admin != null && admin.handleClientPacket(player, packetId, data)) return true;
                return false;
            },false);
        }, 0, EssentialsMenuPacketHandler.PACKET_ID, "essentials", EssentialsAdminPacketHandler.PACKET_ID);
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
        return EssentialsCommandDelegate.actions();
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
        rawYaml = new YamlConfiguration();
        rawYaml.options().parseComments(true);
        rawYaml.load(configFile);
        configPath = configFile.toPath();
        configuration = EssentialsConfiguration.load(rawYaml, logger);
    }

    /**
     * 启动全部服务层：仓储、玩家管理、传送、砍树、背包操作，
     * 注册能力、UI 绑定、命令与 PAPI 扩展。
     *
     * @throws Exception 启动失败时抛出
     */
    @Override
    protected java.util.concurrent.CompletionStage<Void> prepareService() throws Exception {
        work=new EssentialsWork(plugin);
        // 共享模式：使用本体统一数据源，模块只需提供表前缀
        // 若模块配置了独立 storage 节（mode=mysql 等），则走自建模式（向后兼容）
        xuanmo.arcartxsuite.api.storage.StorageDescriptor essDesc;
        javax.sql.DataSource essDs;
        if (configuration.storage().hasOverride()) {
            essDesc = configuration.storage().toDescriptor();
            storageScope = storageManager.openModuleStorage("essentials", null, dataFolder, essDesc);
            essDs = storageScope.dataSource();
        } else {
            essDesc = storageManager.getDescriptor()
                .withTablePrefix(configuration.storage().tablePrefix());
            storageScope = storageManager.openModuleStorage("essentials",
                configuration.storage().sqliteFileName(), dataFolder, null);
            essDs = storageScope.dataSource();
        }
        repository = new EssentialsRepository(essDs, essDesc, dataFolder, logger);
        moderation = new ModerationService(repository, configuration.moderation(), logger, work);
        var repo = repository;
        var bans = moderation;
        bans.beginLoadingBans();
        registerListener(bans);
        return storageScope.initialize(() -> {
            repo.initialize();
            bans.initialize();
        });
    }

    @Override
    protected void startService() throws Exception {
        moderation.start(plugin);
        inventoryInspection = new InventoryInspection();
        registerListener(inventoryInspection);

        playerService = new PlayerManagementService(plugin, logger, configuration,
            () -> getCapability(TabRefreshable.class), this::advancedCrossServerOrchestrationAllowed);
        playerService.start();

        teleportService = new TeleportService(
            plugin, configuration, repository, playerService, logger,
            new WarpDirectory(repository, configPath, rawYaml, logger),
            () -> getCapability(xuanmo.arcartxsuite.api.capability.AbyssAdmissionAccess.class),
            () -> getCapability(xuanmo.arcartxsuite.api.capability.RegionQueryable.class), work);
        teleportService.start();

        // 一键砍树服务
        var treeSection = rawYaml.getConfigurationSection("tree-capitator");
        if (treeSection != null) {
            treeCapitatorService = new TreeCapitatorService(plugin, treeSection,
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
        registerCapability(EssentialsQueryable.class, new EssentialsQueryableImpl(playerService,teleportService,repository,chatMutableSupplier,work));

        commandDelegate = new EssentialsCommandDelegate(
            configuration, playerService, teleportService, repository,
            inventoryActionsService, chatMutableSupplier,
            () -> menuPacketHandler, () -> adminPacketHandler, moderation, inventoryInspection, this::inspectCommand,
            plugin, this::messages, this::reloadSelf, poseMonitorTasks, seatEntities, work
        );

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
                    plugin, packetBridge, playerService, repository,
                    inventoryActionsService, teleportService, menuBinding.runtimeUiId(), commandDelegate, work);
            }
            if (adminBinding.registeredUiId() != null) {
                adminPacketHandler = new EssentialsAdminPacketHandler(
                    plugin, packetBridge, playerService, repository, chatMutableSupplier,
                    teleportService.warps(), adminBinding.runtimeUiId(), commandDelegate, work);
            }
        }

        EssentialsRepository essRepo = repository;
        var runtime=work;
        registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "essentials"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    return runtime.call(()->{try { return essRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { logger.warning("Essentials purge 失败: " + e.getMessage()); return -1; }},-1);
                }
                @Override public int purgeAllPlayerData() {
                    return runtime.call(()->{try { return essRepo.deleteAllPlayerData(); }
                    catch (Exception e) { logger.warning("Essentials purgeAll 失败: " + e.getMessage()); return -1; }},-1);
                }
            });

        registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
            new xuanmo.arcartxsuite.api.capability.DatabaseMigratable() {
                @Override public @NotNull String moduleId() { return "essentials"; }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.MigrationResult migrateDatabase(
                        @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor target, boolean overwrite) {
                    var unavailable=new xuanmo.arcartxsuite.api.storage.MigrationResult(false,0,0);
                    unavailable.addError("Essentials 已停用");
                    return runtime.call(()->essRepo.migrateData(target,overwrite),unavailable);
                }
                @Override public @NotNull xuanmo.arcartxsuite.api.storage.StorageDescriptor currentDescriptor() {
                    return essRepo.getDescriptor();
                }
            });

        // 等待服务端及 POSTWORLD 插件完成初始命令注册，再判断同名命令的归属。
        commandRegistrationTask = Bukkit.getScheduler().runTask(plugin,
            ()->runtime.call(()->{registerShortcuts();return null;},null));
        logger.info("Essentials 模块已启动。");
    }

    private void registerShortcuts() {
        commandRegistrationTask = null;
        boolean protectExisting = configuration.commandConflictProtection();

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
        }, protectExisting);

        // 注册配置中定义的命令简写别名（如 /fly /god /gm 等），转发到 /essentials <action>
        if (!configuration.commandAliases().isEmpty()) {
            for (var entry : configuration.commandAliases().entrySet()) {
                String alias = entry.getKey();
                String action = entry.getValue();
                if (!EssentialsCommandDelegate.actions().contains(action)) {
                    logger.warning("Essentials 简写 /" + alias + " 指向未知动作 " + action);
                    continue;
                }
                if (!configuration.features().actionEnabled(action)) continue;
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
                }, protectExisting, EssentialsCommandDelegate.permissionForAction(action));
            }
            logger.info("已加载 " + configuration.commandAliases().size() + " 条命令简写配置");
        }
    }

    /**
     * 供 {@link EssentialsCommandDelegate} 的 reload 子命令回调：包装 {@link #onReload()}
     * 的受检异常为运行时异常，由委派器统一捕获并回报失败消息。
     */
    private java.util.concurrent.CompletionStage<Void> reloadSelf() { return onReload(); }

    /**
     * 停止并释放全部服务层资源，按反向顺序关闭。
     */
    @Override
    protected void stopService() {
        var runtime=work;var scope=storageScope;
        work=null;storageScope=null;
        var stages=new java.util.ArrayList<java.util.concurrent.CompletableFuture<Void>>();
        if(runtime!=null){runtime.close();stages.add(runtime.stopped().toCompletableFuture());}
        if (commandRegistrationTask != null) {
            cleanup(commandRegistrationTask::cancel,stages);
            commandRegistrationTask = null;
        }
        if (commandDelegate != null) {
            cleanup(commandDelegate::shutdown, stages);
            stages.add(commandDelegate.stopped().toCompletableFuture());
            commandDelegate = null;
        }
        poseMonitorTasks.values().forEach(task->cleanup(task::cancel,stages));
        poseMonitorTasks.clear();
        seatEntities.values().forEach(entity -> {
            cleanup(()->{if(entity.isValid())entity.remove();},stages);
        });
        seatEntities.clear();
        if (menuPacketHandler != null) { cleanup(menuPacketHandler::shutdown,stages); menuPacketHandler = null; }
        if (adminPacketHandler != null) { cleanup(adminPacketHandler::shutdown,stages); adminPacketHandler = null; }
        if (inventoryInspection != null) {
            cleanup(inventoryInspection::closeAll,stages);
            var inspection=inventoryInspection;cleanup(()->org.bukkit.event.HandlerList.unregisterAll(inspection),stages);
            inventoryInspection = null;
        }
        if (moderation != null) {
            cleanup(moderation::stop,stages);
            var current=moderation;cleanup(()->org.bukkit.event.HandlerList.unregisterAll(current),stages);
            moderation = null;
        }
        if (inventoryActionsService != null) { cleanup(inventoryActionsService::shutdown,stages);stages.add(inventoryActionsService.stopped().toCompletableFuture());inventoryActionsService = null; }
        if (treeCapitatorService != null) { cleanup(treeCapitatorService::shutdown,stages);stages.add(treeCapitatorService.stopped().toCompletableFuture());treeCapitatorService = null; }
        if (teleportService != null) { cleanup(teleportService::shutdown,stages);stages.add(teleportService.stopped().toCompletableFuture());teleportService = null; }
        if (playerService != null) { cleanup(playerService::shutdown,stages);stages.add(playerService.stopped().toCompletableFuture());playerService = null; }
        repository = null;
        var stopped=java.util.concurrent.CompletableFuture.allOf(stages.toArray(java.util.concurrent.CompletableFuture[]::new));
        trackShutdown(stopped);
        if(scope!=null){scope.retireAfter(stopped);trackShutdown(scope.retired());}
        configuration = null;
        rawYaml = null;
        configPath = null;
    }

    private void cleanup(Runnable operation,java.util.List<java.util.concurrent.CompletableFuture<Void>> stages) {
        try{operation.run();}
        catch(RuntimeException|LinkageError failure){
            logger.log(java.util.logging.Level.WARNING,"Essentials 关闭步骤失败",failure);
            stages.add(java.util.concurrent.CompletableFuture.failedFuture(failure));
        }
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
        var delegate=commandDelegate;var runtime=work;
        if (delegate == null||runtime==null) {
            sender.sendMessage(messages() != null ? messages().get("common.command-handler-down") : "§cEssentials 命令处理器尚未初始化。");
            return true;
        }
        return runtime.call(()->delegate.onCommand(sender,label,args),true);
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
        var delegate=commandDelegate;var runtime=work;
        if (delegate == null||runtime==null) return List.of();
        return runtime.call(()->delegate.onTabComplete(sender,args),List.of());
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
            @EventHandler(priority = org.bukkit.event.EventPriority.HIGHEST, ignoreCancelled = true)
            public void onSeatClick(org.bukkit.event.player.PlayerInteractEvent event) {
                if (event.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK
                    || event.getHand() != org.bukkit.inventory.EquipmentSlot.HAND
                    || event.useInteractedBlock() == org.bukkit.event.Event.Result.DENY
                    || (event.getItem() != null && !event.getItem().getType().isAir())
                    || event.getPlayer().isSneaking() || commandDelegate == null) return;
                org.bukkit.block.Block block = event.getClickedBlock();
                if (block == null) return;
                var data = block.getBlockData();
                double height;
                if (configuration.interaction().sitOnStairs() && data instanceof org.bukkit.block.data.type.Stairs stairs) {
                    height = stairs.getHalf() == org.bukkit.block.data.Bisected.Half.TOP ? 1.0 : 0.5;
                } else if (configuration.interaction().sitOnSlabs() && data instanceof org.bukkit.block.data.type.Slab slab
                    && slab.getType() != org.bukkit.block.data.type.Slab.Type.DOUBLE) {
                    height = slab.getType() == org.bukkit.block.data.type.Slab.Type.TOP ? 1.0 : 0.5;
                } else {
                    return;
                }
                if (block.getRelative(org.bukkit.block.BlockFace.UP).isPassable()
                    && commandDelegate.sitAt(event.getPlayer(), block.getLocation().add(0.5, height, 0.5))) {
                    event.setCancelled(true);
                }
            }

            @EventHandler
            public void onPlayerQuit(PlayerQuitEvent event) {
                cancelPoseTask(event.getPlayer().getUniqueId());
                if (adminPacketHandler != null) {
                    adminPacketHandler.clearPlayer(event.getPlayer().getUniqueId());
                }
                if (menuPacketHandler != null) {
                    menuPacketHandler.clearPlayer(event.getPlayer().getUniqueId());
                }
            }
        });
    }

    private void cancelPoseTask(UUID playerId) {
        BukkitTask task = poseMonitorTasks.remove(playerId);
        if (task != null) {
            task.cancel();
        }
        org.bukkit.entity.Entity seat = seatEntities.remove(playerId);
        if (seat != null && seat.isValid()) {
            seat.remove();
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
    private static final class EssentialsQueryableImpl implements EssentialsQueryable {
        private final PlayerManagementService playerService;
        private final TeleportService teleportService;
        private final EssentialsRepository repository;
        private final Supplier<ChatMutable> chatMutableSupplier;
        private final EssentialsWork runtime;
        EssentialsQueryableImpl(PlayerManagementService players,TeleportService teleport,EssentialsRepository repository,
                                Supplier<ChatMutable> chat,EssentialsWork runtime) {
            this.playerService=players;this.teleportService=teleport;this.repository=repository;this.chatMutableSupplier=chat;this.runtime=runtime;
        }

        @Override
        public boolean isAfk(@NotNull UUID playerUuid) {
            return runtime.call(()->playerService != null && playerService.isAfk(playerUuid),false);
        }

        @Override
        public boolean isVanished(@NotNull UUID playerUuid) {
            return runtime.call(()->playerService != null && playerService.isVanished(playerUuid),false);
        }

        @Override
        public @NotNull java.util.concurrent.CompletionStage<Boolean> isMuted(@NotNull UUID playerUuid) {
            return runtime.call(() -> {
                ChatMutable chatMutable = chatMutableSupplier != null ? chatMutableSupplier.get() : null;
                return chatMutable != null ? chatMutable.isMuted(playerUuid)
                    : java.util.concurrent.CompletableFuture.completedFuture(false);
            }, java.util.concurrent.CompletableFuture.failedFuture(
                new java.util.concurrent.RejectedExecutionException("Essentials retired")));
        }

        @Override
        public java.util.concurrent.CompletionStage<String> getNickname(@NotNull UUID playerUuid) {
            return runtime.read(() -> repository.getNickname(playerUuid));
        }

        @Override
        public boolean isFlying(@NotNull UUID playerUuid) {
            return runtime.call(()->{Player p = Bukkit.getPlayer(playerUuid);return p != null && p.isFlying();},false);
        }

        @Override
        public boolean isGodMode(@NotNull UUID playerUuid) {
            return runtime.call(()->{Player p = Bukkit.getPlayer(playerUuid);return p != null && p.isInvulnerable();},false);
        }

        @Override
        public java.util.concurrent.CompletionStage<Location> getSpawn(@NotNull World world) {
            return teleportService.getSpawn(world);
        }
    }
}


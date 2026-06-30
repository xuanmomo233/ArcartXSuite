package xuanmo.arcartxsuite.essentials;

import java.io.File;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    @Override
    public ModuleDescriptor descriptor() {
        return ModuleDescriptor.builder("essentials")
            .name("Essentials")
            .version("1.0.0-beta")
            .mainClass(getClass().getName())
            .build();
    }

    @Override
    public String commandId() {
        return "essentials";
    }

    @Override
    public List<String> commandAliases() {
        return List.of("ess");
    }

    @Override
    public List<String> actions() {
        return ACTIONS;
    }

    @Override
    protected String configFileName() {
        return "ArcartXEssentials.yml";
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
            ValidationRule.required("storage.sqlite-file", ValueType.STRING)
        );
    }

    @Override
    protected @NotNull Map<String, String> uiResourceMappings() {
        Map<String, String> mappings = new LinkedHashMap<>();
        mappings.put(EssentialsMenuPacketHandler.UI_RESOURCE_PATH, EssentialsMenuPacketHandler.UI_FILE_PATH);
        mappings.put(EssentialsAdminPacketHandler.UI_RESOURCE_PATH, EssentialsAdminPacketHandler.UI_FILE_PATH);
        return mappings;
    }

    @Override
    protected void loadConfiguration(@Nullable File configFile) throws Exception {
        if (configFile == null) {
            throw new IllegalStateException("ArcartXEssentials.yml 配置文件缺失");
        }
        rawYaml = YamlConfiguration.loadConfiguration(configFile);
        configuration = EssentialsConfiguration.load(rawYaml, context.logger());
    }

    @Override
    protected void startService() throws Exception {
        repository = new EssentialsRepository(
            context.dataFolder(),
            configuration.storage(), context.logger());
        repository.initialize();

        playerService = new PlayerManagementService(context.plugin(), configuration,
            () -> context.getCapability(TabRefreshable.class));
        playerService.start();

        teleportService = new TeleportService(
            context.plugin(), configuration, repository, playerService, context.logger());
        teleportService.start();

        // 一键砍树服务
        var treeSection = rawYaml.getConfigurationSection("tree-capitator");
        if (treeSection != null) {
            treeCapitatorService = new TreeCapitatorService(context.plugin(), treeSection,
                configuration.messages().treeFelled());
            treeCapitatorService.start();
        }

        // 背包操作服务
        var invSection = rawYaml.getConfigurationSection("inv-actions");
        if (invSection != null) {
            inventoryActionsService = new InventoryActionsService(context.plugin(), invSection);
            inventoryActionsService.start();
        }

        // 延迟获取 ChatMutable capability（Chat 模块可能后加载）
        chatMutableSupplier = () -> context.getCapability(ChatMutable.class);

        // 注册 EssentialsQueryable capability 供 Tab/Chat 等模块查询
        context.registerCapability(EssentialsQueryable.class, new EssentialsQueryableImpl());

        // UI 绑定与 Packet Handler 初始化
        PacketBridgeAPI packetBridge = context.packetBridge();
        PacketGuardAPI packetGuard = context.packetGuard();
        if (packetBridge != null && packetBridge.isAvailable()) {
            UiBinding menuBinding = registerModuleUi(
                EssentialsMenuPacketHandler.UI_FILE_PATH, null, true
            );
            UiBinding adminBinding = registerModuleUi(
                EssentialsAdminPacketHandler.UI_FILE_PATH, null, true
            );

            if (menuBinding.registeredUiId() != null) {
                menuPacketHandler = new EssentialsMenuPacketHandler(
                    context.plugin(), packetBridge, packetGuard,
                    playerService, teleportService, repository,
                    inventoryActionsService, menuBinding.runtimeUiId());
            }
            if (adminBinding.registeredUiId() != null) {
                adminPacketHandler = new EssentialsAdminPacketHandler(
                    context.plugin(), packetBridge, packetGuard,
                    playerService, teleportService, repository,
                    adminBinding.runtimeUiId());
            }
        }

        // 注册 /ess 独立缩写命令，转发给 essentials 子命令处理
        context.registerCommand("ess", new org.bukkit.command.TabExecutor() {
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

        EssentialsRepository essRepo = repository;
        context.registerCapability(xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable.class,
            new xuanmo.arcartxsuite.api.capability.PlayerDataPurgeable() {
                @Override public @NotNull String moduleId() { return "essentials"; }
                @Override public int purgePlayerData(@NotNull java.util.UUID playerUuid) {
                    try { return essRepo.deletePlayerData(playerUuid); }
                    catch (Exception e) { context.logger().warning("Essentials purge 失败: " + e.getMessage()); return -1; }
                }
                @Override public int purgeAllPlayerData() {
                    try { return essRepo.deleteAllPlayerData(); }
                    catch (Exception e) { context.logger().warning("Essentials purgeAll 失败: " + e.getMessage()); return -1; }
                }
            });

        context.registerCapability(xuanmo.arcartxsuite.api.capability.DatabaseMigratable.class,
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
            context.plugin(), this::messages, poseMonitorTasks
        );

        context.logger().info("Essentials 模块已启动。");
    }

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
    public boolean onCommand(@NotNull CommandSender sender, @NotNull String label, @NotNull String[] args) {
        if (commandDelegate == null) {
            sender.sendMessage("\u00a7cEssentials \u547d\u4ee4\u5904\u7406\u5668\u5c1a\u672a\u521d\u59cb\u5316\u3002");
            return true;
        }
        return commandDelegate.onCommand(sender, label, args);
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (commandDelegate == null) return List.of();
        return commandDelegate.onTabComplete(sender, args);
    }

    // ─── Sit / Lay ───

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

    public EssentialsRepository getRepository() {
        return repository;
    }

    public PlayerManagementService getPlayerService() {
        return playerService;
    }

    @Override
    protected @Nullable Object createPlaceholderExpansion() {
        return new EssentialsPlaceholderExpansion(context.plugin(), () -> playerService);
    }

    // ─── EssentialsQueryable 实现 ───

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

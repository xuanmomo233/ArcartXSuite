package xuanmo.arcartxsuite.essentials;

import java.io.File;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.WeatherType;
import org.bukkit.World;
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

        context.logger().info("Essentials 模块已启动。");
    }

    @Override
    protected void stopService() {
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
        String action = args.length < 2 ? "help" : args[1].toLowerCase(Locale.ROOT);
        switch (action) {
            case "help" -> sendHelp(sender, label, args[0]);
            case "status" -> sendStatus(sender);
            case "reload" -> requirePermission(sender, "axs.essentials.reload", () -> reload(sender));
            case "menu" -> requirePlayer(sender, p -> {
                if (menuPacketHandler != null) menuPacketHandler.openMenu(p);
                else p.sendMessage(fullMsg("admin.ui.offline"));
            });
            case "admin" -> requirePlayer(sender, p -> {
                if (!p.hasPermission("axs.essentials.admin")) {
                    p.sendMessage(fullMsg("admin.ui.no-permission"));
                    return;
                }
                if (adminPacketHandler != null) adminPacketHandler.openMenu(p);
                else p.sendMessage(fullMsg("admin.ui.offline"));
            });
            // ─── 玩家管理 ───
            case "fly" -> requirePermission(sender, "axs.essentials.fly", () ->
                requirePlayer(sender, p -> playerService.toggleFly(p, resolveTarget(sender, args, 2, p))));
            case "god" -> requirePermission(sender, "axs.essentials.god", () ->
                requirePlayer(sender, p -> playerService.toggleGod(resolveTarget(sender, args, 2, p))));
            case "heal" -> requirePermission(sender, "axs.essentials.heal", () ->
                requirePlayer(sender, p -> playerService.heal(resolveTarget(sender, args, 2, p))));
            case "feed" -> requirePermission(sender, "axs.essentials.feed", () ->
                requirePlayer(sender, p -> playerService.feed(resolveTarget(sender, args, 2, p))));
            case "gamemode" -> requirePermission(sender, "axs.essentials.gamemode", () -> handleGameMode(sender, args));
            case "speed" -> requirePermission(sender, "axs.essentials.speed", () -> handleSpeed(sender, args));
            case "vanish" -> requirePermission(sender, "axs.essentials.vanish", () ->
                requirePlayer(sender, p -> playerService.toggleVanish(p)));
            case "afk" -> requirePermission(sender, "axs.essentials.afk", () ->
                requirePlayer(sender, p -> playerService.toggleAfk(p)));
            case "back" -> requirePermission(sender, "axs.essentials.back", () ->
                requirePlayer(sender, p -> teleportService.back(p)));
            case "repair" -> requirePermission(sender, "axs.essentials.repair", () ->
                requirePlayer(sender, p -> playerService.repair(p)));
            case "hat" -> requirePermission(sender, "axs.essentials.hat", () ->
                requirePlayer(sender, p -> playerService.hat(p)));
            case "enderchest" -> requirePermission(sender, "axs.essentials.enderchest", () ->
                requirePlayer(sender, p -> playerService.openEnderChest(p, resolveTarget(sender, args, 2, p))));
            case "workbench" -> requirePermission(sender, "axs.essentials.workbench", () ->
                requirePlayer(sender, p -> playerService.openWorkbench(p)));
            case "anvil" -> requirePermission(sender, "axs.essentials.anvil", () ->
                requirePlayer(sender, p -> playerService.openAnvil(p)));
            case "trash" -> requirePermission(sender, "axs.essentials.trash", () ->
                requirePlayer(sender, p -> playerService.openTrash(p)));
            case "nick" -> requirePermission(sender, "axs.essentials.nick", () -> handleNick(sender, args));
            case "seen" -> requirePermission(sender, "axs.essentials.seen", () -> handleSeen(sender, args));
            // ─── 传送 ───
            case "home" -> requirePermission(sender, "axs.essentials.home", () ->
                requirePlayer(sender, p -> teleportService.teleportHome(p, arg(args, 2, "home"))));
            case "sethome" -> requirePermission(sender, "axs.essentials.sethome", () ->
                requirePlayer(sender, p -> teleportService.setHome(p, arg(args, 2, "home"))));
            case "delhome" -> requirePermission(sender, "axs.essentials.delhome", () ->
                requirePlayer(sender, p -> teleportService.deleteHome(p, arg(args, 2, "home"))));
            case "warp" -> requirePermission(sender, "axs.essentials.warp", () ->
                requirePlayer(sender, p -> teleportService.teleportWarp(p, arg(args, 2, ""))));
            case "setwarp" -> requirePermission(sender, "axs.essentials.setwarp", () ->
                handleRequireArg(sender, args, 2, "传送点名称", name ->
                    requirePlayer(sender, p -> teleportService.setWarp(p, name))));
            case "delwarp" -> requirePermission(sender, "axs.essentials.delwarp", () ->
                handleRequireArg(sender, args, 2, "传送点名称", name ->
                    requirePlayer(sender, p -> teleportService.deleteWarp(p, name))));
            case "spawn" -> requirePermission(sender, "axs.essentials.spawn", () ->
                requirePlayer(sender, p -> teleportService.teleportSpawn(p)));
            case "setspawn" -> requirePermission(sender, "axs.essentials.setspawn", () ->
                requirePlayer(sender, p -> teleportService.setSpawn(p)));
            case "tpa" -> requirePermission(sender, "axs.essentials.tpa", () -> handleTpa(sender, args, false));
            case "tpahere" -> requirePermission(sender, "axs.essentials.tpa", () -> handleTpa(sender, args, true));
            case "tpaccept" -> requirePermission(sender, "axs.essentials.tpa", () ->
                requirePlayer(sender, p -> teleportService.acceptTpa(p)));
            case "tpdeny" -> requirePermission(sender, "axs.essentials.tpa", () ->
                requirePlayer(sender, p -> teleportService.denyTpa(p)));
            case "tp" -> requirePermission(sender, "axs.essentials.teleport", () -> handleTp(sender, args));
            case "top" -> requirePermission(sender, "axs.essentials.teleport", () ->
                requirePlayer(sender, p -> teleportService.teleportTop(p)));
            case "tppos" -> requirePermission(sender, "axs.essentials.teleport", () -> handleTpPos(sender, args));
            // ─── 世界管理 ───
            case "time" -> requirePermission(sender, "axs.essentials.time", () -> handleTime(sender, args));
            case "weather" -> requirePermission(sender, "axs.essentials.weather", () -> handleWeather(sender, args));
            // ─── 安全管理 ───
            case "ban" -> requirePermission(sender, "axs.essentials.ban", () -> handleBan(sender, args, false));
            case "tempban" -> requirePermission(sender, "axs.essentials.ban", () -> handleBan(sender, args, true));
            case "unban" -> requirePermission(sender, "axs.essentials.unban", () -> handleUnban(sender, args));
            case "mute" -> requirePermission(sender, "axs.essentials.mute", () -> handleMute(sender, args, false));
            case "tempmute" -> requirePermission(sender, "axs.essentials.mute", () -> handleMute(sender, args, true));
            case "unmute" -> requirePermission(sender, "axs.essentials.unmute", () -> handleUnmute(sender, args));
            case "kick" -> requirePermission(sender, "axs.essentials.kick", () -> handleKick(sender, args));
            case "warn" -> requirePermission(sender, "axs.essentials.warn", () -> handleWarn(sender, args));
            case "sudo" -> requirePermission(sender, "axs.essentials.sudo", () -> handleSudo(sender, args));
            case "inv" -> requirePermission(sender, "axs.essentials.inv", () -> handleInv(sender, args));
            // ─── 交互 ───
            case "sit" -> requirePermission(sender, "axs.essentials.sit", () -> requirePlayer(sender, this::handleSit));
            case "lay" -> requirePermission(sender, "axs.essentials.lay", () -> requirePlayer(sender, this::handleLay));
            // ─── 工具 ───
            case "sort" -> requirePermission(sender, "axs.essentials.sort", () -> requirePlayer(sender, p -> {
                if (inventoryActionsService != null) {
                    inventoryActionsService.sortInventory(p);
                    p.sendMessage(prefix() + configuration.messages().sortDone());
                }
            }));
            case "replant" -> requirePermission(sender, "axs.essentials.replant", () -> requirePlayer(sender, p -> {
                if (inventoryActionsService != null) {
                    boolean on = inventoryActionsService.toggleReplant(p.getUniqueId());
                    p.sendMessage(prefix() + (on ? configuration.messages().replantDisabled() : configuration.messages().replantEnabled()));
                }
            }));
            case "autotool" -> requirePermission(sender, "axs.essentials.autotool", () -> requirePlayer(sender, p -> {
                if (inventoryActionsService != null) {
                    boolean on = inventoryActionsService.toggleAutoTool(p.getUniqueId());
                    p.sendMessage(prefix() + (on ? configuration.messages().autotoolDisabled() : configuration.messages().autotoolEnabled()));
                }
            }));
            default -> {
                sender.sendMessage(fullMsg("common.unknown", action));
                sendHelp(sender, label, args[0]);
            }
        }
        return true;
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull String[] args) {
        if (args.length == 2) return filter(ACTIONS, args[1]);
        if (args.length == 3) {
            String action = args[1].toLowerCase(Locale.ROOT);
            switch (action) {
                case "fly", "god", "heal", "feed", "enderchest", "tp", "sudo", "inv",
                     "ban", "tempban", "unban", "mute", "tempmute", "unmute", "kick", "warn" -> {
                    return filterOnline(args[2]);
                }
                case "home", "delhome" -> {
                    if (sender instanceof Player p) return filter(teleportService.getHomeNames(p), args[2]);
                }
                case "warp", "delwarp" -> {
                    return filter(teleportService.getWarpNames(), args[2]);
                }
                case "gamemode" -> {
                    return filter(List.of("survival", "creative", "adventure", "spectator"), args[2]);
                }
                case "speed" -> {
                    return filter(List.of("fly", "walk"), args[2]);
                }
                case "time" -> {
                    return filter(List.of("day", "night", "noon", "midnight", "set"), args[2]);
                }
                case "weather" -> {
                    return filter(List.of("clear", "rain", "thunder"), args[2]);
                }
                case "tpa", "tpahere" -> {
                    return filterOnline(args[2]);
                }
            }
        }
        if (args.length == 4) {
            String action = args[1].toLowerCase(Locale.ROOT);
            if ("gamemode".equals(action)) return filterOnline(args[3]);
            if ("speed".equals(action)) return filter(List.of("1", "2", "3", "5", "10"), args[3]);
        }
        return List.of();
    }

    // ─── Command Handlers ───

    private void handleGameMode(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.gamemode.usage"));
            return;
        }
        GameMode mode = parseGameMode(args[2]);
        if (mode == null) {
            sender.sendMessage(fullMsg("admin.gamemode.invalid", args[2]));
            return;
        }
        Player target = args.length >= 4 ? Bukkit.getPlayer(args[3]) : (sender instanceof Player p ? p : null);
        if (target == null) {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args.length >= 4 ? args[3] : ""));
            return;
        }
        playerService.setGameMode(target, mode);
        if (!sender.equals(target)) {
            sender.sendMessage(fullMsg("admin.gamemode.success", target.getName(), mode.name().toLowerCase()));
        }
    }

    private void handleSpeed(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(fullMsg("admin.speed.usage"));
            return;
        }
        boolean isFly = "fly".equalsIgnoreCase(args[2]);
        try {
            float speed = Float.parseFloat(args[3]);
            requirePlayer(sender, p -> playerService.setSpeed(p, speed, isFly));
        } catch (NumberFormatException e) {
            sender.sendMessage(fullMsg("admin.speed.invalid", args[3]));
        }
    }

    private void handleNick(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.nick.usage"));
            return;
        }
        requirePlayer(sender, p -> {
            if ("off".equalsIgnoreCase(args[2]) || "reset".equalsIgnoreCase(args[2])) {
                p.setDisplayName(p.getName());
                try { repository.removeNickname(p.getUniqueId()); } catch (Exception ignored) {}
                p.sendMessage(prefix() + configuration.messages().nickReset());
            } else {
                String nick = ChatColor.translateAlternateColorCodes('&', args[2]);
                p.setDisplayName(nick);
                try { repository.setNickname(p.getUniqueId(), args[2]); } catch (Exception ignored) {}
                p.sendMessage(prefix() + configuration.messages().nickSet().replace("{nick}", nick));
            }
        });
    }

    private void handleSeen(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.seen.usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target != null) {
            sender.sendMessage(fullMsg("admin.seen.online", args[2]));
            sender.sendMessage(fullMsg("admin.seen.ip", (target.getAddress() != null ? target.getAddress().getHostString() : "未知")));
        } else {
            sender.sendMessage(fullMsg("admin.seen.offline", args[2]));
        }
    }

    private void handleTpa(CommandSender sender, String[] args, boolean here) {
        if (args.length < 3) {
            sender.sendMessage(here ? fullMsg("admin.tpahere.usage") : fullMsg("admin.tpa.usage"));
            return;
        }
        requirePlayer(sender, p -> {
            Player target = Bukkit.getPlayer(args[2]);
            if (target == null || !target.isOnline()) {
                p.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
                return;
            }
            teleportService.sendTpa(p, target, here);
        });
    }

    private void handleTp(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.tp.usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
            return;
        }
        if (args.length >= 4) {
            Player dest = Bukkit.getPlayer(args[3]);
            if (dest == null) {
                sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[3]));
                return;
            }
            target.teleport(dest.getLocation());
            sender.sendMessage(fullMsg("admin.tp.success-other", target.getName(), dest.getName()));
        } else {
            requirePlayer(sender, p -> {
                p.teleport(target.getLocation());
                p.sendMessage(fullMsg("admin.tp.success-self", target.getName()));
            });
        }
    }

    private void handleTpPos(CommandSender sender, String[] args) {
        if (args.length < 5) {
            sender.sendMessage(fullMsg("admin.tppos.usage"));
            return;
        }
        requirePlayer(sender, p -> {
            try {
                double x = Double.parseDouble(args[2]);
                double y = Double.parseDouble(args[3]);
                double z = Double.parseDouble(args[4]);
                teleportService.delayedTeleport(p, new org.bukkit.Location(p.getWorld(), x, y, z, p.getLocation().getYaw(), p.getLocation().getPitch()),
                    messages().get("admin.tppos.success", x, y, z));
            } catch (NumberFormatException e) {
                p.sendMessage(fullMsg("admin.tppos.invalid"));
            }
        });
    }

    private void handleTime(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.time.usage"));
            return;
        }
        requirePlayer(sender, p -> {
            World world = p.getWorld();
            String timeArg = args[2].toLowerCase(Locale.ROOT);
            long ticks = switch (timeArg) {
                case "day" -> 1000L;
                case "night" -> 13000L;
                case "noon" -> 6000L;
                case "midnight" -> 18000L;
                case "set" -> {
                    if (args.length < 4) { p.sendMessage(fullMsg("admin.time.require-val")); yield -1L; }
                    try { yield Long.parseLong(args[3]); } catch (NumberFormatException e) { p.sendMessage(fullMsg("admin.time.invalid-val")); yield -1L; }
                }
                default -> { p.sendMessage(fullMsg("admin.time.invalid-key", timeArg)); yield -1L; }
            };
            if (ticks >= 0) {
                world.setTime(ticks);
                p.sendMessage(prefix() + configuration.messages().timeSet()
                    .replace("{world}", world.getName()).replace("{time}", timeArg));
            }
        });
    }

    private void handleWeather(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.weather.usage"));
            return;
        }
        requirePlayer(sender, p -> {
            World world = p.getWorld();
            String w = args[2].toLowerCase(Locale.ROOT);
            switch (w) {
                case "clear" -> { world.setStorm(false); world.setThundering(false); }
                case "rain" -> { world.setStorm(true); world.setThundering(false); }
                case "thunder" -> { world.setStorm(true); world.setThundering(true); }
                default -> { p.sendMessage(fullMsg("admin.weather.invalid", w)); return; }
            }
            p.sendMessage(prefix() + configuration.messages().weatherSet()
                .replace("{world}", world.getName()).replace("{weather}", w));
        });
    }

    // ─── Moderation ───

    private void handleBan(CommandSender sender, String[] args, boolean temp) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.ban.usage", temp ? "tempban" : "ban"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        String operator = sender.getName();
        long expiresAt = -1;
        int reasonStart = 3;
        if (temp) {
            if (args.length < 4) {
                sender.sendMessage(fullMsg("admin.ban.require-duration"));
                return;
            }
            expiresAt = System.currentTimeMillis() + parseDuration(args[3]);
            reasonStart = 4;
        }
        String reason = joinArgs(args, reasonStart, "无");
        if (target != null) {
            try {
                String ip = target.getAddress() != null ? target.getAddress().getHostString() : null;
                repository.ban(target.getUniqueId(), target.getName(), reason, operator, expiresAt, ip);
                String banMsg = configuration.moderation().banMessage()
                    .replace("{reason}", reason)
                    .replace("{expiry}", expiresAt > 0 ? formatTime(expiresAt) : "永久");
                target.kickPlayer(banMsg);
                Bukkit.broadcastMessage(prefix() + configuration.messages().banBroadcast()
                    .replace("{player}", target.getName()).replace("{operator}", operator).replace("{reason}", reason));
            } catch (Exception e) {
                sender.sendMessage(fullMsg("admin.ban.failed", e.getMessage()));
            }
        } else {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
        }
    }

    private void handleUnban(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.unban.usage"));
            return;
        }
        sender.sendMessage(fullMsg("admin.unban.tip"));
        // 简化实现：通过名字匹配
        Player target = Bukkit.getPlayer(args[2]);
        if (target != null) {
            try {
                repository.unban(target.getUniqueId());
                sender.sendMessage(prefix() + configuration.messages().unbanSuccess().replace("{player}", args[2]));
            } catch (Exception e) {
                sender.sendMessage(fullMsg("admin.unban.failed"));
            }
        } else {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
        }
    }

    private void handleMute(CommandSender sender, String[] args, boolean temp) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.mute.usage", temp ? "tempmute" : "mute"));
            return;
        }
        ChatMutable chatMutable = chatMutableSupplier.get();
        if (chatMutable == null) {
            sender.sendMessage(fullMsg("admin.mute.offline-warn"));
            return;
        }
        String playerName = args[2];
        Instant expiresAt = null;
        int reasonStart = 3;
        if (temp) {
            if (args.length < 4) {
                sender.sendMessage(fullMsg("admin.mute.require-duration"));
                return;
            }
            expiresAt = Instant.ofEpochMilli(System.currentTimeMillis() + parseDuration(args[3]));
            reasonStart = 4;
        }
        String reason = joinArgs(args, reasonStart, "无");
        String result = chatMutable.mutePlayer(playerName, expiresAt, reason, sender.getName());
        sender.sendMessage(prefix() + result);
    }

    private void handleUnmute(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.unmute.usage"));
            return;
        }
        ChatMutable chatMutable = chatMutableSupplier.get();
        if (chatMutable == null) {
            sender.sendMessage(fullMsg("admin.unmute.offline-warn"));
            return;
        }
        String result = chatMutable.unmutePlayer(args[2]);
        sender.sendMessage(prefix() + result);
    }

    private void handleKick(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.kick.usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
            return;
        }
        String reason = joinArgs(args, 3, "无");
        String kickMsg = configuration.moderation().kickMessage().replace("{reason}", reason);
        target.kickPlayer(kickMsg);
        Bukkit.broadcastMessage(prefix() + configuration.messages().kickBroadcast()
            .replace("{player}", target.getName()).replace("{operator}", sender.getName()).replace("{reason}", reason));
    }

    private void handleWarn(CommandSender sender, String[] args) {
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.warn.usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
            return;
        }
        String reason = joinArgs(args, 3, "无");
        try {
            repository.addWarning(target.getUniqueId(), target.getName(), reason, sender.getName());
            Bukkit.broadcastMessage(prefix() + configuration.messages().warnBroadcast()
                .replace("{player}", target.getName()).replace("{operator}", sender.getName()).replace("{reason}", reason));

            // 检查是否达到自动封禁阈值
            int maxWarns = configuration.moderation().maxWarningsBeforeBan();
            if (maxWarns > 0) {
                long since = configuration.moderation().warningExpireDays() > 0
                    ? System.currentTimeMillis() - (configuration.moderation().warningExpireDays() * 86400000L)
                    : 0;
                int count = repository.getWarningCount(target.getUniqueId(), since);
                if (count >= maxWarns) {
                    repository.ban(target.getUniqueId(), target.getName(), "警告次数达到上限 (" + count + ")", "系统", -1, null);
                    target.kickPlayer(configuration.moderation().banMessage()
                        .replace("{reason}", "警告次数达到上限").replace("{expiry}", "永久"));
                    sender.sendMessage(fullMsg("admin.warn.ban-tip", target.getName(), count));
                }
            }
        } catch (Exception e) {
            sender.sendMessage(fullMsg("admin.warn.failed"));
        }
    }

    private void handleSudo(CommandSender sender, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(fullMsg("admin.sudo.usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
            return;
        }
        String cmd = joinArgs(args, 3, "");
        if (cmd.startsWith("/")) cmd = cmd.substring(1);
        target.performCommand(cmd);
        sender.sendMessage(fullMsg("admin.sudo.success", target.getName(), cmd));
    }

    private void handleInv(CommandSender sender, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(fullMsg("common.only-player"));
            return;
        }
        if (args.length < 3) {
            sender.sendMessage(fullMsg("admin.inv.usage"));
            return;
        }
        Player target = Bukkit.getPlayer(args[2]);
        if (target == null) {
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[2]));
            return;
        }
        p.openInventory(target.getInventory());
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

    private void handleSit(Player player) {
        cancelPoseTask(player.getUniqueId());
        // 使用 ArmorStand 让玩家坐下
        org.bukkit.entity.ArmorStand seat = player.getWorld().spawn(
            player.getLocation().subtract(0, 0.2, 0), org.bukkit.entity.ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setSmall(true);
                stand.setMarker(true);
                stand.setInvulnerable(true);
            });
        seat.addPassenger(player);
        player.sendMessage(prefix() + configuration.messages().sitDown());

        BukkitTask monitorTask = Bukkit.getScheduler().runTaskTimer(context.plugin(), () -> {
            if (!seat.isValid() || seat.getPassengers().isEmpty()) {
                seat.remove();
                cancelPoseTask(player.getUniqueId());
            }
        }, 20L, 20L);
        poseMonitorTasks.put(player.getUniqueId(), monitorTask);
    }

    private void handleLay(Player player) {
        cancelPoseTask(player.getUniqueId());
        // 使用 ArmorStand 模拟躺下姿势
        org.bukkit.entity.ArmorStand bed = player.getWorld().spawn(
            player.getLocation().subtract(0, 0.2, 0), org.bukkit.entity.ArmorStand.class, stand -> {
                stand.setVisible(false);
                stand.setGravity(false);
                stand.setSmall(true);
                stand.setMarker(true);
                stand.setInvulnerable(true);
            });
        bed.addPassenger(player);
        player.sendMessage(prefix() + configuration.messages().layDown());

        BukkitTask monitorTask = Bukkit.getScheduler().runTaskTimer(context.plugin(), () -> {
            if (!bed.isValid() || bed.getPassengers().isEmpty()) {
                bed.remove();
                cancelPoseTask(player.getUniqueId());
            }
        }, 20L, 20L);
        poseMonitorTasks.put(player.getUniqueId(), monitorTask);
    }

    // ─── Help / Status / Reload ───

    private void sendHelp(CommandSender sender, String label, String alias) {
        String cmd = "/" + label + " " + alias;
        sender.sendMessage(fullMsg("help.title"));
        sender.sendMessage(ChatColor.GRAY + messages().get("help.player-manager"));
        sender.sendMessage(fullMsg("help.toggle-status", label, alias));
        sender.sendMessage(fullMsg("help.gamemode", label, alias));
        sender.sendMessage(fullMsg("help.speed", label, alias));
        sender.sendMessage(fullMsg("help.items", label, alias));
        sender.sendMessage(fullMsg("help.containers", label, alias));
        sender.sendMessage(ChatColor.GRAY + messages().get("help.teleport-header"));
        sender.sendMessage(fullMsg("help.home", label, alias));
        sender.sendMessage(fullMsg("help.warp", label, alias));
        sender.sendMessage(fullMsg("help.teleport", label, alias));
        sender.sendMessage(ChatColor.GRAY + messages().get("help.world-header"));
        sender.sendMessage(fullMsg("help.time", label, alias));
        sender.sendMessage(fullMsg("help.weather", label, alias));
        sender.sendMessage(ChatColor.GRAY + messages().get("help.security-header"));
        sender.sendMessage(fullMsg("help.ban", label, alias));
        sender.sendMessage(fullMsg("help.admin", label, alias));
        sender.sendMessage(ChatColor.GRAY + messages().get("help.interact-header"));
        sender.sendMessage(fullMsg("help.sit-lay", label, alias));
    }

    private void sendStatus(CommandSender sender) {
        sender.sendMessage(fullMsg("status.title"));
        String runningState = playerService == null ? messages().get("status.stopped") : messages().get("status.running");
        sender.sendMessage(fullMsg("status.service", runningState));
        if (configuration != null) {
            sender.sendMessage(fullMsg("status.storage", configuration.storage().dialect().configKey()));
            sender.sendMessage(fullMsg("status.afk-timeout", configuration.player().afkTimeout()));
            sender.sendMessage(fullMsg("status.teleport-delay", configuration.teleport().teleportDelay()));
            sender.sendMessage(fullMsg("status.max-homes", configuration.teleport().maxHomes()));
        }
    }

    private void reload(CommandSender sender) {
        try {
            onReload();
            sender.sendMessage(fullMsg("reload.success"));
        } catch (Exception e) {
            sender.sendMessage(fullMsg("reload.failed", e.getMessage()));
        }
    }

    // ─── Utilities ───

    private Player resolveTarget(CommandSender sender, String[] args, int index, Player fallback) {
        if (args.length > index) {
            Player target = Bukkit.getPlayer(args[index]);
            if (target != null) return target;
            sender.sendMessage(prefix() + configuration.messages().playerNotFound().replace("{player}", args[index]));
        }
        return fallback;
    }

    private void requirePlayer(CommandSender sender, java.util.function.Consumer<Player> action) {
        if (sender instanceof Player p) {
            action.accept(p);
        } else {
            sender.sendMessage(fullMsg("common.only-player"));
        }
    }

    private void requirePermission(CommandSender sender, String permission, Runnable action) {
        if (sender.hasPermission(permission)) {
            action.run();
            return;
        }
        sender.sendMessage(prefix() + configuration.messages().noPermission());
    }

    private void handleRequireArg(CommandSender sender, String[] args, int index, String name,
                                   java.util.function.Consumer<String> action) {
        if (args.length <= index || args[index].isBlank()) {
            sender.sendMessage(fullMsg("admin.require-arg", name));
            return;
        }
        action.accept(args[index]);
    }

    private static String arg(String[] args, int index, String def) {
        return args.length > index && !args[index].isBlank() ? args[index] : def;
    }

    private static String joinArgs(String[] args, int from, String def) {
        if (args.length <= from) return def;
        StringBuilder sb = new StringBuilder();
        for (int i = from; i < args.length; i++) {
            if (sb.length() > 0) sb.append(' ');
            sb.append(args[i]);
        }
        return sb.length() > 0 ? sb.toString() : def;
    }

    private static GameMode parseGameMode(String s) {
        return switch (s.toLowerCase(Locale.ROOT)) {
            case "0", "survival", "s" -> GameMode.SURVIVAL;
            case "1", "creative", "c" -> GameMode.CREATIVE;
            case "2", "adventure", "a" -> GameMode.ADVENTURE;
            case "3", "spectator", "sp" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    private static long parseDuration(String s) {
        try {
            String lower = s.toLowerCase(Locale.ROOT);
            if (lower.endsWith("d")) return Long.parseLong(lower.replace("d", "")) * 86400000L;
            if (lower.endsWith("h")) return Long.parseLong(lower.replace("h", "")) * 3600000L;
            if (lower.endsWith("m")) return Long.parseLong(lower.replace("m", "")) * 60000L;
            if (lower.endsWith("s")) return Long.parseLong(lower.replace("s", "")) * 1000L;
            return Long.parseLong(lower) * 1000L;
        } catch (NumberFormatException e) {
            return 3600000L; // 默认 1 小时
        }
    }

    private static String formatTime(long timestamp) {
        java.time.Instant instant = java.time.Instant.ofEpochMilli(timestamp);
        return java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")
            .withZone(java.time.ZoneId.systemDefault()).format(instant);
    }

    private List<String> filterOnline(String prefix) {
        List<String> names = new ArrayList<>();
        for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
        return filter(names, prefix);
    }

    // 获取 mute 记录供外部模块联动
    public EssentialsRepository getRepository() {
        return repository;
    }

    public PlayerManagementService getPlayerService() {
        return playerService;
    }

    private String prefix() {
        if (messages() != null) return messages().get("prefix");
        return ChatColor.DARK_AQUA + "◆ " + ChatColor.GOLD + "ArcartXSuite " + ChatColor.GRAY + "| " + ChatColor.RESET;
    }

    private String fullMsg(String key, Object... args) {
        if (messages() == null) return "";
        return messages().get("prefix") + messages().get(key, args);
    }

    private List<String> filter(List<String> candidates, String input) {
        List<String> result = new ArrayList<>();
        String normalized = input == null ? "" : input.toLowerCase(Locale.ROOT);
        for (String candidate : candidates) {
            if (candidate.toLowerCase(Locale.ROOT).startsWith(normalized)) {
                result.add(candidate);
            }
        }
        return result;
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
